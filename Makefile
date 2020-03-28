
# Configurable ARGS
VERSION ?= $(shell mvn org.apache.maven.plugins:maven-help-plugin:evaluate -Dexpression=project.version -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn | grep -v "^\[")
TAG ?= $(VERSION)
DISTBALL ?= dist/target/zeebe-distribution-$(VERSION).tar.gz

.PHONY: clean
clean:
	mvn clean

%.tar.gz:
	mvn package -P docker -DskipTests -Dcheckstyle.skip -Denforcer.skip -Dformatter.skip -Dlicense.skip -pl dist -am

# figure out how to actually generate this caca
package: $(DISTBALL)

docker: package
	docker build -t gcr.io/zeebe-io/zeebe:$(TAG) --build-arg DISTBALL=$(DISTBALL) --target zeebe .

redocker: clean docker
