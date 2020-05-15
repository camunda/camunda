## Environment

.PHONY: env-up
env-up:
	mvn clean install -DskipTests=true \
	&& mvn -pl webapp jib:dockerBuild \
	&& docker-compose up -d elasticsearch zeebe zeebe-tasklist

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
	docker rm -f zeebe-tasklist-e2e || true \
	&& curl --request DELETE --url http://localhost:9200/e2e* \
	&& docker rm -f zeebe-e2e || true \
	&& docker-compose up --force-recreate -d zeebe-e2e \
	&& mvn install -DskipTests=true  \
	&& mvn -pl webapp jib:dockerBuild \
	&& docker-compose up -d zeebe-tasklist-e2e