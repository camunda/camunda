#!/usr/bin/env bash
# Starts the orchestration cluster from the local dist build, with the settings of `make env-up` in webapp/Makefile.
# Needs Elasticsearch on localhost:9200: docker compose -f webapp/docker-compose.yml up -d elasticsearch
set -euo pipefail

REPO_ROOT="$(git -C "$(dirname "$0")" rev-parse --show-toplevel)"

export CAMUNDA_SECURITY_INITIALIZATION_USERS_0_USERNAME=demo
export CAMUNDA_SECURITY_INITIALIZATION_USERS_0_PASSWORD=demo
export CAMUNDA_SECURITY_INITIALIZATION_USERS_0_NAME=Demo
export CAMUNDA_SECURITY_INITIALIZATION_USERS_0_EMAIL=demo@example.com
export CAMUNDA_SECURITY_INITIALIZATION_DEFAULTROLES_ADMIN_USERS_0=demo
export CAMUNDA_SECURITY_AUTHENTICATION_UNPROTECTEDAPI=true
export CAMUNDA_SECURITY_AUTHORIZATIONS_ENABLED=false
export ZEEBE_BROKER_NETWORK_HOST=127.0.0.1
export ZEEBE_BROKER_NETWORK_ADVERTISEDHOST=127.0.0.1
export ZEEBE_BROKER_EXPORTERS_CAMUNDAEXPORTER_CLASSNAME=io.camunda.exporter.CamundaExporter
export ZEEBE_BROKER_EXPORTERS_CAMUNDAEXPORTER_ARGS_BULK_SIZE=1
export ZEEBE_BROKER_EXPORTERS_CAMUNDAEXPORTER_ARGS_BULK_DELAY=1
export ZEEBE_BROKER_EXPORTERS_CAMUNDAEXPORTER_ARGS_CONNECT_TYPE=elasticsearch
export ZEEBE_BROKER_EXPORTERS_CAMUNDAEXPORTER_ARGS_CONNECT_URL=http://localhost:9200
export ZEEBE_BROKER_EXPORTERS_CAMUNDAEXPORTER_ARGS_INDEX_SHOULDWAITFORIMPORTERS=false
export CAMUNDA_DATA_SECONDARYSTORAGE_TYPE=elasticsearch
export CAMUNDA_DATA_SECONDARYSTORAGE_ELASTICSEARCH_URL=http://localhost:9200
export CAMUNDA_DATABASE_TYPE=elasticsearch
export CAMUNDA_DATABASE_URL=http://localhost:9200
export SPRING_PROFILES_ACTIVE=tasklist,operate,broker,dev,consolidated-auth

exec "$REPO_ROOT/dist/target/camunda-zeebe/bin/camunda"
