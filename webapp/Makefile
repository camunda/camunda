skip-fe-build ?= true

.PHONY: env-up
env-up:
	docker compose -f docker-compose.yml up -d elasticsearch \
	&& mvn install -DskipTests=true -Dskip.fe.build=${skip-fe-build} -DskipChecks -DskipQaBuild \
	&& CAMUNDA_SECURITY_INITIALIZATION_USERS_0_USERNAME=demo \
	CAMUNDA_SECURITY_INITIALIZATION_USERS_0_PASSWORD=demo \
	CAMUNDA_SECURITY_INITIALIZATION_USERS_0_NAME=Demo \
	CAMUNDA_SECURITY_INITIALIZATION_USERS_0_EMAIL=demo@example.com \
	CAMUNDA_SECURITY_INITIALIZATION_DEFAULTROLES_ADMIN_USERS_0=demo \
	CAMUNDA_SECURITY_AUTHENTICATION_UNPROTECTEDAPI=true \
	CAMUNDA_SECURITY_AUTHORIZATIONS_ENABLED=false \
	ZEEBE_BROKER_NETWORK_HOST=127.0.0.1 \
	ZEEBE_BROKER_NETWORK_ADVERTISEDHOST=127.0.0.1 \
	ZEEBE_BROKER_EXPORTERS_CAMUNDAEXPORTER_CLASSNAME=io.camunda.exporter.CamundaExporter \
	ZEEBE_BROKER_EXPORTERS_CAMUNDAEXPORTER_ARGS_BULK_SIZE=1 \
	ZEEBE_BROKER_EXPORTERS_CAMUNDAEXPORTER_ARGS_BULK_DELAY=1 \
	ZEEBE_BROKER_EXPORTERS_CAMUNDAEXPORTER_ARGS_CONNECT_TYPE=elasticsearch \
	CAMUNDA_DATA_SECONDARYSTORAGE_TYPE=elasticsearch \
	CAMUNDA_DATA_SECONDARYSTORAGE_ELASTICSEARCH_URL=http://localhost:9200 \
	CAMUNDA_DATABASE_TYPE=elasticsearch \
	CAMUNDA_DATABASE_URL=http://localhost:9200 \
	ZEEBE_BROKER_EXPORTERS_CAMUNDAEXPORTER_ARGS_CONNECT_URL=http://localhost:9200 \
	ZEEBE_BROKER_EXPORTERS_CAMUNDAEXPORTER_ARGS_INDEX_SHOULDWAITFORIMPORTERS=false \
	mvn clean install -DskipTests -DskipChecks -f ../dist/pom.xml spring-boot:run \
		-Dspring-boot.run.mainClass="io.camunda.application.StandaloneCamunda" \
		-Dspring-boot.run.profiles=tmp-webapp,broker,dev,dev-data,consolidated-auth

.PHONY: env-down
env-down:
	docker compose -f docker-compose.yml down -v \
	&& mvn clean
