package com.researchspace.datacite.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Date;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Attributes of a DOI, in both directions.
 *
 * <p>{@code NON_NULL} is load-bearing on the way out, not tidiness. DataCite's
 * {@code PUT /dois/{id}} replaces the whole of each property it is given, and it distinguishes
 * three cases, verified against api.test.datacite.org in August 2026:
 *
 * <ul>
 *   <li>a populated value replaces the registered one
 *   <li>an explicit empty array CLEARS the property
 *   <li>an explicit {@code null} also CLEARS it - and ONLY a key absent from the payload leaves the
 *       registered value alone
 * </ul>
 *
 * <p>Without this annotation every null field went on the wire as an explicit null, so a caller had
 * no way to say "I have no opinion about this property" and every update silently cleared whatever
 * it did not populate. That destroyed registered data: RSpace's PIDINST mapping deliberately leaves
 * {@code relatedIdentifiers} null when it cannot build the addresses (a deployment with no usable
 * server URL), intending to leave the registered entries untouched, and instead stripped them from
 * findable DOIs with no way to put them back.
 *
 * <p>With {@code NON_NULL} the three cases separate the way callers already assumed: {@code null}
 * means "leave it alone", an empty list means "clear it", a populated list means "replace it".
 *
 * <p>A primitive cannot be null, so {@code NON_NULL} alone could not extend that to the numbers:
 * every update sent {@code metadataVersion}, eight zeroed counters and {@code "active": false},
 * whether or not the caller had any opinion about them. They carry {@code NON_DEFAULT} instead, so
 * an unset number is absent exactly as an unset list is. The claim that this did not matter because
 * DataCite ignores those fields was never verified, and {@code publicationYear} - which sat in that
 * same list until it turned out to be writable and to be rejected as {@code 0} - is the reason not
 * to keep asserting it. {@code active} is the sharpest case: DataCite returns that field as
 * {@code isActive}, which does not bind to Lombok's {@code active} property, so the value sent was
 * never anything but the field's default. A caller that does set one of these still sends it.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class DataCiteDoiAttributes {

    private String doi;
    private String event;
    private String prefix;
    private String suffix;
    private List<Object> identifiers;
    private List<AlternateIdentifier> alternateIdentifiers;
    private List<RelatedIdentifier> relatedIdentifiers;
    private List<Creator> creators;
    private List<Title> titles;
    private String publisher;
    /**
     * Omitted when unset, unlike the other numbers here, because this one is writable DOI metadata
     * rather than something DataCite owns. Left to the class-level {@code NON_NULL} it serialized as
     * {@code 0} on any sparse update, a value DataCite rejects, so the "a null property means leave
     * it alone" contract had a hole at the field a caller is most likely to have no opinion about.
     *
     * <p>{@code NON_DEFAULT} rather than making it an {@code Integer}: changing the type would
     * change Lombok's accessor descriptors from {@code ()I} / {@code (I)V}, which is a binary break
     * for any consumer compiled against an earlier release, and this client has more than one
     * consumer that can meet on the same classpath. Year 0 is not a publication year, so treating
     * the default as "no opinion" costs nothing. Pinned by
     * {@code DataCiteDoiTest.publicationYearAccessorsStayBinaryCompatible}.
     *
     */
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
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
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    private int metadataVersion;
    private Object schemaVersion;
    private String source;
    /**
     * Named {@code active}, not {@code isActive}, so that the annotation above actually applies:
     * Lombok's getter for either spelling is {@code isActive()}, which makes Jackson call the
     * property {@code active}, and a field spelled {@code isActive} is therefore a different
     * property from the one being serialized. The accessors are unchanged by the rename, and
     * {@code activeAccessorsStayBinaryCompatible} pins that.
     */
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    private boolean active;
    private String state;
    private Object reason;
    private LandingPage landingPage;
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    private int viewCount;
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    private int downloadCount;
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    private int referenceCount;
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    private int citationCount;
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    private int partCount;
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    private int partOfCount;
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    private int versionCount;
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
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
        private Affiliation [] affiliation;
        public Creator(String name, String nameType){
            this(name,nameType,null);
        }
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Affiliation {
        public static final String SCHEME = "ROR";
        public static final String SCHEME_URI = "https://ror.org";
        private String name;
        //Any requests (get or POST) that do not have ?affiliation=true will have no value in the response from Datacite for these fields.
        private String affiliationIdentifier;
        private String affiliationIdentifierScheme;
        private String schemeUri;

        //this allows requests that do not have ?affiliation=true
        public Affiliation(String name) {
            this(name, null);
        }
        public Affiliation(String name, String affiliationIdentifier) {
            this(name, affiliationIdentifier, SCHEME, SCHEME_URI);
        }
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
    public static class RelatedIdentifier {
        private String relationType;
        private String relatedIdentifier;
        private String relatedIdentifierType;
        private String relationTypeInformation;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DoiDate {
        private String date;
        private String dateType;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LandingPage {
        private String url;
    }

}
