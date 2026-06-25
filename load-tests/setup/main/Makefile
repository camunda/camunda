namespace ?= __NAMESPACE__
secondary_storage ?= __STORAGE_TYPE__
template_output_dir ?= .
helm_chart ?= camunda-platform-8.10
enable_optimize ?= __ENABLE_OPTIMIZE__
# Enable the chaos-killer CronJob (randomly deletes one matching pod per run).
# Use named targets (make install-chaos) or pass directly: make install chaos=true
chaos ?= false
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

helm_chart_platform := charts/camunda-platform-helm/charts/$(helm_chart)
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
# Starting with the defaults, which are always applied and set common baseline configuration for all
# set ups. The other values files are focused and only contain the necessary overrides for each storage type.
platform_values := -f camunda-platform-values-defaults.yaml

# The configuration for the "load test setup" local Helm Chart
helm_chart_load_test_setup := charts/load-test-setup
load_test_setup_values := -f load-test-setup-values.yaml
additional_load_test_setup_configuration ?=

# The Docker image tag for the load test metrics exporter
metrics_exporter_image_tag := latest

# Makefile-side load-test flags. Separate from `additional_load_test_configuration`,
# which CI sets on the make command line and would suppress `+=` here.
_secondary_storage_load_test_flags :=

# Add secondary storage values
ifneq ($(filter $(secondary_storage),postgresql mysql mariadb mssql oracle),)
	platform_values += -f camunda-platform-values-rdbms.yaml
else ifeq ($(secondary_storage),none)
	_secondary_storage_load_test_flags += --set global.extraConfig.load-tester.monitor-data-availability=false
endif
platform_values += -f camunda-platform-values-$(secondary_storage).yaml

# Disable Optimize if not enabled
ifneq ($(enable_optimize),true)
	platform_values += --set optimize.enabled=false

	ifneq ($(filter $(secondary_storage),postgresql mysql mariadb mssql oracle),)
		platform_values += --set elasticsearch.enabled=false
	endif
else
	# Optimize needs Elasticsearch or OpenSearch, so enable Elasticsearch if
	# we are not already creating a cluster for secondary storage.
	ifeq ($(filter $(secondary_storage),elasticsearch opensearch),)
		platform_values += -f camunda-platform-values-optimize-elasticsearch.yaml
	endif
endif

# Platform values with stable configuration
platform_values_stable := $(platform_values) -f values-stable.yaml

ifeq ($(secondary_storage),elasticsearch)
	install_es_prom_exporter := true
	es_prom_exporter_es_uri := http://elastic:9200
	additional_load_test_setup_configuration += --set metricsExporter.database.url=http://elastic:9200
else ifeq ($(secondary_storage),opensearch)
	install_es_prom_exporter := true
	es_prom_exporter_es_uri := http://opensearch:9200
	additional_load_test_setup_configuration += --set metricsExporter.database.url=http://opensearch:9200
else ifeq ($(enable_optimize),true)
	# Here, the secondary storage is either none or a RDBMS database.
	# Elasticsearch is always used in this case as the backend for Optimize.
	install_es_prom_exporter := true
	es_prom_exporter_es_uri := http://elastic:9200
	additional_load_test_setup_configuration += --set metricsExporter.database.url=http://elastic:9200
else
	install_es_prom_exporter := false
endif

ifeq ($(chaos),true)
	additional_load_test_setup_configuration += --set chaosKiller.enabled=true
endif

.PHONY: all
all: install

.PHONY: install
install: check-deadline install-load-test-setup install-storage install-platform install-load-test install-elasticsearch-exporter

.PHONY: install-stable
install-stable: check-deadline install-load-test-setup install-storage install-platform-stable install-load-test install-elasticsearch-exporter

# Fail fast if the namespace TTL deadline (read from load-test-setup-values.yaml,
# the single source of truth) is today or in the past — the TTL cleanup
# workflow will delete the namespace and undo the install. To extend, edit
# `deadlineDate` in load-test-setup-values.yaml and `make install-load-test-setup`.
.PHONY: check-deadline
check-deadline:
	@deadline_date=$$(awk -F'"' '/^[[:space:]]*deadlineDate:/ {print $$2; exit}' load-test-setup-values.yaml); \
	if [ -z "$$deadline_date" ]; then \
		echo "ERROR: could not parse deadlineDate from load-test-setup-values.yaml."; \
		exit 1; \
	fi; \
	today=$$(date +%Y-%m-%d); \
	if [ "$$today" \> "$$deadline_date" ] || [ "$$today" = "$$deadline_date" ]; then \
		echo "ERROR: namespace deadline date ($$deadline_date) is today or earlier (today: $$today)."; \
		echo "       The TTL cleanup workflow will delete this namespace."; \
		echo "       To extend, edit deadlineDate in load-test-setup-values.yaml and run:"; \
		echo "         make install-load-test-setup"; \
		exit 1; \
	fi

