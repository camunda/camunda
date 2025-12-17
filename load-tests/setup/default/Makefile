namespace ?= default
HELM_CHART ?= camunda-platform-8.9
IMAGE_TAG ?= SNAPSHOT

.PHONY: all
all: deploy

.PHONY: deploy
deploy: install-platform install-load-test

# Install Camunda Platform helm chart
.PHONY: install-platform
install-platform:
	@echo "Installing Camunda Platform..."
	helm repo add camunda https://helm.camunda.io/ || true
	helm repo update
	helm upgrade --install $(namespace) camunda/$(HELM_CHART) \
		--wait --timeout 35m0s \
		--namespace $(namespace) \
		--create-namespace \
		--reset-then-reuse-values \
		--render-subchart-notes \
		--set global.image.tag=$(IMAGE_TAG) \
		--set orchestration.image.registry=gcr.io \
		--set orchestration.image.repository=zeebe-io/zeebe \
		--set orchestration.image.tag=$(IMAGE_TAG) \
		-f camunda-platform-values.yaml

# Install Camunda Platform on stable VMs
.PHONY: install-platform-stable
install-platform-stable:
	@echo "Installing Camunda Platform on stable VMs..."
	helm repo add camunda https://helm.camunda.io/ || true
	helm repo update
	helm upgrade --install $(namespace) camunda/$(HELM_CHART) \
		--wait --timeout 35m0s \
		--namespace $(namespace) \
		--create-namespace \
		--reset-then-reuse-values \
		--render-subchart-notes \
		--set global.image.tag=$(IMAGE_TAG) \
		--set orchestration.image.registry=gcr.io \
		--set orchestration.image.repository=zeebe-io/zeebe \
		--set orchestration.image.tag=$(IMAGE_TAG) \
		-f camunda-platform-values.yaml \
		-f values-stable.yaml

# Install load test helm chart
.PHONY: install-load-test
install-load-test:
	@echo "Installing load test..."
	helm repo add camunda-load-tests https://camunda.github.io/camunda-load-tests-helm/ || true
	helm repo update
	helm upgrade --install $(namespace)-test camunda-load-tests/camunda-load-tests \
		--wait --timeout 35m0s \
		--namespace $(namespace) \
		--reuse-values \
		--render-subchart-notes \
		--set global.image.tag=$(IMAGE_TAG)
	@echo "Setting up leader balancing cronjob..."
	@if ! kubectl get cronjob leader-balancer --namespace $(namespace) &>/dev/null; then \
		kubectl create cronjob leader-balancer \
			--namespace $(namespace) \
			--image=curlimages/curl:7.87.0 \
			--schedule="*/10 * * * *" \
			-- curl -L -v -X POST http://camunda:9600/actuator/rebalance; \
	else \
		echo "Leader balancer cronjob already exists"; \
	fi

# Deploy with stable VMs
.PHONY: deploy-stable
deploy-stable: install-platform-stable install-load-test

# Update platform
.PHONY: update-platform
update-platform:
	@echo "Updating Camunda Platform..."
	helm upgrade $(namespace) camunda/$(HELM_CHART) \
		--namespace $(namespace) \
		--reuse-values

# Update load test
.PHONY: update-load-test
update-load-test:
	@echo "Updating load test..."
	helm upgrade $(namespace)-test camunda-load-tests/camunda-load-tests \
		--namespace $(namespace) \
		--reuse-values

# Clean up all resources
.PHONY: clean
clean:
	@echo "Cleaning up load test resources..."
	-kubectl delete cronjob leader-balancer --namespace $(namespace)
	-helm --namespace $(namespace) uninstall $(namespace)-test
	-helm --namespace $(namespace) uninstall $(namespace)
	-kubectl delete -n $(namespace) pvc -l app.kubernetes.io/instance=$(namespace)
	-kubectl delete -n $(namespace) pvc -l app=elasticsearch-master
