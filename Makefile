VERSION ?= $(shell mvn org.apache.maven.plugins:maven-help-plugin:evaluate -Dexpression=project.version -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn | grep -v "^\[")
DISTBALL ?= dist/target/zeebe-distribution-$(VERSION).tar.gz
IMAGE ?= gcr.io/zeebe-io/zeebe
DOCKERFILE ?= Dockerfile
TAG ?= $(VERSION)

%.tar.gz:
	mvn package -P docker -DskipTests -Dcheckstyle.skip -Denforcer.skip -Dformatter.skip -Dlicense.skip -pl dist -am

.PHONY: package
package: | $(DISTBALL)

.PHONY: clean
clean:
	mvn clean

.PHONY: dist
dist: | package
	docker build -f $(DOCKERFILE) -t $(IMAGE):$(TAG) --build-arg DISTBALL=$(DISTBALL) --target zeebe .

.PHONY: redist
redist: | clean dist

.PHONY: push
push: | dist
	docker push $(IMAGE)

.PHONY: repush
repush: | redist push

.EXPORT_ALL_VARIABLES: dev
.PHONY: dev
dev: TAG := $(shell sha1sum $(DISTBALL) | cut -c 1-8)
dev: | package dist
