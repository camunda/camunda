# Camunda Optimize REST API

The following REST API is providing backend service based on elasticsearch(ES) to perform aggregation of the 
data that has been previously imported into elasticsearch(ES) instance\cluster. 

## ES Installation 

please not that you have to add following configuration to your _/etc/elasticsearch/elasticsearch.yml_

```
script.engine.groovy.inline.aggs: on
```

### Enabling scripts debug logging

you have to add following line to your elastic search startup jvm options, the order does not play any 
significant role

```
-Djava.security.policy=file:////etc/elasticsearch/.java.policy
```

here is how one could do that on Ubuntu 

```
sudo vi /etc/elasticsearch/jvm.options
sudo service elasticsearch stop
sudo service elasticsearch start
```

content of policy file: 

```
grant {
   permission org.elasticsearch.script.ClassPermission "*";
};
```

after that you can add following code to your groovy script to test it

```
import  org.elasticsearch.common.logging.*
logger = ESLoggerFactory.getLogger('myscript')
logger.info("TEST")
```

## REST API

in order to run API you have to run 
```
org.camunda.optimize.Main
```
class as a normal class, which will start up embedded Jetty server with listener on port 8080 and endpoints
providing basic operations

to run from command line please execute following from root folder of the project

```
mvn -DskipTests clean package
java -jar ./es-java/es-java-rest/target/es-java-rest-0.0.1-SNAPSHOT-jar-with-dependencies.jar
```

### Enunciate documentation 

you can generate documentation using [enunciate](http://enunciate.webcohesion.com/) 

```
mvn clean package -DskipTests -Pdocs
realpath es-java/es-java-rest/target/docs/apidocs/index.html | xargs firefox
```
### Heatmap

Endpoint: _/heatmap_

Method: *POST*

Example request: 

```bash
curl -XPOST http://localhost:8080/api/heatmap -d '{"key":"leadQualification"}' -H "Content-Type: application/json"
```

Example response: 

```
{
   "callactivity_0cw79oq" : 246280,
   "msleadreceived" : 310696,
   "servicetask_4" : 310234,
   "usertask_0w1r7lc" : 268439,
   "exclusivegateway_04zuj46" : 227010,
   "inclusivegateway_1qmuhxg" : 209630,
   "msleadisnew" : 189194,
   "exclusivegateway_0mapcsy" : 246280,
   "exclusivegateway_1jpz3p1" : 248466,
   "usertask_1g1zsp8" : 191098
 }
 ```

### Correlation

Endpoint: _/heatmap/correlation_

Method: *POST*

Example request: 

```bash
curl -XPOST http://localhost:8080/api/heatmap/correlation -d '{"key":"leadQualification", "correlationActivities": ["EndEvent_0wsfol8","UserTask_0w1r7lc"]}' -H "Content-Type: application/json"
```

Example response: 

```
1396
```