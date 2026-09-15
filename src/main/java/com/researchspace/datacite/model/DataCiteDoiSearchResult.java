package com.researchspace.datacite.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/** Response of {@code GET /dois?query=...}: one page of DOIs plus the total hit count. */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DataCiteDoiSearchResult {

    private List<DataCiteDoi> data = new ArrayList<>();
    private Meta meta = new Meta();

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Meta {
        private int total;
    }
}
