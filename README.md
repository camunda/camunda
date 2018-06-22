# Camunda Optimize

Camunda Optimize is an extension to Camunda BPM for enterprise customers, that provides continuous monitoring and insights about your deployed business processes.

* [Issue Tracker](https://app.camunda.com/jira/secure/RapidBoard.jspa?rapidView=49)
* [Documentation](https://docs.camunda.org/optimize/)

## Building Optimize

Apache Maven 3 and Java JDK 8 are prerequisites for building Camunda Optimize. Once you have setup Java and Maven, run

```
mvn clean install
```

And if you don't want to run the tests
```
mvn clean install -DskipTests
```

After the build is completed, you will find the distributions under
```
distro/target
```

## Front-end

Learn how to build, debug, test and more in the front-end in the [Front-End README](client/README.md)

## Back-end

Learn how to build, debug, test and more in the back-end in the [Back-End README](backend/README.md)

## Development Setup

### Docker

It is possible to spawn the required services for Optimize by running the following cli command in the root of the Optimize repository:
```
docker-compose -f docker-compose.postgresql.yml up -d
```
This will start Elasticsearch and a PostgreSQL-backed Camunda BPM instance.
The services are exposed to localhost on the following ports:
- Elasticsearch: 9200, 9300
- PostgreSQL: 5432
- CamBPM: 8080

You can also just start the platform with H2 by issuing:
```
docker-compose up -d
```

Services can be shutdown again using
```
docker-compose rm -sfv
```
