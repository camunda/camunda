## Environment

.PHONY: env-h2-up
env-h2-up:
	mvn install -DskipTests=true -Dskip.fe.build=false -DskipChecks -DskipQaBuild \
    && CAMUNDA_SECURITY_INITIALIZATION_USERS_0_USERNAME=demo \
	CAMUNDA_SECURITY_INITIALIZATION_USERS_0_PASSWORD=demo \
	CAMUNDA_SECURITY_INITIALIZATION_USERS_0_NAME=Demo \
	CAMUNDA_SECURITY_INITIALIZATION_USERS_0_EMAIL=demo@example.com \
	CAMUNDA_SECURITY_INITIALIZATION_DEFAULTROLES_ADMIN_USERS_0=demo \
	CAMUNDA_SECURITY_AUTHENTICATION_UNPROTECTEDAPI=true \
	CAMUNDA_SECURITY_AUTHORIZATIONS_ENABLED=false \
	CAMUNDA_BACKUP_WEBAPPS_ENABLED=false \
	CAMUNDA_DATABASE_URL=jdbc:h2:mem:camunda \
	CAMUNDA_DATA_SECONDARYSTORAGE_TYPE=rdbms \
	ZEEBE_BROKER_EXPORTERS_RDBMS_CLASSNAME=io.camunda.exporter.rdbms.RdbmsExporter \
	mvn clean install -DskipTests -DskipChecks -f ../dist/pom.xml exec:java \
	  -Dexec.mainClass="io.camunda.application.StandaloneCamunda" \
	  -Dspring.profiles.active=operate,broker,dev,dev-data,admin,consolidated-auth

.PHONY: env-up
env-up:
	docker compose -f docker-compose.yml up -d elasticsearch \
    && mvn install -DskipTests=true -Dskip.fe.build=false -DskipChecks -DskipQaBuild \
	&& CAMUNDA_SECURITY_INITIALIZATION_USERS_0_USERNAME=demo \
	CAMUNDA_SECURITY_INITIALIZATION_USERS_0_PASSWORD=demo \
	CAMUNDA_SECURITY_INITIALIZATION_USERS_0_NAME=Demo \
	CAMUNDA_SECURITY_INITIALIZATION_USERS_0_EMAIL=demo@example.com \
	CAMUNDA_SECURITY_INITIALIZATION_DEFAULTROLES_ADMIN_USERS_0=demo \
  CAMUNDA_SECURITY_AUTHENTICATION_UNPROTECTEDAPI=true \
  CAMUNDA_SECURITY_AUTHORIZATIONS_ENABLED=false \
	ZEEBE_BROKER_EXPORTERS_CAMUNDAEXPORTER_CLASSNAME=io.camunda.exporter.CamundaExporter \
	ZEEBE_BROKER_EXPORTERS_CAMUNDAEXPORTER_ARGS_BULK_SIZE=1 \
	ZEEBE_BROKER_EXPORTERS_CAMUNDAEXPORTER_ARGS_BULK_DELAY=1 \
	ZEEBE_BROKER_EXPORTERS_CAMUNDAEXPORTER_ARGS_CONNECT_TYPE=elasticsearch \
    CAMUNDA_DATA_SECONDARYSTORAGE_TYPE=elasticsearch \
	CAMUNDA_DATABASE_TYPE=elasticsearch \
	CAMUNDA_TASKLIST_DATABASE=elasticsearch \
	CAMUNDA_OPERATE_DATABASE=elasticsearch \
	CAMUNDA_DATA_SECONDARYSTORAGE_ELASTICSEARCH_URL=http://localhost:9200 \
	ZEEBE_BROKER_EXPORTERS_CAMUNDAEXPORTER_ARGS_CONNECT_URL=http://localhost:9200 \
	CAMUNDA_TASKLIST_ELASTICSEARCH_URL=http://localhost:9200 \
	CAMUNDA_OPERATE_ELASTICSEARCH_URL=http://localhost:9200 \
	CAMUNDA_DATABASE_URL=http://localhost:9200 \
	ZEEBE_BROKER_EXPORTERS_CAMUNDAEXPORTER_ARGS_INDEX_SHOULDWAITFORIMPORTERS=false \
	mvn clean install -DskipTests -DskipChecks -f ../dist/pom.xml exec:java \
	  -Dexec.mainClass="io.camunda.application.StandaloneCamunda" \
	  -Dspring.profiles.active=operate,broker,dev,dev-data,consolidated-auth

