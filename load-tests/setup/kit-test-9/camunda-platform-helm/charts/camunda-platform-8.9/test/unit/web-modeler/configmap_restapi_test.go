package web_modeler

import (
	"maps"
	"path/filepath"
	"strings"
	"testing"

	"github.com/gruntwork-io/terratest/modules/helm"
	"github.com/gruntwork-io/terratest/modules/k8s"
	"github.com/gruntwork-io/terratest/modules/random"
	"github.com/stretchr/testify/require"
	"github.com/stretchr/testify/suite"
	"gopkg.in/yaml.v3"
	corev1 "k8s.io/api/core/v1"
)

type configmapRestAPITemplateTest struct {
	suite.Suite
	chartPath string
	release   string
	namespace string
	templates []string
}

var requiredValues = map[string]string{
	"webModeler.enabled":                                             "true",
	"webModeler.restapi.mail.fromAddress":                            "example@example.com",
	"connectors.security.authentication.oidc.existingSecret.name":    "foo",
	"orchestration.security.authentication.oidc.existingSecret.name": "foo",
}

func TestRestAPIConfigmapTemplate(t *testing.T) {
	t.Parallel()

	chartPath, err := filepath.Abs("../../../")
	require.NoError(t, err)

	suite.Run(t, &configmapRestAPITemplateTest{
		chartPath: chartPath,
		release:   "camunda-platform-test",
		namespace: "camunda-platform-" + strings.ToLower(random.UniqueId()),
		templates: []string{"templates/web-modeler/configmap-restapi.yaml"},
	})
}

func (s *configmapRestAPITemplateTest) TestContainerShouldSetCorrectAuthClientApiAudience() {
	// given
	values := map[string]string{
		"identity.enabled":                                  "true",
		"global.identity.auth.enabled":                      "true",
		"global.identity.auth.webModeler.clientApiAudience": "custom-audience",
	}
	maps.Insert(values, maps.All(requiredValues))
	options := &helm.Options{
		SetValues:      values,
		KubectlOptions: k8s.NewKubectlOptions("", "", s.namespace),
	}

	// when
	output := helm.RenderTemplate(s.T(), options, s.chartPath, s.release, s.templates)
	var configmap corev1.ConfigMap
	var configmapApplication WebModelerRestAPIApplicationYAML
	helm.UnmarshalK8SYaml(s.T(), output, &configmap)

	err := yaml.Unmarshal([]byte(configmap.Data["application.yaml"]), &configmapApplication)
	if err != nil {
		s.Fail("Failed to unmarshal yaml. error=", err)
	}

	// then
	s.Require().Equal("custom-audience", configmapApplication.Camunda.Modeler.Security.JWT.Audience.InternalAPI)
}

func (s *configmapRestAPITemplateTest) TestContainerShouldSetCorrectAuthPublicApiAudience() {
	// given
	values := map[string]string{
		"identity.enabled":                                  "true",
		"global.identity.auth.enabled":                      "true",
		"global.identity.auth.webModeler.publicApiAudience": "custom-audience",
	}
	maps.Insert(values, maps.All(requiredValues))
	options := &helm.Options{
		SetValues:      values,
		KubectlOptions: k8s.NewKubectlOptions("", "", s.namespace),
	}

	// when
	output := helm.RenderTemplate(s.T(), options, s.chartPath, s.release, s.templates)
	var configmap corev1.ConfigMap
	var configmapApplication WebModelerRestAPIApplicationYAML
	helm.UnmarshalK8SYaml(s.T(), output, &configmap)

	err := yaml.Unmarshal([]byte(configmap.Data["application.yaml"]), &configmapApplication)
	if err != nil {
		s.Fail("Failed to unmarshal yaml. error=", err)
	}

	// then
	s.Require().Equal("custom-audience", configmapApplication.Camunda.Modeler.Security.JWT.Audience.PublicAPI)
}

