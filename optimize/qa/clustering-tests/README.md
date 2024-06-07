# Camunda Optimize Clustering Tests

This test suite verifies that the Optimize operates correctly in a clustering scenario.

It is based on a cluster consisting of at least one importing and one not importing optimize instances.

# General Requirements

* the machine the test is run on needs to have `jq` and `nc` (netcat) installed
* a cluster consisting of at least one importing and one not importing Optimize instance needs to be setup

# How to run

The tests are setup in the `clustering-tests` maven profile.

Given you have a cluster running with both instances running on localhost,
the importing one listening on 8090 and the not importing one on 8190,
you can run the tests with the following command:

```
mvn -Pclustering-tests clean verify \
    -Doptimize.importing.host=localhost -Doptimize.importing.port=8090 \
    -Doptimize.notImporting.host=localhost -Doptimize.importing.port=8190
```

If you don't have a cluster at hand you can run the following command for one being created
as part of running the tests:

```
mvn -Pclustering-tests clean verify -Dskip.docker=false
```


# Maven Execution Parameters

There are four parameters applicable when running the upgrade tests:

* `-Doptimize.importing.host` & `-Doptimize.importing.host` pointing to the instance that performs the import
* `-Doptimize.notImporting.host` & `-Doptimize.notImporting.host` pointing to one instance that does not perform the import