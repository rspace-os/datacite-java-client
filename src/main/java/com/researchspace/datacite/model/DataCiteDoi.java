package com.researchspace.datacite.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DataCiteDoi {

    private String id;
    private String type = "dois";
    private DataCiteDoiAttributes attributes = new DataCiteDoiAttributes();
    private DataCiteDoiRelationships relationships = new DataCiteDoiRelationships();

    public boolean isValidForPublish() {
        return attributes.getUrl() != null;
    }
}
