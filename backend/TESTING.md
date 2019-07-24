# Testing

There are three kinds of tests in the backend:

* [Unit tests](#unit-testing)
* [Integration tests](#integration-testing)
* [Performance tests](#performance-testing)

## Prerequisites

* You need to have a recent version of Docker and `docker-compose` installed.
* `docker` and `docker-compose` commands must be usable without `sudo`. See [Docker website](https://docs.docker.com/install/linux/linux-postinstall/) how to archieve that.

## Unit testing

Run the following command to run the unit tests:

```
mvn clean test
```

All the unit tests are contained in the [src/test](./src/test/java/.) folder.
To write your own unit test, just add the springrunner class and the respective application context like this:
```java
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"/unit/applicationContext.xml"})
public class YourCustomUnitTest {

    // example optimze bean creation
    @Autowired
    private ConfigurationService configurationService;

    // do you testing here ...

}
```

The spring context enables you to instantiate an object of each class used in Optimize.

## Integration testing

This project has integration tests implemented that rely on following facts:

* tomcat is with engine, engine-rest and engine-it-plugin modules is started and is listening to port 8080
for HTTP requests
* elasticsearch is started and is listening to port 9300 for TCP connections, as well as as port 9200
for HTTP connections
* build is performed with ```it``` profile

in order to debug your test locally you have the following options:

* [run all integration tests via maven](#run-all-tests)
* [setup environment and run tests manually](#run-tests-manually)

### Run all tests

If you just want to run all tests in one command without making additional
efforts, just run the following:
```
mvn -Pproduction,it clean verify
```

Elasticsearch and the engine are setup automatically through Docker.

### Run tests manually

Especially, if you want to debug a test, it can make sense to setup the
environment manually to step through a failing test case.

Start Tomcat with the engine deployed and Elasticsearch together using the following cmdline:
```
mvn -Pproduction,it pre-integration-test
```

This basically spins up the integration test environment using Docker. Now you should be able to run your tests in your preferred IDE.

### Writing your own test

All the integration tests are contained in the [src/it](./src/it/java/.) folder.

To write your own integration test, there are three rules to help you setting up the environment and interacting with it:

* **EngineIntegrationRule**: Cleans up the engine after each test and allows for interaction with/population of data to the engine using the rest api.
* **EmbeddedOptimizeRule**: Starts Optimize, ensures that the configuration is always reset, allows direct interaction with Optimize components.
* **ElasticSearchIntegrationTestRule**: Cleans up Elasticsearch after each test, allows interaction with, populating data to Elasticsearch.

In addition to the rules, you need to add the the springrunner class and the respective application context to your test class. The following shows an example template for a test class:

```java
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"/it/it-applicationContext.xml"})
public class YourCustomIT  {

  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();

  @Rule
  public RuleChain chain = RuleChain
      .outerRule(elasticSearchRule).around(engineRule).around(embeddedOptimizeRule);

  @Test
  public void myFirstCustomTest() {
    // write your test here...
  }

  // write more tests here...
```

Note, that there are two kind of integration tests:

* Backend end to end test: Data is added to the engine, imported to Optimize and a query executed. Therefore, all three rules are needed.
* Rest service tests: The idea is just to test the rest endpoint. Data is added to elasticsearch manually, a rest request is performed against Optimize and the result validated. Therefore, only the *EmbeddedOptimizeRule* and the *ElasticSearchIntegrationTestRule* are needed here. Also use the application context */it/it-applicationContext.xml* for this kind of tests.

### Working with snapshots 

While executing integration tests it might be useful snapshots of data from elasticsearch. Please refer to [wiki](https://github.com/camunda/camunda-optimize/wiki/Using-ES-snapshots) for more information. 

## Performance testing

There are two kinds of performance tests:

* [Import Performance Tests](../qa/import-performance-tests/README.md)
* [Query Performance Tests](../qa/service-performance-tests/README.md)

Have a look at the dedicated readme files to get more information about how to run them.

## Migration testing

### Prerequisites

* Operating system: Linux/OSX as test setup uses bash cmds
* Available binaries on path: curl, [jq](https://stedolan.github.io/jq/)

To run the schema migration tests locally, execute the following cmds:

### Execute tests locally

```shell
// first build everything required for running the integration tests
mvn clean install -Dskip.docker -DskipTests -Pproduction,it,engine-latest -pl backend,upgrade -am

// then run the schema migration test
mvn clean verify -f qa/upgrade-es-schema-tests/pom.xml -Pupgrade-es-schema-tests
```