env-os-up:
	docker compose -f docker-compose.yml up -d opensearch zeebe-opensearch \
	&& mvn install -DskipTests=true -Dskip.fe.build=false -DskipChecks -DskipQaBuild \
	&& 	CAMUNDA_OPERATE_TASKLIST_URL="http://localhost:8081" \
	  CAMUNDA_DATA_SECONDARYSTORAGE_TYPE=opensearch \
	  ZEEBE_BROKER_EXPORTERS_CAMUNDAEXPORTER_ARGS_CONNECT_TYPE=opensearch \
      CAMUNDA_OPERATE_DATABASE=opensearch \
	  CAMUNDA_TASKLIST_DATABASE=opensearch \
	  CAMUNDA_DATABASE_TYPE=opensearch \
      CAMUNDA_OPERATE_BACKUP_REPOSITORYNAME=test \
      CAMUNDA_SECURITY_INITIALIZATION_USERS_0_USERNAME=demo \
      CAMUNDA_SECURITY_INITIALIZATION_USERS_0_PASSWORD=demo \
      CAMUNDA_SECURITY_INITIALIZATION_USERS_0_NAME=Demo \
      CAMUNDA_SECURITY_INITIALIZATION_USERS_0_EMAIL=demo@example.com \
      CAMUNDA_SECURITY_INITIALIZATION_DEFAULTROLES_ADMIN_USERS_0=demo \
      CAMUNDA_SECURITY_AUTHENTICATION_UNPROTECTEDAPI=true \
      CAMUNDA_SECURITY_AUTHORIZATIONS_ENABLED=false \
         mvn clean install -DskipTests -DskipChecks -f ../dist/pom.xml exec:java -Dexec.mainClass="io.camunda.application.StandaloneOperate" -Dspring.profiles.active=dev,dev-data,auth -Dcamunda.operate.zeebe.compatibility.enabled=true

# Look up for users (bender/bender etc) in: https://github.com/rroemhild/docker-test-openldap
.PHONY: env-ldap-up
env-ldap-up:
	@echo "Starting ldap-testserver: Look up for users (fry/fry, bender/bender etc) at: https://github.com/rroemhild/docker-test-openldap" \
	&& docker compose -f docker-compose.yml up -d elasticsearch zeebe ldap-test-server \
	&& mvn install -DskipTests=true -Dskip.fe.build=false -DskipChecks -DskipQaBuild \
	&& CAMUNDA_OPERATE_LDAP_BASEDN=dc=planetexpress,dc=com \
       CAMUNDA_OPERATE_LDAP_URL=ldap://localhost:10389/ \
       CAMUNDA_OPERATE_LDAP_MANAGERDN=cn=admin,dc=planetexpress,dc=com \
       CAMUNDA_OPERATE_LDAP_MANAGERPASSWORD=GoodNewsEveryone \
       CAMUNDA_OPERATE_LDAP_USERSEARCHFILTER=uid={0} \
	   mvn clean install -DskipTests -DskipChecks -f ../dist/pom.xml exec:java -Dexec.mainClass="io.camunda.application.StandaloneOperate" -Dspring.profiles.active=dev,dev-data,ldap-auth -Dcamunda.operate.zeebe.compatibility.enabled=true

