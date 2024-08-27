## Environment
SHELL := /bin/bash
IDENTITY_CONFIG:=/tmp/.env.identity.tmp
DATABASE_CONFIG:=/tmp/.env.database.tmp
MULTI_TENANCY_CONFIG:=/tmp/.env.multi-tenancy.tmp

.PHONY: env-up
env-up: elasticsearch-env
	@DATABASE=elasticsearch docker compose up -d zeebe
	@mvn clean install -B -DskipTests=true -DskipChecks -Dskip.fe.build=false -DskipQaBuild
	@set -o allexport && source $(ELASTICSEARCH_CONFIG) && set +o allexport && env \
		CAMUNDA_TASKLIST_ZEEBE_GATEWAYADDRESS=$${EXT_CAMUNDA_TASKLIST_ZEEBE_GATEWAYADDRESS} \
		CAMUNDA_TASKLIST_ZEEBE_RESTADDRESS=$${EXT_CAMUNDA_TASKLIST_ZEEBE_RESTADDRESS} \
		CAMUNDA_TASKLIST_$${DATABASE_CAPS}_URL=$${EXT_DATABASE_${DATABASE_CAPS}_URL} \
		CAMUNDA_TASKLIST_ZEEBE$${DATABASE_CAPS}_URL=$${EXT_DATABASE_${DATABASE_CAPS}_URL} \
	mvn -f ../dist/pom.xml exec:java -Dexec.mainClass="io.camunda.application.StandaloneTasklist" -Dspring.profiles.active=dev,dev-data,auth

.PHONY: env-up-os
env-up-os: opensearch-env
	@DATABASE=opensearch docker compose up -d zeebe
	@mvn clean install -B -DskipTests=true -DskipChecks -Dskip.fe.build=false -DskipQaBuild
	@set -o allexport && source $(DATABASE_CONFIG) && set +o allexport && env \
		CAMUNDA_TASKLIST_ZEEBE_GATEWAYADDRESS=$${EXT_CAMUNDA_TASKLIST_ZEEBE_GATEWAYADDRESS} \
		CAMUNDA_TASKLIST_ZEEBE_RESTADDRESS=$${EXT_CAMUNDA_TASKLIST_ZEEBE_RESTADDRESS} \
		CAMUNDA_TASKLIST_$${DATABASE_CAPS}_URL=$${EXT_DATABASE_${DATABASE_CAPS}_URL} \
		CAMUNDA_TASKLIST_ZEEBE$${DATABASE_CAPS}_URL=$${EXT_DATABASE_${DATABASE_CAPS}_URL} \
	mvn -f ../dist/pom.xml exec:java -Dexec.mainClass="io.camunda.application.StandaloneTasklist" -Dspring.profiles.active=dev,dev-data,auth

# Set the env var ZEEBE_TASKLIST_AUTH0_CLIENTSECRET in your shell please, eg: export ZEEBE_TASKLIST_AUTH0_CLIENTSECRET=<client-secret>
.PHONY: env-sso-up
env-sso-up:
	@DATABASE=elasticsearch docker compose up -d elasticsearch zeebe
	@mvn clean install -B -DskipTests=true -DskipChecks -Dskip.fe.build=false -DskipQaBuild
	@CAMUNDA_TASKLIST_AUTH0_CLAIMNAME=https://camunda.com/orgs
	   CAMUNDA_TASKLIST_AUTH0_CLIENTID=CLGSo9RQ1K290Fvy2ohDomndvLR3Qgl3 \
       CAMUNDA_TASKLIST_AUTH0_CLIENTSECRET=DBXQsK6Csz7Y_jCV5xSN6FbYv1ZiOSgnIuaaJ-JVYnZqBPFm78Cuez2S-7QahBr6 \
       CAMUNDA_TASKLIST_AUTH0_DOMAIN=weblogin.cloud.ultrawombat.com \
       CAMUNDA_TASKLIST_AUTH0_ORGANIZATION=6ff582aa-a62e-4a28-aac7-4d2224d8c58a \
       CAMUNDA_TASKLIST_CLOUD_CLUSTER_ID=449ac2ad-d3c6-4c73-9c68-7752e39ae616 \
       CAMUNDA_TASKLIST_CLOUD_CONSOLE_URL=https://console.cloud.ultrawombat.com/ \
       CAMUNDA_TASKLIST_CLOUD_ORGANIZATIONID=6ff582aa-a62e-4a28-aac7-4d2224d8c58a \
       CAMUNDA_TASKLIST_CLOUD_PERMISSION_AUDIENCE=cloud.ultrawombat.com \
       CAMUNDA_TASKLIST_CLOUD_PERMISSIONURL=https://accounts.cloud.ultrawombat.com/external/organizations/ \
       mvn -f ../dist/pom.xml exec:java -Dexec.mainClass="io.camunda.application.StandaloneTasklist" -Dspring.profiles.active=dev,dev-data,sso-auth

