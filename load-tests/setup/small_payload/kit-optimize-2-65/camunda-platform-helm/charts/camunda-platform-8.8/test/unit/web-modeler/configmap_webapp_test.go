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

type configmapWebAppTemplateTest struct {
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

	suite.Run(t, &configmapWebAppTemplateTest{
		chartPath: chartPath,
		release:   "camunda-platform-test",
		namespace: "camunda-platform-" + strings.ToLower(random.UniqueId()),
		templates: []string{"templates/web-modeler/configmap-webapp.yaml"},
	})
}

func (s *configmapWebAppTemplateTest) TestDifferentValuesInputs() {
	testCases := []testhelpers.TestCase{
		{
			Name: "TestContainerShouldSetCorrectAuthClientApiAudience",
			Values: map[string]string{
				"identity.enabled":                                  "true",
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
				"identity.enabled":                         "true",
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
				"identity.enabled":                    "true",
				"webModeler.enabled":                  "true",
				"webModeler.restapi.mail.fromAddress": "example@example.com",
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
				s.Require().Equal(false, configmapApplication.Client.Pusher.ForceTLS)
			},
		}, {
			Name: "TestContainerShouldSetCorrectClientPusherConfigurationWithGlobalIngressTlsEnabled",
			Values: map[string]string{
				"identity.enabled":                    "true",
				"webModeler.enabled":                  "true",
				"webModeler.restapi.mail.fromAddress": "example@example.com",
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
				s.Require().Equal(true, configmapApplication.Client.Pusher.ForceTLS)
			},
		}, {
			Name: "TestContainerShouldSetCorrectIdentityServiceUrlWithFullnameOverride",
			Values: map[string]string{
				"identity.enabled":                    "true",
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
				"identity.enabled":                    "true",
				"identity.nameOverride":               "custom-identity",
				"webModeler.enabled":                  "true",
				"webModeler.restapi.mail.fromAddress": "example@example.com",
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
				"identity.enabled":                            "true",
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
				"identity.enabled":                      "true",
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
				"identity.enabled":                      "true",
				"webModeler.enabled":                    "true",
				"webModeler.restapi.mail.fromAddress":   "example@example.com",
				"global.identity.auth.type":             "MICROSOFT",
				"global.identity.auth.issuer":           "https://login.microsoftonline.com/00000000-0000-0000-0000-000000000000/v2.0",
				"global.identity.auth.issuerBackendUrl": "https://login.microsoftonline.com/00000000-0000-0000-0000-000000000000/v2.0",
				"global.identity.auth.authUrl":          "https://login.microsoftonline.com/00000000-0000-0000-0000-000000000000/oauth2/v2.0/authorize",
				"global.identity.auth.tokenUrl":         "https://login.microsoftonline.com/00000000-0000-0000-0000-000000000000/oauth2/v2.0/token",
				"global.identity.auth.jwksUrl":          "https://login.microsoftonline.com/00000000-0000-0000-0000-000000000000/discovery/v2.0/keys",
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
		}, {
			Name: "TestContainerShouldSetCorrectIdentityUserNameClaim",
			Values: map[string]string{
				"identity.enabled":                                         "true",
				"webModeler.enabled":                                       "true",
				"webModeler.restapi.mail.fromAddress":                      "example@example.com",
				"orchestration.security.authentication.oidc.usernameClaim": "example-claim",
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
				s.Require().Equal("example-claim", configmapApplication.OAuth2.Token.UsernameClaim)
			},
		}, {
			Name: "TestContainerShouldSetPusherHost",
			Values: map[string]string{
				"identity.enabled":                                         "true",
				"webModeler.enabled":                                       "true",
				"webModeler.restapi.mail.fromAddress":                      "example@example.com",
				"webModeler.websockets.publicHost":                         "example.com",
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
				s.Require().Equal("example.com", configmapApplication.Client.Pusher.Host)
			},
		}, {
			Name: "TestContainerShouldSetPusherPort",
			Values: map[string]string{
				"identity.enabled":                                         "true",
				"webModeler.enabled":                                       "true",
				"webModeler.restapi.mail.fromAddress":                      "example@example.com",
				"webModeler.websockets.publicHost":                         "example.com",
				"webModeler.websockets.publicPort":                         "8082",
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
				s.Require().Equal("8082", configmapApplication.Client.Pusher.Port)
			},
		},
	}

	testhelpers.RunTestCasesE(s.T(), s.chartPath, s.release, s.namespace, s.templates, testCases)
}