# Set the env var CAMUNDA_OPERATE_AUTH0_CLIENTSECRET in your shell please, eg: export CAMUNDA_OPERATE_AUTH0_CLIENTSECRET=<client-secret>
.PHONY: env-sso-up
env-sso-up:
	@docker compose -f docker-compose.yml up -d elasticsearch zeebe \
	&& mvn install -DskipTests=true -Dskip.fe.build=false -DskipChecks -DskipQaBuild \
	&& CAMUNDA_OPERATE_AUTH0_BACKENDDOMAIN=camunda-dev.eu.auth0.com \
	   CAMUNDA_OPERATE_AUTH0_CLAIMNAME=https://camunda.com/orgs \
       CAMUNDA_OPERATE_AUTH0_CLIENTID=tgbfvBTrXZroWWap8DgtTIOKGn1Vq9F6 \
       CAMUNDA_OPERATE_AUTH0_DOMAIN=weblogin.cloud.ultrawombat.com \
       CAMUNDA_OPERATE_CLOUD_PERMISSIONAUDIENCE=cloud.ultrawombat.com \
       CAMUNDA_OPERATE_CLOUD_PERMISSIONURL=https://accounts.cloud.ultrawombat.com/external/organizations \
       CAMUNDA_OPERATE_CLOUD_ORGANIZATIONID=6ff582aa-a62e-4a28-aac7-4d2224d8c58a \
       CAMUNDA_OPERATE_AUTH0_ORGANIZATION=6ff582aa-a62e-4a28-aac7-4d2224d8c58a \
       CAMUNDA_OPERATE_AUTH0_ORGANIZATIONSKEY=https://camunda.com/orgs \
       CAMUNDA_OPERATE_CLOUD_CONSOLE_URL=https://console.cloud.ultrawombat.com/ \
	   mvn clean install -DskipTests -DskipChecks -f ../dist/pom.xml exec:java -Dexec.mainClass="io.camunda.application.StandaloneOperate" -Dspring.profiles.active=dev,dev-data,sso-auth -Dcamunda.operate.zeebe.compatibility.enabled=true

.PHONY: env-identity-up
env-identity-up:
	docker compose -f docker-compose.yml up -d elasticsearch \
    && mvn install -DskipTests=true -Dskip.fe.build=false -DskipChecks -DskipQaBuild \
	&& ZEEBE_BROKER_EXPORTERS_CAMUNDAEXPORTER_CLASSNAME=io.camunda.exporter.CamundaExporter \
	ZEEBE_BROKER_EXPORTERS_CAMUNDAEXPORTER_ARGS_BULK_SIZE=1 \
	ZEEBE_BROKER_EXPORTERS_CAMUNDAEXPORTER_ARGS_BULK_DELAY=1 \
	ZEEBE_BROKER_EXPORTERS_CAMUNDAEXPORTER_ARGS_CONNECT_TYPE=elasticsearch \
    CAMUNDA_DATA_SECONDARYSTORAGE_TYPE=elasticsearch \
	CAMUNDA_DATABASE_TYPE=elasticsearch \
	CAMUNDA_TASKLIST_DATABASE=elasticsearch \
	CAMUNDA_OPERATE_DATABASE=elasticsearch \
	CAMUNDA_DATA_SECONDARYSTORAGE_ELASTICSEARCH_URL=http://localhost:9200 \
	ZEEBE_BROKER_EXPORTERS_CAMUNDAEXPORTER_ARGS_CONNECT_URL=http://localhost:9200 \
	CAMUNDA_TASKLIST_ELASTICSEARCH_URL=http://localhost:9200 \
	CAMUNDA_OPERATE_ELASTICSEARCH_URL=http://localhost:9200 \
	CAMUNDA_DATABASE_URL=http://localhost:9200 \
	ZEEBE_BROKER_EXPORTERS_CAMUNDAEXPORTER_ARGS_INDEX_SHOULDWAITFORIMPORTERS=false \
	CAMUNDA_SECURITY_AUTHENTICATION_METHOD=BASIC \
	CAMUNDA_SECURITY_AUTHENTICATION_UNPROTECTEDAPI=false \
	CAMUNDA_SECURITY_AUTHORIZATIONS_ENABLED=true \
  CAMUNDA_SECURITY_INITIALIZATION_USERS_0_USERNAME=demo \
  CAMUNDA_SECURITY_INITIALIZATION_USERS_0_PASSWORD=demo \
  CAMUNDA_SECURITY_INITIALIZATION_USERS_0_NAME=Demo \
  CAMUNDA_SECURITY_INITIALIZATION_USERS_0_EMAIL=demo@example.com \
  CAMUNDA_SECURITY_INITIALIZATION_DEFAULTROLES_ADMIN_USERS_0=demo \
  CAMUNDA_SECURITY_AUTHENTICATION_UNPROTECTEDAPI=true \
  CAMUNDA_SECURITY_AUTHORIZATIONS_ENABLED=false \
	mvn clean install -DskipTests -DskipChecks -f ../dist/pom.xml exec:java \
	  -Dexec.mainClass="io.camunda.application.StandaloneCamunda" \
	  -Dspring.profiles.active=operate,broker,dev,dev-data,consolidated-auth,admin


