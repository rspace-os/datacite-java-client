package com.researchspace.datacite.client;

import com.researchspace.datacite.model.DataCiteConnectionException;
import com.researchspace.datacite.model.DataCiteDoi;
import com.researchspace.datacite.model.DataCiteDoiRequestWrapper;
import java.net.URI;
import java.net.URLEncoder;
import java.time.Duration;
import java.util.Base64;
import java.util.Collections;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

public class DataCiteClientImpl implements DataCiteClient {

    private URI dataciteDoisApiURI;

    private String basicAuthenticationHeader;
    
    private String username;
    
    private String repositoryPrefix;

    private RestTemplate restTemplate;

    /**
     * A DOI: the mandatory {@code 10.} prefix, a numeric registrant, then a non-empty suffix.
     *
     * <p>Deliberately permissive about the suffix. The DOI handbook allows almost any printable
     * character there, so an allow-list of the characters DataCite happens to generate would refuse
     * legal identifiers - {@code +} and {@code =} among them - and this is a shared client, so that
     * would stop a consumer retrieving or deleting a DOI it already owns. What must be refused is
     * not "unusual" but "changes which endpoint this addresses", which is what the two checks below
     * cover.
     */
    private static final Pattern DOI_ID = Pattern.compile("10\\.[0-9]+(\\.[0-9]+)*/.+");

    /**
     * Constructs that would move the request somewhere else, or let something downstream re-read it
     * as though they had: the query and fragment delimiters, a percent sign (which could smuggle an
     * encoded delimiter past this check), a backslash, and any whitespace or control character.
     */
    private static final Pattern ENDPOINT_CHANGING = Pattern.compile("[?#%\\\\]|\\s|\\p{Cntrl}");

    /**
     * Checks a DOI id before it is interpolated into the request path, and returns it unchanged.
     *
     * <p>The id is not trusted input: it comes from stored RSpace data and from DataCite responses.
     * {@code URI.resolve} normalises {@code ..} segments and treats {@code ?} and {@code #} as
     * delimiters, so an id carrying any of them silently addresses a different endpoint than the
     * caller asked for. That matters most on {@code updateDoi}, because a DataCite PUT is an
     * upsert, so a redirected write can overwrite or mint a DOI under the repository prefix using
     * the deployment's own credentials. Percent-encoding the value instead is not an option: the
     * {@code /} between prefix and suffix is a real path separator and must survive.
     *
     * <p>Encoding is not the fix, so this rejects rather than sanitises: a malformed id means the
     * caller's data is wrong, and guessing which record was meant would be worse than failing.
     */
    private String checkedDoiPath(String doiId) {
        if (doiId == null
                || !DOI_ID.matcher(doiId).matches()
                || ENDPOINT_CHANGING.matcher(doiId).find()
                || hasNonCanonicalSegment(doiId)) {
            throw new IllegalArgumentException(
                "Not a DOI, so it will not be put in a request path: '" + doiId
                    + "'. Expected the form 10.<prefix>/<suffix>.");
        }
        return doiId;
    }

