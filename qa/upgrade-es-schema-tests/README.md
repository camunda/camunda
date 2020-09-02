# Camunda Optimize Upgrade Performance

This test suite verifies that the Optimize upgrade completes within a limited time window.

It starts a given previous version, waits until all data is imported, kills it and starts the upgrade to the target version.

It has two profile for its execution, static and base data.

# General Requirements

* the machine the test is run on needs to have `jq`, `nc` (netcat) & `cmp` (diffutils) installed

# Camunda Optimize Upgrade Performance - static data

This profile requires a given static dataset served by a running cambpm instance as well a a running elasticsearch to run against..

Prerequisites:
* A Camunda Engine is running. Ideally with a lot of data to put
a lot of stress on the upgrade
* An Elasticsearch instance is running.

After the upgrade completes this test completes as well, if it times out it fails and aborts the upgrade.

```
mvn -Pstatic-data-upgrade-es-schema-tests clean verify
```


# Camunda Optimize Upgrade Performance - base data

This profile has no prerequisites, it starts the engine, an elasticsearch instance, waits until the engine has generated sample data and performs the upgrade.

```
mvn -Pupgrade-es-schema-tests clean verify
```

After the upgrade a full integration test run is executed to ensure the schema is fully functional.

# Maven Execution Parameters

There are two parameters applicable when running the upgrade tests:

* `-Dupgrade.versions=2.3.0,2.4.0-SNAPSHOT` this is a list of versions to upgrade and start in sequence. It defaults to the previous and current minor release but can be overridden to start from even older releases, e.g. `2.1.0,2.2.0`.
* `-Dupgrade.expected.activityInstance.count=21` this parameter determines the amount of imported activity instances for which the test considers the import from the engine to be finished.
It defaults to 21 which is the number of activity instances in a deployment of the engine with the default sample data.
