package com.researchspace.datacite.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DataCiteDoiRelationships {

    private RelationshipDetails client;
    private RelationshipDetails provider;
    private RelationshipDetails media;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RelationshipDetails {
        private RelationshipData data;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RelationshipData {
        private String id;
        private String type;
    }

}
