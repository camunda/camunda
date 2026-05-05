namespace ?= __NAMESPACE__
secondary_storage ?= __STORAGE_TYPE__
helm_chart ?= camunda-platform-8.9
enable_optimize ?= __ENABLE_OPTIMIZE__
curl_image ?= curlimages/curl:7.87.0
camunda_management_url ?= http://camunda:9600
prometheus_elasticsearch_exporter_chart_version ?= 7.2.1
# Optional: additional Helm configuration for the Camunda Platform release.
# Use this to pass extra `--set`/`-f` flags, for example:
#   make install additional_platform_configuration="--set zeebeGateway.env[0].name=FOO --set zeebeGateway.env[0].value=bar"
# See the load test README for more examples and details.
additional_platform_configuration ?=
# Optional: additional Helm configuration for the load test release.
# Use this to pass extra `--set`/`-f` flags when installing/upgrading the load tests.
# See the load test README for example values and guidance.
additional_load_test_configuration ?=

helm_chart_platform := camunda-platform-helm/charts/$(helm_chart)
helm_chart_load_tests := camunda-load-tests/camunda-load-tests

# Scenario: controls the workload profile for the load test.
# Options: latency, realistic, typical, max, archiver
# Use named targets (make max) or pass directly: make install scenario=max
scenario ?=

ifeq ($(scenario),latency)
_scenario_load_test_flags := --set starter.rate=1 --set workers.worker.replicas=1
_scenario_platform_flags  :=
else ifeq ($(scenario),realistic)
_scenario_load_test_flags := -f https://raw.githubusercontent.com/camunda/camunda-load-tests-helm/refs/heads/main/charts/camunda-load-tests/values-realistic-benchmark.yaml
_scenario_platform_flags  :=
else ifeq ($(scenario),typical)
_scenario_load_test_flags := --set starter.rate=50 --set workers.worker.replicas=6 --set starter.bpmnXmlPath=bpmn/typical_process.bpmn
_scenario_platform_flags  :=
else ifeq ($(scenario),max)
_scenario_load_test_flags := --set starter.rate=300
_scenario_platform_flags  := --set-file 'orchestration.extraConfiguration[1].content=./camunda-platform-override-values.yaml'
else ifeq ($(scenario),archiver)
_scenario_load_test_flags := --set starter.rate=1 --set starter.rateDuration=10m --set starter.processId=multiInstanceElements --set starter.bpmnXmlPath=bpmn/multiInstanceElements.bpmn --set starter.payloadPath=bpmn/multiInstanceElementsPayload.json --set workers.worker.replicas=0
_scenario_platform_flags  :=
else
_scenario_load_test_flags :=
_scenario_platform_flags  :=
endif

# Construct platform values files based on configuration
platform_values := -f camunda-platform-values.yaml

# Makefile-side load-test flags. Separate from `additional_load_test_configuration`,
# which CI sets on the make command line and would suppress `+=` here.
_secondary_storage_load_test_flags :=

# Add secondary storage values
ifeq ($(secondary_storage),postgresql)
platform_values += -f camunda-platform-values-rdbms.yaml
else ifeq ($(secondary_storage),opensearch)
platform_values += -f camunda-platform-values-opensearch.yaml
else ifeq ($(secondary_storage),none)
platform_values += -f camunda-platform-no-secondary-storage.yaml
_secondary_storage_load_test_flags += --set global.extraConfig.load-tester.monitor-data-availability=false
endif

# Disable Optimize if not enabled
ifneq ($(enable_optimize),true)
platform_values += --set optimize.enabled=false
ifeq ($(secondary_storage),postgresql)
platform_values += --set elasticsearch.enabled=false
endif
endif

# Platform values with stable configuration
platform_values_stable := $(platform_values) -f values-stable.yaml

.PHONY: all
all: install

.PHONY: install
install: install-storage create-credentials install-platform install-load-test install-elasticsearch-exporter

.PHONY: install-stable
install-stable: install-storage create-credentials install-platform-stable install-load-test install-elasticsearch-exporter

# Install PostgreSQL database if secondary_storage is postgresql, or OpenSearch if secondary_storage is opensearch
.PHONY: install-storage
install-storage:
ifeq ($(secondary_storage),postgresql)
	@echo "Installing PostgreSQL database for namespace $(namespace)..."
	# Install Postgres database - configuration provided via camunda-platform-values.yaml and camunda-platform-values-rdbms.yaml
	helm upgrade --install postgres oci://registry-1.docker.io/bitnamicharts/postgresql \
		--namespace $(namespace) \
		$(platform_values)
else ifeq ($(secondary_storage),opensearch)
	@echo "Installing OpenSearch cluster for namespace $(namespace)..."
	helm upgrade --install opensearch opensearch/opensearch \
		--version "2.31.0" \
		--namespace $(namespace) \
		$(platform_values)
else
	@echo "Skipping secondary storage installation (secondary_storage=$(secondary_storage))"
endif

# Create camunda-credentials secret
.PHONY: create-credentials
create-credentials:
	@echo "Creating camunda-credentials secret for namespace $(namespace)..."
	./createCredsLoadTest.sh $(namespace)

