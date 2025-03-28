## Environment
SHELL := /bin/bash
MAVEN_CONTEXT:=/tmp/.env.maven-context.tmp
COMPOSE_FILE:=config/docker-compose.yml
DATABASE:=elasticsearch
MAIN_CLASS:=io.camunda.application.StandaloneTasklist
SPRING_PROFILES_ACTIVE:=dev,dev-data,auth
WITH_IDENTITY:=false
WITH_OAUTH:=false
WITH_MULTI_TENANCY:=false
# Bash script to write or replace a variable in a file with the format VAR=value, args: variable name, variable value, target file
WRITE_OR_REPLACE_SCRIPT:=./config/write_or_replace_var.sh


.PHONY: env-up
env-up: $(DATABASE) setup-maven-context
	$(eval SERVICES := elasticsearch)
	$(eval SPRING_PROFILES_ACTIVE = tasklist,broker,dev,dev-data,consolidated-auth)
	$(eval MAIN_CLASS = io.camunda.application.StandaloneCamunda)
	@set -o allexport && source $(MAVEN_CONTEXT) set +o allexport && \
		$(WRITE_OR_REPLACE_SCRIPT) CAMUNDA_TASKLIST_IMPORTER_ENABLED "false" $(MAVEN_CONTEXT) && \
		$(WRITE_OR_REPLACE_SCRIPT) ZEEBE_BROKER_NETWORK_HOST "localhost" $(MAVEN_CONTEXT)
	$(call run-env)

# Set the env var ZEEBE_TASKLIST_AUTH0_CLIENTSECRET in your shell please, eg: export ZEEBE_TASKLIST_AUTH0_CLIENTSECRET=<client-secret>
.PHONY: env-sso-up
env-sso-up: $(DATABASE) setup-maven-context
	$(eval SERVICES := zeebe)
	$(eval SPRING_PROFILES_ACTIVE = dev,dev-data,sso-auth)
	@set -o allexport && source $(MAVEN_CONTEXT) set +o allexport && \
		$(WRITE_OR_REPLACE_SCRIPT) CAMUNDA_TASKLIST_AUTH0_CLAIMNAME "https://camunda.com/orgs" $(MAVEN_CONTEXT) && \
		$(WRITE_OR_REPLACE_SCRIPT) CAMUNDA_TASKLIST_AUTH0_CLIENTID "CLGSo9RQ1K290Fvy2ohDomndvLR3Qgl3" $(MAVEN_CONTEXT) && \
		$(WRITE_OR_REPLACE_SCRIPT) CAMUNDA_TASKLIST_AUTH0_CLIENTSECRET "DBXQsK6Csz7Y_jCV5xSN6FbYv1ZiOSgnIuaaJ-JVYnZqBPFm78Cuez2S-7QahBr6" $(MAVEN_CONTEXT) && \
		$(WRITE_OR_REPLACE_SCRIPT) CAMUNDA_TASKLIST_AUTH0_DOMAIN "weblogin.cloud.ultrawombat.com" $(MAVEN_CONTEXT) && \
		$(WRITE_OR_REPLACE_SCRIPT) CAMUNDA_TASKLIST_AUTH0_ORGANIZATION "6ff582aa-a62e-4a28-aac7-4d2224d8c58a" $(MAVEN_CONTEXT) && \
		$(WRITE_OR_REPLACE_SCRIPT) CAMUNDA_TASKLIST_CLOUD_CLUSTER_ID "449ac2ad-d3c6-4c73-9c68-7752e39ae616" $(MAVEN_CONTEXT) && \
		$(WRITE_OR_REPLACE_SCRIPT) CAMUNDA_TASKLIST_CLOUD_CONSOLE_URL "https://console.cloud.ultrawombat.com/" $(MAVEN_CONTEXT) && \
		$(WRITE_OR_REPLACE_SCRIPT) CAMUNDA_TASKLIST_CLOUD_ORGANIZATIONID "6ff582aa-a62e-4a28-aac7-4d2224d8c58a" $(MAVEN_CONTEXT) && \
		$(WRITE_OR_REPLACE_SCRIPT) CAMUNDA_TASKLIST_CLOUD_PERMISSION_AUDIENCE "cloud.ultrawombat.com" $(MAVEN_CONTEXT) && \
		$(WRITE_OR_REPLACE_SCRIPT) CAMUNDA_TASKLIST_CLOUD_PERMISSIONURL "https://accounts.cloud.ultrawombat.com/external/organizations/" $(MAVEN_CONTEXT)
	$(call run-env)

