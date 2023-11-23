package com.researchspace.datacite.client;

import com.researchspace.datacite.model.DataCiteConnectionException;
import com.researchspace.datacite.model.DataCiteDoi;
import com.researchspace.datacite.model.DataCiteDoiRequestWrapper;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.util.Collections;

import lombok.SneakyThrows;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.Validate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Base64Utils;
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
     * @param dataciteApiURI url to datacite api, e.g. "https://api.test.datacite.org/"
     * @param username datacite username to use for creating/updating DOIs
     * @param password datacite user's password
     * @param repositoryPrefix prefix to use when creating new DOIs, e.g. "10.82316"
     */
    public DataCiteClientImpl(URI dataciteApiURI, String username, String password, String repositoryPrefix) {
        Validate.notNull(dataciteApiURI);
        this.dataciteDoisApiURI = dataciteApiURI;
        this.restTemplate = new RestTemplate();
        this.basicAuthenticationHeader = String.format("Basic %s", new String(Base64Utils.encode((username + ":" + password).getBytes())));
        this.username = username;
        this.repositoryPrefix = repositoryPrefix;
    }

    @Override
    public DataCiteDoi retrieveDoi(String doiId) {
        URI uri = dataciteDoisApiURI.resolve("/dois/" + doiId+"/?affiliation=true");
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
        URI uri = dataciteDoisApiURI.resolve("/dois/" + doiUpdate.getId()+"/?affiliation=true");
        DataCiteDoiRequestWrapper doiRequest = new DataCiteDoiRequestWrapper();
        doiRequest.setData(doiUpdate);
        RequestEntity creationRequest = new RequestEntity<>(doiRequest, getHttpHeaders(), HttpMethod.PUT, uri);
        return callDataCiteWithDoiRequest(creationRequest).getBody().getData();
    }

    @Override
    public boolean deleteDoi(String doiId) {
        URI uri = dataciteDoisApiURI.resolve("/dois/" + doiId);
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
