package com.researchspace.datacite.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Date;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DataCiteDoiAttributes {

    private String doi;
    private String event;
    private String prefix;
    private String suffix;
    private List<Object> identifiers;
    private List<AlternateIdentifier> alternateIdentifiers;
    private List<Creator> creators;
    private List<Title> titles;
    private String publisher;
    private int publicationYear;
    private List<Subject> subjects;
    private List<Description> descriptions;
    private List<GeoLocation> geoLocations;
    private List<DoiDate> dates;
    private List<Object> contributors;
    private Types types;
    private Object version;
    private String xml;
    private String url;
    private Object contentUrl;
    private int metadataVersion;
    private Object schemaVersion;
    private String source;
    private boolean isActive;
    private String state;
    private Object reason;
    private String landingPage;
    private int viewCount;
    private int downloadCount;
    private int referenceCount;
    private int citationCount;
    private int partCount;
    private int partOfCount;
    private int versionCount;
    private int versionOfCount;
    private Date created;
    private Date registered;
    private String published;
    private Date updated;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Creator {
        private String name;
        private String nameType;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Title {
        private String title;
    }
 
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Types {
        private String resourceType;
        private String resourceTypeGeneral;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Subject {
        private String subject;
        private String subjectScheme;
        private String schemeUri;
        private String valueUri;
        private String classificationCode;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Description {
        private String description;
        private String descriptionType;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GeoLocation {
        private GeoLocationPoint geoLocationPoint;
        private GeoLocationBox geoLocationBox;
        private String geoLocationPlace;
        private List<GeoLocationPolygonPoint> geoLocationPolygon;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GeoLocationPoint {
        private String pointLatitude;
        private String pointLongitude;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GeoLocationPolygonPoint {
        private GeoLocationPoint polygonPoint;
        
        public GeoLocationPolygonPoint(String pointLatitude, String pointLongitude) {
            polygonPoint = new GeoLocationPoint(pointLatitude, pointLongitude);
        }
        
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GeoLocationBox {
        private String westBoundLongitude;
        private String eastBoundLongitude;
        private String southBoundLatitude;
        private String northBoundLatitude;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AlternateIdentifier {
        private String alternateIdentifier;
        private String alternateIdentifierType;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DoiDate {
        private String date;
        private String dateType;
    }

}
