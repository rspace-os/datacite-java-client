package com.researchspace.datacite.client;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.datacite.model.DataCiteConnectionException;
import com.researchspace.datacite.model.DataCiteDoi;
import com.researchspace.datacite.model.DataCiteDoiAttributes;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * A DOI id is interpolated straight into the request path. It reaches this client from stored
 * RSpace data and from DataCite responses, so it is not trusted input, and {@code URI.resolve}
 * normalises {@code ..} segments and treats {@code ?} and {@code #} as delimiters. An id carrying
 * any of those therefore addresses a different endpoint than the caller asked for, and because a
 * DataCite PUT is an upsert that is a write, made with the deployment's credentials.
 *
 * <p>Offline by design: the client points at a port nothing is listening on, so a request that is
 * not rejected up front fails as a connection error instead, which is a different exception and so
 * still a failing test.
 */
public class DoiIdPathValidationTest {

    private DataCiteClientImpl offlineClient() {
        return new DataCiteClientImpl(
            URI.create("http://127.0.0.1:1/"), "user", "password", "10.82316");
    }

    private DataCiteDoi doiWithId(String id) {
        DataCiteDoi doi = new DataCiteDoi();
        doi.setId(id);
        return doi;
    }

    @Test
    public void updateRejectsAnIdThatWouldEscapeTheDoisPath() {
        assertThrows(IllegalArgumentException.class,
            () -> offlineClient().updateDoi(doiWithId("10.82316/../../client-prefixes")));
    }

    /** Publish and retract are the same call carrying an event, so they inherit the guard. */
    @Test
    public void publishAndRetractRejectAnEscapingIdToo() {
        DataCiteDoi doi = doiWithId("10.82316/../../client-prefixes");
        doi.setAttributes(new DataCiteDoiAttributes());
        assertThrows(IllegalArgumentException.class, () -> offlineClient().publishDoi(doi));
        assertThrows(IllegalArgumentException.class, () -> offlineClient().retractDoi(doi));
    }

    @Test
    public void deleteRejectsAnIdThatWouldEscapeTheDoisPath() {
        assertThrows(IllegalArgumentException.class,
            () -> offlineClient().deleteDoi("10.82316/../../client-prefixes"));
    }

    @Test
    public void retrieveRejectsAnIdThatWouldEscapeTheDoisPath() {
        assertThrows(IllegalArgumentException.class,
            () -> offlineClient().retrieveDoi("10.82316/../../client-prefixes"));
    }

    /**
     * The trailing {@code /?affiliation=true} means a {@code ?} in the id turns the intended query
     * into part of the caller's, and a {@code #} truncates the path at a fragment.
     */
    @Test
    public void anIdCarryingQueryOrFragmentDelimitersIsRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> offlineClient().deleteDoi("10.82316/abcd-1234?confirm=false"));
        assertThrows(IllegalArgumentException.class,
            () -> offlineClient().deleteDoi("10.82316/abcd-1234#frag"));
    }

    @Test
    public void anIdThatIsNotADoiAtAllIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> offlineClient().deleteDoi(null));
        assertThrows(IllegalArgumentException.class, () -> offlineClient().deleteDoi(""));
        assertThrows(IllegalArgumentException.class,
            () -> offlineClient().deleteDoi("client-prefixes"));
    }

    /**
     * The guard must not be so strict that it rejects real DOIs. Reaching the network is the proof
     * it let the id through: nothing listens on the port, so a permitted id fails as a connection
     * error, which is a different exception from the rejection above.
     */
    @Test
    public void realDoiShapesAreAllowedThrough() {
        for (String legitimate : new String[] {
            "10.82316/9w24-z012",      // an RSpace-minted DataCite test DOI
            "10.5281/zenodo.1234567",  // dots in the suffix
            "10.1000.10/abc-123",      // a sub-divided registrant
            "10.82316/a/b",            // a hierarchical suffix
            "10.1234/foo..bar",        // consecutive dots INSIDE a segment are not traversal
            "10.1234/....",            // likewise: one segment, however many dots
            "10.1234/foo+bar",         // + and = are legal DOI suffix characters
            "10.1234/x=y",
            "10.1234/a~b!c",
        }) {
            assertThrows(DataCiteConnectionException.class,
                () -> offlineClient().deleteDoi(legitimate),
                legitimate + " must be allowed through to the network");
        }
    }

    /** Only a complete {@code ..} segment is traversal, and it must still be refused. */
    @Test
    public void onlyACompleteDotDotSegmentIsTreatedAsTraversal() {
        for (String traversal : new String[] {
            "10.82316/../../client-prefixes",
            "10.82316/a/../b",
            "10.82316/..",
            "10.82316/a//b",           // an EMPTY segment normalises away just as "." does
            "10.82316//a",
        }) {
            assertThrows(IllegalArgumentException.class,
                () -> offlineClient().deleteDoi(traversal),
                traversal + " must be rejected");
        }
    }

    /**
     * The characters the DOI pattern excludes outright. Percent-encoding would let a caller smuggle
     * a delimiter past the check, and whitespace would make URI parsing throw somewhere less
     * explanatory than here.
     */
    @Test
    public void percentEncodingAndWhitespaceAreRejected() {
        for (String bad : new String[] {
            "10.82316/abcd%2F1234",
            "10.82316/abcd%20efgh",
            "10.82316/abcd 1234",
            "10.82316/abcd\t1234",
            "10.82316/ abcd-1234",
        }) {
            assertThrows(IllegalArgumentException.class,
                () -> offlineClient().deleteDoi(bad),
                bad + " must be rejected");
        }
    }

    /**
     * And it must not alter the path it passes. Captured off a real socket because the URI is built
     * inside the client and only the wire shows what was actually asked for.
     */
    @Test
    public void aPermittedDoiKeepsItsExactPathOnTheWire() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            server.setSoTimeout(10_000);
            List<String> requestLines = new ArrayList<>();
            Thread serverThread = new Thread(() -> {
                try (Socket socket = server.accept()) {
                    BufferedReader in = new BufferedReader(
                        new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                    String line;
                    while ((line = in.readLine()) != null && !line.isEmpty()) {
                        requestLines.add(line);
                    }
                    OutputStream out = socket.getOutputStream();
                    out.write(("HTTP/1.1 200 OK\r\n"
                        + "Content-Type: application/json\r\n"
                        + "Content-Length: 2\r\n"
                        + "Connection: close\r\n"
                        + "\r\n"
                        + "{}").getBytes(StandardCharsets.UTF_8));
                    out.flush();
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            });
            serverThread.start();

            DataCiteClientImpl client = new DataCiteClientImpl(
                URI.create("http://127.0.0.1:" + server.getLocalPort() + "/"),
                "user", "password", "10.82316");
            client.updateDoi(doiWithId("10.82316/9w24-z012"));
            serverThread.join(10_000);

            assertTrue(
                requestLines.stream().anyMatch(
                    l -> l.startsWith("PUT /dois/10.82316/9w24-z012/?affiliation=true ")),
                "unexpected request line: " + requestLines);
        }
    }
}
