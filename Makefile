## Environment

.PHONY: env-up
env-up:
	docker-compose up -d elasticsearch zeebe \
	&& mvn install -DskipTests=true -Dskip.fe.build=true \
	&& mvn -f webapp/pom.xml exec:java -Dexec.mainClass="io.zeebe.tasklist.Application" -Dspring.profiles.active=dev,dev-data,auth

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
	&& ZEEBE_TASKLIST_ZEEBE_BROKERCONTACTPOINT=localhost:26503 \
	ZEEBE_TASKLIST_ZEEBEELASTICSEARCH_PREFIX=e2e \
	ZEEBE_TASKLIST_ELASTICSEARCH_INDEXPREFIX=e2etasklist \
	ZEEBE_TASKLIST_IMPORTER_READERBACKOFF=0 \
	ZEEBE_TASKLIST_IMPORTER_SCHEDULERBACKOFF=0 \
	mvn -f webapp/pom.xml exec:java -Dexec.mainClass="io.zeebe.tasklist.Application" -Dserver.port=8081