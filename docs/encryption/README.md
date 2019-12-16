# Encryption

TODO: add some basics docs about how is encryption implemented/handled/used.

## Lucene

### Updating Lucene

Identify what version of lucene is used by this elasticsearch

    grep lucene buildSrc/version.properties

Example output:

    lucene            = 8.2.0

To update the encrypted lucene you need to package the encrypted version of proper lucene version (e.g. `8.2.0`) and copy the archive into `lucene/libs`:

    cd [path/to/lucene-solr]/lucene/core
    git co branch_8_2_0_encrypted
    ant jar
    cd -
    cp [path/to/lucene-solr]/lucene-solr/lucene/build/core/lucene-core-8.2.0-SNAPSHOT.jar lucene-libs/lucene-core-8.2.0-SNAPSHOT.jar

Don't forget to run tests afterwards

    ./gradlew :server:test
