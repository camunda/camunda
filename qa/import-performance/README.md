# Camunda Optimize Import Performance

In order to measure performance of the import process following steps are required 

* start Camunda BPM Platform with one of the supported databases 
* populate test data into Platform 
* run import process 
* evaluate results\ prepare report 

## Camunda BP Platform 

Test itself does not provide Camunda BPM Platform, but rather relies on connection properties to an instance 
which is already configured in desired manner. Following setups are recommended for execution: 

* Tomcat distribution of Camunda BPM Platform with PostgreSQL database 
* Tomcat distribution of Camunda BPM Platform with Oracle database

You are strongly encouraged to use one of the [Docker containers][docker-containers] already provided by 
Camunda. Otherwise you have to perform following steps: 

* download Camunda BPM Platform distribution [[1]][camunda-distro]
* download install and configure Database of your choice 
* download JDBC driver required to work with your database into _lib_ folder of application server
* adjust _server.xml_, replacing datasource properties with configuration parameters required in order
to connect to your database instance

### Configuration with PostreSQL\MySQL

Please follow instruction provided by [Docker container][docker-containers] setup

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

## Test data generation

In order to measure performance of import process test case will create some data by deploying and starting
 a process into the running process engine instance. By default 100 000 process instances will be started. This
  value can be adjusted through the property 
  
## Import process 

Once test data is generated import process will start in Camunda Optimize instance embedded into Jetty web 
 container. Progress of the process will be printed out to the standard output stream while operation is running. 
 
## Results\Reporting 

Once importing is finished a report will be generated in `target/reports` folder. 

## Known limitations

At the moment of writing, following issues are known

* slow import against Camunda Engine with PostgreSQL database and authorization enabled 
* slow generation of test data while running against Camunda Engine with Oracle XE database

[docker-containers]: https://github.com/camunda/docker-camunda-bpm-platform
[camunda-distro]: https://camunda.org/release/camunda-bpm/tomcat/
[ojdbc6]: https://app.camunda.com/nexus/service/local/repositories/thirdparty/content/com/oracle/jdbc/ojdbc6/12.1.0.2/ojdbc6-12.1.0.2.jar