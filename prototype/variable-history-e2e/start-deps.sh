#!/bin/bash
D="$(cd "$(dirname "$0")" && pwd)"
docker run -d --name es-vh-e2e -p 9210:9200 -e discovery.type=single-node -e xpack.security.enabled=false -e ES_JAVA_OPTS="-Xms512m -Xmx512m" docker.elastic.co/elasticsearch/elasticsearch:8.19.16
docker run -d --name oauth-vh-e2e -p 8099:8080 -e JSON_CONFIG_PATH=/config/oauth.json -v "$D/oauth.json:/config/oauth.json:ro" ghcr.io/navikt/mock-oauth2-server:2.1.10