.PHONY: env-identity-up
env-identity-up: $(DATABASE) identity setup-maven-context
	$(eval SERVICES := zeebe_identity)
	$(eval SPRING_PROFILES_ACTIVE = dev,dev-data,identity-auth)
	@set -o allexport && source $(MAVEN_CONTEXT) set +o allexport && \
		$(WRITE_OR_REPLACE_SCRIPT) CAMUNDA_TASKLIST_IDENTITY_ISSUER_BACKEND_URL $${EXT_CAMUNDA_IDENTITY_ISSUER_BACKEND_URL} $(MAVEN_CONTEXT) && \
		$(WRITE_OR_REPLACE_SCRIPT) CAMUNDA_TASKLIST_IDENTITY_BASEURL $${EXT_CAMUNDA_IDENTITY_URL} $(MAVEN_CONTEXT) && \
		$(WRITE_OR_REPLACE_SCRIPT) SERVER_PORT 8082 $(MAVEN_CONTEXT)
	$(call run-env)

.PHONY: env-identity-oauth-up
env-identity-oauth-up: $(DATABASE) identity-oauth setup-maven-context
	$(eval SERVICES := zeebe_oauth)
	$(eval SPRING_PROFILES_ACTIVE = dev,dev-data,identity-auth)
	@set -o allexport && source $(MAVEN_CONTEXT) set +o allexport && \
		$(WRITE_OR_REPLACE_SCRIPT) CAMUNDA_TASKLIST_IDENTITY_ISSUER_BACKEND_URL $${EXT_CAMUNDA_IDENTITY_ISSUER_BACKEND_URL} $(MAVEN_CONTEXT) && \
		$(WRITE_OR_REPLACE_SCRIPT) CAMUNDA_TASKLIST_IDENTITY_BASEURL $${EXT_CAMUNDA_IDENTITY_URL} $(MAVEN_CONTEXT) && \
		$(WRITE_OR_REPLACE_SCRIPT) SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUERURI $${EXT_SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUERURI} $(MAVEN_CONTEXT) && \
		$(WRITE_OR_REPLACE_SCRIPT) SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWKSETURI $${EXT_SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWKSETURI} $(MAVEN_CONTEXT) && \
		$(WRITE_OR_REPLACE_SCRIPT) ZEEBE_AUTHORIZATION_SERVER_URL $${EXT_ZEEBE_AUTHORIZATION_SERVER_URL} $(MAVEN_CONTEXT) && \
		$(WRITE_OR_REPLACE_SCRIPT) SERVER_PORT 8082 $(MAVEN_CONTEXT)
	$(call run-env)

.PHONY: env-identity-mt-up
env-identity-mt-up: $(DATABASE) identity-oauth multi-tenancy setup-maven-context
	$(eval SERVICES := zeebe_mt)
	$(eval SPRING_PROFILES_ACTIVE = dev,dev-data,identity-auth)
	@set -o allexport && source $(MAVEN_CONTEXT) set +o allexport && \
		$(WRITE_OR_REPLACE_SCRIPT) CAMUNDA_TASKLIST_IDENTITY_ISSUER_BACKEND_URL $${EXT_CAMUNDA_IDENTITY_ISSUER_BACKEND_URL} $(MAVEN_CONTEXT) && \
		$(WRITE_OR_REPLACE_SCRIPT) CAMUNDA_TASKLIST_IDENTITY_BASEURL $${EXT_CAMUNDA_IDENTITY_URL} $(MAVEN_CONTEXT) && \
		$(WRITE_OR_REPLACE_SCRIPT) SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUERURI $${EXT_SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUERURI} $(MAVEN_CONTEXT) && \
        $(WRITE_OR_REPLACE_SCRIPT) SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWKSETURI $${EXT_SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWKSETURI} $(MAVEN_CONTEXT) && \
		$(WRITE_OR_REPLACE_SCRIPT) ZEEBE_AUTHORIZATION_SERVER_URL $${EXT_ZEEBE_AUTHORIZATION_SERVER_URL} $(MAVEN_CONTEXT) && \
		$(WRITE_OR_REPLACE_SCRIPT) SERVER_PORT 8082 $(MAVEN_CONTEXT)
	$(call run-env)

.PHONY: operate-up
operate-up:
	@DATABASE=$(DATABASE) docker compose -f $(COMPOSE_FILE) up -d operate

.PHONY: operate-identity-up
operate-identity-up:
	@DATABASE=$(DATABASE) docker compose -f $(COMPOSE_FILE) up -d operate_identity

.PHONY: operate-identity-oauth-up
operate-identity-oauth-up:
	@DATABASE=$(DATABASE) docker compose -f $(COMPOSE_FILE) up -d operate_oauth

.PHONY: operate-identity-mt-up
operate-identity-mt-up:
	@DATABASE=$(DATABASE) docker compose -f $(COMPOSE_FILE) up -d operate_mt

.PHONY: env-down
env-down:
	@DATABASE=opensearch docker compose -f $(COMPOSE_FILE) down -v
	@DATABASE=elasticsearch docker compose -f $(COMPOSE_FILE) down -v
	@rm -rf ./data
	@mvn clean

.PHONY: env-status
env-status:
	@DATABASE=$(DATABASE) docker compose -f $(COMPOSE_FILE) ps

