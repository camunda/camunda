namespace ?= __NAMESPACE__
curl_image ?= curlimages/curl:7.87.0
# Optional: additional Helm configuration for the load test release.
# Use this to pass extra `--set`/`-f` flags when installing/upgrading the load tests.
# See the load test README for example values and guidance.
additional_load_test_configuration ?= --set global.image.repository=registry.camunda.cloud/team-zeebe --set global.image.pullSecrets[0].name=harbor-registry

SHELL = /bin/bash

.PHONY: all
all: install

.PHONY: install
install: install-load-test

.PHONY: realistic
realistic: additional_load_test_configuration = -f https://raw.githubusercontent.com/camunda/camunda-load-tests-helm/refs/heads/main/charts/camunda-load-tests/values-realistic-benchmark.yaml --set global.image.repository=registry.camunda.cloud/team-zeebe --set global.image.pullSecrets[0].name=harbor-registry
realistic: install-load-test

.PHONY: typical
typical: additional_load_test_configuration = --set starter.rate=50 --set workers.worker.replicas=6 --set starter.bpmnXmlPath=bpmn/typical_process.bpmn --set global.image.repository=registry.camunda.cloud/team-zeebe --set global.image.pullSecrets[0].name=harbor-registry
typical: install-load-test

# Install/upgrade load test helm chart
.PHONY: install-load-test
install-load-test:
	. 'credentials.txt' && \
	helm upgrade $(namespace)-test camunda-load-tests/camunda-load-tests \
		--install \
		--namespace $(namespace) \
		--reset-then-reuse-values \
		--render-subchart-notes \
		--set saas.enabled=true \
		--set saas.credentials.clientId="$${ZEEBE_CLIENT_ID}" \
		--set saas.credentials.clientSecret="$${ZEEBE_CLIENT_SECRET}" \
		--set saas.credentials.zeebeRestAddress="$${ZEEBE_REST_ADDRESS}" \
		--set saas.credentials.authServer="$${ZEEBE_AUTHORIZATION_SERVER_URL}" \
		--set saas.credentials.authType="OAUTH" \
		--set saas.credentials.zeebeGrpcAddress="$${ZEEBE_GRPC_ADDRESS}" \
		--set saas.credentials.authorizationAudience="$${ZEEBE_TOKEN_AUDIENCE}" \
		-f load-test-values.yaml \
		$(additional_load_test_configuration)

.PHONY: clean
clean:
	-helm --namespace $(namespace) uninstall $(namespace)-test
