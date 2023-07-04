package com.researchspace.datacite.client;

import com.researchspace.datacite.model.DataCiteDoi;
import com.researchspace.datacite.model.DataCiteDoiRequestWrapper;
import java.net.MalformedURLException;
import java.net.URISyntaxException;

public interface DataCiteClient {

    /**
     * Retrieve DOI details by its id.
     */
    DataCiteDoi retrieveDoi(String doiId);

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
}