.PHONY: env-down
env-down:
	@docker compose -f docker-compose.yml down -v \
	&& docker-compose -f ./config/docker-compose.identity.yml down -v \
	&& mvn clean

.PHONY: env-status
env-status:
	docker compose -f docker-compose.yml ps

.PHONY: env-clean
env-clean: env-down
	docker system prune -a

.PHONY: start-e2e
start-e2e:
	curl --request DELETE --url http://localhost:9200/e2e* || true \
	&& mvn install -DskipTests=true -Dskip.fe.build=true -DskipChecks -DskipQaBuild \
	&& ZEEBE_BROKER_EXPORTERS_CAMUNDAEXPORTER_CLASSNAME=io.camunda.exporter.CamundaExporter \
	ZEEBE_BROKER_EXPORTERS_CAMUNDAEXPORTER_ARGS_BULK_SIZE=1 \
	ZEEBE_BROKER_EXPORTERS_CAMUNDAEXPORTER_ARGS_BULK_DELAY=1 \
	ZEEBE_BROKER_EXPORTERS_CAMUNDAEXPORTER_ARGS_CONNECT_TYPE=elasticsearch \
    CAMUNDA_DATA_SECONDARYSTORAGE_TYPE=elasticsearch \
	CAMUNDA_DATABASE_TYPE=elasticsearch \
	CAMUNDA_TASKLIST_DATABASE=elasticsearch \
	CAMUNDA_OPERATE_DATABASE=elasticsearch \
	CAMUNDA_DATA_SECONDARYSTORAGE_ELASTICSEARCH_URL=http://localhost:9200 \
	ZEEBE_BROKER_EXPORTERS_CAMUNDAEXPORTER_ARGS_CONNECT_URL=http://localhost:9200 \
	CAMUNDA_TASKLIST_ELASTICSEARCH_URL=http://localhost:9200 \
	CAMUNDA_OPERATE_ELASTICSEARCH_URL=http://localhost:9200 \
	CAMUNDA_DATABASE_URL=http://localhost:9200 \
	ZEEBE_BROKER_EXPORTERS_CAMUNDAEXPORTER_ARGS_CONNECT_INDEXPREFIX=e2eoperate \
	ZEEBE_BROKER_EXPORTERS_CAMUNDAEXPORTER_ARGS_INDEX_SHOULDWAITFORIMPORTERS=false \
	ZEEBE_BROKER_BACKPRESSURE_ENABLED=false \
	ZEEBE_BROKER_NETWORK_COMMANDAPI_PORT=26505 \
	ZEEBE_BROKER_NETWORK_INTERNALAPI_PORT=26504 \
	ZEEBE_BROKER_GATEWAY_NETWORK_PORT=26503 \
	CAMUNDA_DATABASE_INDEXPREFIX=e2eoperate \
	CAMUNDA_OPERATE_ZEEBE_GATEWAYADDRESS=localhost:26503 \
	CAMUNDA_OPERATE_ELASTICSEARCH_INDEXPREFIX=e2eoperate \
	CAMUNDA_OPERATE_CSRF_PREVENTION_ENABLED=false \
  CAMUNDA_SECURITY_INITIALIZATION_USERS_0_USERNAME=demo \
  CAMUNDA_SECURITY_INITIALIZATION_USERS_0_PASSWORD=demo \
  CAMUNDA_SECURITY_INITIALIZATION_USERS_0_NAME=Demo \
  CAMUNDA_SECURITY_INITIALIZATION_USERS_0_EMAIL=demo@example.com \
  CAMUNDA_SECURITY_INITIALIZATION_DEFAULTROLES_ADMIN_USERS_0=demo \
  CAMUNDA_SECURITY_AUTHENTICATION_UNPROTECTEDAPI=true \
  CAMUNDA_SECURITY_AUTHORIZATIONS_ENABLED=false \
	SERVER_PORT=8081 \
	MANAGEMENT_SERVER_PORT=9601 \
	mvn clean install -DskipTests -DskipChecks -f ../dist/pom.xml exec:java \
	  -Dexec.mainClass="io.camunda.application.StandaloneCamunda" \
	  -Dspring.profiles.active=operate,broker,consolidated-auth
