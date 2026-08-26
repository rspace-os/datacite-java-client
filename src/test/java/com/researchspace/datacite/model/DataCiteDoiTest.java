package com.researchspace.datacite.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.util.List;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;

public class DataCiteDoiTest {

    @Test
    public void convertJsonToModel() throws IOException {

        File file = new File("src/test/resources/TestResources/doi_draft_example.json");
        String jsonString = FileUtils.readFileToString(file, "UTF-8");
        assertNotNull(jsonString);
        
        ObjectMapper mapper = new ObjectMapper();
        DataCiteDoiRequestWrapper convertedDoiResponse = mapper.readValue(jsonString, DataCiteDoiRequestWrapper.class);
        assertEquals("10.82316/m906-wb49", convertedDoiResponse.getData().getId());
        assertEquals("FOS: Computer and information sciences", convertedDoiResponse.getData().getAttributes().getSubjects().get(0).getSubject());
        assertEquals("Fields of Science and Technology (FOS)", convertedDoiResponse.getData().getAttributes().getSubjects().get(0).getSubjectScheme());
        assertEquals("http://www.oecd.org/science/inno/38235147.pdf", convertedDoiResponse.getData().getAttributes().getSubjects().get(0).getSchemeUri());
        assertEquals("RSpace Test Description", convertedDoiResponse.getData().getAttributes().getDescriptions().get(0).getDescription());
        assertEquals("https://raw.githubusercontent.com/rspace-os/rspace-marketing-resources/main/rspace_logo_300x100.png",
            convertedDoiResponse.getData().getAttributes().getLandingPage().getUrl());
    }
    

    @Test
    public void relatedIdentifiersRoundTrip() throws Exception {
        DataCiteDoiAttributes attributes = new DataCiteDoiAttributes();
        attributes.setRelatedIdentifiers(
            List.of(
                new DataCiteDoiAttributes.RelatedIdentifier(
                    "IsDescribedBy",
                    "https://rspace.example.com/globalId/IN114",
                    "URL",
                    "Measurement Technique")));

        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(attributes);

        // Asserted on the parsed tree rather than on substrings of the JSON: the property names are
        // the wire contract DataCite reads, so they have to be pinned, but field order and
        // whitespace must not make the test fail. Reading back into the model would not pin them,
        // since a renamed property still round-trips through Jackson symmetrically.
        JsonNode wire = mapper.readTree(json).at("/relatedIdentifiers/0");
        assertEquals("IsDescribedBy", wire.at("/relationType").asText());
        assertEquals(
            "https://rspace.example.com/globalId/IN114", wire.at("/relatedIdentifier").asText());
        assertEquals("URL", wire.at("/relatedIdentifierType").asText());
        assertEquals("Measurement Technique", wire.at("/relationTypeInformation").asText());

        DataCiteDoiAttributes parsed = mapper.readValue(json, DataCiteDoiAttributes.class);
        assertEquals("IsDescribedBy", parsed.getRelatedIdentifiers().get(0).getRelationType());
        assertEquals("URL", parsed.getRelatedIdentifiers().get(0).getRelatedIdentifierType());
    }

    @Test
    public void relatedIdentifiersTolerateUnknownResponseProperties() throws Exception {
        // shape taken from a real DataCite REST response (RSDEV-1253 attachment)
        String response =
            "{\"relatedIdentifiers\":[{\"schemeUri\":null,\"schemeType\":null,"
                + "\"relationType\":\"IsDescribedBy\","
                + "\"relatedIdentifier\":\"http://localhost:8080/globalId/IN114\","
                + "\"resourceTypeGeneral\":null,\"relatedIdentifierType\":\"URL\","
                + "\"relatedMetadataScheme\":null,"
                + "\"relationTypeInformation\":\"Measurement Technique\"}]}";
        DataCiteDoiAttributes parsed =
            new ObjectMapper().readValue(response, DataCiteDoiAttributes.class);
        assertEquals(
            "Measurement Technique",
            parsed.getRelatedIdentifiers().get(0).getRelationTypeInformation());
    }

}
