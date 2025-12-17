namespace ?= default
helm_chart ?= camunda-platform-8.9

.PHONY: all
all: install

# Install both Camunda Platform and load test
.PHONY: install
install: install-platform install-load-test

# Install Camunda Platform helm chart
.PHONY: install-platform
install-platform:
	@echo "Adding Camunda Helm repo..."
	helm repo add camunda https://helm.camunda.io/
	helm repo update
	@echo "Installing Camunda Platform..."
	helm upgrade --install $(namespace) camunda/$(helm_chart) \
		--wait --timeout 35m0s \
		--namespace $(namespace) \
		--create-namespace \
		--render-subchart-notes \
		-f camunda-platform-values.yaml

# Install Camunda Platform with stable VMs
.PHONY: install-platform-stable
install-platform-stable:
	@echo "Adding Camunda Helm repo..."
	helm repo add camunda https://helm.camunda.io/
	helm repo update
	@echo "Installing Camunda Platform on stable VMs..."
	helm upgrade --install $(namespace) camunda/$(helm_chart) \
		--wait --timeout 35m0s \
		--namespace $(namespace) \
		--create-namespace \
		--render-subchart-notes \
		-f camunda-platform-values.yaml \
		-f values-stable.yaml

# Install load test helm chart
.PHONY: install-load-test
install-load-test:
	@echo "Adding Load test Helm repo..."
	helm repo add camunda-load-tests https://camunda.github.io/camunda-load-tests-helm/
	helm repo update
	@echo "Installing load test..."
	helm upgrade --install $(namespace)-test camunda-load-tests/camunda-load-tests \
		--wait --timeout 35m0s \
		--namespace $(namespace) \
		--render-subchart-notes
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

# Update Camunda Platform
.PHONY: update-platform
update-platform:
	helm repo update
	helm upgrade --namespace $(namespace) $(namespace) camunda/$(helm_chart) \
		--reuse-values \
		-f camunda-platform-values.yaml

# Update load test
.PHONY: update-load-test
update-load-test:
	helm repo update
	helm upgrade --namespace $(namespace) $(namespace)-test camunda-load-tests/camunda-load-tests \
		--reuse-values

# Uninstall both platform and load test
.PHONY: clean
clean:
	@echo "Uninstalling load test..."
	-helm --namespace $(namespace) uninstall $(namespace)-test
	@echo "Uninstalling Camunda Platform..."
	-helm --namespace $(namespace) uninstall $(namespace)
	@echo "Deleting cronjobs..."
	-kubectl delete cronjob leader-balancer --namespace $(namespace)
	@echo "Deleting PVCs..."
	-kubectl delete -n $(namespace) pvc -l app.kubernetes.io/instance=$(namespace)
	-kubectl delete -n $(namespace) pvc -l app=elasticsearch-master
