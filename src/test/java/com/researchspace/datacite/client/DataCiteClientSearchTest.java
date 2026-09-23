package com.researchspace.datacite.client;

import static org.junit.jupiter.api.Assertions.assertTrue;
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
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

/**
 * Search goes through the client's own RestTemplate, so a MockRestServiceServer can observe it. It is
 * a RestTemplate of its own, because a search gets a longer read timeout than every other call
 * (RSDEV-1522).
 */
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

    private static final String EMPTY_RESPONSE = "{\"data\":[],\"meta\":{\"total\":0}}";

    private static final String USER = "user";
    private static final String PASSWORD = "secret";

    /** Built from the same credentials the client is given, so the assertion shows what it checks. */
    private static final String EXPECTED_AUTH = "Basic " + Base64.getEncoder()
        .encodeToString((USER + ":" + PASSWORD).getBytes(StandardCharsets.UTF_8));

    private DataCiteClientImpl client;
    private MockRestServiceServer server;

    @BeforeEach
    public void setUp() throws URISyntaxException {
        client = new DataCiteClientImpl(
            new URI("https://api.test.datacite.org"), USER, PASSWORD, "10.82316");
        server = MockRestServiceServer.bindTo(
            (RestTemplate) ReflectionTestUtils.getField(client, "searchRestTemplate")).build();
    }

    /**
     * The point of the separate template: a lookup searches on a leading wildcard, which costs
     * DataCite tens of seconds, while registering or publishing a DOI has no reason to be slow and
     * holds a database connection in rspace-web while it runs (RSDEV-1506).
     */
    @Test
    public void searchesGetALongerReadTimeoutThanEveryOtherCall() throws URISyntaxException {
        // its own client: setUp() swaps the mock server's factory into the one under test, which
        // would hide the timeouts this is about
        DataCiteClientImpl unmocked = new DataCiteClientImpl(
            new URI("https://api.test.datacite.org"), USER, PASSWORD, "10.82316");
        assertTrue(
            readTimeoutOf(unmocked, "searchRestTemplate") > readTimeoutOf(unmocked, "restTemplate"),
            "a search must be allowed to run longer than a registration");
    }

    private int readTimeoutOf(DataCiteClientImpl target, String templateField) {
        RestTemplate template = (RestTemplate) ReflectionTestUtils.getField(target, templateField);
        Object buffering = template.getRequestFactory();
        Object simple = ReflectionTestUtils.getField(buffering, "requestFactory");
        return (int) ReflectionTestUtils.getField(simple, "readTimeout");
    }

    @Test
    public void searchDoisQueriesInstrumentsWithCredentialsAndParsesTypedContributors() {
        server.expect(requestTo(SEARCH_URL))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("authorization", EXPECTED_AUTH))
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
        server.verify();
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

    /**
     * A '+' is legal in a query component, so Spring's QUERY_PARAM encoding leaves it alone and the
     * receiver decodes it as a space: searching "C++" silently searched "C  ". Only expansion of a
     * URI template variable encodes it, which is why the value is not concatenated into the builder.
     */
    @Test
    public void searchDoisEncodesAPlusSoItIsNotReadAsASpace() {
        server.expect(requestTo(
                "https://api.test.datacite.org/dois?query=C%2B%2B&resource-type-id=instrument"
                    + "&page%5Bsize%5D=50&affiliation=true"))
            .andRespond(withSuccess(EMPTY_RESPONSE, MediaType.APPLICATION_JSON));

        client.searchDois("C++", "instrument", null, 50);
        server.verify();
    }

    /** The encoding is the whole defence: a query may not add or override a request parameter. */
    @Test
    public void searchDoisCannotInjectAnotherRequestParameter() {
        server.expect(requestTo(
                "https://api.test.datacite.org/dois"
                    + "?query=a%26page%5Bsize%5D%3D1000%26state%3Ddraft"
                    + "&resource-type-id=instrument&page%5Bsize%5D=5&affiliation=true"))
            .andRespond(withSuccess(EMPTY_RESPONSE, MediaType.APPLICATION_JSON));

        client.searchDois("a&page[size]=1000&state=draft", "instrument", null, 5);
        server.verify();
    }

    /**
     * Blank means "no filter" to DataCite, not "no results": an unguarded blank query returns the
     * whole registry, and an unguarded resource type widens past instruments. Both are caller bugs,
     * so they fail here rather than costing a request.
     */
    @Test
    public void searchDoisRefusesABlankQueryInsteadOfMatchingTheWholeRegistry() {
        assertThrows(IllegalArgumentException.class,
            () -> client.searchDois("   ", "instrument", "findable", 50));
        assertThrows(IllegalArgumentException.class,
            () -> client.searchDois(null, "instrument", "findable", 50));
        server.verify();
    }

    @Test
    public void searchDoisRefusesABlankResourceType() {
        assertThrows(IllegalArgumentException.class,
            () -> client.searchDois("Zeiss", "  ", "findable", 50));
        server.verify();
    }

    /** An empty body must still honour the non-null defaults the result type advertises. */
    @Test
    public void searchDoisNeverReturnsNull() {
        server.expect(requestTo(
                "https://api.test.datacite.org/dois?query=Zeiss&resource-type-id=instrument"
                    + "&page%5Bsize%5D=50&affiliation=true"))
            .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

        DataCiteDoiSearchResult result = client.searchDois("Zeiss", "instrument", null, 50);

        assertEquals(0, result.getMeta().getTotal());
        assertEquals(0, result.getData().size());
        server.verify();
    }

    /**
     * The other structural arguments are rejected locally, so this one must be too: sending it and
     * wrapping DataCite's refusal reports a caller's own mistake as a connection failure.
     */
    @Test
    public void searchDoisRefusesANegativePageSize() {
        assertThrows(IllegalArgumentException.class,
            () -> client.searchDois("Zeiss", "instrument", "findable", -1));
    }

    /** Zero is not an error to DataCite: it is how you ask for the count alone. */
    @Test
    public void searchDoisPassesAZeroPageSizeThroughAsACountOnlyQuery() {
        server.expect(requestTo(
                "https://api.test.datacite.org/dois?query=Zeiss&resource-type-id=instrument"
                    + "&page%5Bsize%5D=0&affiliation=true"))
            .andRespond(withSuccess("{\"data\":[],\"meta\":{\"total\":41}}",
                MediaType.APPLICATION_JSON));

        DataCiteDoiSearchResult result = client.searchDois("Zeiss", "instrument", null, 0);

        assertEquals(41, result.getMeta().getTotal());
        assertEquals(0, result.getData().size());
        server.verify();
    }
}
