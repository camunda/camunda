# Camunda Optimize Data Generation

This is a util module to be used when one wants to generate a
large amount of data. The generation is done using the Camunda
REST-API.

The generation itself deploys more than 20 BPMN diagrams with
varying amount of versions. The diagrams try to cover all possible
BPMN symbols. The process instance instantiation is done
with variables containing each possible variable type in Camunda.

Before you can start the data generation the Camunda BPM platform
must have already been started.  

**Important note:** the user operations log will only be written to the Engine if the property `restrictUserOperationLogToAuthenticatedUsers` is set to false in the configuration. So before generating data with this module, you need to add the following line to the properties section of your Engine configuration:
```
<property name="restrictUserOperationLogToAuthenticatedUsers">false</property>
```
To then start the data generation,
just execute the following command from the module root directory:
```
mvn clean compile exec:java
```

### Configuration parameters

To configure the data generation, you have the following two possibilities:

* adjust the number of process instances that are being generated (the default value is displayed):
```
mvn clean compile exec:java -Dexec.args="--numberOfProcessInstances 100000"
```

* adjust the rest endpoint to the engine (the default value is displayed):

```
mvn clean compile exec:java -Dexec.args="--engineRest http://localhost:8080/engine-rest"
```

* adjust until the data generation timeouts (the default value is displayed):

```
mvn clean compile exec:java -Dexec.args="--timeoutInHours 16"
```

* clean up all the deployments before generating data (the default value is displayed):

```
mvn clean compile exec:java -Dexec.args="--removeDeployments true"
```

### Data generation progress

Once the data generation is started, it will print out the progress of
the generation to the standard output stream while operation is running.
Be aware that the progress is just an estimate and might vary depending
on the number of process definition deployed and process instances
started.