func (s *configmapRestAPITemplateTest) TestContainerShouldSetCorrectIdentityServiceUrlWithFullnameOverride() {
	// given
	values := map[string]string{
		"global.identity.auth.enabled": "true",
		"identity.enabled":             "true",
		"identity.fullnameOverride":    "custom-identity-fullname",
	}
	maps.Insert(values, maps.All(requiredValues))
	options := &helm.Options{
		SetValues:      values,
		KubectlOptions: k8s.NewKubectlOptions("", "", s.namespace),
	}

	// when
	output := helm.RenderTemplate(s.T(), options, s.chartPath, s.release, s.templates)
	var configmap corev1.ConfigMap
	var configmapApplication WebModelerRestAPIApplicationYAML
	helm.UnmarshalK8SYaml(s.T(), output, &configmap)

	err := yaml.Unmarshal([]byte(configmap.Data["application.yaml"]), &configmapApplication)
	if err != nil {
		s.Fail("Failed to unmarshal yaml. error=", err)
	}

	// then
	s.Require().Equal("http://custom-identity-fullname:80", configmapApplication.Camunda.Identity.BaseURL)
}

func (s *configmapRestAPITemplateTest) TestContainerShouldSetCorrectIdentityServiceUrlWithNameOverride() {
	// given
	values := map[string]string{
		"global.identity.auth.enabled": "true",
		"identity.enabled":             "true",
		"identity.nameOverride":        "custom-identity",
	}
	maps.Insert(values, maps.All(requiredValues))
	options := &helm.Options{
		SetValues:      values,
		KubectlOptions: k8s.NewKubectlOptions("", "", s.namespace),
	}

	// when
	output := helm.RenderTemplate(s.T(), options, s.chartPath, s.release, s.templates)
	var configmap corev1.ConfigMap
	var configmapApplication WebModelerRestAPIApplicationYAML
	helm.UnmarshalK8SYaml(s.T(), output, &configmap)

	err := yaml.Unmarshal([]byte(configmap.Data["application.yaml"]), &configmapApplication)
	if err != nil {
		s.Fail("Failed to unmarshal yaml. error=", err)
	}

	// then
	s.Require().Equal("http://camunda-platform-test-custom-identity:80", configmapApplication.Camunda.Identity.BaseURL)
}

func (s *configmapRestAPITemplateTest) TestContainerShouldSetCorrectIdentityType() {
	// given
	values := map[string]string{
		"identity.enabled":                             "true",
		"global.identity.auth.enabled":                 "true",
		"global.identity.auth.type":                    "MICROSOFT",
		"global.identity.auth.identity.existingSecret": "foo",
		"global.identity.auth.issuer":                  "https://login.microsoftonline.com/00000000-0000-0000-0000-000000000000/v2.0",
		"global.identity.auth.issuerBackendUrl":        "https://login.microsoftonline.com/00000000-0000-0000-0000-000000000000/v2.0",
		"global.identity.auth.authUrl":                 "https://login.microsoftonline.com/00000000-0000-0000-0000-000000000000/oauth2/v2.0/authorize",
		"global.identity.auth.tokenUrl":                "https://login.microsoftonline.com/00000000-0000-0000-0000-000000000000/oauth2/v2.0/token",
		"global.identity.auth.jwksUrl":                 "https://login.microsoftonline.com/00000000-0000-0000-0000-000000000000/discovery/v2.0/keys",
	}
	maps.Insert(values, maps.All(requiredValues))
	options := &helm.Options{
		SetValues:      values,
		KubectlOptions: k8s.NewKubectlOptions("", "", s.namespace),
	}

	// when
	output := helm.RenderTemplate(s.T(), options, s.chartPath, s.release, s.templates)
	var configmap corev1.ConfigMap
	var configmapApplication WebModelerRestAPIApplicationYAML
	helm.UnmarshalK8SYaml(s.T(), output, &configmap)

	err := yaml.Unmarshal([]byte(configmap.Data["application.yaml"]), &configmapApplication)
	if err != nil {
		s.Fail("Failed to unmarshal yaml. error=", err)
	}

	// then
	s.Require().Equal("MICROSOFT", configmapApplication.Camunda.Identity.Type)
}

