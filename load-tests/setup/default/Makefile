namespace ?= default
secondary_storage ?= elasticsearch
helm_chart ?= camunda-platform-8.9
curl_image ?= curlimages/curl:7.87.0
camunda_management_url ?= http://camunda:9600

# Conditional values file based on secondary_storage
ifeq ($(secondary_storage),postgresql)
storage_values_file := camunda-platform-values-rdbms.yaml
else # default is elasticsearch
storage_values_file := camunda-platform-values-elasticsearch.yaml
endif

.PHONY: all
all: install

.PHONY: install
install: install-storage install-platform install-load-test

# Install PostgreSQL database if secondary_storage is postgresql
.PHONY: install-storage
install-storage:
ifeq ($(secondary_storage),postgresql)
	@echo "Installing PostgreSQL database for namespace $(namespace)..."
  helm upgrade --install postgres oci://registry-1.docker.io/bitnamicharts/postgresql \
    --namespace $(namespace) \
    --wait --timeout 5m0s \
    --set global.postgresql.auth.database=camunda \
    --set global.postgresql.auth.username=camunda \
    --set global.postgresql.auth.password=camunda \
    --set primary.resources.requests.memory=4Gi \
    --set primary.resources.requests.cpu=3000m \
    --set primary.resources.limits.memory=6Gi \
    --set primary.resources.limits.cpu=6000m \
    --set primary.persistence.size=10Gi
else
	@echo "Skipping secondary storage installation (secondary_storage=$(secondary_storage))"
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
		$(if $(storage_values_file),-f $(storage_values_file))

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
		$(if $(storage_values_file),-f $(storage_values_file))

# Install/upgrade load test helm chart
.PHONY: install-load-test
install-load-test:
	helm upgrade $(namespace)-test camunda-load-tests/camunda-load-tests \
		--install \
		--namespace $(namespace) \
		--reuse-values \
		--render-subchart-notes

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
	-kubectl delete -n $(namespace) pvc -l app.kubernetes.io/instance=$(namespace)
	-kubectl delete -n $(namespace) pvc -l app=elasticsearch-master
	-kubectl delete -n $(namespace) cronjob leader-balancer
ifeq ($(secondary_storage),postgresql)
	@echo "Cleaning up PostgreSQL database for namespace $(namespace)..."
	-helm --namespace $(namespace) uninstall postgres
	-kubectl delete -n $(namespace) pvc -l app.kubernetes.io/name=postgresql
endif
