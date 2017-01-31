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

### Pre-populating data

in order to work with authentication, please add a user to corresponding index type

```
curl -XPUT http://localhost:9200/optimize/users/1?pretty -d '{ "username":"admin", "password":"admin"}'
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

### Authentication

in order to check if you are authenticated or not you can use following request

```
curl http://localhost:8080/api/authentication/test
```

which will reply with something like 

```
<html>
<head>
<meta http-equiv="Content-Type" content="text/html;charset=ISO-8859-1"/>
<title>Error 401 </title>
</head>
<body>
<h2>HTTP ERROR: 401</h2>
<p>Problem accessing /api/authentication/test. Reason:
<pre>    Unauthorized</pre></p>
<hr /><i><small>Powered by Jetty://</small></i>
</body>
</html>
```

since you did not provide any valid bearer token. In order to perform authentication for the first time 
one hast to send POST request with username and password to /authenticate endpoint

```
curl -XPOST http://localhost:8080/api/authentication -d '{ "username":"admin", "password": "admin"}' -H "Content-Type: application/json"
```

which will return a Bearer token 

```
eyJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJhZG1pbiIsImV4cCI6MTQ4Mzk2NTMyNn0.8LtTNQCygAvajH_HeXAkOCFPi20e-3KHPlC6D009HUg
```

that can be used to access a secure endpoint 

```
curl http://localhost:8080/api/authentication/test -H "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJhZG1pbiIsImV4cCI6MTQ4Mzk2NTMyNn0.8LtTNQCygAvajH_HeXAkOCFPi20e-3KHPlC6D009HUg"
```

to logout

```
curl -XGET http://localhost:8080/api/authentication/logout -H "Content-Type: application/json" -H "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiJ9.S8EUdXzC3pL5UHz11aBwx36OBlYEL02FS5GH81XFneE"
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

## Integration testing 

This project has integration tests implemented that rely on following facts: 

* tomcat is with engine, engine-rest and engine-purge modules is started and is listening to port 48080 
for HTTP requests 
* elasticsearch is started and is listening to port 9300 for TCP connections, as well as as port 9200 
for HTTP connections
* build is performed with ```it``` profile

in order to debug your test locally you have to perform following steps: 

* run tomcat with proper modules deployed 
```
mvn -Pit -f backend/pom.xml cargo:run
```
* run elastic search instance with proper configuration
```
mvn -Pit -f backend/pom.xml elasticsearch:runforked
backend/target/elasticsearch0/bin/elasticsearch 
```
now you should be able to run your tests

### Cleaning up data after test run

please note that your test is expected to be responsible for populating 
data into engine\elasticsearch as well as clean up. In order to ease 
clean up process, there are rule classes implemented, you can use them
as follows: 

```java
  @Rule
  public ElasticSearchIntegrationTestRule rule = ElasticSearchIntegrationTestRule.getInstance(); 
```

```java
  @Rule
  public EngineIntegrationRule rule = EngineIntegrationRule.getInstance();
```