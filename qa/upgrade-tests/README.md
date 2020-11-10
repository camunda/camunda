# Camunda Optimize Upgrade Tests

This test suite verifies that the Optimize upgrade completes successfully and that the schema has the expected structure.

It starts a given previous version, waits until all data is imported, kills it and starts the upgrade to the target version.
There are two test suites:
- A full schema and instance focused test with the profile `upgrade-es-schema-tests`
- An Optimize entity focused test with the profile `upgrade-optimize-data`

It can be run either on a prepared environment (usually Jenkins) or using an environment created by docker-compose (locally).

# Camunda Optimize Upgrade Test - Prepared environment

Prerequisites:
* A Camunda Engine is running on localhost port 8080
* An Elasticsearch instance compatible with the previous Optimize version running on port 9250
* An Elasticsearch instance compatible with the new Optimize version running on port 9200

```
mvn -Pupgrade-es-schema-tests clean verify
```

OR for the optimize data test suite:

```
mvn -Pupgrade-optimize-data clean verify
```


# Camunda Optimize Upgrade Test - docker-compose environment

Prerequisites:
* docker-compose must be available

```
mvn -Pupgrade-es-schema-tests,docker clean verify
```

OR for the optimize data test suite:

```
mvn -Pupgrade-optimize-data,docker clean verify
```
