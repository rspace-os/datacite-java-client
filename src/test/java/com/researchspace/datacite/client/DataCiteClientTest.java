package com.researchspace.datacite.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.researchspace.datacite.model.DataCiteDoi;
import com.researchspace.datacite.model.DataCiteDoiAttributes;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.client.HttpClientErrorException;

public class DataCiteClientTest {

    private DataCiteClient dataCiteClient;

    public DataCiteClientTest() throws IOException, URISyntaxException {
        loadDataCiteConfigProperties();
    }

    private void loadDataCiteConfigProperties() throws IOException, URISyntaxException {
        Properties configProps = new Properties();
        InputStream iStream = new ClassPathResource("datacite-config.properties").getInputStream();
        configProps.load(iStream);
        URI testDataCiteUrl = new URI(configProps.getProperty("datacite.url"));
        String testDataCiteUsername = configProps.getProperty("datacite.username");
        String testDataCitePassword = configProps.getProperty("datacite.password");
        String testRepositoryPrefix = configProps.getProperty("datacite.repository.prefix");
        dataCiteClient = new DataCiteClientImpl(testDataCiteUrl, testDataCiteUsername, testDataCitePassword, testRepositoryPrefix);
    }

    /**
     * Retrieve known doi registered in api.test.datacite.org (default in datacite-config.properties).
     * Doesn't require credentials. 
     * 
     * @throws IOException
     * @throws URISyntaxException
     */
    @Test
    public void canRetrieveKnownFindableDoi() throws IOException, URISyntaxException {
        // registered & findable DOI from api.test.datacite.org
        DataCiteDoi foundDoi = dataCiteClient.retrieveDoi("10.82316/9w24-z012");
        assertEquals("10.82316/9w24-z012", foundDoi.getId());
        assertEquals("findable", foundDoi.getAttributes().getState());
        assertEquals("ResearchSpace", foundDoi.getAttributes().getPublisher());
    }

    /**
     * Full workflow for draft DOI. On successful run the draft DOI creted during the test is deleted.
     * 
     * Requires repository credentials in datacite-config.properties. 
     * To run this test, add the credentials to config file and uncomment @Test annotation.
     */
    //@Test
    public void canCreateRetrieveUpdateDeleteDraftDoi() throws MalformedURLException, URISyntaxException {
        // create new draft doi
        DataCiteDoi doiToCreate = new DataCiteDoi();
        doiToCreate.getAttributes().setTitles(List.of(new DataCiteDoiAttributes.Title("new title")));
        doiToCreate.getAttributes().setTypes(new DataCiteDoiAttributes.Types("RS type", "PhysicalObject"));

        DataCiteDoi createdDoi = dataCiteClient.registerDoi(doiToCreate);
        assertNotNull(createdDoi);
        String createdDoiId = createdDoi.getId();
        assertNotNull(createdDoiId);
        assertEquals("new title", createdDoi.getAttributes().getTitles().get(0).getTitle());
        assertEquals("PhysicalObject", createdDoi.getAttributes().getTypes().getResourceTypeGeneral());
        assertEquals("draft", createdDoi.getAttributes().getState());

        // retrieve created 
        DataCiteDoi retrievedDoi = dataCiteClient.retrieveDoi(createdDoiId);
        assertNotNull(retrievedDoi);
        assertEquals(createdDoi, retrievedDoi);
        
        // update 
        DataCiteDoi doiUpdate = new DataCiteDoi();
        doiUpdate.setId(createdDoiId);
        doiUpdate.getAttributes().setTitles(List.of(new DataCiteDoiAttributes.Title("updated title")));
        DataCiteDoi updatedDoi = dataCiteClient.updateDoi(doiUpdate);
        assertNotNull(updatedDoi);
        assertEquals(1, updatedDoi.getAttributes().getTitles().size());
        assertEquals("updated title", updatedDoi.getAttributes().getTitles().get(0).getTitle());
        // partial update nullifies other fields
        assertNull(updatedDoi.getAttributes().getTypes().getResourceTypeGeneral());
        
        // delete 
        boolean deleted = dataCiteClient.deleteDoi(createdDoiId);
        assertTrue(deleted);
        
        // confirm cannot retrieve after deletion
        try {
            dataCiteClient.retrieveDoi(createdDoiId);
            fail("should not find deleted doi");
        } catch (HttpClientErrorException.NotFound nfe) {
            // expected
        }
    }

    /**
     * Full workflow for findable DOI. After publishing the doi can no longer be deleted, so will stay forever
     * on your repository account (as retracted). You probably don't want to run test on production api. 
     * 
     * Requires repository credentials in datacite-config.properties. 
     * To run this test, add the credentials to config file and uncomment @Test annotation.
     */
    //@Test
    public void canRegisterPublishRetractDoi() throws MalformedURLException, URISyntaxException {
        // create new draft doi
        DataCiteDoi doiToCreate = new DataCiteDoi();
        doiToCreate.getAttributes().setTitles(List.of(new DataCiteDoiAttributes.Title("RSpace test DOI " + new Date())));
        doiToCreate.getAttributes().setCreators(List.of(new DataCiteDoiAttributes.Creator("ResearchSpace TestScript", "Organizational")));
        doiToCreate.getAttributes().setPublisher("ResearchSpace");
        doiToCreate.getAttributes().setPublicationYear(2023);
        doiToCreate.getAttributes().setTypes(new DataCiteDoiAttributes.Types("RS page", "Software"));
        doiToCreate.getAttributes().setUrl("https://researchspace.com/integrations");
        doiToCreate.isValidForPublish();

        DataCiteDoi createdDoi = dataCiteClient.registerDoi(doiToCreate);
        assertNotNull(createdDoi);
        String createdDoiId = createdDoi.getId();
        assertNotNull(createdDoiId);
        assertTrue(createdDoi.getAttributes().getTitles().get(0).getTitle().startsWith("RSpace test DOI"));
        assertEquals("draft", createdDoi.getAttributes().getState());

        // publish 
        DataCiteDoi publishedDoi = dataCiteClient.publishDoi(createdDoi);
        assertNotNull(publishedDoi);
        assertEquals("findable", publishedDoi.getAttributes().getState());
        
        // retract
        DataCiteDoi retractedDoi = dataCiteClient.retractDoi(publishedDoi);
        assertNotNull(retractedDoi);
        assertEquals("registered", retractedDoi.getAttributes().getState());
    }

}
