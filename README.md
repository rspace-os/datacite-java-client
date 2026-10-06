# datacite-java-client
A java client for interacting with DataCite REST API

Initialize by ` new DataCiteClientImpl(dataCiteUrl, dataCiteUsername, dataCitePassword, repositoryPrefix)`.

For anonymous read-only access to the public registry (search and retrieve only) use `new DataCiteClientImpl(dataCiteUrl, contactEmail)`.

Registering and changing DOIs needs basic authentication, i.e. with username/password of repository user.

The jar files for this library are built using Jitpack, see https://jitpack.io/#rspace-os/datacite-java-client. Clicking on
the 'get it' button performs a build and associates it with a hash to use as the maven version in eg RSpace pom file.
