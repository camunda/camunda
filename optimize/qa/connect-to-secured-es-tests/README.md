# Test secured connection to Elasticsearch

Run the following command to test if Optimize can connect
to an X-Pack secured version of Elasticsearch:

```
mvn clean verify -Psecured-es-it
```

If you want to run your test manually and just start a
secured Elasticsearch version, run the following command:

```
mvn clean pre-integration-test -Psecured-es-it
```

Once you executed this command, Elasticsearch will run in the background. To 
stop Elasticsearch again, execute the following line:
```
docker-compose rm -sfv
```

The secured Elastics instance is created using docker. 
For more information about how to adjust the settings, have
a look at the *Encrypting Communications in an Elasticsearch Docker Image* documentation:
https://www.elastic.co/guide/en/elasticsearch/reference/6.0/configuring-tls-docker.html