package com.researchspace.datacite.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import org.apache.commons.lang.StringUtils;
import org.springframework.util.CollectionUtils;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DataCiteDoi {

    private String id;
    private String type = "dois";
    private DataCiteDoiAttributes attributes = new DataCiteDoiAttributes();
    private DataCiteDoiRelationships relationships = new DataCiteDoiRelationships();

    public boolean isValidForPublish() {
        return !CollectionUtils.isEmpty(getAttributes().getTitles()) &&
               !CollectionUtils.isEmpty(getAttributes().getCreators()) &&
               StringUtils.isNotEmpty(getAttributes().getPublisher()) &&
               getAttributes().getTypes() != null &&
               getAttributes().getUrl() != null;
    }
}
