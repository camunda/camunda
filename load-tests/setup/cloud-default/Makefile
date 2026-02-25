namespace ?= __NAMESPACE__
curl_image ?= curlimages/curl:7.87.0
# Optional: additional Helm configuration for the load test release.
# Use this to pass extra `--set`/`-f` flags when installing/upgrading the load tests.
# See the load test README for example values and guidance.
additional_load_test_configuration ?=

SHELL = /bin/bash

.PHONY: all
all: install

.PHONY: install
install: credentials install-load-test

.PHONY: credentials
credentials:
	. 'credentials.txt'

# Install/upgrade load test helm chart
.PHONY: install-load-test
install-load-test:
	helm upgrade $(namespace)-test camunda-load-tests/camunda-load-tests \
		--install \
		--namespace $(namespace) \
		--reuse-values \
		--render-subchart-notes \
		--set saas.enabled=true \
		--set saas.credentials.clientId="$(ZEEBE_CLIENT_ID)" \
		--set saas.credentials.clientSecret="$(ZEEBE_CLIENT_SECRET)" \
		--set saas.credentials.zeebeRestAddress="$(ZEEBE_REST_ADDRESS)" \
		--set saas.credentials.authServer="$(ZEEBE_AUTHORIZATION_SERVER_URL)" \
		--set saas.credentials.authType="OAUTH" \
		--set saas.credentials.zeebeGrpcAddress="$(ZEEBE_GRPC_ADDRESS)" \
		--set saas.credentials.authorizationAudience="$(ZEEBE_TOKEN_AUDIENCE)"
		$(additional_load_test_configuration)

.PHONY: clean
clean:
	-helm --namespace $(namespace) uninstall $(namespace)-test
