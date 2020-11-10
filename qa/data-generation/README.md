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

**Important note:** the user operations log will only be written to the Engine if the property 
`restrictUserOperationLogToAuthenticatedUsers` is set to false in the configuration. So before 
generating data with this module, you need to add the following line to the properties section 
of your Engine configuration:
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

* adjust the number of decision instances that are being evaluated (the default value is displayed).
Please note that some of the DMN diagrams contain DRDs (Decision Requirement Diagrams). That means 
 at the end you'll end up with more decision instances than you stated in the generation (because
 decision tables are connected and the output of one decision triggers another decision):
```
mvn clean compile exec:java -Dexec.args="--numberOfDecisionInstances 10000"
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

* specify **process definitions** and number of versions to deploy (default value includes all 
of the 25 processes, so an example value is displayed) List comma-separated definitions specifying 
the number of versions to deploy after a colon. A random number of versions are deployed if the number 
is not specified explicitly.

```
mvn clean compile exec:java -Dexec.args="--processDefinitions Invoice:10,ChangeContactData:3,BookRequestNoBusinessKey:5"
```
Available processes are:  
* AnalysisTesting
* AuthorizationArrangement  
* BookRequestForOneTenant
* BookRequestNoBusinessKey
* BookRequestWithSuspendedInstances  
* BranchAnalysis  
* ChangeContactData  
* DocumentCheckHandling  
* DRIProcessWithLoadsOfVariables
* EmbeddedSubprocessRequest  
* ExportInsurance  
* ExtendedOrder  
* GroupElements
* HiringProcessFor5Tenants
* HiringProcessWithUniqueCorrelationValues  
* InvoiceDataFor2TenantsAndShared
* InvoiceWithAlternativeCorrelationVariable  
* LeadQualification  
* MultiInstanceSubprocessRequest  
* MultiParallel  
* OrderConfirmation  
* PickUpHandling  
* ProcessData
* ProcessRequest  
* ReviewCase  
* TransshipmentArrangement  
* IncidentProcess
* LeadQualificationWithIncident

* specify **decision definitions** and number of versions to deploy (default value includes all of 
the 6 decisions, so an example value is displayed).  List comma-separated definitions specifying the 
number of versions to deploy after a colon. A random number of versions are deployed if the number 
is not specified explicitly.

```
mvn clean compile exec:java -Dexec.args="--decisionDefinitions DecideDish:10,InvoiceBusinessDecisions:5"
```

Available decisions are:
* BerStatusDateInputDecision
* DecideDish
* ExampleDmn11
* ExampleDmn12
* ExampleDmn13
* InvoiceBusinessDecisions
* InvoiceBusinessDecisionsFor2TenantsAndShared


### Data generation progress
Once the data generation is started, it will print out the progress of
the generation to the standard output stream while operation is running.
Be aware that the progress is just an estimate and might vary depending
on the number of process definition deployed and process instances
started.
### Tenant scenarios
3 Process definitions contain distinct tenant scenarios.
```
BookRequestNoBusinessKey - 1 tenant specific definition (tenant: library) & no shared definition
HiringProcessWithUniqueCorrelationValues - 5 tenant specific definitions (tenants: hr, engineering, sales, support, csm) & no shared definition
InvoiceWithAlternativeCorrelationVariable - 2 tenant specific definitions (tenants: sales, engineering) & shared definition
```
### Correlation scenarios
Most definitions have a correlatable business key and a variable name (`correlatingVariable`). However, 3 Process definitions contain distinct correlation scenarios.
```
BookRequestNoBusinessKey - Instances have no business key
HiringProcessWithUniqueCorrelationValues - This contains the `correlatingVariable` key, but its value will be unique to this process so cannot be used to correlate
InvoiceWithAlternativeCorrelationVariable - This process can be correlated, but instead of `correlatingVariable`, it uses the key `alternativeCorrelationVariable`
```
### Analysis testing
AnalysisTesting process contains outlier instances and is suitable to test branch and outlier 
analysis (e.g. in the E2E tests)

### Incident testing
The following processes will create incident data:
```
IncidentProcess
LeadQualificationWithIncident
```
