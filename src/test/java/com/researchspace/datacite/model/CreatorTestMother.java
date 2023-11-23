package com.researchspace.datacite.model;

public class CreatorTestMother {
    public static DataCiteDoiAttributes.Affiliation affiliation = new DataCiteDoiAttributes.Affiliation("University of Northern Iowa",
            "https://ror.org/02h4qpx12","ROR","https://ror.org");
    public static DataCiteDoiAttributes.Creator creatorWithAfilliations(){
        DataCiteDoiAttributes.Creator creator =
                new DataCiteDoiAttributes.Creator("aCreator","Personal", new DataCiteDoiAttributes.Affiliation[]{affiliation});
        return creator;
    }

    public static DataCiteDoiAttributes.Creator creatorWithoutAfilliations(){
        DataCiteDoiAttributes.Creator creator =
                new DataCiteDoiAttributes.Creator("aCreator","Personal");
        return creator;
    }
}