# Install Elasticsearch Prometheus exporter if secondary_storage is elasticsearch
.PHONY: install-elasticsearch-exporter
install-elasticsearch-exporter:
ifeq ($(secondary_storage),elasticsearch)
	@echo "Installing Elasticsearch Prometheus Exporter for namespace $(namespace)..."
	helm upgrade --install elasticsearch-exporter oci://ghcr.io/prometheus-community/charts/prometheus-elasticsearch-exporter \
    --namespace $(namespace) \
    --version $(prometheus_elasticsearch_exporter_chart_version) \
    --wait --timeout 5m0s \
    -f prometheus-elasticsearch-exporter-values.yaml
else
	@echo "Skipping Elasticsearch Prometheus Exporter installation (secondary_storage=$(secondary_storage))"
endif

# Install/upgrade Camunda Platform helm chart
.PHONY: install-platform
install-platform: setup-leader-balancer
	helm upgrade $(namespace) $(helm_chart_platform) \
		--install \
		--namespace $(namespace) \
		--reset-then-reuse-values \
		--render-subchart-notes \
		$(platform_values) \
		-f values-preemptible.yaml \
		$(_scenario_platform_flags) \
		$(additional_platform_configuration)

# Install/upgrade Camunda Platform on stable VMs
.PHONY: install-platform-stable
install-platform-stable: setup-leader-balancer
	helm upgrade $(namespace) $(helm_chart_platform) \
		--install \
		--namespace $(namespace) \
		--reset-then-reuse-values \
		--render-subchart-notes \
		$(platform_values_stable) \
		$(_scenario_platform_flags) \
		$(additional_platform_configuration)

# Install/upgrade load test helm chart
.PHONY: install-load-test
install-load-test:
	helm upgrade $(namespace)-test $(helm_chart_load_tests) \
		--install \
		--namespace $(namespace) \
		--reset-then-reuse-values \
		--render-subchart-notes \
		-f load-test-values.yaml \
		$(_scenario_load_test_flags) \
		$(_secondary_storage_load_test_flags) \
		$(additional_load_test_configuration)

# Setup leader balancing cronjob
.PHONY: setup-leader-balancer
setup-leader-balancer:
	@if ! kubectl get cronjob leader-balancer --namespace $(namespace) >/dev/null 2>&1; then \
		kubectl create cronjob leader-balancer \
			--namespace $(namespace) \
			--image=$(curl_image) \
			--schedule="*/10 * * * *" \
			-- curl -L -v -X POST $(camunda_management_url)/actuator/rebalance; \
		echo "Leader balancer cronjob created"; \
	else \
		echo "Leader balancer cronjob already exists"; \
	fi

# Generates templates from the Camunda Platform helm chart
.PHONY: template
template:
	helm template $(namespace) $(helm_chart_platform) \
		$(platform_values) \
		-f values-preemptible.yaml \
		$(_scenario_platform_flags) \
		$(additional_platform_configuration) \
		--output-dir .

.PHONY: template-stable
template-stable:
	helm template $(namespace) $(helm_chart_platform) \
		$(platform_values_stable) \
		$(_scenario_platform_flags) \
		$(additional_platform_configuration) \
		--output-dir .

# Generates templates from the load test helm chart
.PHONY: template-load-test
template-load-test:
	helm template $(namespace)-test $(helm_chart_load_tests) \
		-f load-test-values.yaml \
		$(_scenario_load_test_flags) \
		$(_secondary_storage_load_test_flags) \
		$(additional_load_test_configuration) \
		--output-dir .

# Print the resolved scenario flags without running any Helm commands
.PHONY: print-scenario
print-scenario:
	@echo "Scenario:         $(if $(scenario),$(scenario),(none — chart defaults apply))"
	@echo "Load test flags:  $(if $(_scenario_load_test_flags),$(_scenario_load_test_flags),(none))"
	@echo "Platform flags:   $(if $(_scenario_platform_flags),$(_scenario_platform_flags),(none))"

# Workload scenario shortcuts — each runs 'make install' with the corresponding scenario profile.
# For stable VMs, use: make install-stable scenario=<name>
.PHONY: latency realistic typical max archiver
latency:
	$(MAKE) install scenario=latency
realistic:
	$(MAKE) install scenario=realistic
typical:
	$(MAKE) install scenario=typical
max:
	$(MAKE) install scenario=max
archiver:
	$(MAKE) install scenario=archiver

.PHONY: clean
clean:
	-helm --namespace $(namespace) uninstall $(namespace)
	-helm --namespace $(namespace) uninstall $(namespace)-test
	-helm --namespace $(namespace) uninstall elasticsearch-exporter
	-kubectl delete -n $(namespace) pvc -l app.kubernetes.io/instance=$(namespace)
	-kubectl delete -n $(namespace) pvc -l app=elasticsearch-master
	-kubectl delete -n $(namespace) cronjob leader-balancer
ifeq ($(secondary_storage),postgresql)
	@echo "Cleaning up PostgreSQL database for namespace $(namespace)..."
	-helm --namespace $(namespace) uninstall postgres
	-kubectl delete -n $(namespace) pvc -l app.kubernetes.io/name=postgresql
else ifeq ($(secondary_storage),opensearch)
	@echo "Cleaning up OpenSearch cluster for namespace $(namespace)..."
	-helm --namespace $(namespace) uninstall opensearch
	-kubectl delete -n $(namespace) pvc -l app.kubernetes.io/name=opensearch
endif
