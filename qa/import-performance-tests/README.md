# Camunda Optimize Import Performance - Static

In order to measure performance of the import process of a static data set the following steps are required

* start Camunda Platform with one of the supported databases 
* populate test data into Platform 
* run import process 
* evaluate results\ prepare report 

to start the tests one could use following command

```
mvn -Pstatic-data-import-performance-test clean verify
```

please note that test relies on the fact that you already have Camunda Platform started and 
configured to connect ot the database against which test will be run. Elasticsearch
instance will be started by maven.

# Camunda Optimize Import Performance - Live

In order to measure performance of the import process while the engine is generating data

* start Camunda Platform, postgres and elasticsearch using the following docker commands

```
docker-compose -f ../../docker-compose.postgresql.yml up -d --force-recreate
```

to start the tests one could use following command

```
mvn -Plive-data-import-performance-test clean verify
```



## Camunda Platform 

Test itself does not provide Camunda Platform, but rather relies on connection properties to an instance 
which is already configured in desired manner. Following setups are recommended for execution: 

* Tomcat distribution of Camunda Platform with PostgreSQL database 
* Tomcat distribution of Camunda Platform with Oracle database

You are strongly encouraged to use one of the [Docker containers][docker-containers] already provided by 
Camunda. Otherwise you have to perform following steps: 

* download Camunda Platform distribution [[1]][camunda-distro]
* download install and configure Database of your choice 
* download JDBC driver required to work with your database into _lib_ folder of application server
* adjust _server.xml_, replacing datasource properties with configuration parameters required in order
to connect to your database instance

### Configuration with PostreSQL\MySQL

Please follow instruction provided by [Docker container][docker-containers] setup

Docker: 
```
docker run -d -p 5432:5432 registry.camunda.com/camunda-ci-postgresql
psql -hlocalhost -d process-engine -U camunda
```

DS: 
```
    <Resource name="jdbc/ProcessEngine"
              auth="Container"
              type="javax.sql.DataSource"
              factory="org.apache.tomcat.jdbc.pool.DataSourceFactory"
              uniqueResourceName="process-engine"
              driverClassName="org.postgresql.Driver"
              url="jdbc:postgresql://localhost:5432/process-engine"
              defaultTransactionIsolation="READ_COMMITTED"
              username="camunda"  
              password="camunda"
              maxActive="20"
              minIdle="5" />
```

JDBC
```
cp ~/.m2/repository/org/postgresql/postgresql/9.3-1102-jdbc4/postgresql-9.3-1102-jdbc4.jar server/apache-tomcat-8.0.47/lib/
```

### Configuration with Oracle Database

* download and run oracle docker image 
```
docker run -d -p 1521:1521 registry.camunda.com/camunda-ci-oracle
```
* download ojdbc6 driver and put it in _lib_ folder of applciation server [[2]][ojdbc6]
* replace datasource definition with oracle database connection
```
    <Resource name="jdbc/ProcessEngine"
              auth="Container"
              type="javax.sql.DataSource" 
              factory="org.apache.tomcat.jdbc.pool.DataSourceFactory"
              uniqueResourceName="process-engine"
              driverClassName="oracle.jdbc.OracleDriver" 
              url="jdbc:oracle:thin:@localhost:1521:orcl"
              defaultTransactionIsolation="READ_COMMITTED"
              username="camunda"  
              password="camunda"
              maxActive="20"
              minIdle="5" />
```
* start engine

  
## Import process 

The import process will automatically start. The progress of the import will be printed out to the standard output stream while the import is running.
 
## Results\Reporting 

During the test execution runtime statistics as well as final results will be printed in standard output stream. 

## Known limitations

At the moment of writing, following issues are known

* slow import against Camunda Engine with PostgreSQL database and authorization enabled 
* slow generation of test data while running against Camunda Engine with Oracle XE database

[docker-containers]: https://github.com/camunda/docker-camunda-bpm-platform
[camunda-distro]: https://camunda.org/release/camunda-bpm/tomcat/
[ojdbc6]: https://artifacts.camunda.com/artifactory/thirdparty/com/oracle/jdbc/ojdbc6/12.1.0.2/ojdbc6-12.1.0.2.jar
