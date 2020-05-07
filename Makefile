## Environment

.PHONY: env-up
env-up:
	docker-compose up -d elasticsearch zeebe \
	&& mvn install -DskipTests=true -Dskip.fe.build=true \
	&& mvn -f webapp/pom.xml exec:java -Dexec.mainClass="org.camunda.operate.Application" -Dspring.profiles.active=dev,dev-data,auth

.PHONY: env-down
env-down:
	docker-compose down -v \
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
	&& CAMUNDA_OPERATE_ZEEBE_BROKERCONTACTPOINT=localhost:26503 \
	CAMUNDA_OPERATE_ZEEBEELASTICSEARCH_PREFIX=e2e \
	CAMUNDA_OPERATE_ELASTICSEARCH_INDEXPREFIX=e2eoperate \
	CAMUNDA_OPERATE_IMPORTER_READERBACKOFF=0 \
	CAMUNDA_OPERATE_IMPORTER_SCHEDULERBACKOFF=0 \
	mvn -f webapp/pom.xml exec:java -Dexec.mainClass="org.camunda.operate.Application" -Dserver.port=8081