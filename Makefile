## Environment

.PHONY: env-up
env-up:
	mvn clean install -DskipTests=true \
	&& mvn -pl webapp jib:dockerBuild \
	&& docker-compose up -d elasticsearch zeebe zeebe-tasklist

# Set the env var ZEEBE_TASKLIST_AUTH0_CLIENTSECRET in your shell please, eg: export ZEEBE_TASKLIST_AUTH0_CLIENTSECRET=<client-secret>
.PHONY: env-sso-up
env-sso-up:
	mvn clean install -DskipTests=true \
	&& mvn -pl webapp jib:dockerBuild \
	&& docker-compose up -d elasticsearch zeebe zeebe-tasklist-sso

.PHONY: operate-up
operate-up:
	docker-compose up -d operate

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