.PHONY: operate-up
operate-up:
	@DATABASE=elasticsearch docker compose up -d operate

.PHONY: operate-up-os
operate-os-up:
	@DATABASE=opensearch docker compose up -d operate

.PHONY: env-identity-up
env-identity-up: elasticsearch-env identity-env
	@DATABASE=elasticsearch docker compose -f ./config/docker-compose.identity.yml up -d identity zeebe
	@mvn install -B -DskipTests=true -DskipChecks -Dskip.fe.build=false -DskipQaBuild
	@set -o allexport && source $(IDENTITY_CONFIG) && source $(DATABASE_CONFIG) && set +o allexport && env \
		CAMUNDA_TASKLIST_IDENTITY_ISSUER_BACKEND_URL=$${EXT_CAMUNDA_IDENTITY_ISSUER_BACKEND_URL} \
		CAMUNDA_TASKLIST_IDENTITY_BASEURL=$${EXT_CAMUNDA_IDENTITY_URL} \
		CAMUNDA_TASKLIST_ZEEBE_GATEWAYADDRESS=$${EXT_CAMUNDA_TASKLIST_ZEEBE_GATEWAYADDRESS} \
		CAMUNDA_TASKLIST_ZEEBE_RESTADDRESS=$${EXT_CAMUNDA_TASKLIST_ZEEBE_RESTADDRESS} \
		CAMUNDA_TASKLIST_$${DATABASE_CAPS}_URL=$${EXT_DATABASE_${DATABASE_CAPS}_URL} \
		CAMUNDA_TASKLIST_ZEEBE$${DATABASE_CAPS}_URL=$${EXT_DATABASE_${DATABASE_CAPS}_URL} \
		SERVER_PORT=8082 \
	mvn -f ../dist/pom.xml exec:java -Dexec.mainClass="io.camunda.application.StandaloneTasklist" -Dspring.profiles.active=dev,dev-data,identity-auth

.PHONY: env-identity-os-up
env-identity-os-up: opensearch-env identity-env
	@DATABASE=opensearch docker compose -f ./config/docker-compose.identity.yml up -d identity zeebe
	@mvn install -B -DskipTests=true -DskipChecks -Dskip.fe.build=false -DskipQaBuild
	@set -o allexport && source $(IDENTITY_CONFIG) && source $(OPENSEARCH_CONFIG) && set +o allexport && env \
		CAMUNDA_TASKLIST_IDENTITY_ISSUER_BACKEND_URL=$${EXT_CAMUNDA_IDENTITY_ISSUER_BACKEND_URL} \
		CAMUNDA_TASKLIST_IDENTITY_BASEURL=$${EXT_CAMUNDA_IDENTITY_URL} \
		CAMUNDA_TASKLIST_ZEEBE_GATEWAYADDRESS=$${EXT_CAMUNDA_TASKLIST_ZEEBE_GATEWAYADDRESS} \
		CAMUNDA_TASKLIST_ZEEBE_RESTADDRESS=$${EXT_CAMUNDA_TASKLIST_ZEEBE_RESTADDRESS} \
		CAMUNDA_TASKLIST_$${DATABASE_CAPS}_URL=$${EXT_DATABASE_${DATABASE_CAPS}_URL} \
		CAMUNDA_TASKLIST_ZEEBE$${DATABASE_CAPS}_URL=$${EXT_DATABASE_${DATABASE_CAPS}_URL} \
		SERVER_PORT=8082 \
	mvn -f ../dist/pom.xml exec:java -Dexec.mainClass="io.camunda.application.StandaloneTasklist" -Dspring.profiles.active=dev,dev-data,identity-auth

