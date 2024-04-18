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

start:
	mvn spring-boot:run

debug:
	mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005"
