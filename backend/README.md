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
java -jar ./es-java/es-java-rest/target/es-java-rest-1.4.0-SNAPSHOT-jar-with-dependencies.jar
```

### Authentication

The whole Optimize REST API is secured. To access it you need to authenticate, which returns a security token. Whenever you send a request you always need to provide that token in the header.

In order to check if you are authenticated or not you can use following request

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
one has to send POST request with username and password to /authenticate endpoint

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

### Trigger data import from engine to elasticsearch

Whenever you start Optimize, the import is triggered automatically. However, you can still do that manually by 
sending the following request:

```bash
curl -XGET http://localhost:8080/api/import -H "Content-Type: application/json" -H "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiJ9.S8EUdXzC3pL5UHz11aBwx36OBlYEL02FS5GH81XFneE"
```

The response tells you, if the import was successfully triggered.

### Enunciate REST-API documentation

You can generate documentation for the REST-API using [enunciate](http://enunciate.webcohesion.com/).
Just run the following command:

```
mvn clean package -DskipTests -Pdocs
realpath es-java/es-java-rest/target/docs/apidocs/index.html | xargs firefox
```

[Here](https://hq2.camunda.com/jenkins/optimize/view/All/job/camunda-optimize/job/master/lastSuccessfulBuild/artifact/backend/target/docs/apidocs/index.html) you can also find the documentation of the last successful build.

## Testing

There are two kinds of tests in the backend:
* [Unit tests](#unit-testing)
* [Integration tests](#integration-testing)
* [Performance tests](#performance-testing)

### Unit testing

Run the following command to run the unit tests:
```
mvn clean test -Pproduction
```

There are certain tests, which need the webapps. Therfore, we need to
add the production profile.

### Integration testing

This project has integration tests implemented that rely on following facts: 

* tomcat is with engine, engine-rest and engine-purge modules is started and is listening to port 48080 
for HTTP requests 
* elasticsearch is started and is listening to port 9300 for TCP connections, as well as as port 9200 
for HTTP connections
* build is performed with ```it``` profile

in order to debug your test locally you have the following options:

* [run all integration tests via maven](#run-all-tests)
* [setup environment and run tests manually](#run-tests-manually)

#### Run all tests

If you just want to run all tests in one command without making additional
efforts, just run the following:
```
mvn -Pit clean verify
```

Elasticsearch and the engine are setup automatically.

#### Run tests manually

Especially, if you want to debug a test, it can make sense to setup the
environment manually to step through a failing test case.

First, run tomcat with the engine deployed:
```
mvn -Pit cargo:run
```

Second, run elastic search instance with proper configuration
```
mvn -Pit elasticsearch:runforked
```

Now you should be able to run your tests in your preferred IDE.

#### Cleaning up data after test run

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

### Performance testing

There are two kinds of performance tests:

* [Import Performance Tests](../qa/import-performance-tests/README.md)
* [Query Performance Tests](../qa/service-performance-tests/README.md)

Have a look at the dedicated readme files to get more information about how to run them.
