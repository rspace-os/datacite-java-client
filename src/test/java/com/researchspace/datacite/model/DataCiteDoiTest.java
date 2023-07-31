package com.researchspace.datacite.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
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
        assertEquals("RSpace Test Description", convertedDoiResponse.getData().getAttributes().getDescriptions().get(0).getDescription());
    }
    
}
