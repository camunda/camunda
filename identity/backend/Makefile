MAKEFLAGS += --warn-undefined-variables
SHELL := bash
.SHELLFLAGS := -eu -o pipefail -c
.DELETE_ON_ERROR:
.SUFFIXES:

build:
	mvn clean package

install:
	mvn clean install

license-check:
	mvn license:check

license-fix:
	mvn license:format

test: license-check
	mvn clean verify

generate-dependency-docs:
	mvn org.codehaus.mojo:license-maven-plugin:2.0.0:add-third-party \
		-Dlicense.excludedScopes=test \
		-Dlicense.outputDirectory='$${project.build.directory}/../docs/' \
		-Dlicense.fileTemplate='$${project.build.directory}/../docs/dependency-template.ftl'

start:
	mvn spring-boot:run

debug:
	mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005"