func (s *configmapRestAPITemplateTest) TestContainerShouldSetCorrectKeycloakServiceUrl() {
	// given
	values := map[string]string{
		"identity.enabled":                      "true",
		"global.identity.auth.enabled":          "true",
		"global.identity.keycloak.url.protocol": "http",
		"global.identity.keycloak.url.host":     "keycloak",
		"global.identity.keycloak.url.port":     "80",
	}
	maps.Insert(values, maps.All(requiredValues))
	options := &helm.Options{
		SetValues:      values,
		KubectlOptions: k8s.NewKubectlOptions("", "", s.namespace),
	}

	// when
	output := helm.RenderTemplate(s.T(), options, s.chartPath, s.release, s.templates)
	var configmap corev1.ConfigMap
	var configmapApplication WebModelerRestAPIApplicationYAML
	helm.UnmarshalK8SYaml(s.T(), output, &configmap)

	err := yaml.Unmarshal([]byte(configmap.Data["application.yaml"]), &configmapApplication)
	if err != nil {
		s.Fail("Failed to unmarshal yaml. error=", err)
	}

	// then
	s.Require().Equal("http://keycloak:80/auth/realms/camunda-platform", configmapApplication.Camunda.Modeler.Security.JWT.Issuer.BackendUrl)
}

func (s *configmapRestAPITemplateTest) TestContainerShouldSetCorrectKeycloakServiceUrlWithCustomPort() {
	// given
	values := map[string]string{
		"identity.enabled":                      "true",
		"global.identity.auth.enabled":          "true",
		"global.identity.keycloak.url.protocol": "http",
		"global.identity.keycloak.url.host":     "keycloak",
		"global.identity.keycloak.url.port":     "8888",
	}
	maps.Insert(values, maps.All(requiredValues))
	options := &helm.Options{
		SetValues:      values,
		KubectlOptions: k8s.NewKubectlOptions("", "", s.namespace),
	}

	// when
	output := helm.RenderTemplate(s.T(), options, s.chartPath, s.release, s.templates)
	var configmap corev1.ConfigMap
	var configmapApplication WebModelerRestAPIApplicationYAML
	helm.UnmarshalK8SYaml(s.T(), output, &configmap)

	err := yaml.Unmarshal([]byte(configmap.Data["application.yaml"]), &configmapApplication)
	if err != nil {
		s.Fail("Failed to unmarshal yaml. error=", err)
	}

	// then
	s.Require().Equal("http://keycloak:8888/auth/realms/camunda-platform", configmapApplication.Camunda.Modeler.Security.JWT.Issuer.BackendUrl)
}

func (s *configmapRestAPITemplateTest) TestContainerShouldSetSmtpCredentials() {
	// given
	values := map[string]string{
		"identity.enabled":                     "true",
		"webModeler.restapi.mail.smtpUser":     "modeler-user",
		"webModeler.restapi.mail.smtpPassword": "modeler-password",
	}
	maps.Insert(values, maps.All(requiredValues))
	options := &helm.Options{
		SetValues:      values,
		KubectlOptions: k8s.NewKubectlOptions("", "", s.namespace),
	}

	// when
	output := helm.RenderTemplate(s.T(), options, s.chartPath, s.release, s.templates)
	var configmap corev1.ConfigMap
	var configmapApplication WebModelerRestAPIApplicationYAML
	helm.UnmarshalK8SYaml(s.T(), output, &configmap)

	err := yaml.Unmarshal([]byte(configmap.Data["application.yaml"]), &configmapApplication)
	if err != nil {
		s.Fail("Failed to unmarshal yaml. error=", err)
	}

	// then
	s.Require().Equal("modeler-user", configmapApplication.Spring.Mail.Username)
}

