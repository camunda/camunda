# Camunda Optimize Reimport Preparation

When upgrading Optimize certain features might not work out of the box for 
the old data. This is due to old versions of Optimize
do not fetch data that is necessary for this specific feature to work.
This is where this module comes into play. 

## Building the Optimize Upgrade jar

Apache Maven 3 and Java JDK 8 are prerequisites for building the jar. 
Once you have setup Java and Maven, run

```
mvn clean install -DskipTests -Dskip.docker
```

In the ``/target`` folder you can then find a file named
``optimize-reimport-preparation.jar``. This is the jar file,
which you can execute in the `environment` folder of your Optimize.

## Run the tests

Run the following command to run the tests:

```
mvn clean verify -Pit
```

Run the following command to start Elasticsearch and the 
Camunda Platform to execute the tests manually later:

```
mvn -Pit pre-integration-test
```