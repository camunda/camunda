# Golden file snapshot tests for the load test setup.
#
# Each test scaffolds a setup version with newLoadTest.sh and renders the
# platform, load-tester, and umbrella Helm charts, splitting the output into one
# file per Kubernetes resource and comparing against the committed golden files
# under golden/<version>/<scenario>/<chart>/.
#
# Requires: go, helm, git, and network access (charts are cloned and pulled).

SHELL := /bin/bash

.PHONY: help build helm-repos warm-chart-cache test update-golden clean

help:
	@echo "Targets:"
	@echo "  build            Compile the test package"
	@echo "  helm-repos       Add/update the Helm repositories the charts need"
	@echo "  warm-chart-cache Pre-resolve/pull chart dependencies once, so the"
	@echo "                   parallel scenarios below reuse them instead of"
	@echo "                   each re-fetching the same charts over the network"
	@echo "  test             Run the golden file tests (verify mode)"
	@echo "  update-golden    Regenerate golden files after an intentional change,"
	@echo "                   then review 'git diff golden/' before committing"
	@echo "  clean            Remove leftover scaffolded namespace directories"
	@echo ""
	@echo "Scope test/update-golden to one setup version with PATTERN=, e.g.:"
	@echo "  make update-golden PATTERN=stable-89"

# Each scenario scaffolds a namespace, which clones camunda-platform-helm and
# runs `helm dependency build`. Cap parallelism so many simultaneous clones / OCI
# pulls don't exhaust helm's timeouts on high-core machines (the default is
# GOMAXPROCS). Override with `make test PARALLEL=N`.
PARALLEL ?= 4

# Optional: scope test/update-golden to one setup version (e.g. PATTERN=stable-89).
# Leave unset to run/update every version listed in golden_test.go.
PATTERN ?=
ifneq ($(PATTERN),)
run_filter := -run '.*$(PATTERN).*'
endif

# Ephemeral, single-`make`-invocation chart cache consumed by newLoadTest.sh
# (via LOAD_TEST_CHART_CACHE_DIR, see warm-chart-cache below). `:=` evaluates
# `mktemp -d` once at parse time so every recipe in this invocation shares the
# same path. A fresh temp dir every invocation means it can never survive
# across runs, so a Renovate version bump can never see a stale cache.
export LOAD_TEST_CHART_CACHE_DIR := $(shell mktemp -d)
# helm-repos (below) already primes the repo index once for the whole run;
# scenarios don't each need to repeat it themselves.
export LOAD_TEST_SKIP_REPO_UPDATE := true

build:
	go build ./...

helm-repos:
	source ../utils.sh; \
	retry_with_backoff helm repo add camunda https://helm.camunda.io/ --force-update; \
	retry_with_backoff helm repo add camunda-load-tests https://camunda.github.io/camunda-load-tests-helm/ --force-update; \
	retry_with_backoff helm repo add opensearch https://opensearch-project.github.io/helm-charts/ --force-update; \
	retry_with_backoff helm repo update

# Resolves load-test-setup's Chart.lock deps ONCE (instead of once per
# scenario) by running against the shared source chart, which newLoadTest.sh
# copies wholesale into every scenario's namespace dir. Pre-pulls each unique
# camunda-platform chart version ONCE into LOAD_TEST_CHART_CACHE_DIR via
# newLoadTest.sh's own --warm-platform-chart-cache (so the version-to-chart
# mapping and cache path convention live in one place, not duplicated here).
# Runs entirely sequentially, before the parallel go test phase, so there is
# no read/write race: every scenario only ever reads this tree afterwards.
warm-chart-cache: helm-repos
	set -e; \
	source ../utils.sh; \
	echo "Pre-resolving load-test-setup chart dependencies..."; \
	retry_with_backoff helm dependency update ../charts/load-test-setup; \
	for v in $$(../newLoadTest.sh --list-versions); do \
	  ../newLoadTest.sh -t $$v --warm-platform-chart-cache; \
	done

test: warm-chart-cache
	go test -v -count=1 -timeout 50m -parallel $(PARALLEL) $(run_filter) ./...

update-golden: warm-chart-cache
	go test -v -count=1 -timeout 50m -parallel $(PARALLEL) $(run_filter) -update-golden ./...

clean:
	rm -rf ../c8-golden-*
