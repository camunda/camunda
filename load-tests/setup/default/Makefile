namespace ?= default
HELM_CHART ?= camunda-platform-8.9
IMAGE_TAG ?= SNAPSHOT

.PHONY: all
all: install

.PHONY: install
install: install-platform install-load-test

# Install Camunda Platform helm chart
.PHONY: install-platform
install-platform:
	@echo "Installing Camunda Platform..."
	helm repo add camunda https://helm.camunda.io/
	helm repo update
	helm upgrade --install $(namespace) camunda/$(HELM_CHART) \
		--namespace $(namespace) \
		--create-namespace \
		--wait \
		--timeout 35m0s \
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
	helm repo add camunda https://helm.camunda.io/
	helm repo update
	helm upgrade --install $(namespace) camunda/$(HELM_CHART) \
		--namespace $(namespace) \
		--create-namespace \
		--wait \
		--timeout 35m0s \
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
	@echo "Installing Load Test..."
	helm repo add camunda-load-tests https://camunda.github.io/camunda-load-tests-helm/
	helm repo update
	helm upgrade --install $(namespace)-test camunda-load-tests/camunda-load-tests \
		--namespace $(namespace) \
		--wait \
		--timeout 35m0s \
		--reuse-values \
		--render-subchart-notes \
		--set global.image.tag=$(IMAGE_TAG)
	@echo "Setting up leader balancing..."
	-kubectl create cronjob leader-balancer \
		--namespace $(namespace) \
		--image=curlimages/curl:7.87.0 \
		--schedule="*/10 * * * *" \
		-- curl -L -v -X POST http://camunda:9600/actuator/rebalance

# Install both platform and load test on stable VMs
.PHONY: install-stable
install-stable: install-platform-stable install-load-test

# Update platform installation
.PHONY: update-platform
update-platform:
	helm upgrade $(namespace) camunda/$(HELM_CHART) \
		--namespace $(namespace) \
		--reuse-values \
		-f camunda-platform-values.yaml

# Update load test installation
.PHONY: update-load-test
update-load-test:
	helm upgrade $(namespace)-test camunda-load-tests/camunda-load-tests \
		--namespace $(namespace) \
		--reuse-values

# Update both
.PHONY: update
update: update-platform update-load-test

.PHONY: clean
clean:
	-helm --namespace $(namespace) uninstall $(namespace)-test
	-helm --namespace $(namespace) uninstall $(namespace)
	-kubectl delete -n $(namespace) pvc -l app.kubernetes.io/instance=$(namespace)
	-kubectl delete -n $(namespace) pvc -l app=elasticsearch-master
	-kubectl delete cronjob leader-balancer --namespace $(namespace)
