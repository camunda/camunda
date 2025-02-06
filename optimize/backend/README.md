# Camunda Optimize REST API

The following REST API is providing backend service based on elasticsearch(ES) to perform
aggregation of the
data that has been previously imported into elasticsearch(ES) instance\cluster.

## ES Installation

please note that you have to add following configuration to your
_/etc/elasticsearch/elasticsearch.yml_

```
script.engine.groovy.inline.aggs: on
```

### Enabling scripts debug logging

you have to add following line to your elastic search startup jvm options, the order does not play
any
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

```java
import org.elasticsearch.common.logging.*

logger =ESLoggerFactory.

getLogger('myscript')
logger.

info("TEST")
```

## Code Structure

### High level overview

        ┌──────────┐  ┌──────────────┐  ┌──────────┐  ┌────────────┐  ┌──────────────────┐
        │          │  │              │  │          │  │            │  │                  │
        │ Optimize │  │ *RestService │  │ *Service │  │ *Reader/   │  │                  │
        │   REST   │  │ Java         │  │ Java     │  │ *Writer    │  │                  │
        │   API    │  │ Classes      │  │ Classes  │  │ Interfaces │  │                  │
        │          │  │              │  │          │  │            │  │                  │
        └──────────┘  └──────────────┘  └──────────┘  └────────────┘  │  Database        │
                                            ┌──────────────────┐      │  (e.g.,          │
                                            │                  │      │  ElasticSearch)  │
                                            │     Scheduled    │      │                  │
                                            │     services     │      │                  │
                                            │                  │      │                  │
                                            └──────────────────┘      │                  │
        ┌───────────┐      ┌───────────┐    ┌─────────────────┐       │                  │
        │ Import    │ <──> │ Import    │    │ Import handlers │──────>│                  │
        │ scheduler │      │ mediator  │    │ (state storage) │       │                  │
        └───────────┘      └───────────┘    └─────────────────┘       │                  │
                                            ┌─────────────────┐       │                  │
                                            │ Import fetchers │       │                  │
                                            │ (data fetching) │       │                  │
                                            └─────────────────┘       │                  │
                      ┌──────────────────┐ ┌──────────────────┐       │                  │
                      │ Import service   │ │    Import job    │       │                  │
                      │ (data transform) │ └──────────────────┘       │                  │
                      └──────────────────┘                            └──────────────────┘

The shape of this diagram hints that each block only interacts with its neighboring blocks, e.g.,
no `*RestService` class interacts with the database, and only implementations of `*Reader` and
`*Writer` interfaces can do so.

### Optimize REST API

It's the component that communicates with the client (front-end react application). A subset of the
REST API is also available as public API, with restricted scope and purpose.

### *RestService Java Classes

This layer implements the REST API and it is identifiable in the code by naming conventions: every
time there is a file named `<Anything>RestService.java`, that is where the REST endpoint is defined.
Some examples are the following:

* ReportRestService.java defines:
  * `/api/report`
  * `/api/report/{id}/evaluate`
    etc...
* ProcessVariableRestService.java defines:
  * `/api/variables`
  * `/api/variables/values` etc...

Each endpoint has some input object. These can be:

* `requestContext`, which is a web server object that carries metadata that is computed from the
  headers
* `<anything>RequestDto`, which is the object associated to the payload of each request
  (e.g., the endpoint `/api/report/process/single` will have an input of type
  `SingleProcessReportDefinitionRequestDto`)

Currently, the input objects, instead of being one single `*RequestDto` object, could also be a list
of `*RequestDto` objects.

Similarly to the request naming convention, there is also a `*Response` naming convention for
the returned objects.

The *RestService classes should be the place where the input validation happens, although there are
cases where the input validation is happening in the next layer instead.

### *Service Java Classes

*Service classes are where the business logic of their respective *RestService classes is
implemented. The *Service objects are injected as beams and they are dependencies of their
respective *RestService object (e.g., `ProcessVariableService` is a beam injected within
`ProcessVariableRestService`).

NOTE: More than one `*Service` object could be used for the implementation of one API.

### Reader/Writer Interfaces

Entities with names *Reader and *Writer are Java Interfaces that implement the reading from and
the writing to the database. As these are interfaces, they need concrete implementation. The
concrete implementations of these interfaces are the only classes that shall read from or write to
the database.

These are interfaces because, in general, we can have multiple implementations, and we are going to
support more than one database, e.g., ElasticSearch and OpenSearch. The ElasticSearch
implementations follow the same naming schema with an extra "ES" suffix (e.g.,
`ProcessVariableReader` -> `ProcessVariableReaderES`), while the OpenSearch implementations use the
"OS" suffix.

### Other blocks

In addition to the (synchronous) blocks mentioned above, there are other activities that happen
asynchronously in Optimize. The most important is the **Import Scheduler**.

All of the core logic about imports is implemented here, and it is implemented
with polling mechanisms. The implementation can be found starting from the class
`AbstractImportScheduler`. The imports are run in multithreading, with each thread corresponding to
one configuration of the tuple `(partitionType, recordType)`.

## Run the REST API

In order to run API you have to run

```
io.camunda.optimize.Main
```

class as a normal class, which will start up embedded Tomcat server with listener on port 8080 and
endpoints providing basic operations

to run from command line please execute following from root folder of the project

```bash
$ mvn -DskipTests clean package
$ java -jar ./es-java/es-java-rest/target/es-java-rest-1.4.0-SNAPSHOT-jar-with-dependencies.jar
```

### Authentication

The whole Optimize REST API is secured. To access it you need to authenticate, which returns a
security token. Whenever you send a request you always need to provide that token in the header.

In order to check if you are authenticated or not you can use following request

```bash
$ curl http://localhost:8080/api/authentication/test
```

which will reply with something like

```html

<html>
<head>
  <meta http-equiv="Content-Type" content="text/html;charset=ISO-8859-1"/>
  <title>Error 401 </title>
</head>
<body>
<h2>HTTP ERROR: 401</h2>
<p>Problem accessing /api/authentication/test. Reason:
<pre>    Unauthorized</pre>
</p>
<hr/>
</body>
</html>
```

since you did not provide any valid bearer token. In order to perform authentication for the first
time one has to send POST request with username and password to /authenticate endpoint

```bash
$ curl -XPOST http://localhost:8080/api/authentication -d '{ "username":"admin", "password": "admin"}' -H "Content-Type: application/json"
```

which will return a Bearer token

```
eyJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJhZG1pbiIsImV4cCI6MTQ4Mzk2NTMyNn0.8LtTNQCygAvajH_HeXAkOCFPi20e-3KHPlC6D009HUg
```

that can be used to access a secure endpoint

```bash
$ curl http://localhost:8080/api/authentication/test -H "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJhZG1pbiIsImV4cCI6MTQ4Mzk2NTMyNn0.8LtTNQCygAvajH_HeXAkOCFPi20e-3KHPlC6D009HUg"
```

to logout

```bash
$ curl -XGET http://localhost:8080/api/authentication/logout -H "Content-Type: application/json" -H "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiJ9.S8EUdXzC3pL5UHz11aBwx36OBlYEL02FS5GH81XFneE"
```

### Trigger data import from engine to elasticsearch

Whenever you start Optimize, the import is triggered automatically. However, you can still do that
manually by sending the following request:

```bash
curl -XGET http://localhost:8080/api/import -H "Content-Type: application/json" -H "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiJ9.S8EUdXzC3pL5UHz11aBwx36OBlYEL02FS5GH81XFneE"
```

## Testing

Read all information on this topic [here](TESTING.md).
