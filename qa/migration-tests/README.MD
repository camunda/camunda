# Migration

This project generates data for the migration tests and provides validation of migrated data.

# Prerequisites

* Zeebe is running
* Elasticsearch is running
* Elasticsearch exporter is configured 
* Operate is running

# How to run

## Data generation

mvn spring-boot:run 


## Migrate with help from els-schema module (Shellscript and JSON files)

cd ../../els-schema/
mvn compile
cd target/classes
sh ./migrate.sh

## Data validation

cd ../../../qa/migration-tests/
mvn -DskipTests=false verify