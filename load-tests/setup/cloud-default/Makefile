namespace ?= __NAMESPACE__
curl_image ?= curlimages/curl:7.87.0

# Test parameters — override on the command line or edit defaults here.
# Example: make install starter_rate=100 worker_replicas=6
starter_rate ?=
starter_replicas ?=
worker_replicas ?=
bpmn_process ?=
payload_path ?=

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
realistic: additional_load_test_configuration = -f https://raw.githubusercontent.com/camunda/camunda-load-tests-helm/refs/heads/main/charts/camunda-load-tests/values-realistic-benchmark.yaml
realistic: install-load-test

.PHONY: typical
typical: starter_rate = 50
typical: worker_replicas = 6
typical: bpmn_process = bpmn/typical_process.bpmn
typical: install-load-test

# Install/upgrade load test helm chart
.PHONY: install-load-test
install-load-test:
	. 'credentials.txt' && \
	if [ -n "$(starter_rate)" ]; then test_params="$$test_params --set starter.rate=$(starter_rate)"; fi; \
	if [ -n "$(starter_replicas)" ]; then test_params="$$test_params --set starter.replicas=$(starter_replicas)"; fi; \
	if [ -n "$(worker_replicas)" ]; then test_params="$$test_params --set workers.worker.replicas=$(worker_replicas)"; fi; \
	if [ -n "$(bpmn_process)" ]; then test_params="$$test_params --set starter.bpmnXmlPath=$(bpmn_process)"; fi; \
	if [ -n "$(payload_path)" ]; then test_params="$$test_params --set starter.payloadPath=$(payload_path)"; fi; \
	cmd="helm upgrade $(namespace)-test camunda-load-tests/camunda-load-tests \
		--install \
		--namespace $(namespace) \
		--reuse-values \
		--render-subchart-notes \
		--set saas.enabled=true \
		--set saas.credentials.clientId="$${ZEEBE_CLIENT_ID}" \
		--set saas.credentials.clientSecret="$${ZEEBE_CLIENT_SECRET}" \
		--set saas.credentials.zeebeRestAddress="$${ZEEBE_REST_ADDRESS}" \
		--set saas.credentials.authServer="$${ZEEBE_AUTHORIZATION_SERVER_URL}" \
		--set saas.credentials.authType="OAUTH" \
		--set saas.credentials.zeebeGrpcAddress="$${ZEEBE_GRPC_ADDRESS}" \
		--set saas.credentials.authorizationAudience="$${ZEEBE_TOKEN_AUDIENCE}" \
		$$test_params \
		$(additional_load_test_configuration)"; \
	eval "$$cmd" && \
	{ echo "--- $$(date -Iseconds) ---"; echo "$$cmd"; echo ""; } >> runs.log

.PHONY: clean
clean:
	-helm --namespace $(namespace) uninstall $(namespace)-test
