namespace ?= __NAMESPACE__
curl_image ?= curlimages/curl:7.87.0
# Optional: additional Helm configuration for the load test release.
# Use this to pass extra `--set`/`-f` flags when installing/upgrading the load tests.
# See the load test README for example values and guidance.

common_configuration = \
	--set global.image.repository=registry.camunda.cloud/team-zeebe \
	--set global.image.pullSecrets[0].name=harbor-registry

additional_load_test_configuration ?= $(common_configuration)

realistic_configuration = $(common_configuration) \
	-f load-test-values-realistic-benchmark.yaml

typical_configuration = $(common_configuration) \
	--set starter.rate=50 \
	--set workers.worker.replicas=6 \
	--set starter.bpmnXmlPath=bpmn/typical_process.bpmn

SHELL = /bin/bash

.PHONY: all
all: install

.PHONY: install
install: install-load-test

.PHONY: realistic
realistic: additional_load_test_configuration = $(realistic_configuration)
realistic: install-load-test

.PHONY: typical
typical: additional_load_test_configuration = $(typical_configuration)
typical: install-load-test

saas_helm_flags = \
		--set saas.enabled=true \
		--set saas.credentials.clientId="$${ZEEBE_CLIENT_ID}" \
		--set saas.credentials.clientSecret="$${ZEEBE_CLIENT_SECRET}" \
		--set saas.credentials.zeebeRestAddress="$${ZEEBE_REST_ADDRESS}" \
		--set saas.credentials.authServer="$${ZEEBE_AUTHORIZATION_SERVER_URL}" \
		--set saas.credentials.authType="OAUTH" \
		--set saas.credentials.zeebeGrpcAddress="$${ZEEBE_GRPC_ADDRESS}" \
		--set saas.credentials.authorizationAudience="$${ZEEBE_TOKEN_AUDIENCE}" \
		-f load-test-values.yaml \
		-f load-test-values-generated.yaml \
		$(additional_load_test_configuration)

# Install/upgrade load test helm chart
.PHONY: install-load-test
install-load-test:
	. 'credentials.txt' && \
	helm upgrade $(namespace)-test camunda-load-tests/camunda-load-tests \
		--install \
		--namespace $(namespace) \
		--reset-then-reuse-values \
		--render-subchart-notes \
		$(saas_helm_flags)

template_output_dir ?= .

.PHONY: template realistic-template typical-template
template:
	. 'credentials.txt' && \
	helm template $(namespace)-test camunda-load-tests/camunda-load-tests \
		--namespace $(namespace) \
		$(saas_helm_flags) \
		--output-dir $(template_output_dir)

realistic-template: additional_load_test_configuration = $(realistic_configuration)
realistic-template: template

typical-template: additional_load_test_configuration = $(typical_configuration)
typical-template: template

.PHONY: clean
clean:
	-helm --namespace $(namespace) uninstall $(namespace)-test
