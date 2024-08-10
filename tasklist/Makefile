## Environment

.PHONY: env-up
env-up:
	@mvn clean install -B -DskipTests=true -DskipChecks -Dskip.fe.build=false -DskipQaBuild
	@docker-compose up -d elasticsearch zeebe
	@mvn clean install -DskipTests -DskipChecks -f ../dist/pom.xml exec:java -Dexec.mainClass="io.camunda.application.StandaloneTasklist" -Dspring.profiles.active=dev,dev-data,auth

.PHONY: env-up-os
env-up-os:
	@mvn clean install -B -DskipTests=true -DskipChecks -Dskip.fe.build=false -DskipQaBuild
	@docker-compose up -d opensearch zeebe-opensearch
	@mvn clean install -DskipTests -DskipChecks -f ../dist/pom.xml exec:java -Dexec.mainClass="io.camunda.application.StandaloneTasklist" -Dspring.profiles.active=dev,dev-data,auth -Dcamunda.tasklist.database=opensearch

# Set the env var ZEEBE_TASKLIST_AUTH0_CLIENTSECRET in your shell please, eg: export ZEEBE_TASKLIST_AUTH0_CLIENTSECRET=<client-secret>
.PHONY: env-sso-up
env-sso-up:
	@mvn clean install -B -DskipTests=true -DskipChecks -Dskip.fe.build=false -DskipQaBuild
	@docker-compose up -d elasticsearch zeebe
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
       mvn clean install -DskipTests -DskipChecks -f ../dist/pom.xml exec:java -Dexec.mainClass="io.camunda.application.StandaloneTasklist" -Dspring.profiles.active=dev,dev-data,sso-auth

.PHONY: operate-up
operate-up:
	@docker-compose up -d operate

.PHONY: env-identity-up
env-identity-up:
	@docker-compose -f ./config/docker-compose.identity.yml up -d identity elasticsearch zeebe
	@mvn install -B -DskipTests=true -DskipChecks -Dskip.fe.build=false -DskipQaBuild
	@CAMUNDA_IDENTITY_ISSUER=http://localhost:18080/auth/realms/camunda-platform \
		CAMUNDA_IDENTITY_ISSUER_BACKEND_URL=http://localhost:18080/auth/realms/camunda-platform \
		CAMUNDA_IDENTITY_BASE_URL=http://localhost:8084 \
		CAMUNDA_IDENTITY_CLIENT_ID=tasklist \
		CAMUNDA_IDENTITY_CLIENT_SECRET=the-cake-is-alive \
		CAMUNDA_IDENTITY_AUDIENCE=tasklist-api \
		CAMUNDA_TASKLIST_PERSISTENTSESSIONSENABLED=true \
		CAMUNDA_TASKLIST_IDENTITY_RESOURCE_PERMISSIONS_ENABLED=true \
		SERVER_PORT=8082 \
		mvn clean install -DskipTests -DskipChecks -f ../dist/pom.xml exec:java -Dexec.mainClass="io.camunda.application.StandaloneTasklist" -Dspring.profiles.active=dev,dev-data,identity-auth

.PHONY: env-identity-up
env-identity-os-up:
	@docker-compose -f ./config/docker-compose.identity.yml up -d identity opensearch zeebe-opensearch
	@mvn install -B -DskipTests=true -DskipChecks -Dskip.fe.build=false -DskipQaBuild
	@CAMUNDA_IDENTITY_ISSUER=http://localhost:18080/auth/realms/camunda-platform \
		CAMUNDA_IDENTITY_ISSUER_BACKEND_URL=http://localhost:18080/auth/realms/camunda-platform \
		CAMUNDA_IDENTITY_BASE_URL=http://localhost:8084 \
		CAMUNDA_IDENTITY_CLIENT_ID=tasklist \
		CAMUNDA_IDENTITY_CLIENT_SECRET=the-cake-is-alive \
		CAMUNDA_IDENTITY_AUDIENCE=tasklist-api \
		CAMUNDA_TASKLIST_PERSISTENTSESSIONSENABLED=true \
		CAMUNDA_TASKLIST_IDENTITY_RESOURCE_PERMISSIONS_ENABLED=true \
		CAMUNDA_TASKLIST_DATABASE=opensearch \
		CAMUNDA_TASKLIST_ZEEBE_GATEWAYADDRESS=localhost:26500 \
		CAMUNDA_TASKLIST_ZEEBE_RESTADDRESS=http://localhost:8089 \
		CAMUNDA_TASKLIST_OPENSEARCH_URL=http://localhost:9205 \
		CAMUNDA_TASKLIST_ZEEBEOPENSEARCH_URL=http://localhost:9205 \
		SERVER_PORT=8082 \
		mvn clean install -DskipTests -DskipChecks -f ../dist/pom.xml exec:java -Dexec.mainClass="io.camunda.application.StandaloneTasklist" -Dspring.profiles.active=dev,dev-data,identity-auth

