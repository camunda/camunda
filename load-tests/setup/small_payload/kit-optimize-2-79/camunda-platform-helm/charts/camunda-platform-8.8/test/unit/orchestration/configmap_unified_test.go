// Copyright 2022 Camunda Services GmbH
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package orchestration

import (
	"camunda-platform/test/unit/testhelpers"
	"path/filepath"
	"strings"
	"testing"

	"github.com/gruntwork-io/terratest/modules/random"
	"github.com/stretchr/testify/require"
	"github.com/stretchr/testify/suite"
)

type ConfigmapTemplateTest struct {
	suite.Suite
	chartPath string
	release   string
	namespace string
	templates []string
}

func TestConfigmapUnifiedTemplate(t *testing.T) {
	t.Parallel()

	chartPath, err := filepath.Abs("../../../")
	require.NoError(t, err)

	suite.Run(t, &ConfigmapTemplateTest{
		chartPath: chartPath,
		release:   "camunda-platform-test",
		namespace: "camunda-platform-" + strings.ToLower(random.UniqueId()),
		templates: []string{"templates/orchestration/configmap-unified.yaml"},
	})
}

func (s *ConfigmapTemplateTest) TestDifferentValuesInputsUnified() {
	testCases := []testhelpers.TestCase{
		{
			Name: "TestApplicationYamlShouldContainEnabledProfilesBroker",
			Values: map[string]string{
				"orchestration.profiles.broker": "false",
			},
			Expected: map[string]string{
				"configmapApplication.spring.profiles.active": "identity,operate,tasklist,consolidated-auth",
			},
		},
		{
			Name: "TestApplicationYamlShouldContainEnabledProfilesOperate",
			Values: map[string]string{
				"orchestration.profiles.operate": "false",
			},
			Expected: map[string]string{
				"configmapApplication.spring.profiles.active": "broker,identity,tasklist,consolidated-auth",
			},
		},
		{
			Name: "TestApplicationYamlShouldContainEnabledProfilesTasklist",
			Values: map[string]string{
				"orchestration.profiles.tasklist": "false",
			},
			Expected: map[string]string{
				"configmapApplication.spring.profiles.active": "broker,identity,operate,consolidated-auth",
			},
		},
		{
			Name: "TestApplicationYamlShouldContainContextPath",
			Values: map[string]string{
				"orchestration.contextPath": "/custom",
			},
			Expected: map[string]string{
				"configmapApplication.server.servlet.context-path": "/custom",
				"configmapApplication.management.server.base-path": "/custom",
			},
		},
		{
			Name: "TestApplicationYamlShouldContainSecondaryStorageOpenSearchEnabled",
			Values: map[string]string{
				"global.opensearch.enabled":  "true",
				"global.opensearch.url.host": "opensearch.example.com",
			},
			Expected: map[string]string{
				"configmapApplication.camunda.data.secondary-storage.opensearch.url": "https://opensearch.example.com:443",
			},
		},
		{
			Name: "TestApplicationYamlShouldContainAuthOIDCClientId",
			Values: map[string]string{
				"orchestration.security.authentication.method": "oidc",
			},
			Expected: map[string]string{
				"configmapApplication.camunda.security.authentication.oidc.client-id": "orchestration",
			},
		},
	}

	testhelpers.RunTestCases(s.T(), s.chartPath, s.release, s.namespace, s.templates, testCases)
}

