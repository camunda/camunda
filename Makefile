## Environment

.PHONY: env-up
env-up:
	mvn clean install -DskipTests=true \
	&& mvn -pl webapp jib:dockerBuild -Dimage=camunda/tasklist:SNAPSHOT \
	&& docker-compose up -d elasticsearch zeebe tasklist

# Set the env var ZEEBE_TASKLIST_AUTH0_CLIENTSECRET in your shell please, eg: export ZEEBE_TASKLIST_AUTH0_CLIENTSECRET=<client-secret>
.PHONY: env-sso-up
env-sso-up:
	mvn clean install -DskipTests=true \
	&& mvn -pl webapp jib:dockerBuild -Dimage=camunda/tasklist:SNAPSHOT \
	&& docker-compose up -d elasticsearch zeebe tasklist-sso

.PHONY: operate-up
operate-up:
	docker-compose up -d operate

.PHONY: env-identity-up
env-identity-up:
	@docker-compose -f ./config/docker-compose.identity.yml up -d \
	&& docker-compose up -d elasticsearch zeebe \
	&& mvn install -DskipTests=true -Dskip.fe.build=false \
	&& CAMUNDA_TASKLIST_IDENTITY_ISSUER_URL=http://localhost:18080/auth/realms/camunda-platform \
	   CAMUNDA_TASKLIST_IDENTITY_ISSUER_BACKEND_URL=http://localhost:18080/auth/realms/camunda-platform \
       CAMUNDA_TASKLIST_IDENTITY_CLIENT_ID=tasklist \
       CAMUNDA_TASKLIST_IDENTITY_CLIENT_SECRET=the-cake-is-alive \
       CAMUNDA_TASKLIST_IDENTITY_AUDIENCE=tasklist-api \
       CAMUNDA_TASKLIST_PERSISTENTSESSIONSENABLED=true \
	   mvn -f webapp/pom.xml exec:java -Dexec.mainClass="io.camunda.tasklist.Application" -Dspring.profiles.active=dev,dev-data,identity-auth

.PHONY: env-down
env-down:
	@docker-compose -f ./config/docker-compose.identity.yml down \
	&& docker-compose down -v \
	&& mvn clean

.PHONY: env-status
env-status:
	docker-compose ps

.PHONY: env-clean
env-clean: env-down
	docker system prune -a

.PHONY: start-e2e
start-e2e:
	docker rm -f tasklist-e2e || true \
	&& curl --request DELETE --url http://localhost:9200/e2e* \
	&& docker rm -f zeebe-e2e || true \
	&& docker-compose up --force-recreate -d zeebe-e2e \
	&& mvn install -DskipTests=true  \
	&& mvn -pl webapp jib:dockerBuild \
	&& docker-compose up -d tasklist-e2e
