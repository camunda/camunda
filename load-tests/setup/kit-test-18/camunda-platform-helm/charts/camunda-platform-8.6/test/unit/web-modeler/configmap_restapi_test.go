package web_modeler

import (
	"camunda-platform/test/unit/testhelpers"
	"path/filepath"
	"strings"
	"testing"

	"github.com/gruntwork-io/terratest/modules/helm"
	"github.com/gruntwork-io/terratest/modules/random"
	"github.com/stretchr/testify/require"
	"github.com/stretchr/testify/suite"
	"gopkg.in/yaml.v3"
	corev1 "k8s.io/api/core/v1"
)

type ConfigmapRestAPITemplateTest struct {
	suite.Suite
	chartPath string
	release   string
	namespace string
	templates []string
}

func TestRestAPIConfigmapTemplate(t *testing.T) {
	t.Parallel()

	chartPath, err := filepath.Abs("../../../")
	require.NoError(t, err)

	suite.Run(t, &ConfigmapRestAPITemplateTest{
		chartPath: chartPath,
		release:   "camunda-platform-test",
		namespace: "camunda-platform-" + strings.ToLower(random.UniqueId()),
		templates: []string{"templates/web-modeler/configmap-restapi.yaml"},
	})
}

func (s *ConfigmapRestAPITemplateTest) TestDifferentValuesInputs() {
	testCases := []testhelpers.TestCase{
		{
			Name: "TestContainerShouldSetCorrectAuthClientApiAudience",
			Values: map[string]string{
				"webModeler.enabled":                                "true",
				"webModeler.restapi.mail.fromAddress":               "example@example.com",
				"global.identity.auth.webModeler.clientApiAudience": "custom-audience",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var configmap corev1.ConfigMap
				var configmapApplication WebModelerRestAPIApplicationYAML
				helm.UnmarshalK8SYaml(s.T(), output, &configmap)

				e := yaml.Unmarshal([]byte(configmap.Data["application.yaml"]), &configmapApplication)
				if e != nil {
					s.Fail("Failed to unmarshal yaml. error=", e)
				}

				// then
				s.Require().Equal("custom-audience", configmapApplication.Camunda.Modeler.Security.JWT.Audience.InternalAPI)
			},
		}, {
			Name: "TestContainerShouldSetCorrectAuthPublicApiAudience",
			Values: map[string]string{
				"webModeler.enabled":                                "true",
				"webModeler.restapi.mail.fromAddress":               "example@example.com",
				"global.identity.auth.webModeler.publicApiAudience": "custom-audience",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var configmap corev1.ConfigMap
				var configmapApplication WebModelerRestAPIApplicationYAML
				helm.UnmarshalK8SYaml(s.T(), output, &configmap)

				e := yaml.Unmarshal([]byte(configmap.Data["application.yaml"]), &configmapApplication)
				if e != nil {
					s.Fail("Failed to unmarshal yaml. error=", e)
				}

				// then
				s.Require().Equal("custom-audience", configmapApplication.Camunda.Modeler.Security.JWT.Audience.PublicAPI)
			},
		}, {
			Name: "TestContainerShouldSetCorrectIdentityServiceUrlWithFullnameOverride",
			Values: map[string]string{
				"webModeler.enabled":                  "true",
				"webModeler.restapi.mail.fromAddress": "example@example.com",
				"identity.fullnameOverride":           "custom-identity-fullname",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var configmap corev1.ConfigMap
				var configmapApplication WebModelerRestAPIApplicationYAML
				helm.UnmarshalK8SYaml(s.T(), output, &configmap)

				e := yaml.Unmarshal([]byte(configmap.Data["application.yaml"]), &configmapApplication)
				if e != nil {
					s.Fail("Failed to unmarshal yaml. error=", e)
				}

				// then
				s.Require().Equal("http://custom-identity-fullname:80", configmapApplication.Camunda.Identity.BaseURL)
			},
		}, {
			Name: "TestContainerShouldSetCorrectIdentityServiceUrlWithNameOverride",
			Values: map[string]string{
				"webModeler.enabled":                  "true",
				"webModeler.restapi.mail.fromAddress": "example@example.com",
				"identity.nameOverride":               "custom-identity",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var configmap corev1.ConfigMap
				var configmapApplication WebModelerRestAPIApplicationYAML
				helm.UnmarshalK8SYaml(s.T(), output, &configmap)

				e := yaml.Unmarshal([]byte(configmap.Data["application.yaml"]), &configmapApplication)
				if e != nil {
					s.Fail("Failed to unmarshal yaml. error=", e)
				}

				// then
				s.Require().Equal("http://camunda-platform-test-custom-identity:80", configmapApplication.Camunda.Identity.BaseURL)
			},
		}, {
			Name: "TestContainerShouldSetCorrectIdentityType",
			Values: map[string]string{
				"webModeler.enabled":                    "true",
				"webModeler.restapi.mail.fromAddress":   "example@example.com",
				"global.identity.auth.type":             "MICROSOFT",
				"global.identity.auth.issuerBackendUrl": "https://example.com",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var configmap corev1.ConfigMap
				var configmapApplication WebModelerRestAPIApplicationYAML
				helm.UnmarshalK8SYaml(s.T(), output, &configmap)

				e := yaml.Unmarshal([]byte(configmap.Data["application.yaml"]), &configmapApplication)
				if e != nil {
					s.Fail("Failed to unmarshal yaml. error=", e)
				}

				// then
				s.Require().Equal("MICROSOFT", configmapApplication.Camunda.Identity.Type)
			},
		}, {
			Name: "TestContainerShouldSetCorrectKeycloakServiceUrl",
			Values: map[string]string{
				"webModeler.enabled":                    "true",
				"webModeler.restapi.mail.fromAddress":   "example@example.com",
				"global.identity.keycloak.url.protocol": "http",
				"global.identity.keycloak.url.host":     "keycloak",
				"global.identity.keycloak.url.port":     "80",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var configmap corev1.ConfigMap
				var configmapApplication WebModelerRestAPIApplicationYAML
				helm.UnmarshalK8SYaml(s.T(), output, &configmap)

				e := yaml.Unmarshal([]byte(configmap.Data["application.yaml"]), &configmapApplication)
				if e != nil {
					s.Fail("Failed to unmarshal yaml. error=", e)
				}

				// then
				s.Require().Equal("http://keycloak:80/auth/realms/camunda-platform", configmapApplication.Camunda.Modeler.Security.JWT.Issuer.BackendUrl)
			},
		}, {
			Name: "TestContainerShouldSetCorrectKeycloakServiceUrlWithCustomPort",
			Values: map[string]string{
				"webModeler.enabled":                    "true",
				"webModeler.restapi.mail.fromAddress":   "example@example.com",
				"global.identity.keycloak.url.protocol": "http",
				"global.identity.keycloak.url.host":     "keycloak",
				"global.identity.keycloak.url.port":     "8888",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var configmap corev1.ConfigMap
				var configmapApplication WebModelerRestAPIApplicationYAML
				helm.UnmarshalK8SYaml(s.T(), output, &configmap)

				e := yaml.Unmarshal([]byte(configmap.Data["application.yaml"]), &configmapApplication)
				if e != nil {
					s.Fail("Failed to unmarshal yaml. error=", e)
				}

				// then
				s.Require().Equal("http://keycloak:8888/auth/realms/camunda-platform", configmapApplication.Camunda.Modeler.Security.JWT.Issuer.BackendUrl)
			},
		}, {
			Name: "TestContainerShouldSetSmtpCredentials",
			Values: map[string]string{
				"webModeler.enabled":                   "true",
				"webModeler.restapi.mail.fromAddress":  "example@example.com",
				"webModeler.restapi.mail.smtpUser":     "modeler-user",
				"webModeler.restapi.mail.smtpPassword": "modeler-password",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var configmap corev1.ConfigMap
				var configmapApplication WebModelerRestAPIApplicationYAML
				helm.UnmarshalK8SYaml(s.T(), output, &configmap)

				e := yaml.Unmarshal([]byte(configmap.Data["application.yaml"]), &configmapApplication)
				if e != nil {
					s.Fail("Failed to unmarshal yaml. error=", e)
				}

				// then
				s.Require().Equal("modeler-user", configmapApplication.Spring.Mail.Username)
			},
		}, {
			Name: "TestContainerShouldSetExternalDatabaseConfiguration",
			Values: map[string]string{
				"webModeler.enabled":                           "true",
				"webModeler.restapi.mail.fromAddress":          "example@example.com",
				"postgresql.enabled":                           "false",
				"webModeler.restapi.externalDatabase.url":      "jdbc:postgresql://postgres.example.com:65432/modeler-database",
				"webModeler.restapi.externalDatabase.user":     "modeler-user",
				"webModeler.restapi.externalDatabase.password": "modeler-password",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var configmap corev1.ConfigMap
				var configmapApplication WebModelerRestAPIApplicationYAML
				helm.UnmarshalK8SYaml(s.T(), output, &configmap)

				e := yaml.Unmarshal([]byte(configmap.Data["application.yaml"]), &configmapApplication)
				if e != nil {
					s.Fail("Failed to unmarshal yaml. error=", e)
				}

				// then
				s.Require().Equal("jdbc:postgresql://postgres.example.com:65432/modeler-database", configmapApplication.Spring.Datasource.Url)
				s.Require().Equal("modeler-user", configmapApplication.Spring.Datasource.Username)
			},
		}, {
			Name: "TestContainerShouldSetJwkSetUriFromJwksUrlProperty",
			Values: map[string]string{
				"webModeler.enabled":                  "true",
				"webModeler.restapi.mail.fromAddress": "example@example.com",
				"global.identity.auth.jwksUrl":        "https://example.com/auth/realms/test/protocol/openid-connect/certs",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var configmap corev1.ConfigMap
				var configmapApplication WebModelerRestAPIApplicationYAML
				helm.UnmarshalK8SYaml(s.T(), output, &configmap)

				e := yaml.Unmarshal([]byte(configmap.Data["application.yaml"]), &configmapApplication)
				if e != nil {
					s.Fail("Failed to unmarshal yaml. error=", e)
				}

				// then
				s.Require().Equal("https://example.com/auth/realms/test/protocol/openid-connect/certs", configmapApplication.Spring.Security.OAuth2.ResourceServer.JWT.JwkSetURI)
			},
		}, {
			Name: "TestContainerShouldSetJwkSetUriFromIssuerBackendUrlProperty",
			Values: map[string]string{
				"webModeler.enabled":                    "true",
				"webModeler.restapi.mail.fromAddress":   "example@example.com",
				"global.identity.auth.issuerBackendUrl": "http://test-keycloak/auth/realms/test",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var configmap corev1.ConfigMap
				var configmapApplication WebModelerRestAPIApplicationYAML
				helm.UnmarshalK8SYaml(s.T(), output, &configmap)

				e := yaml.Unmarshal([]byte(configmap.Data["application.yaml"]), &configmapApplication)
				if e != nil {
					s.Fail("Failed to unmarshal yaml. error=", e)
				}

				// then
				s.Require().Equal("http://test-keycloak/auth/realms/test/protocol/openid-connect/certs", configmapApplication.Spring.Security.OAuth2.ResourceServer.JWT.JwkSetURI)
			},
		}, {
			Name: "TestContainerShouldSetJwkSetUriFromKeycloakUrlProperties",
			Values: map[string]string{
				"webModeler.enabled":                    "true",
				"webModeler.restapi.mail.fromAddress":   "example@example.com",
				"global.identity.keycloak.url.protocol": "https",
				"global.identity.keycloak.url.host":     "example.com",
				"global.identity.keycloak.url.port":     "443",
				"global.identity.keycloak.contextPath":  "/",
				"global.identity.keycloak.realm":        "test",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var configmap corev1.ConfigMap
				var configmapApplication WebModelerRestAPIApplicationYAML
				helm.UnmarshalK8SYaml(s.T(), output, &configmap)

				e := yaml.Unmarshal([]byte(configmap.Data["application.yaml"]), &configmapApplication)
				if e != nil {
					s.Fail("Failed to unmarshal yaml. error=", e)
				}

				// then
				s.Require().Equal("https://example.com:443/test/protocol/openid-connect/certs", configmapApplication.Spring.Security.OAuth2.ResourceServer.JWT.JwkSetURI)
			},
		},
	}

	testhelpers.RunTestCasesE(s.T(), s.chartPath, s.release, s.namespace, s.templates, testCases)
}