func (s *ConfigmapTemplateTest) TestDifferentValuesInputsUnifiedCompatibility() {
	testCases := []testhelpers.TestCase{
		{
			Name: "TestApplicationYamlShouldContainEnabledProfilesBroker",
			Values: map[string]string{
				"global.compatibility.orchestration.enabled": "true",
				"orchestration.profiles.broker":              "false",
				"zeebe.enabled":                              "false",
			},
			Expected: map[string]string{
				"configmapApplication.spring.profiles.active": "identity,operate,tasklist,consolidated-auth",
			},
		},
		{
			Name: "TestApplicationYamlShouldContainEnabledProfilesOperate",
			Values: map[string]string{
				"global.compatibility.orchestration.enabled": "true",
				"orchestration.profiles.operate":             "false",
				"operate.enabled":                            "true",
			},
			Expected: map[string]string{
				"configmapApplication.spring.profiles.active": "broker,identity,operate,tasklist,consolidated-auth",
			},
		},
		{
			Name: "TestApplicationYamlShouldContainEnabledProfilesTasklist",
			Values: map[string]string{
				"global.compatibility.orchestration.enabled": "true",
				"orchestration.profiles.tasklist":            "false",
				"tasklist.enabled":                           "true",
			},
			Expected: map[string]string{
				"configmapApplication.spring.profiles.active": "broker,identity,operate,tasklist,consolidated-auth",
			},
		},
		{
			Name: "TestApplicationYamlShouldContainContextPath",
			Values: map[string]string{
				"global.compatibility.orchestration.enabled": "true",
				"zeebeGateway.enabled":                       "true",
				"zeebeGateway.contextPath":                   "/custom",
			},
			Expected: map[string]string{
				"configmapApplication.server.servlet.context-path": "/custom",
				"configmapApplication.management.server.base-path": "/custom",
			},
		},
		{
			Name: "TestApplicationYamlShouldContainPortServer",
			Values: map[string]string{
				"global.compatibility.orchestration.enabled": "true",
				"zeebeGateway.enabled":                       "true",
				"zeebeGateway.service.restPort":              "1111",
			},
			Expected: map[string]string{
				"configmapApplication.server.port": "1111",
			},
		},
		{
			Name: "TestApplicationYamlShouldContainPortGRPC",
			Values: map[string]string{
				"global.compatibility.orchestration.enabled": "true",
				"zeebeGateway.enabled":                       "true",
				"zeebeGateway.service.grpcPort":              "1111",
			},
			Expected: map[string]string{
				"configmapApplication.camunda.api.grpc.port": "1111",
			},
		},
	}

	testhelpers.RunTestCases(s.T(), s.chartPath, s.release, s.namespace, s.templates, testCases)
}

