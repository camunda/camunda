# Migration

This project generates data for the migration tests and provides validation of migrated data.

# Prerequisites

* Zeebe is running
* Elasticsearch is running
* Elasticsearch exporter is configured 

# How to run

## Data generation
mvn spring-boot:run -Dmigration.app=DataGenerationApp

## Data validation
mvn spring-boot:run -Dmigration.app=ValidationApp