    /**
     * Whether any path segment of the id is empty, {@code .} or {@code ..} - the three that {@code
     * URI.resolve}, or a server or proxy normalising the path, collapses away to address something
     * other than what the caller wrote.
     *
     * <p>Segments, not substrings. Consecutive dots are perfectly legal inside a DOI suffix
     * ({@code 10.1234/foo..bar} is a real shape) and cannot move up a level, so rejecting those
     * refused valid identifiers while adding no safety.
     */
    private static boolean hasNonCanonicalSegment(String doiId) {
        for (String segment : doiId.split("/", -1)) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)) {
                return true;
            }
        }
        return false;
    }

    /**
     * A caller can be a user waiting on a save, so a DataCite that accepts a connection and then
     * stops talking must not hold that request open indefinitely. Spring's default is no timeout at
     * all on either. The values match the B2INST connector in rspace-web, which bounds the same
     * kind of call for the same reason.
     */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

    private static SimpleClientHttpRequestFactory timeoutBoundedRequestFactory() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        return requestFactory;
    }

    /**
     * @param dataciteApiURI url to datacite api, e.g. "https://api.test.datacite.org/"
     * @param username datacite username to use for creating/updating DOIs
     * @param password datacite user's password
     * @param repositoryPrefix prefix to use when creating new DOIs, e.g. "10.82316"
     */
    public DataCiteClientImpl(URI dataciteApiURI, String username, String password, String repositoryPrefix) {
        Validate.notNull(dataciteApiURI);
        this.dataciteDoisApiURI = dataciteApiURI;
        // Buffer request bodies so JSON POSTs carry a Content-Length header. Spring 6.1+
        // streams bodies of unknown length as chunked, which DataCite rejects.
        this.restTemplate = new RestTemplate(
            new BufferingClientHttpRequestFactory(timeoutBoundedRequestFactory()));
        this.basicAuthenticationHeader = String.format("Basic %s", Base64.getEncoder().encodeToString((username + ":" + password).getBytes()));
        this.username = username;
        this.repositoryPrefix = repositoryPrefix;
    }

    @Override
    public DataCiteDoi retrieveDoi(String doiId) {
        URI uri = dataciteDoisApiURI.resolve("/dois/" + checkedDoiPath(doiId) + "/?affiliation=true");
        return restTemplate.exchange(uri, HttpMethod.GET, new HttpEntity<>(getHttpHeaders()),
                DataCiteDoiRequestWrapper.class).getBody().getData();
    }

    @Override
    public DataCiteDoi registerDoi(DataCiteDoi doiToCreate) {
        URI uri = dataciteDoisApiURI.resolve("/dois/?affiliation=true");
        DataCiteDoiRequestWrapper doiRequest = new DataCiteDoiRequestWrapper();
        doiToCreate.getAttributes().setPrefix(repositoryPrefix);
        doiRequest.setData(doiToCreate);
        RequestEntity creationRequest = new RequestEntity<>(doiRequest, getHttpHeaders(), HttpMethod.POST, uri);
        return callDataCiteWithDoiRequest(creationRequest).getBody().getData();
    }

    /** Methods that calls DataCite API and wraps connection errors into custom exception */
    private ResponseEntity<DataCiteDoiRequestWrapper> callDataCiteWithDoiRequest(RequestEntity creationRequest) {
        ResponseEntity<DataCiteDoiRequestWrapper> response = null;
        try {
            response = restTemplate.exchange(creationRequest, DataCiteDoiRequestWrapper.class);

        } catch (HttpServerErrorException.InternalServerError e) {
            throw new DataCiteConnectionException("InternalServerError when connecting to DataCite Members API. Is repository prefix correct?", e);
        } catch (HttpClientErrorException.NotFound e) {
            throw new DataCiteConnectionException("NotFound error when connecting to DataCite Members API. Are connection credentials correct?", e);
        } catch (Exception e) {
            throw new DataCiteConnectionException("Unknown problem with connecting to DataCite API.", e);
        }
        return response;
        
    }

    @Override
    public DataCiteDoi updateDoi(DataCiteDoi doiUpdate) {
        URI uri = dataciteDoisApiURI.resolve("/dois/" + checkedDoiPath(doiUpdate.getId()) + "/?affiliation=true");
        DataCiteDoiRequestWrapper doiRequest = new DataCiteDoiRequestWrapper();
        doiRequest.setData(doiUpdate);
        RequestEntity creationRequest = new RequestEntity<>(doiRequest, getHttpHeaders(), HttpMethod.PUT, uri);
        return callDataCiteWithDoiRequest(creationRequest).getBody().getData();
    }

    @Override
    public boolean deleteDoi(String doiId) {
        URI uri = dataciteDoisApiURI.resolve("/dois/" + checkedDoiPath(doiId));
        RequestEntity creationRequest = new RequestEntity<>(null, getHttpHeaders(), HttpMethod.DELETE, uri);
        ResponseEntity<DataCiteDoiRequestWrapper> response = callDataCiteWithDoiRequest(creationRequest);
        return HttpStatus.NO_CONTENT.equals(response.getStatusCode());
    }
    
    @Override
    public DataCiteDoi publishDoi(DataCiteDoi doiToPublish) {
        doiToPublish.getAttributes().setEvent("publish");
        return updateDoi(doiToPublish);
    }

    @Override
    public DataCiteDoi retractDoi(DataCiteDoi doiToRetract) {
        doiToRetract.getAttributes().setEvent("hide");
        return updateDoi(doiToRetract);
    }

    @Override
    public boolean testConnectionToDataCite() {
        
        /* first let's try connecting to public DataCite API, to validate the URL */
        try {
            URI uri = dataciteDoisApiURI.resolve("/heartbeat");
            restTemplate.exchange(uri, HttpMethod.GET, new HttpEntity<>(getHttpHeaders()), String.class).getBody();
        } catch (Exception e) {
            throw new DataCiteConnectionException("Problem with checking status of DataCite server. Is DataCite URL correct?", e);
        }
        
        /* let's try finding the provided prefix, which can be done with unauthorized user */
        try {
            URI uri = dataciteDoisApiURI.resolve("/client-prefixes?" 
                    + "client-id=" + URLEncoder.encode(username) 
                    + "&prefix-id=" + URLEncoder.encode(repositoryPrefix));
            String prefixResponseBody = restTemplate.exchange(uri, HttpMethod.GET, new HttpEntity<>(getHttpHeaders()), String.class).getBody();
            if (StringUtils.isEmpty(prefixResponseBody) || prefixResponseBody.contains("\"total\":0")) {
                throw new DataCiteConnectionException("Cannot find repository prefix for provided client-id and prefix-id. Is repositoryPrefix correct?", null);
            }
                
        } catch (RestClientException e) {
            throw new DataCiteConnectionException("Problem with checking repository prefix. Are DataCite URL and repositoryPrefix correct?", e);
        }
            
        /* next let's try to use authenticated DataCite API, to validate credentials */
        try {    
            /* there is no obvious members API endpoint to call for testing the connection, so let's
               try register an empty DOI without repostiory prefix which, if credentials are good, will return 403 for unauthenticated */
            URI uri = dataciteDoisApiURI.resolve("/dois");
            DataCiteDoiRequestWrapper doiRequest = new DataCiteDoiRequestWrapper();
            doiRequest.setData(new DataCiteDoi());
            RequestEntity creationRequest = new RequestEntity<>(doiRequest, getHttpHeaders(), HttpMethod.POST, uri);
            callDataCiteWithDoiRequest(creationRequest).getBody().getData();

        } catch (DataCiteConnectionException e) {
            if (e.getCause() instanceof HttpClientErrorException.Forbidden) {
                // that's expected - we didn't provide repository prefix, which for correct credentials results in Forbidden exception
                return true;
            } else {
                throw e; // rethrow
            }
        }
        return false;
    }

    private HttpHeaders getHttpHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        headers.add("authorization", basicAuthenticationHeader);
        return headers;
    }
    
}