func (s *configmapRestAPITemplateTest) TestContainerShouldSetExternalDatabaseConfiguration() {
	// given
	values := map[string]string{
		"identity.enabled":                             "true",
		"webModelerPostgresql.enabled":                 "false",
		"webModeler.restapi.externalDatabase.url":      "jdbc:postgresql://postgres.example.com:65432/modeler-database",
		"webModeler.restapi.externalDatabase.user":     "modeler-user",
		"webModeler.restapi.externalDatabase.password": "modeler-password",
	}
	maps.Insert(values, maps.All(requiredValues))
	options := &helm.Options{
		SetValues:      values,
		KubectlOptions: k8s.NewKubectlOptions("", "", s.namespace),
	}

	// when
	output := helm.RenderTemplate(s.T(), options, s.chartPath, s.release, s.templates)
	var configmap corev1.ConfigMap
	var configmapApplication WebModelerRestAPIApplicationYAML
	helm.UnmarshalK8SYaml(s.T(), output, &configmap)

	err := yaml.Unmarshal([]byte(configmap.Data["application.yaml"]), &configmapApplication)
	if err != nil {
		s.Fail("Failed to unmarshal yaml. error=", err)
	}

	// then
	s.Require().Equal("jdbc:postgresql://postgres.example.com:65432/modeler-database", configmapApplication.Spring.Datasource.Url)
	s.Require().Equal("modeler-user", configmapApplication.Spring.Datasource.Username)
}

func (s *configmapRestAPITemplateTest) TestContainerShouldConfigureClusterFromSameHelmInstallationWithCustomValues() {
	// given
	testCases := []struct {
		name                   string
		authEnabled            string
		authMethod             string
		expectedAuthentication string
	}{
		{
			name:                   "OIDC Authentication",
			authEnabled:            "true",
			authMethod:             "oidc",
			expectedAuthentication: "BEARER_TOKEN",
		},
		{
			name:                   "Basic Authentication",
			authEnabled:            "true",
			authMethod:             "basic",
			expectedAuthentication: "BASIC",
		},
		{
			name:                   "No Authentication",
			authEnabled:            "false",
			authMethod:             "none",
			expectedAuthentication: "NONE",
		},
	}

	for _, tc := range testCases {
		s.Run(tc.name, func() {
			values := map[string]string{
				"identity.enabled":                              "true",
				"webModelerPostgresql.enabled":                  "false",
				"global.zeebeClusterName":                       "test-zeebe",
				"global.identity.auth.enabled":                  tc.authEnabled,
				"global.ingress.enabled":                        "true",
				"global.ingress.tls.enabled":                    "true",
				"global.ingress.host":                           "example.com",
				"webModeler.security.authentication.method":     tc.authMethod,
				"orchestration.image.tag":                       "8.8.x-alpha1",
				"orchestration.contextPath":                     "/orchestration",
				"orchestration.service.grpcPort":                "26600",
				"orchestration.service.httpPort":                "8090",
				"orchestration.security.authorizations.enabled": "false",
			}
			maps.Insert(values, maps.All(requiredValues))
			options := &helm.Options{
				SetValues:      values,
				KubectlOptions: k8s.NewKubectlOptions("", "", s.namespace),
			}

			// when
			output := helm.RenderTemplate(s.T(), options, s.chartPath, s.release, s.templates)
			var configmap corev1.ConfigMap
			var configmapApplication WebModelerRestAPIApplicationYAML
			helm.UnmarshalK8SYaml(s.T(), output, &configmap)

			err := yaml.Unmarshal([]byte(configmap.Data["application.yaml"]), &configmapApplication)
			if err != nil {
				s.Fail("Failed to unmarshal yaml. error=", err)
			}

			// then
			s.Require().Equal(1, len(configmapApplication.Camunda.Modeler.Clusters))
			s.Require().Equal("default-cluster", configmapApplication.Camunda.Modeler.Clusters[0].Id)
			s.Require().Equal("test-zeebe", configmapApplication.Camunda.Modeler.Clusters[0].Name)
			s.Require().Equal("8.8.x-alpha1", configmapApplication.Camunda.Modeler.Clusters[0].Version)
			s.Require().Equal(tc.expectedAuthentication, configmapApplication.Camunda.Modeler.Clusters[0].Authentication)
			s.Require().Equal(false, configmapApplication.Camunda.Modeler.Clusters[0].Authorizations.Enabled)
			s.Require().Equal("grpc://camunda-platform-test-zeebe-gateway:26600", configmapApplication.Camunda.Modeler.Clusters[0].Url.Grpc)
			s.Require().Equal("http://camunda-platform-test-zeebe-gateway:8090/orchestration", configmapApplication.Camunda.Modeler.Clusters[0].Url.Rest)
			s.Require().Equal("https://example.com/orchestration", configmapApplication.Camunda.Modeler.Clusters[0].Url.WebApp)
		})
	}
}

