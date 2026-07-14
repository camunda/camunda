# Golden file snapshot tests for the load test setup.
#
# Each test scaffolds a setup version with newLoadTest.sh and renders the
# platform, load-tester, and umbrella Helm charts, splitting the output into one
# file per Kubernetes resource and comparing against the committed golden files
# under golden/<version>/<scenario>/<chart>/.
#
# Requires: go, helm, git, and network access (charts are cloned and pulled).

.PHONY: help build helm-repos test update-golden clean

help:
	@echo "Targets:"
	@echo "  build          Compile the test package"
	@echo "  helm-repos     Add/update the Helm repositories the charts need"
	@echo "  test           Run the golden file tests (verify mode)"
	@echo "  update-golden  Regenerate golden files after an intentional change,"
	@echo "                 then review 'git diff golden/' before committing"
	@echo "  clean          Remove leftover scaffolded namespace directories"
	@echo ""
	@echo "Scope test/update-golden to one setup version with VERSION=, e.g.:"
	@echo "  make update-golden VERSION=stable-89"

# Each scenario scaffolds a namespace, which clones camunda-platform-helm and
# runs `helm dependency build`. Cap parallelism so many simultaneous clones / OCI
# pulls don't exhaust helm's timeouts on high-core machines (the default is
# GOMAXPROCS). Override with `make test PARALLEL=N`.
PARALLEL ?= 4

# Optional: scope test/update-golden to one setup version (e.g. VERSION=stable-89).
# Leave unset to run/update every version listed in golden_test.go.
VERSION ?=
ifneq ($(VERSION),)
run_filter := -run 'TestGoldenFiles/.*$(VERSION).*'
endif

build:
	go build ./...

helm-repos:
	helm repo add camunda https://helm.camunda.io/ --force-update
	helm repo add camunda-load-tests https://camunda.github.io/camunda-load-tests-helm/ --force-update
	helm repo add opensearch https://opensearch-project.github.io/helm-charts/ --force-update
	helm repo update

test: helm-repos
	go test -v -timeout 50m -parallel $(PARALLEL) $(run_filter) ./...

update-golden: helm-repos
	go test -v -timeout 50m -parallel $(PARALLEL) $(run_filter) -update-golden ./...

clean:
	rm -rf ../c8-golden-*
