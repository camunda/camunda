# Optimize data migration testing

During the developement, we sometimes might change the structure of Optimize data, e.g. Reports, Alerts, Dashboards.
To make sure that the Optimize features still work, we need to test the data after migration, to found possible mismatches.

This module runs as follows:

1. Start Camunda bpm platform and Elasticsearch.
2. Generate 1000 process instances and deploy these to the engine.
3. Start the previous version of Optimize.
4. Generate all possible Optimize data using the data structures of the previous version of Optimize.
5. Terminate previous version of optimize and run the migration script.
6. Start the newest version of optimize after migration.
7. Run the post migration tests, which verify that all the data present in the ES can be retrieved from Optimize without exceptions.

To run the module you can execute the following command from the `camunda-optimize/qa/upgrade-optimize-data/` directory:

```
mvn clean verify -f pom.xml -Pupgrade-optimize-data
```
