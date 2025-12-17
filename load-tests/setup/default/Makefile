namespace ?= default
helm_chart ?= camunda-platform-8.9
curl_image ?= curlimages/curl:7.87.0
camunda_management_url ?= http://camunda:9600

.PHONY: all
all: install

.PHONY: install
install: install-platform install-load-test

# Install/upgrade Camunda Platform helm chart
.PHONY: install-platform
install-platform: setup-leader-balancer
	helm upgrade --install $(namespace) camunda/$(helm_chart) \
		--namespace $(namespace) \
		--wait --timeout 35m0s \
		--reuse-values \
		--render-subchart-notes \
		-f camunda-platform-values.yaml

# Install/upgrade Camunda Platform on stable VMs
.PHONY: install-platform-stable
install-platform-stable: setup-leader-balancer
	helm upgrade --install $(namespace) camunda/$(helm_chart) \
		--namespace $(namespace) \
		--wait --timeout 35m0s \
		--reuse-values \
		--render-subchart-notes \
		-f camunda-platform-values.yaml \
		-f values-stable.yaml

# Install/upgrade load test helm chart
.PHONY: install-load-test
install-load-test:
	helm upgrade --install $(namespace)-test camunda-load-tests/camunda-load-tests \
		--namespace $(namespace) \
		--wait --timeout 35m0s \
		--reuse-values \
		--render-subchart-notes

# Setup leader balancing cronjob
.PHONY: setup-leader-balancer
setup-leader-balancer:
	@if ! kubectl get cronjob leader-balancer --namespace $(namespace) &>/dev/null; then \
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
		--output-dir .

.PHONY: template-stable
template-stable:
	helm template $(namespace) camunda/$(helm_chart) \
		-f camunda-platform-values.yaml \
		-f values-stable.yaml \
		--output-dir .

.PHONY: clean
clean:
	-helm --namespace $(namespace) uninstall $(namespace)
	-helm --namespace $(namespace) uninstall $(namespace)-test
	-kubectl delete -n $(namespace) pvc -l app.kubernetes.io/instance=$(namespace)
	-kubectl delete -n $(namespace) pvc -l app=elasticsearch-master
	-kubectl delete -n $(namespace) cronjob leader-balancer