.PHONY: env-clean
env-clean: env-down
	@docker system prune -a

.PHONY: start-e2e
start-e2e: elasticsearch setup-maven-context
	@curl --request DELETE --url http://localhost:9200/e2e* || true
	$(eval SERVICES := elasticsearch)
	$(eval SPRING_PROFILES_ACTIVE = e2e-test,tasklist,broker,consolidated-auth)
	$(eval MAIN_CLASS = io.camunda.application.StandaloneCamunda)
	@set -o allexport && source $(MAVEN_CONTEXT) set +o allexport && \
		$(WRITE_OR_REPLACE_SCRIPT) CAMUNDA_TASKLIST_ELASTICSEARCH_INDEXPREFIX "e2e" $(MAVEN_CONTEXT) && \
		$(WRITE_OR_REPLACE_SCRIPT) CAMUNDA_TASKLIST_ZEEBEELASTICSEARCH_PREFIX "e2e" $(MAVEN_CONTEXT) && \
		$(WRITE_OR_REPLACE_SCRIPT) ZEEBE_BROKER_EXPORTERS_CAMUNDAEXPORTER_ARGS_CONNECT_INDEXPREFIX "e2e" $(MAVEN_CONTEXT) && \
		$(WRITE_OR_REPLACE_SCRIPT) CAMUNDA_DATABASE_INDEXPREFIX "e2e" $(MAVEN_CONTEXT) && \
		$(WRITE_OR_REPLACE_SCRIPT) CAMUNDA_TASKLIST_IMPORTER_ENABLED "false" $(MAVEN_CONTEXT) && \
		$(WRITE_OR_REPLACE_SCRIPT) CAMUNDA_TASKLIST_CLOUD_CLUSTERID "449ac2ad-d3c6-4c73-9c68-7752e39ae616" $(MAVEN_CONTEXT) && \
		$(WRITE_OR_REPLACE_SCRIPT) EXT_CAMUNDA_TASKLIST_ZEEBE_RESTADDRESS "http://localhost:8081" $(MAVEN_CONTEXT) && \
		$(WRITE_OR_REPLACE_SCRIPT) ZEEBE_BROKER_NETWORK_HOST "localhost" $(MAVEN_CONTEXT) && \
		$(WRITE_OR_REPLACE_SCRIPT) SERVER_PORT 8081 $(MAVEN_CONTEXT)
	$(call run-env)

.PHONY: lint
lint:
	@mvn spotless:apply

elasticsearch:
	$(eval DATABASE := elasticsearch)
	$(eval DATABASE_CAPS := ELASTICSEARCH)
	$(call parse_env_file,env.database.elasticsearch,elasticsearch)

opensearch:
	$(eval DATABASE := opensearch)
	$(eval DATABASE_CAPS := OPENSEARCH)
	$(call parse_env_file,env.database.opensearch,opensearch)

identity:
	$(eval WITH_IDENTITY := true)
	$(call parse_env_file,env.identity)

identity-oauth: identity
	$(eval WITH_OAUTH := true)
	$(call parse_env_file,env.identity-oauth)

multi-tenancy:
	$(eval WITH_MULTI_TENANCY := true)
	$(call parse_env_file,env.multi-tenancy)

define parse_env_file
	$(eval ENV_FILE := $(1))
 	@grep -v '^#' ./config/envs/.$(ENV_FILE) | DATABASE=$(DATABASE) DATABASE_CAPS=$(DATABASE_CAPS) \
 		ZEEBE_CONTAINER_NAME=zeebe-$(DATABASE) \
 		TASKLIST_CONTAINER_NAME=tasklist-$(DATABASE) \
 		DATABASE_CONTAINER_NAME=$(DATABASE) \
 	envsubst > /tmp/$(notdir .$(ENV_FILE)).tmp
	@{ if [ -n "$(DATABASE_CAPS)" ]; then \
        echo "DATABASE_CAPS=$(DATABASE_CAPS)" >> /tmp/$(notdir .$(ENV_FILE)).tmp; \
      fi; }
endef

.PHONY: setup-maven-context
setup-maven-context:
	DATABASE=$(DATABASE) DATABASE_CAPS=$(DATABASE_CAPS) ./config/env_setup.sh $(WITH_IDENTITY) $(WITH_OAUTH) $(WITH_MULTI_TENANCY)

define run-env
	@DATABASE=$(DATABASE) docker compose -f $(COMPOSE_FILE) up -d $(SERVICES)
	@mvn clean install -B -T1C -DskipTests=true -DskipChecks -Dskip.fe.build=false -DskipQaBuild
	@set -o allexport && source $(MAVEN_CONTEXT) && set +o allexport && rm -rf $(MAVEN_CONTEXT) && \
        mvn -f ../dist/pom.xml exec:java -Dexec.mainClass="$(MAIN_CLASS)" -Dspring.profiles.active=$(SPRING_PROFILES_ACTIVE)
endef