.PHONY: env-identity-mt-up
env-identity-mt-up:
	@docker-compose -f ./config/docker-compose.identity.mt.yml up -d identity elasticsearch zeebe
	@mvn install -B -DskipTests=true -DskipChecks -Dskip.fe.build=false -DskipQaBuild
	@CAMUNDA_TASKLIST_IDENTITY_ISSUERURL=http://localhost:18080/auth/realms/camunda-platform \
		CAMUNDA_TASKLIST_IDENTITY_ISSUERBACKENDURL=http://localhost:18080/auth/realms/camunda-platform \
		CAMUNDA_TASKLIST_IDENTITY_BASEURL=http://localhost:8084 \
		CAMUNDA_TASKLIST_IDENTITY_CLIENT_ID=tasklist \
		CAMUNDA_TASKLIST_IDENTITY_CLIENT_SECRET=the-cake-is-alive \
		CAMUNDA_TASKLIST_IDENTITY_AUDIENCE=tasklist-api \
		CAMUNDA_TASKLIST_PERSISTENTSESSIONSENABLED=true \
		CAMUNDA_TASKLIST_IDENTITY_RESOURCE_PERMISSIONS_ENABLED=true \
		CAMUNDA_TASKLIST_MULTITENANCY_ENABLED=true \
		ZEEBE_CLIENT_ID=zeebe \
        ZEEBE_CLIENT_SECRET=zecret \
        ZEEBE_AUTHORIZATION_SERVER_URL=http://localhost:18080/auth/realms/camunda-platform/protocol/openid-connect/token \
        ZEEBE_TOKEN_AUDIENCE=zeebe-api \
		SERVER_PORT=8082 \
		mvn -f webapp/pom.xml exec:java -Dexec.mainClass="io.camunda.application.StandaloneTasklist" -Dspring.profiles.active=dev,dev-data,identity-auth

.PHONY: env-down
env-down:
	@docker-compose -f ./config/docker-compose.identity.yml down
	@docker-compose down -v
	@mvn clean

.PHONY: env-status
env-status:
	@docker-compose ps

.PHONY: env-clean
env-clean: env-down
	@docker system prune -a

.PHONY: start-e2e
start-e2e:
	@curl --request DELETE --url http://localhost:9200/e2e* \
    && docker rm -f zeebe-e2e || true \
    && docker-compose up --force-recreate -d elasticsearch zeebe-e2e \
    && mvn install -DskipTests=true -DskipChecks -Dskip.fe.build=false -DskipQaBuild  \
    && CAMUNDA_TASKLIST_ELASTICSEARCH_INDEXPREFIX=e2etasklist \
       CAMUNDA_TASKLIST_ZEEBEELASTICSEARCH_PREFIX=e2e \
       CAMUNDA_TASKLIST_ZEEBE_GATEWAYADDRESS=localhost:26503 \
       CAMUNDA_TASKLIST_ZEEBE_RESTADDRESS=http://localhost:8088 \
       CAMUNDA_TASKLIST_IMPORTER_READERBACKOFF=0 \
       CAMUNDA_TASKLIST_IMPORTER_SCHEDULERBACKOFF=0 \
       SERVER_PORT=8081 \
    mvn clean install -DskipTests -DskipChecks -f ../dist/pom.xml exec:java -Dexec.mainClass="io.camunda.application.StandaloneTasklist" -Dspring.profiles.active=e2e-test -Dcamunda.tasklist.cloud.clusterId=449ac2ad-d3c6-4c73-9c68-7752e39ae616

.PHONY: lint
lint:
	@mvn spotless:apply
