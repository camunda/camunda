MAKEFLAGS += --warn-undefined-variables
SHELL := bash
.SHELLFLAGS := -eu -o pipefail -c
.DELETE_ON_ERROR:
.SUFFIXES:

.PHONY: build
build:
	yarn install --frozen-lockfile
	yarn build

.PHONY: install-dependencies
install-dependencies:
	yarn install

.PHONY: generate-dependency-docs
generate-dependency-docs:
	yarn document

.PHONY: start
start: install-dependencies
	yarn start

.PHONY: test
test: install-dependencies
	yarn test