# The namespace **must** exist before trying to install/upgrade with Helm:
# normally, Helm should be able to create it itself, but our camunda-benchmark
# Teleport/RBAC configuration returns "permission denied" when trying to list
# secrets on a non-existent namespace (instead of "not found"), which stop Helm
# prematurely with an error instead of proceeding with the creation of a new
# namespace.
.PHONY: create-namespace
create-namespace:
	@echo "Making sure the namespace $(namespace) exists..."
	kubectl get namespace "$(namespace)" > /dev/null 2>&1 || kubectl create namespace "$(namespace)"

# Apply the camunda-credentials secret. Idempotent: rerunning after a TTL
# deletion reapplies the SAME credentials, so the orchestration secret in
# load-test-values.yaml stays in sync with the live secret.
.PHONY: create-credentials
create-credentials:
	@echo "Applying camunda-credentials secret for namespace $(namespace)..."
	kubectl apply -n $(namespace) -f resources/camunda-credentials.yaml

# Install secondary storage based on configuration (PostgreSQL, MySQL, MariaDB, MSSQL, Oracle, OpenSearch)
.PHONY: install-storage
install-storage:
ifeq ($(secondary_storage),postgresql)
	@echo "Installing PostgreSQL database for namespace $(namespace)..."
	# Install Postgres database - configuration provided via camunda-platform-values-defaults.yaml, camunda-platform-values-rdbms.yaml and camunda-platform-values-postgresql.yaml
	helm upgrade --install postgresql oci://registry-1.docker.io/bitnamicharts/postgresql \
		--namespace $(namespace) \
		$(platform_values)
else ifeq ($(secondary_storage),mysql)
	@echo "Installing MySQL database for namespace $(namespace)..."
	# Install MySQL database - configuration provided via camunda-platform-values-defaults.yaml, camunda-platform-values-rdbms.yaml and camunda-platform-values-mysql.yaml
	helm upgrade --install mysql oci://registry-1.docker.io/bitnamicharts/mysql \
		--namespace $(namespace) \
		$(platform_values)
else ifeq ($(secondary_storage),mariadb)
	@echo "Installing MariaDB database for namespace $(namespace)..."
	# Install MariaDB database - configuration provided via camunda-platform-values-defaults.yaml, camunda-platform-values-rdbms.yaml and camunda-platform-values-mariadb.yaml
	helm upgrade --install mariadb oci://registry-1.docker.io/bitnamicharts/mariadb \
		--namespace $(namespace) \
		$(platform_values)
else ifeq ($(secondary_storage),mssql)
	@echo "Installing MSSQL database for namespace $(namespace)..."
	# Deploy MSSQL via plain Kubernetes manifests (mssql.yaml) since no good public chart is available
	kubectl apply --namespace $(namespace) -f databases/mssql.yaml
else ifeq ($(secondary_storage),oracle)
	@echo "Installing Oracle database for namespace $(namespace)..."
	# Deploy Oracle Free 23c via plain Kubernetes manifests (oracle.yaml) since no maintained public chart is available
	kubectl apply --namespace $(namespace) -f databases/oracle.yaml
else ifeq ($(secondary_storage),opensearch)
	@echo "Installing OpenSearch cluster for namespace $(namespace)..."
	helm upgrade --install opensearch opensearch/opensearch \
		--version "2.31.0" \
		--namespace $(namespace) \
		$(platform_values)
else
	@echo "Skipping secondary storage installation (secondary_storage=$(secondary_storage))"
endif

