package com.researchspace.datacite.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.researchspace.datacite.model.DataCiteConnectionException;
import com.researchspace.datacite.model.DataCiteDoiSearchResult;
import java.net.URI;
import java.net.URISyntaxException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;

/** Search goes through the client's own RestTemplate, so a MockRestServiceServer can observe it. */
public class DataCiteClientSearchTest {

    private static final String SEARCH_URL =
        "https://api.test.datacite.org/dois?query=Zeiss%20microscope&resource-type-id=instrument"
            + "&state=findable&page%5Bsize%5D=50&affiliation=true";

    private static final String SEARCH_RESPONSE = "{"
        + "\"data\":[{\"id\":\"10.15151/esrf-instr-gco8\",\"type\":\"dois\",\"attributes\":{"
        + "\"doi\":\"10.15151/esrf-instr-gco8\","
        + "\"titles\":[{\"title\":\"ID21 - X-ray Micro Spectroscopy Beamline\"}],"
        + "\"creators\":[{\"name\":\"European Synchrotron Radiation Facility\",\"nameType\":\"Organizational\"}],"
        + "\"contributors\":[{\"name\":\"European Synchrotron Radiation Facility\","
        + "\"nameType\":\"Organizational\",\"contributorType\":\"HostingInstitution\"}],"
        + "\"identifiers\":[{\"identifier\":\"ID21\",\"identifierType\":\"alias\"}],"
        + "\"types\":{\"resourceType\":\"Beamline\",\"resourceTypeGeneral\":\"Instrument\"},"
        + "\"url\":\"https://doi.esrf.fr/10.15151/ESRF-INSTR-GCO8/\",\"state\":\"findable\"}}],"
        + "\"meta\":{\"total\":63}}";

    private DataCiteClientImpl client;
    private MockRestServiceServer server;

    @BeforeEach
    public void setUp() throws URISyntaxException {
        client = new DataCiteClientImpl(
            new URI("https://api.test.datacite.org"), "user", "secret", "10.82316");
        server = MockRestServiceServer.bindTo(client.getRestTemplate()).build();
    }

    @Test
    public void searchDoisQueriesInstrumentsWithCredentialsAndParsesTypedContributors() {
        server.expect(requestTo(SEARCH_URL))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("authorization", "Basic dXNlcjpzZWNyZXQ="))
            .andRespond(withSuccess(SEARCH_RESPONSE, MediaType.APPLICATION_JSON));

        DataCiteDoiSearchResult result = client.searchDois("Zeiss microscope", "instrument", "findable", 50);

        assertEquals(63, result.getMeta().getTotal());
        assertEquals(1, result.getData().size());
        assertEquals("HostingInstitution",
            result.getData().get(0).getAttributes().getContributors().get(0).getContributorType());
        assertEquals("ID21",
            result.getData().get(0).getAttributes().getIdentifiers().get(0).getIdentifier());
        server.verify();
    }

    @Test
    public void searchDoisWrapsTransportErrors() {
        server.expect(requestTo(SEARCH_URL)).andRespond(withServerError());

        assertThrows(DataCiteConnectionException.class,
            () -> client.searchDois("Zeiss microscope", "instrument", "findable", 50));
    }

    @Test
    public void searchDoisOmitsTheStateParameterWhenNoStateIsAsked() {
        server.expect(requestTo(
                "https://api.test.datacite.org/dois?query=Zeiss&resource-type-id=instrument"
                    + "&page%5Bsize%5D=50&affiliation=true"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("{\"data\":[],\"meta\":{\"total\":0}}", MediaType.APPLICATION_JSON));

        assertEquals(0, client.searchDois("Zeiss", "instrument", null, 50).getMeta().getTotal());
        server.verify();
    }
}
