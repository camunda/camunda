MVNW = ../../mvnw -pl .

all: build test

.PHONY: build
build:
	$(MVNW) clean package -DskipTests -DskipChecks

.PHONY: clean
clean:
	$(MVNW) -am clean

.PHONY: test
test:
	$(MVNW) -am clean test -DskipChecks

.PHONY: check
check:
	$(MVNW) -am package

.PHONY: starter
starter: build
	java -jar target/camunda-load-tester-*.jar --spring.profiles.active=starter

.PHONY: worker
worker: build
	java -jar target/camunda-load-tester-*.jar --spring.profiles.active=worker

.PHONY: docker-starter
docker-starter:
	$(MVNW) jib:build -Pstarter

.PHONY: docker-worker
docker-worker:
	$(MVNW) jib:build -Pworker