func (s *ConfigmapTemplateTest) TestDifferentValuesInputsUnifiedAuthOIDC() {
	testCases := []testhelpers.TestCase{
		{
			Name: "TestApplicationYamlShouldContainAuthOIDCClientId",
			Values: map[string]string{
				"orchestration.security.authentication.method": "oidc",
			},
			Expected: map[string]string{
				"configmapApplication.camunda.security.authentication.oidc.client-id":     "orchestration",
				"configmapApplication.camunda.security.authentication.oidc.client-secret": "${VALUES_ORCHESTRATION_CLIENT_SECRET:}",
			},
		},
		{
			Name: "TestApplicationYamlShouldContainAuthOIDCWithIssuerAndKeycloakEnabled",
			Values: map[string]string{
				"identity.enabled":                                       "true",
				"identityKeycloak.enabled":                               "true",
				"global.identity.auth.enabled":                           "true",
				"global.identity.auth.publicIssuerUrl":                   "https://public-issuer-url.com/realms/camunda",
				"orchestration.security.authentication.method":           "oidc",
				"orchestration.security.authentication.oidc.redirectUrl": "https://redirect.com/orchestration",
			},
			Expected: map[string]string{
				"configmapApplication.camunda.security.authentication.oidc.authorization-uri": "https://public-issuer-url.com/realms/camunda/protocol/openid-connect/auth",
				"configmapApplication.camunda.security.authentication.oidc.jwk-set-uri":       "http://camunda-platform-test-keycloak/auth/realms/camunda-platform/protocol/openid-connect/certs",
				"configmapApplication.camunda.security.authentication.oidc.token-uri":         "http://camunda-platform-test-keycloak/auth/realms/camunda-platform/protocol/openid-connect/token",
				"configmapApplication.camunda.security.authentication.oidc.redirect-uri":      "https://redirect.com/orchestration/sso-callback",
			},
		},
		{
			Name: "TestApplicationYamlShouldContainAuthOIDCWithIssuerUrlAndKeycloakDisabled",
			Values: map[string]string{
				"identity.enabled":                                       "false",
				"identityKeycloak.enabled":                               "false",
				"global.identity.auth.enabled":                           "false",
				"global.identity.auth.issuer":                            "https://public-issuer-url.com/realms/camunda",
				"orchestration.security.authentication.method":           "oidc",
				"orchestration.security.authentication.oidc.redirectUrl": "https://redirect-url.com/orchestration",
			},
			Expected: map[string]string{
				"configmapApplication.camunda.security.authentication.oidc.issuer-uri": "https://public-issuer-url.com/realms/camunda",
			},
		},
		{
			Name: "TestApplicationYamlShouldContainAuthOIDCWithIssuerUrlUnUsedAndKeycloakDisabled",
			Values: map[string]string{
				"identity.enabled":                                       "false",
				"identityKeycloak.enabled":                               "false",
				"global.identity.auth.enabled":                           "false",
				"global.identity.auth.issuer":                            "",
				"global.identity.auth.authUrl":                           "https://public-issuer-url.com/auth",
				"global.identity.auth.tokenUrl":                          "https://public-issuer-url.com/token",
				"global.identity.auth.jwksUrl":                           "https://public-issuer-url.com/certs",
				"orchestration.security.authentication.method":           "oidc",
				"orchestration.security.authentication.oidc.redirectUrl": "https://redirect-url.com/orchestration",
			},
			Expected: map[string]string{
				"configmapApplication.camunda.security.authentication.oidc.authorization-uri": "https://public-issuer-url.com/auth",
				"configmapApplication.camunda.security.authentication.oidc.jwk-set-uri":       "https://public-issuer-url.com/certs",
				"configmapApplication.camunda.security.authentication.oidc.token-uri":         "https://public-issuer-url.com/token",
				"configmapApplication.camunda.security.authentication.oidc.redirect-uri":      "https://redirect-url.com/orchestration/sso-callback",
			},
		},
		{
			Name: "TestApplicationYamlShouldContainAuthOIDCWithIssuerUrlUnUsedAndKeycloakExternal",
			Values: map[string]string{
				"identity.enabled":                                       "false",
				"identityKeycloak.enabled":                               "false",
				"global.identity.auth.enabled":                           "false",
				"global.identity.auth.publicIssuerUrl":                   "https://my-keycloak.com:8080/authz/realms/camunda-platform",
				"global.identity.keycloak.contextPath":                   "/authz",
				"global.identity.keycloak.url.protocol":                  "https",
				"global.identity.keycloak.url.host":                      "my-keycloak.com",
				"global.identity.keycloak.url.port":                      "8080",
				"orchestration.security.authentication.method":           "oidc",
				"orchestration.security.authentication.oidc.redirectUrl": "https://redirect-url.com/orchestration",
			},
			Expected: map[string]string{
				"configmapApplication.camunda.security.authentication.oidc.authorization-uri": "https://my-keycloak.com:8080/authz/realms/camunda-platform/protocol/openid-connect/auth",
				"configmapApplication.camunda.security.authentication.oidc.jwk-set-uri":       "https://my-keycloak.com:8080/authz/realms/camunda-platform/protocol/openid-connect/certs",
				"configmapApplication.camunda.security.authentication.oidc.token-uri":         "https://my-keycloak.com:8080/authz/realms/camunda-platform/protocol/openid-connect/token",
				"configmapApplication.camunda.security.authentication.oidc.redirect-uri":      "https://redirect-url.com/orchestration/sso-callback",
			},
		},
	}

	testhelpers.RunTestCases(s.T(), s.chartPath, s.release, s.namespace, s.templates, testCases)
}
