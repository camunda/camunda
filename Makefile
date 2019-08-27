## Environment

.PHONY: env-up
env-up: env-down
	docker-compose up --force-recreate --build -d elasticsearch kibana zeebe operate generator

.PHONY: env-down
env-down:
	docker-compose down -v

.PHONY: env-status
env-status:
	docker-compose ps

.PHONY: env-clean
env-clean: env-down
	docker system prune -a

.PHONY: start-backend
start-backend:
	docker-compose up -d elasticsearch zeebe \
	&& mvn install -DskipTests=true -Dskip.fe.build=true \
  && mvn -f webapp/pom.xml exec:java -Dexec.mainClass="org.camunda.operate.webapp.Application" -Dspring.profiles.active=dev-data