.PHONY: operate-identity-up
operate-identity-up:
	@DATABASE=elasticsearch docker compose -f ./config/docker-compose.identity.yml up -d operate

.PHONY: operate-identity-os
operate-identity-os-up:
	@DATABASE=opensearch docker compose -f ./config/docker-compose.identity.yml up -d operate

.PHONY: env-identity-mt-up
env-identity-mt-up: multi-tenancy-env elasticsearch-env
	@docker compose -f ./config/docker-compose.identity.mt.yml up -d identity elasticsearch zeebe
	@mvn install -B -DskipTests=true -DskipChecks -Dskip.fe.build=false -DskipQaBuild
	@set -o allexport && source $(IDENTITY_CONFIG) && source $(DATABASE_CONFIG) && source $(MULTI_TENANCY_CONFIG) && set +o allexport && env \
		CAMUNDA_TASKLIST_IDENTITY_ISSUER_BACKEND_URL=$${EXT_CAMUNDA_IDENTITY_ISSUER_BACKEND_URL} \
		CAMUNDA_TASKLIST_IDENTITY_BASEURL=$${EXT_CAMUNDA_IDENTITY_URL} \
		CAMUNDA_TASKLIST_ZEEBE_GATEWAYADDRESS=$${EXT_CAMUNDA_TASKLIST_ZEEBE_GATEWAYADDRESS} \
		CAMUNDA_TASKLIST_ZEEBE_RESTADDRESS=$${EXT_CAMUNDA_TASKLIST_ZEEBE_RESTADDRESS} \
		CAMUNDA_TASKLIST_$${DATABASE_CAPS}_URL=$${EXT_DATABASE_${DATABASE_CAPS}_URL} \
		CAMUNDA_TASKLIST_ZEEBE$${DATABASE_CAPS}_URL=$${EXT_DATABASE_${DATABASE_CAPS}_URL} \
        ZEEBE_AUTHORIZATION_SERVER_URL=$${EXT_ZEEBE_AUTHORIZATION_SERVER_URL} \
		SERVER_PORT=8082 \
	mvn -f ../dist/pom.xml exec:java -Dexec.mainClass="io.camunda.application.StandaloneTasklist" -Dspring.profiles.active=dev,dev-data,identity-auth

.PHONY: env-identity-mt-os-up
env-identity-mt-os-up: multi-tenancy-env opensearch-env
	@DATABASE=opensearch docker compose -f ./config/docker-compose.identity.mt.yml up -d identity zeebe
	@mvn install -B -DskipTests=true -DskipChecks -Dskip.fe.build=false -DskipQaBuild
	@set -o allexport && source $(IDENTITY_CONFIG) && source $(DATABASE_CONFIG) && source $(MULTI_TENANCY_CONFIG) && set +o allexport && env \
		CAMUNDA_TASKLIST_IDENTITY_ISSUER_BACKEND_URL=$${EXT_CAMUNDA_IDENTITY_ISSUER_BACKEND_URL} \
		CAMUNDA_TASKLIST_IDENTITY_BASEURL=$${EXT_CAMUNDA_IDENTITY_URL} \
		CAMUNDA_TASKLIST_ZEEBE_GATEWAYADDRESS=$${EXT_CAMUNDA_TASKLIST_ZEEBE_GATEWAYADDRESS} \
		CAMUNDA_TASKLIST_ZEEBE_RESTADDRESS=$${EXT_CAMUNDA_TASKLIST_ZEEBE_RESTADDRESS} \
		CAMUNDA_TASKLIST_$${DATABASE_CAPS}_URL=$${EXT_DATABASE_${DATABASE_CAPS}_URL} \
		CAMUNDA_TASKLIST_ZEEBE$${DATABASE_CAPS}_URL=$${EXT_DATABASE_${DATABASE_CAPS}_URL} \
		ZEEBE_AUTHORIZATION_SERVER_URL=$${EXT_ZEEBE_AUTHORIZATION_SERVER_URL} \
		SERVER_PORT=8082 \
	mvn -f ../dist/pom.xml exec:java -Dexec.mainClass="io.camunda.application.StandaloneTasklist" -Dspring.profiles.active=dev,dev-data,identity-auth