# Install Elasticsearch Prometheus exporter if Elasticsearch/OpenSearch is deployed (secondary storage or Optimize)
.PHONY: install-elasticsearch-exporter
install-elasticsearch-exporter:
ifeq ($(install_es_prom_exporter),true)
	@echo "Installing Elasticsearch Prometheus Exporter for namespace $(namespace)..."
	helm upgrade --install elasticsearch-exporter oci://ghcr.io/prometheus-community/charts/prometheus-elasticsearch-exporter \
    --namespace $(namespace) \
    --version $(prometheus_elasticsearch_exporter_chart_version) \
    --wait --timeout 5m0s \
    -f prometheus-elasticsearch-exporter-values.yaml \
    --set "es.uri=$(es_prom_exporter_es_uri)"
else
	@echo "Skipping Elasticsearch Prometheus Exporter installation (secondary_storage=$(secondary_storage))"
endif

# Install/upgrade Camunda Platform helm chart
.PHONY: install-platform
install-platform:
	helm upgrade $(namespace) $(helm_chart_platform) \
		--install \
		--namespace $(namespace) \
		--reset-then-reuse-values \
		--render-subchart-notes \
		$(platform_values) \
		$(_scenario_platform_flags) \
		$(additional_platform_configuration)

# Install/upgrade Camunda Platform on stable VMs
.PHONY: install-platform-stable
install-platform-stable:
	helm upgrade $(namespace) $(helm_chart_platform) \
		--install \
		--namespace $(namespace) \
		--reset-then-reuse-values \
		--render-subchart-notes \
		$(platform_values_stable) \
		$(_scenario_platform_flags) \
		$(additional_platform_configuration)

# Install the load-test-setup Helm Chart
.PHONY: install-load-test-setup
install-load-test-setup: create-namespace
	helm upgrade load-test-setup $(helm_chart_load_test_setup) \
		--install \
		--namespace $(namespace) \
		--reset-then-reuse-values \
		--render-subchart-notes \
		--take-ownership --create-namespace \
		$(load_test_setup_values) $(additional_load_test_setup_configuration)

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

# Generates templates from the Camunda Platform helm chart
.PHONY: template
template:
	helm template $(namespace) $(helm_chart_platform) \
		--namespace $(namespace) \
		$(platform_values) \
		$(_scenario_platform_flags) \
		$(additional_platform_configuration) \
		--output-dir $(template_output_dir)

.PHONY: template-stable
template-stable:
	helm template $(namespace) $(helm_chart_platform) \
		--namespace $(namespace) \
		$(platform_values_stable) \
		$(_scenario_platform_flags) \
		$(additional_platform_configuration) \
		--output-dir $(template_output_dir)

.PHONY: template-load-test-setup
template-load-test-setup:
	helm template load-test-setup $(helm_chart_load_test_setup) \
		--namespace $(namespace) \
		$(load_test_setup_values) \
		$(additional_load_test_setup_configuration) \
		--output-dir $(template_output_dir)

# Renders the load-test-setup chart with the chaos-killer enabled.
.PHONY: template-load-test-setup-chaos
template-load-test-setup-chaos:
	$(MAKE) template-load-test-setup chaos=true

# Generates templates from the load test helm chart
.PHONY: template-load-test
template-load-test:
	helm template $(namespace)-test $(helm_chart_load_tests) \
		--namespace $(namespace) \
		-f load-test-values.yaml \
		$(_scenario_load_test_flags) \
		$(_secondary_storage_load_test_flags) \
		$(additional_load_test_configuration) \
		--output-dir $(template_output_dir)

# Print the resolved scenario flags without running any Helm commands
.PHONY: print-scenario
print-scenario:
	@echo "Scenario:         $(if $(scenario),$(scenario),(none — chart defaults apply))"
	@echo "Load test flags:  $(if $(_scenario_load_test_flags),$(_scenario_load_test_flags),(none))"
	@echo "Platform flags:   $(if $(_scenario_platform_flags),$(_scenario_platform_flags),(none))"

# Chaos shortcuts — install (or render) with the chaos-killer CronJob enabled.
.PHONY: install-chaos install-stable-chaos
install-chaos:
	$(MAKE) install chaos=true
install-stable-chaos:
	$(MAKE) install-stable chaos=true

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
	@echo "Deleting namespace $(namespace) and waiting for finalization..."
	@# `--wait` (default) blocks until the namespace is fully gone. We intentionally
	@# wait so that a subsequent `make install` (or `make clean install`) doesn't
	@# race against finalizers — applying manifests into a still-terminating
	@# namespace errors out with "namespace X is being terminated".
	-kubectl delete namespace $(namespace) --ignore-not-found --wait
