package com.researchspace.datacite.client;

import com.researchspace.datacite.model.DataCiteDoi;
import com.researchspace.datacite.model.DataCiteDoiRequestWrapper;
import java.net.URI;
import java.util.Collections;
import org.apache.commons.lang.Validate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Base64Utils;
import org.springframework.web.client.RestTemplate;

public class DataCiteClientImpl implements DataCiteClient {

    private URI dataciteDoisApiURI;

    private String basicAuthenticationHeader;
    
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
        this.repositoryPrefix = repositoryPrefix;
    }

    @Override
    public DataCiteDoi retrieveDoi(String doiId) {
        URI uri = dataciteDoisApiURI.resolve("/dois/" + doiId);
        return restTemplate.exchange(uri, HttpMethod.GET, new HttpEntity<>(getHttpHeaders()),
                DataCiteDoiRequestWrapper.class).getBody().getData();
    }

    @Override
    public DataCiteDoi registerDoi(DataCiteDoi doiToCreate) {
        URI uri = dataciteDoisApiURI.resolve("/dois");
        DataCiteDoiRequestWrapper doiRequest = new DataCiteDoiRequestWrapper();
        doiToCreate.getAttributes().setPrefix(repositoryPrefix);
        doiRequest.setData(doiToCreate);
        RequestEntity creationRequest = new RequestEntity<>(doiRequest, getHttpHeaders(), 
                HttpMethod.POST, uri);
        return restTemplate.exchange(creationRequest, DataCiteDoiRequestWrapper.class).getBody().getData();
    }

    @Override
    public DataCiteDoi updateDoi(DataCiteDoi doiUpdate) {
        URI uri = dataciteDoisApiURI.resolve("/dois/" + doiUpdate.getId());
        DataCiteDoiRequestWrapper doiRequest = new DataCiteDoiRequestWrapper();
        doiRequest.setData(doiUpdate);
        RequestEntity creationRequest = new RequestEntity<>(doiRequest, getHttpHeaders(), HttpMethod.PUT, uri);
        ResponseEntity<DataCiteDoiRequestWrapper> response = restTemplate.exchange(creationRequest, DataCiteDoiRequestWrapper.class);
        return response.getBody().getData();
    }

    @Override
    public boolean deleteDoi(String doiId) {
        URI uri = dataciteDoisApiURI.resolve("/dois/" + doiId);
        RequestEntity creationRequest = new RequestEntity<>(null, getHttpHeaders(), HttpMethod.DELETE, uri);
        ResponseEntity<DataCiteDoiRequestWrapper> response = restTemplate.exchange(creationRequest, DataCiteDoiRequestWrapper.class);
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
    
    
    private HttpHeaders getHttpHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        headers.add("authorization", basicAuthenticationHeader);
        return headers;
    }
    
}
