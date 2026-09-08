package com.researchspace.datacite.client;

import com.researchspace.datacite.model.DataCiteDoi;
import com.researchspace.datacite.model.DataCiteDoiSearchResult;

public interface DataCiteClient {

    /**
     * Retrieve DOI details by its id.
     */
    DataCiteDoi retrieveDoi(String doiId);

    /**
     * Search DOIs by DataCite's free-text {@code query} parameter, restricted to one
     * {@code resource-type-id} (for instruments: "instrument"), returning at most one page of
     * {@code pageSize} DOIs plus the total. Authenticated like every other call; the result is
     * still the global registry.
     */
    DataCiteDoiSearchResult searchDois(String query, String resourceTypeId, int pageSize);

    /**
     * Register/mint new DOI.
     */
    DataCiteDoi registerDoi(DataCiteDoi doiToCreate);

    /**
     * Update DOI. 
     */
    DataCiteDoi updateDoi(DataCiteDoi doiUpdate);

    /**
     * Delete DOI with given id. Only possible for DOIs in 'draft' state.
     */
    boolean deleteDoi(String doiId);

    /**
     * Update and publish DOI. Only possible for DOIs in 'draft' or 'registered' state.
     * Before calling run #DataCiteDoi.get To ensure DOI has enough data to be published   
     */
    DataCiteDoi publishDoi(DataCiteDoi doiToPublish);

    /**
     * Retract published DOI. Only possible for DOIs in 'published' state.
     */
    DataCiteDoi retractDoi(DataCiteDoi doiToRetract);

    /**
     * Check if this instance of DataCiteClient is able to successfully connect to DataCite.
     * Currently verifies datacite url, username and password, but doesn't really validate repository prefix.
     * 
     * @return true if url/username/password combination can be used to successfully connect to Datacite API. 
     *      Doesn't verify repository prefix.
     */
    boolean testConnectionToDataCite();
    
}
