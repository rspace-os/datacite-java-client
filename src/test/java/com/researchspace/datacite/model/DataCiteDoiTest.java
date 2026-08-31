package com.researchspace.datacite.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    /**
     * DataCite tells "leave this property alone" from "clear it" by whether the key is present at
     * all: an explicit null clears exactly as [] does, and only an absent key preserves the
     * registered value (verified against api.test.datacite.org, August 2026). The NON_NULL
     * inclusion on DataCiteDoiAttributes is what lets a caller express the difference, so it is
     * pinned here rather than left to be re-discovered by losing someone's registered metadata.
     */
    @Test
    public void nullAttributesAreOmittedSoTheyDoNotClearRegisteredValues() throws IOException {
        DataCiteDoi doi = new DataCiteDoi();
        doi.setId("10.1234/abcd-efgh");
        doi.getAttributes().setTitles(List.of(new DataCiteDoiAttributes.Title("A title")));
        doi.getAttributes().setSubjects(List.of());

        JsonNode attributes = new ObjectMapper().valueToTree(doi).get("attributes");

        // no opinion: absent, so DataCite keeps whatever it holds
        assertFalse(attributes.has("relatedIdentifiers"),
                "a null property must not reach DataCite, or it clears the registered value");
        assertFalse(attributes.has("descriptions"));
        assertFalse(attributes.has("geoLocations"));
        assertFalse(attributes.has("dates"));
        assertFalse(attributes.has("url"));
        assertFalse(attributes.has("event"));

        // an explicit statement about the data: present, so DataCite acts on it
        assertTrue(attributes.has("subjects"), "an empty list is how a caller clears a property");
        assertTrue(attributes.get("subjects").isEmpty());
        assertEquals("A title", attributes.get("titles").get(0).get("title").asText());

        // Server-owned numbers a caller never has an opinion about: omitted too, so a sparse
        // update asserts nothing about them. Note the wire name: Lombok's isActive() getter makes
        // Jackson call the field "active", not "isActive".
        assertFalse(attributes.has("active"),
                "an update must not assert that the DOI is inactive");
        assertFalse(attributes.has("metadataVersion"));
        for (String counter :
                List.of(
                        "viewCount",
                        "downloadCount",
                        "referenceCount",
                        "citationCount",
                        "partCount",
                        "partOfCount",
                        "versionCount",
                        "versionOfCount")) {
            assertFalse(attributes.has(counter), counter + " is server-owned; do not send it");
        }
    }

    /**
     * A caller that does set one of them still sends it, so omitting the default is not the same as
     * making the field unusable.
     */
    @Test
    public void aSetServerOwnedValueIsStillSent() throws IOException {
        DataCiteDoi doi = new DataCiteDoi();
        doi.getAttributes().setActive(true);
        doi.getAttributes().setViewCount(3);

        JsonNode attributes = new ObjectMapper().valueToTree(doi).get("attributes");

        assertTrue(attributes.get("active").asBoolean());
        assertEquals(3, attributes.get("viewCount").asInt());
    }

    /**
     * publicationYear is writable DOI metadata rather than server-owned, so it has to be able to say
     * nothing at all - which is why it is the one number here carrying its own {@code NON_DEFAULT}.
     * Under the class-level {@code NON_NULL} alone it serialized as
     * {@code "publicationYear": 0} on any sparse update - a value DataCite rejects - and NON_NULL
     * could not omit it, which left the "a null property means leave it alone" contract incomplete
     * for the one field where a caller is most likely to have no opinion.
     */
    @Test
    public void anUnsetPublicationYearIsOmittedRatherThanSentAsZero() throws IOException {
        DataCiteDoi doi = new DataCiteDoi();
        doi.setId("10.1234/abcd-efgh");
        doi.getAttributes().setTitles(List.of(new DataCiteDoiAttributes.Title("A title")));

        JsonNode attributes = new ObjectMapper().valueToTree(doi).get("attributes");

        assertFalse(attributes.has("publicationYear"),
                "an unset publication year must not reach DataCite as 0");
    }

    /**
     * The accessors must stay binary compatible. Making the field an {@code Integer} changed
     * Lombok's descriptors from {@code ()I} / {@code (I)V}, so a consumer compiled against an
     * earlier release would die with {@code NoSuchMethodError} if this jar were substituted at
     * runtime - and this client has more than one consumer, which can meet each other on the same
     * classpath. Omitting the default on the wire is what the caller actually needed, and that costs
     * no signature change.
     */
    @Test
    public void publicationYearAccessorsStayBinaryCompatible() throws Exception {
        assertEquals(int.class,
                DataCiteDoiAttributes.class.getMethod("getPublicationYear").getReturnType(),
                "getPublicationYear must keep returning int");
        assertNotNull(DataCiteDoiAttributes.class.getMethod("setPublicationYear", int.class),
                "setPublicationYear(int) must still exist");
    }

    /**
     * Same guarantee for {@code active}, whose private field had to be renamed from {@code isActive}
     * for the inclusion annotation to reach the property Jackson actually serializes. Lombok's
     * accessors are identical for both spellings, so this must stay true.
     */
    @Test
    public void activeAccessorsStayBinaryCompatible() throws Exception {
        assertEquals(boolean.class,
                DataCiteDoiAttributes.class.getMethod("isActive").getReturnType(),
                "isActive() must keep returning boolean");
        assertNotNull(DataCiteDoiAttributes.class.getMethod("setActive", boolean.class),
                "setActive(boolean) must still exist");
    }

    /** And a caller that does have an opinion still sends it. */
    @Test
    public void aSetPublicationYearIsStillSent() throws IOException {
        DataCiteDoi doi = new DataCiteDoi();
        doi.getAttributes().setPublicationYear(2026);

        JsonNode attributes = new ObjectMapper().valueToTree(doi).get("attributes");

        assertEquals(2026, attributes.get("publicationYear").asInt());
    }
}
