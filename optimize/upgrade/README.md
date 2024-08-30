# Camunda Optimize Upgrade

Camunda Optimize Upgrade module is used to build an Optimize upgrade jar.
The executable jar can then be used to upgrade the Optimize data structure in
Elasticsearch.

## Running the Upgrade

To run the upgrade, you can run the `upgrade.sh` or the `upgrade.bat` script found in the [distro upgrade folder](https://github.com/camunda/camunda/tree/main/optimize-distro/src/upgrade).

## Run the tests

Run the following command to run the tests:

```
mvn clean verify
```