func (s *configmapRestAPITemplateTest) TestContainerShouldUseClustersFromCustomConfiguration() {
	// given
	values := map[string]string{
		"identity.enabled":                              "true",
		"webModeler.restapi.clusters[0].id":             "test-cluster-1",
		"webModeler.restapi.clusters[0].name":           "test cluster 1",
		"webModeler.restapi.clusters[0].version":        "8.6.0",
		"webModeler.restapi.clusters[0].authentication": "NONE",
		"webModeler.restapi.clusters[0].url.zeebe.grpc": "grpc://orchestration.test-1:26500",
		"webModeler.restapi.clusters[0].url.zeebe.rest": "http://orchestration.test-1:8080",
		"webModeler.restapi.clusters[0].url.operate":    "http://operate.test-1:8080",
		"webModeler.restapi.clusters[0].url.tasklist":   "http://tasklist.test-1:8080",
		"webModeler.restapi.clusters[1].id":             "test-cluster-2",
		"webModeler.restapi.clusters[1].name":           "test cluster 2",
		"webModeler.restapi.clusters[1].version":        "8.8.0-alpha1",
		"webModeler.restapi.clusters[1].authentication": "BEARER_TOKEN",
		"webModeler.restapi.clusters[1].url.grpc":       "grpc://orchestration.test-2:26500",
		"webModeler.restapi.clusters[1].url.rest":       "http://orchestration.test-2:8080",
		"webModeler.restapi.clusters[1].url.web-app":    "http://localhost:8088",
		"webModeler.restapi.clusters[2].id":             "test-cluster-3",
		"webModeler.restapi.clusters[2].name":           "test cluster 3",
		"webModeler.restapi.clusters[2].version":        "8.8.0-alpha1",
		"webModeler.restapi.clusters[2].authentication": "BASIC",
		"webModeler.restapi.clusters[2].url.grpc":       "grpc://orchestration.test-3:26500",
		"webModeler.restapi.clusters[2].url.rest":       "http://orchestration.test-3:8080",
		"webModeler.restapi.clusters[2].url.web-app":    "http://localhost:8088",
		"webModelerPostgresql.enabled":                  "false",
	}
	maps.Insert(values, maps.All(requiredValues))
	options := &helm.Options{
		SetValues:      values,
		KubectlOptions: k8s.NewKubectlOptions("", "", s.namespace),
	}

	// when
	output := helm.RenderTemplate(s.T(), options, s.chartPath, s.release, s.templates)
	var configmap corev1.ConfigMap
	var configmapApplication WebModelerRestAPIApplicationYAML
	helm.UnmarshalK8SYaml(s.T(), output, &configmap)

	err := yaml.Unmarshal([]byte(configmap.Data["application.yaml"]), &configmapApplication)
	if err != nil {
		s.Fail("Failed to unmarshal yaml. error=", err)
	}

	// then
	s.Require().Equal(3, len(configmapApplication.Camunda.Modeler.Clusters))
	s.Require().Equal("test-cluster-1", configmapApplication.Camunda.Modeler.Clusters[0].Id)
	s.Require().Equal("test cluster 1", configmapApplication.Camunda.Modeler.Clusters[0].Name)
	s.Require().Equal("8.6.0", configmapApplication.Camunda.Modeler.Clusters[0].Version)
	s.Require().Equal("NONE", configmapApplication.Camunda.Modeler.Clusters[0].Authentication)
	s.Require().Equal("grpc://orchestration.test-1:26500", configmapApplication.Camunda.Modeler.Clusters[0].Url.Zeebe.Grpc)
	s.Require().Equal("http://orchestration.test-1:8080", configmapApplication.Camunda.Modeler.Clusters[0].Url.Zeebe.Rest)
	s.Require().Equal("test-cluster-2", configmapApplication.Camunda.Modeler.Clusters[1].Id)
	s.Require().Equal("test cluster 2", configmapApplication.Camunda.Modeler.Clusters[1].Name)
	s.Require().Equal("8.8.0-alpha1", configmapApplication.Camunda.Modeler.Clusters[1].Version)
	s.Require().Equal("BEARER_TOKEN", configmapApplication.Camunda.Modeler.Clusters[1].Authentication)
	s.Require().Equal("grpc://orchestration.test-2:26500", configmapApplication.Camunda.Modeler.Clusters[1].Url.Grpc)
	s.Require().Equal("http://orchestration.test-2:8080", configmapApplication.Camunda.Modeler.Clusters[1].Url.Rest)
	s.Require().Equal("http://localhost:8088", configmapApplication.Camunda.Modeler.Clusters[1].Url.WebApp)
	s.Require().Equal("test-cluster-3", configmapApplication.Camunda.Modeler.Clusters[2].Id)
	s.Require().Equal("test cluster 3", configmapApplication.Camunda.Modeler.Clusters[2].Name)
	s.Require().Equal("8.8.0-alpha1", configmapApplication.Camunda.Modeler.Clusters[2].Version)
	s.Require().Equal("BASIC", configmapApplication.Camunda.Modeler.Clusters[2].Authentication)
	s.Require().Equal("grpc://orchestration.test-3:26500", configmapApplication.Camunda.Modeler.Clusters[2].Url.Grpc)
	s.Require().Equal("http://orchestration.test-3:8080", configmapApplication.Camunda.Modeler.Clusters[2].Url.Rest)
	s.Require().Equal("http://localhost:8088", configmapApplication.Camunda.Modeler.Clusters[2].Url.WebApp)
}

