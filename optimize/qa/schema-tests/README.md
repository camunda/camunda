# Camunda Optimize Upgrade Tests

This test suite verifies that the Optimize upgrade completes successfully and that the schema has the expected structure.

It starts a given previous version, waits until all data is imported, kills it and starts the upgrade to the target version.
There is a single profile as follows:
- A full schema and instance focused test with the profile `upgrade-es-schema-tests`

It can be run either on a prepared environment or using an environment created by docker-compose (locally).

## Windows

If you're on Windows, you might experience issues running the commands using `bin/bash`. You can use `cmd.exe` instead.
For example, you can replace `def command = ["/bin/bash", "./optimize-startup.sh"]` in `OptimizeWrapper.java` with
`def command = ["cmd.exe", "/C", "optimize-startup.bat"]`.

# Camunda Optimize Upgrade Test - Prepared environment

Prerequisites:
* An Elasticsearch instance compatible with the previous Optimize version running on port 9250
* An Elasticsearch instance compatible with the new Optimize version running on port 9200

```
mvn -Pupgrade-es-schema-tests clean verify
```

# Camunda Optimize Upgrade Test - docker-compose environment

Prerequisites:
* docker-compose must be available

```
mvn -Pupgrade-es-schema-tests,docker clean verify
```

