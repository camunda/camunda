# Camunda Optimize

Camunda Optimize is an extension to Camunda BPM for enterprise customers, that provides continuous monitoring and insights about your deployed business processes.

* [Issue Tracker](https://app.camunda.com/jira/secure/RapidBoard.jspa?rapidView=49)
* [Documentation](https://docs.camunda.org/optimize/)

## Building Optimize

Apache Maven 3 and Java JDK 8 are prerequisites for building camunda BPM platform. Once you have setup Java and Maven, run

```
mvn clean install -Pit
```

And if you don't want to run the tests
```
mvn clean install -DskipTests -Pit
```

After the build is completed, you will find the distributions under
```
distro/target
```

## Front-end

Learn how to build, debug, test and more in the front-end in the [Front-End README](client/README.md)

## Back-end

Learn how to build, debug, test and more in the back-end in the [Back-End README](backend/README.md)
