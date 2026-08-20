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

# Ephemeral chart cache consumed by newLoadTest.sh (via LOAD_TEST_CHART_CACHE_DIR,
# see warm-chart-cache below). Gated on $(MAKECMDGOALS) rather than a plain
# top-level `:=` so `make help`/`build`/`clean` never call `mktemp -d` at all
# — and gated as a global variable, not a target-specific one, so warm-chart-cache
# and test/update-golden's own `go test` share the exact same path (a
# target-specific `:=` on multiple targets evaluates $(shell mktemp -d)
# separately per target, silently handing each its own temp dir). A fresh
# temp dir every invocation means it can never survive across runs, so a
# Renovate version bump can never see a stale cache; test/update-golden
# remove it once go test finishes (see below).
ifneq ($(filter test update-golden warm-chart-cache,$(MAKECMDGOALS)),)
# An explicit template (rather than bare `mktemp -d`) is required by BSD/macOS
# mktemp and also accepted by GNU mktemp, so this works on both.
export LOAD_TEST_CHART_CACHE_DIR := $(shell mktemp -d "$${TMPDIR:-/tmp}/load-test-chart-cache.XXXXXXXXXX")
ifeq ($(LOAD_TEST_CHART_CACHE_DIR),)
$(error mktemp -d failed to create LOAD_TEST_CHART_CACHE_DIR)
endif
# helm-repos (below) already primes the repo index once for the whole run;
# scenarios don't each need to repeat it themselves.
export LOAD_TEST_SKIP_REPO_UPDATE := true
endif

build:
	go build ./...

helm-repos:
	set -e; \
	source ../utils.sh; \
	retry_with_backoff helm repo add camunda https://helm.camunda.io/ --force-update; \
	retry_with_backoff helm repo add camunda-load-tests https://camunda.github.io/camunda-load-tests-helm/ --force-update; \
	retry_with_backoff helm repo add opensearch https://opensearch-project.github.io/helm-charts/ --force-update; \
	retry_with_backoff helm repo update

# Resolves load-test-setup's Chart.lock deps ONCE (instead of once per
# scenario) against a scratch copy under LOAD_TEST_CHART_CACHE_DIR, never the
# git-tracked source chart, so this never leaves Chart.lock modified in
# `git status`. Pre-pulls each unique camunda-platform chart version ONCE
# into LOAD_TEST_CHART_CACHE_DIR via newLoadTest.sh's own
# --warm-platform-chart-cache (so the version-to-chart mapping and cache path
# convention live in one place, not duplicated here). Both honor PATTERN so
# `make test PATTERN=stable-89` only warms what it's actually going to use.
# Runs entirely sequentially, before the parallel go test phase, so there is
# no read/write race: every scenario only ever reads this tree afterwards.
warm-chart-cache: helm-repos
	set -e; \
	source ../utils.sh; \
	echo "Pre-resolving load-test-setup chart dependencies..."; \
	rm -rf "$(LOAD_TEST_CHART_CACHE_DIR)/load-test-setup-scratch" "$(LOAD_TEST_CHART_CACHE_DIR)/load-test-setup-deps"; \
	cp -r ../charts/load-test-setup "$(LOAD_TEST_CHART_CACHE_DIR)/load-test-setup-scratch"; \
	retry_with_backoff helm dependency update "$(LOAD_TEST_CHART_CACHE_DIR)/load-test-setup-scratch"; \
	mv "$(LOAD_TEST_CHART_CACHE_DIR)/load-test-setup-scratch/charts" "$(LOAD_TEST_CHART_CACHE_DIR)/load-test-setup-deps"; \
	for v in $$(../newLoadTest.sh --list-versions); do \
	  case "$$v" in \
	    *$(PATTERN)*) ../newLoadTest.sh -t $$v --warm-platform-chart-cache ;; \
	    *) echo "Skipping $$v pre-pull (doesn't match PATTERN=$(PATTERN))" ;; \
	  esac; \
	done

test: warm-chart-cache
	go test -v -count=1 -timeout 50m -parallel $(PARALLEL) $(run_filter) ./...; \
	status=$$?; rm -rf "$(LOAD_TEST_CHART_CACHE_DIR)"; exit $$status

update-golden: warm-chart-cache
	go test -v -count=1 -timeout 50m -parallel $(PARALLEL) $(run_filter) -update-golden ./...; \
	status=$$?; rm -rf "$(LOAD_TEST_CHART_CACHE_DIR)"; exit $$status

clean:
	rm -rf ../c8-golden-*