func (s *configmapRestAPITemplateTest) TestContainerShouldNotConfigureClustersIfZeebeDisabledAndNoCustomConfiguration() {
	// given
	values := map[string]string{
		"identity.enabled":             "true",
		"webModelerPostgresql.enabled": "false",
		"orchestration.enabled":        "false",
	}
	maps.Insert(values, maps.All(requiredValues))
	options := &helm.Options{
		SetValues:      values,
		KubectlOptions: k8s.NewKubectlOptions("", "", s.namespace),
	}

	// when
	output := helm.RenderTemplate(s.T(), options, s.chartPath, s.release, s.templates)
	var configmap corev1.ConfigMap
	var configmapApplication WebModelerRestAPIApplicationYAML
	helm.UnmarshalK8SYaml(s.T(), output, &configmap)

	err := yaml.Unmarshal([]byte(configmap.Data["application.yaml"]), &configmapApplication)
	if err != nil {
		s.Fail("Failed to unmarshal yaml. error=", err)
	}

	// then
	s.Require().Empty(configmapApplication.Camunda.Modeler.Clusters)
}

func (s *configmapRestAPITemplateTest) TestContainerShouldSetJwkSetUriFromJwksUrlProperty() {
	// given
	values := map[string]string{
		"identity.enabled":             "true",
		"global.identity.auth.enabled": "true",
		"global.identity.auth.jwksUrl": "https://example.com/auth/realms/test/protocol/openid-connect/certs",
	}
	maps.Insert(values, maps.All(requiredValues))
	options := &helm.Options{
		SetValues:      values,
		KubectlOptions: k8s.NewKubectlOptions("", "", s.namespace),
	}

	// when
	output := helm.RenderTemplate(s.T(), options, s.chartPath, s.release, s.templates)
	var configmap corev1.ConfigMap
	var configmapApplication WebModelerRestAPIApplicationYAML
	helm.UnmarshalK8SYaml(s.T(), output, &configmap)

	err := yaml.Unmarshal([]byte(configmap.Data["application.yaml"]), &configmapApplication)
	if err != nil {
		s.Fail("Failed to unmarshal yaml. error=", err)
	}

	// then
	s.Require().Equal("https://example.com/auth/realms/test/protocol/openid-connect/certs", configmapApplication.Spring.Security.OAuth2.ResourceServer.JWT.JwkSetURI)
}