.PHONY: operate-identity-mt-up
operate-identity-mt-up:
	@DATABASE=elasticsearch docker compose -f ./config/docker-compose.identity.mt.yml up -d operate

.PHONY: operate-identity-mt-os-up
operate-identity-mt-os-up:
	@DATABASE=opensearch docker compose -f ./config/docker-compose.identity.mt.yml up -d operate

.PHONY: env-down
env-down:
	@DATABASE=opensearch docker compose -f ./config/docker-compose.identity.yml down
	@DATABASE=opensearch docker compose down -v
	@DATABASE=elasticsearch docker compose -f ./config/docker-compose.identity.yml down
	@DATABASE=elasticsearch docker compose down -v
	@mvn clean

.PHONY: env-status
env-status:
	@docker compose ps

.PHONY: env-clean
env-clean: env-down
	@docker system prune -a

.PHONY: start-e2e
start-e2e:
	@curl --request DELETE --url http://localhost:9200/e2e* \
    && docker rm -f zeebe-e2e || true \
    && docker compose up --force-recreate -d elasticsearch zeebe-e2e \
    && mvn install -DskipTests=true -DskipChecks -Dskip.fe.build=false -DskipQaBuild  \
    && CAMUNDA_TASKLIST_ELASTICSEARCH_INDEXPREFIX=e2etasklist \
       CAMUNDA_TASKLIST_ZEEBEELASTICSEARCH_PREFIX=e2e \
       CAMUNDA_TASKLIST_ZEEBE_GATEWAYADDRESS=localhost:26503 \
       CAMUNDA_TASKLIST_ZEEBE_RESTADDRESS=http://localhost:8088 \
       CAMUNDA_TASKLIST_IMPORTER_READERBACKOFF=0 \
       CAMUNDA_TASKLIST_IMPORTER_SCHEDULERBACKOFF=0 \
       SERVER_PORT=8081 \
    mvn -f ../dist/pom.xml exec:java -Dexec.mainClass="io.camunda.application.StandaloneTasklist" -Dspring.profiles.active=e2e-test -Dcamunda.tasklist.cloud.clusterId=449ac2ad-d3c6-4c73-9c68-7752e39ae616

.PHONY: lint
lint:
	@mvn spotless:apply

define setup_env
	$(eval ENV_FILE := $(1))
 	$(eval DATABASE := $(2))
 	$(eval DATABASE_CAPS := $(shell echo "$(2)" | tr '[:lower:]' '[:upper:]'))
 	@grep -v '^#' ./config/envs/.$(ENV_FILE) | DATABASE=$(DATABASE) DATABASE_CAPS=$(DATABASE_CAPS) \
 		ZEEBE_CONTAINER_NAME=zeebe-$(DATABASE) \
 		TASKLIST_CONTAINER_NAME=tasklist-$(DATABASE) \
 		DATABASE_CONTAINER_NAME=$(DATABASE) \
 	envsubst > /tmp/$(notdir .$(ENV_FILE)).tmp
	@{ if [ -n "$(DATABASE_CAPS)" ]; then \
        echo "DATABASE_CAPS=$(DATABASE_CAPS)" >> /tmp/$(notdir .$(ENV_FILE)).tmp; \
      fi; }
endef

elasticsearch-env:
	@rm -f $(DATABASE_CONFIG)
	$(call setup_env,env.database,elasticsearch)

opensearch-env:
	@rm -f $($DATABASE_CONFIG)
	$(call setup_env,env.database,opensearch)

identity-env:
	@rm -f $(IDENTITY_CONFIG)
	$(call setup_env,env.identity)

multi-tenancy-env: identity-env
	@rm -f $(MULTI_TENANCY_CONFIG)
	$(call setup_env,env.multi-tenancy)
