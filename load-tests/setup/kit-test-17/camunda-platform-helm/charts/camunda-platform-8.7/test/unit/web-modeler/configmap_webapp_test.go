package web_modeler

import (
	"camunda-platform/test/unit/testhelpers"
	"path/filepath"
	"strings"
	"testing"

	"github.com/BurntSushi/toml"
	"github.com/gruntwork-io/terratest/modules/helm"
	"github.com/gruntwork-io/terratest/modules/random"
	"github.com/stretchr/testify/require"
	"github.com/stretchr/testify/suite"
	corev1 "k8s.io/api/core/v1"
)

type ConfigmapWebAppTemplateTest struct {
	suite.Suite
	chartPath string
	release   string
	namespace string
	templates []string
}

func TestWebAppConfigmapTemplate(t *testing.T) {
	t.Parallel()

	chartPath, err := filepath.Abs("../../../")
	require.NoError(t, err)

	suite.Run(t, &ConfigmapWebAppTemplateTest{
		chartPath: chartPath,
		release:   "camunda-platform-test",
		namespace: "camunda-platform-" + strings.ToLower(random.UniqueId()),
		templates: []string{"templates/web-modeler/configmap-webapp.yaml"},
	})
}

func (s *ConfigmapWebAppTemplateTest) TestDifferentValuesInputs() {
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
				var configmapApplication WebModelerWebAppTOML
				helm.UnmarshalK8SYaml(s.T(), output, &configmap)

				e := toml.Unmarshal([]byte(configmap.Data["application.toml"]), &configmapApplication)
				if e != nil {
					s.Fail("Failed to unmarshal yaml. error=", e)
				}
				// then
				s.Require().Equal("custom-audience", configmapApplication.OAuth2.Token.Audience)
			},
		}, {
			Name: "TestContainerShouldSetCorrectAuthClientId",
			Values: map[string]string{
				"webModeler.enabled":                       "true",
				"webModeler.restapi.mail.fromAddress":      "example@example.com",
				"global.identity.auth.webModeler.clientId": "custom-clientId",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var configmap corev1.ConfigMap
				var configmapApplication WebModelerWebAppTOML
				helm.UnmarshalK8SYaml(s.T(), output, &configmap)

				e := toml.Unmarshal([]byte(configmap.Data["application.toml"]), &configmapApplication)
				if e != nil {
					s.Fail("Failed to unmarshal yaml. error=", e)
				}

				// then
				s.Require().Equal("custom-clientId", configmapApplication.OAuth2.ClientId)
			},
		}, {
			Name: "TestContainerShouldSetCorrectClientPusherConfigurationWithGlobalIngressTlsDisabled",
			Values: map[string]string{
				"webModeler.enabled":                  "true",
				"webModeler.restapi.mail.fromAddress": "example@example.com",
				"webModeler.ingress.enabled":          "false",
				"webModeler.contextPath":              "/modeler",
				"global.ingress.enabled":              "true",
				"global.ingress.host":                 "c8.example.com",
				"global.ingress.tls.enabled":          "false",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var configmap corev1.ConfigMap
				var configmapApplication WebModelerWebAppTOML
				helm.UnmarshalK8SYaml(s.T(), output, &configmap)

				e := toml.Unmarshal([]byte(configmap.Data["application.toml"]), &configmapApplication)
				if e != nil {
					s.Fail("Failed to unmarshal yaml. error=", e)
				}

				// then
				s.Require().Equal("c8.example.com", configmapApplication.Client.Pusher.Host)
				s.Require().Equal("80", configmapApplication.Client.Pusher.Port)
				s.Require().Equal("/modeler-ws", configmapApplication.Client.Pusher.Path)
				s.Require().Equal("false", configmapApplication.Client.Pusher.ForceTLS)
			},
		}, {
			Name: "TestContainerShouldSetCorrectClientPusherConfigurationWithGlobalIngressTlsEnabled",
			Values: map[string]string{
				"webModeler.enabled":                  "true",
				"webModeler.restapi.mail.fromAddress": "example@example.com",
				"webModeler.ingress.enabled":          "false",
				"webModeler.contextPath":              "/modeler",
				"global.ingress.enabled":              "true",
				"global.ingress.host":                 "c8.example.com",
				"global.ingress.tls.enabled":          "true",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var configmap corev1.ConfigMap
				var configmapApplication WebModelerWebAppTOML
				helm.UnmarshalK8SYaml(s.T(), output, &configmap)

				e := toml.Unmarshal([]byte(configmap.Data["application.toml"]), &configmapApplication)
				if e != nil {
					s.Fail("Failed to unmarshal yaml. error=", e)
				}

				// then
				s.Require().Equal("c8.example.com", configmapApplication.Client.Pusher.Host)
				s.Require().Equal("443", configmapApplication.Client.Pusher.Port)
				s.Require().Equal("/modeler-ws", configmapApplication.Client.Pusher.Path)
				s.Require().Equal("true", configmapApplication.Client.Pusher.ForceTLS)
			},
		}, {
			Name: "TestContainerShouldSetCorrectClientPusherConfigurationWithIngressTlsEnabled",
			Values: map[string]string{
				"webModeler.enabled":                        "true",
				"webModeler.restapi.mail.fromAddress":       "example@example.com",
				"webModeler.ingress.enabled":                "true",
				"webModeler.ingress.websockets.host":        "modeler-ws.example.com",
				"webModeler.ingress.websockets.tls.enabled": "true",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var configmap corev1.ConfigMap
				var configmapApplication WebModelerWebAppTOML
				helm.UnmarshalK8SYaml(s.T(), output, &configmap)

				e := toml.Unmarshal([]byte(configmap.Data["application.toml"]), &configmapApplication)
				if e != nil {
					s.Fail("Failed to unmarshal yaml. error=", e)
				}

				// then
				s.Require().Equal("modeler-ws.example.com", configmapApplication.Client.Pusher.Host)
				s.Require().Equal("443", configmapApplication.Client.Pusher.Port)
				s.Require().Equal("true", configmapApplication.Client.Pusher.ForceTLS)
			},
		}, {
			Name: "TestContainerShouldSetCorrectClientPusherConfigurationWithIngressTlsDisabled",
			Values: map[string]string{
				"webModeler.enabled":                        "true",
				"webModeler.restapi.mail.fromAddress":       "example@example.com",
				"webModeler.ingress.enabled":                "true",
				"webModeler.ingress.websockets.host":        "modeler-ws.example.com",
				"webModeler.ingress.websockets.tls.enabled": "false",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var configmap corev1.ConfigMap
				var configmapApplication WebModelerWebAppTOML
				helm.UnmarshalK8SYaml(s.T(), output, &configmap)

				e := toml.Unmarshal([]byte(configmap.Data["application.toml"]), &configmapApplication)
				if e != nil {
					s.Fail("Failed to unmarshal yaml. error=", e)
				}

				// then
				s.Require().Equal("modeler-ws.example.com", configmapApplication.Client.Pusher.Host)
				s.Require().Equal("80", configmapApplication.Client.Pusher.Port)
				s.Require().Equal("false", configmapApplication.Client.Pusher.ForceTLS)
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
				var configmapApplication WebModelerWebAppTOML
				helm.UnmarshalK8SYaml(s.T(), output, &configmap)

				e := toml.Unmarshal([]byte(configmap.Data["application.toml"]), &configmapApplication)
				if e != nil {
					s.Fail("Failed to unmarshal yaml. error=", e)
				}

				// then
				s.Require().Equal("http://custom-identity-fullname:80", configmapApplication.Identity.BaseUrl)
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
				var configmapApplication WebModelerWebAppTOML
				helm.UnmarshalK8SYaml(s.T(), output, &configmap)

				e := toml.Unmarshal([]byte(configmap.Data["application.toml"]), &configmapApplication)
				if e != nil {
					s.Fail("Failed to unmarshal yaml. error=", e)
				}

				// then
				s.Require().Equal("http://camunda-platform-test-custom-identity:80", configmapApplication.Identity.BaseUrl)
			},
		}, {
			Name: "TestContainerShouldSetServerHttpsOnly",
			Values: map[string]string{
				"webModeler.enabled":                          "true",
				"webModeler.restapi.mail.fromAddress":         "example@example.com",
				"global.identity.auth.webModeler.redirectUrl": "https://modeler.example.com",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var configmap corev1.ConfigMap
				var configmapApplication WebModelerWebAppTOML
				helm.UnmarshalK8SYaml(s.T(), output, &configmap)

				e := toml.Unmarshal([]byte(configmap.Data["application.toml"]), &configmapApplication)
				if e != nil {
					s.Fail("Failed to unmarshal yaml. error=", e)
				}

				// then
				s.Require().Equal("true", configmapApplication.Server.HttpsOnly)
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
				var configmapApplication WebModelerWebAppTOML
				helm.UnmarshalK8SYaml(s.T(), output, &configmap)

				e := toml.Unmarshal([]byte(configmap.Data["application.toml"]), &configmapApplication)
				if e != nil {
					s.Fail("Failed to unmarshal yaml. error=", e)
				}

				// then
				s.Require().Equal("http://keycloak:80/auth/realms/camunda-platform/protocol/openid-connect/certs", configmapApplication.OAuth2.Token.JwksUrl)
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
				var configmapApplication WebModelerWebAppTOML
				helm.UnmarshalK8SYaml(s.T(), output, &configmap)

				e := toml.Unmarshal([]byte(configmap.Data["application.toml"]), &configmapApplication)
				if e != nil {
					s.Fail("Failed to unmarshal yaml. error=", e)
				}

				// then
				s.Require().Equal("MICROSOFT", configmapApplication.OAuth2.Type)
			},
		},
	}

	testhelpers.RunTestCasesE(s.T(), s.chartPath, s.release, s.namespace, s.templates, testCases)
}
