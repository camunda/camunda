# Camunda Optimize Upgrade Tests

This test suite verifies that the Optimize upgrade completes successfully and that the schema has the expected structure.

It starts a given previous version, waits until all data is imported, kills it and starts the upgrade to the target version.
There are two profiles available, depending on which DB is being used. (different test classes are invoked):
- A full schema in Elasticsearch with the profile `es-schema-integrity-tests`
- A full schema in OpenSearch with the profile `os-schema-integrity-tests`

It can be run either on a prepared environment or using an environment created by docker-compose (locally).

## Windows

If you're on Windows, you might experience issues running the commands using `bin/bash`. You can use `cmd.exe` instead.
For example, you can replace `def command = ["/bin/bash", "./optimize-startup.sh"]` in `OptimizeWrapper.java` with
`def command = ["cmd.exe", "/C", "optimize-startup.bat"]`.

# Camunda Optimize Upgrade Test - Prepared environment

Prerequisites:
* A Database instance compatible with the previous Optimize version running on port 9250
* A Database instance compatible with the new Optimize version running on port 9200

```
mvn -Pes-schema-integrity-tests clean verify
```

# Camunda Optimize Upgrade Test - docker-compose environment

Note that the `docker` profile for running this locally assumes use against an Elasticsearch database, and not against OpenSearch

Prerequisites:
* docker-compose must be available

```
mvn -Pes-schema-integrity-tests,docker clean verify
```