func (s *configmapRestAPITemplateTest) TestContainerShouldSetJwkSetUriFromIssuerBackendUrlProperty() {
	// given
	values := map[string]string{
		"identity.enabled":                      "true",
		"global.identity.auth.enabled":          "true",
		"global.identity.auth.issuerBackendUrl": "http://test-keycloak/auth/realms/test",
	}
	maps.Insert(values, maps.All(requiredValues))
	options := &helm.Options{
		SetValues:      values,
		KubectlOptions: k8s.NewKubectlOptions("", "", s.namespace),
	}

	// when
	output := helm.RenderTemplate(s.T(), options, s.chartPath, s.release, s.templates)
	var configmap corev1.ConfigMap
	var configmapApplication WebModelerRestAPIApplicationYAML
	helm.UnmarshalK8SYaml(s.T(), output, &configmap)

	err := yaml.Unmarshal([]byte(configmap.Data["application.yaml"]), &configmapApplication)
	if err != nil {
		s.Fail("Failed to unmarshal yaml. error=", err)
	}

	// then
	s.Require().Equal("http://test-keycloak/auth/realms/test/protocol/openid-connect/certs", configmapApplication.Spring.Security.OAuth2.ResourceServer.JWT.JwkSetURI)
}

func (s *configmapRestAPITemplateTest) TestContainerShouldSetJwkSetUriFromKeycloakUrlProperties() {
	// given
	values := map[string]string{
		"identity.enabled":                      "true",
		"global.identity.auth.enabled":          "true",
		"global.identity.keycloak.url.protocol": "https",
		"global.identity.keycloak.url.host":     "example.com",
		"global.identity.keycloak.url.port":     "443",
		"global.identity.keycloak.contextPath":  "/",
		"global.identity.keycloak.realm":        "test",
	}
	maps.Insert(values, maps.All(requiredValues))
	options := &helm.Options{
		SetValues:      values,
		KubectlOptions: k8s.NewKubectlOptions("", "", s.namespace),
	}

	// when
	output := helm.RenderTemplate(s.T(), options, s.chartPath, s.release, s.templates)
	var configmap corev1.ConfigMap
	var configmapApplication WebModelerRestAPIApplicationYAML
	helm.UnmarshalK8SYaml(s.T(), output, &configmap)

	err := yaml.Unmarshal([]byte(configmap.Data["application.yaml"]), &configmapApplication)
	if err != nil {
		s.Fail("Failed to unmarshal yaml. error=", err)
	}

	// then
	s.Require().Equal("https://example.com:443/test/protocol/openid-connect/certs", configmapApplication.Spring.Security.OAuth2.ResourceServer.JWT.JwkSetURI)
}

func (s *configmapRestAPITemplateTest) TestContainerShouldSetJdbcUrlFromHostPortDatabase() {
	// given
	values := map[string]string{
		"identity.enabled":                             "true",
		"webModelerPostgresql.enabled":                 "false",
		"webModeler.restapi.externalDatabase.host":     "custom-db.example.com",
		"webModeler.restapi.externalDatabase.port":     "65432",
		"webModeler.restapi.externalDatabase.database": "custom-modeler-db",
	}
	maps.Insert(values, maps.All(requiredValues))
	options := &helm.Options{
		SetValues:      values,
		KubectlOptions: k8s.NewKubectlOptions("", "", s.namespace),
	}

	// when
	output := helm.RenderTemplate(s.T(), options, s.chartPath, s.release, s.templates)
	var configmap corev1.ConfigMap
	var configmapApplication WebModelerRestAPIApplicationYAML
	helm.UnmarshalK8SYaml(s.T(), output, &configmap)

	err := yaml.Unmarshal([]byte(configmap.Data["application.yaml"]), &configmapApplication)
	if err != nil {
		s.Fail("Failed to unmarshal yaml. error=", err)
	}

	// then
	s.Require().Equal("jdbc:postgresql://custom-db.example.com:65432/custom-modeler-db", configmapApplication.Spring.Datasource.Url)
}
