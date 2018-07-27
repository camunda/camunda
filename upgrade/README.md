# Camunda Optimize Upgrade

Camunda Optimize Upgrade module is used to build an Optimize upgrade jar.
The executable jar can then be used to upgrade the Optimize data structure in
Elasticsearch.

## Building the Optimize Upgrade jar

Apache Maven 3 and Java JDK 8 are prerequisites for building the Camunda
Optimize Upgrade jar. Once you have setup Java and Maven, run

```
mvn clean install -DskipTests
```

In the ``/target`` folder you can then find a file named
``upgrade-optimize-from-2.X.0-to-2.Y.0.jar``. This is the jar file,
which you can execute in the `upgrade` folder of your Optimize.

## Run the tests

Run the following command to run the tests:

```
mvn clean verify
```