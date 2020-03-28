VERSION ?= $(shell mvn org.apache.maven.plugins:maven-help-plugin:evaluate -Dexpression=project.version -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn | grep -v "^\[")
DISTBALL ?= dist/target/zeebe-distribution-$(VERSION).tar.gz
REGISTRY ?= gcr.io/zeebe-io
TAG ?= $(VERSION)
IMAGE ?= $(REGISTRY)/zeebe:$(TAG)
DOCKERFILE ?= dev.Dockerfile

.PHONY: clean
clean:
	mvn clean

%.tar.gz:
	mvn package -P docker -DskipTests -Dcheckstyle.skip -Denforcer.skip -Dformatter.skip -Dlicense.skip -pl dist -am

package: $(DISTBALL)

dist: package
	docker build -f $(DOCKERFILE) -t $(IMAGE) --build-arg DISTBALL=$(DISTBALL) --target zeebe .

redist: clean dist

push: dist
	docker push $(IMAGE)

repush: redist push
