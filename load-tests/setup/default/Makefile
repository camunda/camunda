namespace ?= default
secondary_storage ?= elasticsearch
helm_chart ?= camunda-platform-8.9
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

# Conditional values file based on secondary_storage
ifeq ($(secondary_storage),postgresql)
storage_values_file := camunda-platform-values-rdbms.yaml
else ifeq ($(secondary_storage),opensearch)
storage_values_file := camunda-platform-values-opensearch.yaml
else # default is elasticsearch
storage_values_file := camunda-platform-values-elasticsearch.yaml
endif

.PHONY: all
all: install

.PHONY: install
install: install-storage install-platform install-load-test install-elasticsearch-exporter

.PHONY: install-stable
install-stable: install-storage install-platform-stable install-load-test install-elasticsearch-exporter

# Install PostgreSQL database if secondary_storage is postgresql, or OpenSearch if secondary_storage is opensearch
.PHONY: install-storage
install-storage:
ifeq ($(secondary_storage),postgresql)
	@echo "Installing PostgreSQL database for namespace $(namespace)..."
	# Install Postgres database with
	# - default user camunda:camunda
	# - additional extension pg_stat_statements for advanced query performance monitoring (must be activated with postgres user before use)
	helm upgrade --install postgres oci://registry-1.docker.io/bitnamicharts/postgresql \
		--namespace $(namespace) \
		--set global.postgresql.auth.database=camunda \
		--set global.postgresql.auth.username=camunda \
		--set global.postgresql.auth.password=camunda \
		--set global.postgresql.auth.postgresPassword=camunda \
		--set postgresqlSharedPreloadLibraries=pg_stat_statements \
		--set primary.resources.requests.memory=4Gi \
		--set primary.resources.requests.cpu=3000m \
		--set primary.resources.limits.memory=6Gi \
		--set primary.resources.limits.cpu=6000m \
		--set primary.persistence.size=128Gi
else ifeq ($(secondary_storage),opensearch)
	@echo "Installing OpenSearch cluster for namespace $(namespace)..."
	helm upgrade --install opensearch opensearch/opensearch \
		--version "2.31.0" \
		--namespace $(namespace) \
		-f secondary-storage-values-opensearch.yaml
else
	@echo "Skipping secondary storage installation (secondary_storage=$(secondary_storage))"
endif

# Install Elasticsearch Prometheus exporter if secondary_storage is elasticsearch
.PHONY: install-elasticsearch-exporter
install-elasticsearch-exporter:
ifeq ($(secondary_storage),elasticsearch)
	@echo "Installing Elasticsearch Prometheus Exporter for namespace $(namespace)..."
	helm upgrade --install elasticsearch-exporter oci://ghcr.io/prometheus-community/charts/prometheus-elasticsearch-exporter \
    --namespace $(namespace) \
    --version $(prometheus_elasticsearch_exporter_chart_version) \
    --wait --timeout 5m0s \
    -f prometheus-elasticsearch-exporter-values.yaml \
    --set es.uri="http://$(namespace)-elasticsearch:9200"
else
	@echo "Skipping Elasticsearch Prometheus Exporter installation (secondary_storage=$(secondary_storage))"
endif

# Install/upgrade Camunda Platform helm chart
.PHONY: install-platform
install-platform: setup-leader-balancer
	helm upgrade $(namespace) camunda-platform-helm/charts/$(helm_chart) \
		--install \
		--namespace $(namespace) \
		--reset-then-reuse-values \
		--render-subchart-notes \
		-f camunda-platform-values.yaml \
		-f camunda-platform-values-optimize.yaml \
		$(if $(storage_values_file),-f $(storage_values_file)) \
		$(additional_platform_configuration)

# Install/upgrade Camunda Platform on stable VMs
.PHONY: install-platform-stable
install-platform-stable: setup-leader-balancer
	helm upgrade $(namespace) camunda-platform-helm/charts/$(helm_chart) \
		--install \
		--namespace $(namespace) \
		--reset-then-reuse-values \
		--render-subchart-notes \
		-f camunda-platform-values.yaml \
		-f values-stable.yaml \
		$(if $(storage_values_file),-f $(storage_values_file)) \
		$(additional_platform_configuration)

# Install/upgrade load test helm chart
.PHONY: install-load-test
install-load-test:
	helm upgrade $(namespace)-test camunda-load-tests/camunda-load-tests \
		--install \
		--namespace $(namespace) \
		--reuse-values \
		--render-subchart-notes \
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
	helm template $(namespace) camunda/$(helm_chart) \
		-f camunda-platform-values.yaml \
		$(if $(storage_values_file),-f $(storage_values_file)) \
		--output-dir .

.PHONY: template-stable
template-stable:
	helm template $(namespace) camunda/$(helm_chart) \
		-f camunda-platform-values.yaml \
		-f values-stable.yaml \
		$(if $(storage_values_file),-f $(storage_values_file)) \
		--output-dir .

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
