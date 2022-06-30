## Environment

.PHONY: env-up
env-up:
	docker-compose up -d elasticsearch zeebe \
	&& mvn install -DskipTests=true -Dskip.fe.build=true \
	&& mvn -f webapp/pom.xml exec:java -Dexec.mainClass="io.camunda.operate.Application" -Dspring.profiles.active=dev,dev-data,auth

# Look up for users (bender/bender etc) in: https://github.com/rroemhild/docker-test-openldap
.PHONY: env-ldap-up
env-ldap-up:
	@echo "Starting ldap-testserver: Look up for users (fry/fry, bender/bender etc) at: https://github.com/rroemhild/docker-test-openldap" \
	&& docker-compose up -d elasticsearch zeebe ldap-test-server \
	&& mvn install -DskipTests=true -Dskip.fe.build=false \
	&& CAMUNDA_OPERATE_LDAP_BASEDN=dc=planetexpress,dc=com \
       CAMUNDA_OPERATE_LDAP_URL=ldap://localhost:10389/ \
       CAMUNDA_OPERATE_LDAP_MANAGERDN=cn=admin,dc=planetexpress,dc=com \
       CAMUNDA_OPERATE_LDAP_MANAGERPASSWORD=GoodNewsEveryone \
       CAMUNDA_OPERATE_LDAP_USERSEARCHFILTER=uid={0} \
	   mvn -f webapp/pom.xml exec:java -Dexec.mainClass="io.camunda.operate.Application" -Dspring.profiles.active=dev,dev-data,ldap-auth

# Set the env var CAMUNDA_OPERATE_AUTH0_CLIENTSECRET in your shell please, eg: export CAMUNDA_OPERATE_AUTH0_CLIENTSECRET=<client-secret>
.PHONY: env-sso-up
env-sso-up:
	@docker-compose up -d elasticsearch zeebe \
	&& mvn install -DskipTests=true -Dskip.fe.build=false \
	&& CAMUNDA_OPERATE_AUTH0_BACKENDDOMAIN=camunda-dev.eu.auth0.com \
       CAMUNDA_OPERATE_AUTH0_CLAIMNAME=https://camunda.com/orgs \
       CAMUNDA_OPERATE_AUTH0_CLIENTID=tgbfvBTrXZroWWap8DgtTIOKGn1Vq9F6 \
       CAMUNDA_OPERATE_AUTH0_DOMAIN=weblogin.cloud.ultrawombat.com \
       CAMUNDA_OPERATE_CLOUD_PERMISSIONAUDIENCE=cloud.ultrawombat.com \
       CAMUNDA_OPERATE_CLOUD_PERMISSIONURL=https://acounts.cloud.ultrawombat.com/external/organizations \
       CAMUNDA_OPERATE_CLOUD_ORGANIZATIONID=6ff582aa-a62e-4a28-aac7-4d2224d8c58a \
	   mvn -f webapp/pom.xml exec:java -Dexec.mainClass="io.camunda.operate.Application" -Dspring.profiles.active=dev,dev-data,sso-auth

.PHONY: env-identity-up
env-identity-up:
	@docker-compose -f ./config/docker-compose.identity.yml up -d identity elasticsearch zeebe \
	&& mvn install -DskipTests=true -Dskip.fe.build=false \
	&& CAMUNDA_OPERATE_IDENTITY_ISSUERURL=http://localhost:18080/auth/realms/camunda-platform \
	   CAMUNDA_OPERATE_IDENTITY_ISSUERBACKENDURL=http://localhost:18080/auth/realms/camunda-platform \
       CAMUNDA_OPERATE_IDENTITY_CLIENT_ID=operate \
       CAMUNDA_OPERATE_IDENTITY_CLIENT_SECRET=the-cake-is-alive \
       CAMUNDA_OPERATE_IDENTITY_AUDIENCE=operate-api \
       CAMUNDA_OPERATE_PERSISTENT_SESSIONS_ENABLED=true \
       SERVER_PORT=8081 \
	   mvn -f webapp/pom.xml exec:java -Dexec.mainClass="io.camunda.operate.Application" -Dspring.profiles.active=dev,dev-data,identity-auth

.PHONY: env-down
env-down:
	@docker-compose down -v \
	&& docker-compose -f ./config/docker-compose.identity.yml down -v \
	&& mvn clean

.PHONY: env-status
env-status:
	docker-compose ps

.PHONY: env-clean
env-clean: env-down
	docker system prune -a

.PHONY: start-e2e
start-e2e:
	curl --request DELETE --url http://localhost:9200/e2e* \
	&& docker rm -f zeebe-e2e || true \
	&& docker-compose up --force-recreate -d zeebe-e2e \
	&& mvn install -DskipTests=true -Dskip.fe.build=true \
	&& CAMUNDA_OPERATE_ZEEBE_GATEWAYADDRESS=localhost:26503 \
	CAMUNDA_OPERATE_ZEEBEELASTICSEARCH_PREFIX=e2e \
	CAMUNDA_OPERATE_ELASTICSEARCH_INDEXPREFIX=e2eoperate \
	CAMUNDA_OPERATE_IMPORTER_READERBACKOFF=0 \
	CAMUNDA_OPERATE_IMPORTER_SCHEDULERBACKOFF=0 \
	mvn -f webapp/pom.xml exec:java -Dexec.mainClass="io.camunda.operate.Application" -Dserver.port=8081
