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

package web_modeler

import (
	"camunda-platform/test/unit/testhelpers"
	"path/filepath"
	"strings"
	"testing"

	corev1 "k8s.io/api/core/v1"

	"github.com/gruntwork-io/terratest/modules/helm"
	"github.com/gruntwork-io/terratest/modules/random"
	"github.com/stretchr/testify/require"
	"github.com/stretchr/testify/suite"
	appsv1 "k8s.io/api/apps/v1"
)

type WebappDeploymentTemplateTest struct {
	suite.Suite
	chartPath string
	release   string
	namespace string
	templates []string
}

func TestWebappDeploymentTemplate(t *testing.T) {
	t.Parallel()

	chartPath, err := filepath.Abs("../../../")
	require.NoError(t, err)

	suite.Run(t, &WebappDeploymentTemplateTest{
		chartPath: chartPath,
		release:   "camunda-platform-test",
		namespace: "camunda-platform-" + strings.ToLower(random.UniqueId()),
		templates: []string{"templates/web-modeler/deployment-webapp.yaml"},
	})
}

func (s *WebappDeploymentTemplateTest) TestDifferentValuesInputs() {
	testCases := []testhelpers.TestCase{
		{
			Name: "TestContainerShouldSetCorrectKeycloakServiceUrl",
			Values: map[string]string{
				"webModeler.enabled":                    "true",
				"webModeler.restapi.mail.fromAddress":   "example@example.com",
				"global.identity.keycloak.url.protocol": "http",
				"global.identity.keycloak.url.host":     "keycloak",
				"global.identity.keycloak.url.port":     "80",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(s.T(), output, &deployment)

				// then
				env := deployment.Spec.Template.Spec.Containers[0].Env
				s.Require().Contains(env,
					corev1.EnvVar{
						Name:  "OAUTH2_JWKS_URL",
						Value: "http://keycloak:80/auth/realms/camunda-platform/protocol/openid-connect/certs",
					})
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
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(s.T(), output, &deployment)

				// then
				env := deployment.Spec.Template.Spec.Containers[0].Env
				s.Require().Contains(env,
					corev1.EnvVar{
						Name:  "OAUTH2_TYPE",
						Value: "MICROSOFT",
					})
			},
		}, {
			Name: "TestContainerShouldSetCorrectIdentityServiceUrlWithFullnameOverride",
			Values: map[string]string{
				"webModeler.enabled":                  "true",
				"webModeler.restapi.mail.fromAddress": "example@example.com",
				"identity.fullnameOverride":           "custom-identity-fullname",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(s.T(), output, &deployment)

				// then
				env := deployment.Spec.Template.Spec.Containers[0].Env
				s.Require().Contains(env, corev1.EnvVar{Name: "IDENTITY_BASE_URL", Value: "http://custom-identity-fullname:80"})
			},
		}, {
			Name: "TestContainerShouldSetCorrectIdentityServiceUrlWithNameOverride",
			Values: map[string]string{
				"webModeler.enabled":                  "true",
				"webModeler.restapi.mail.fromAddress": "example@example.com",
				"identity.nameOverride":               "custom-identity",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(s.T(), output, &deployment)

				// then
				env := deployment.Spec.Template.Spec.Containers[0].Env
				s.Require().Contains(env,
					corev1.EnvVar{Name: "IDENTITY_BASE_URL", Value: "http://camunda-platform-test-custom-identity:80"})
			},
		}, {
			Name: "TestContainerShouldSetCorrectAuthClientId",
			Values: map[string]string{
				"webModeler.enabled":                       "true",
				"webModeler.restapi.mail.fromAddress":      "example@example.com",
				"global.identity.auth.webModeler.clientId": "custom-clientId",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(s.T(), output, &deployment)

				// then
				env := deployment.Spec.Template.Spec.Containers[0].Env
				s.Require().Contains(env,
					corev1.EnvVar{
						Name:  "OAUTH2_CLIENT_ID",
						Value: "custom-clientId",
					})
			},
		}, {
			Name: "TestContainerShouldSetCorrectAuthClientApiAudience",
			Values: map[string]string{
				"webModeler.enabled":                                "true",
				"webModeler.restapi.mail.fromAddress":               "example@example.com",
				"global.identity.auth.webModeler.clientApiAudience": "custom-audience",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(s.T(), output, &deployment)

				// then
				env := deployment.Spec.Template.Spec.Containers[0].Env
				s.Require().Contains(env,
					corev1.EnvVar{
						Name:  "OAUTH2_TOKEN_AUDIENCE",
						Value: "custom-audience",
					})
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
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(s.T(), output, &deployment)

				// then
				env := deployment.Spec.Template.Spec.Containers[0].Env
				s.Require().Contains(env, corev1.EnvVar{Name: "CLIENT_PUSHER_HOST", Value: "modeler-ws.example.com"})
				s.Require().Contains(env, corev1.EnvVar{Name: "CLIENT_PUSHER_PORT", Value: "443"})
				s.Require().Contains(env, corev1.EnvVar{Name: "CLIENT_PUSHER_FORCE_TLS", Value: "true"})
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
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(s.T(), output, &deployment)

				// then
				env := deployment.Spec.Template.Spec.Containers[0].Env
				s.Require().Contains(env, corev1.EnvVar{Name: "CLIENT_PUSHER_HOST", Value: "modeler-ws.example.com"})
				s.Require().Contains(env, corev1.EnvVar{Name: "CLIENT_PUSHER_PORT", Value: "80"})
				s.Require().Contains(env, corev1.EnvVar{Name: "CLIENT_PUSHER_FORCE_TLS", Value: "false"})
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
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(s.T(), output, &deployment)

				// then
				env := deployment.Spec.Template.Spec.Containers[0].Env
				s.Require().Contains(env, corev1.EnvVar{Name: "CLIENT_PUSHER_HOST", Value: "c8.example.com"})
				s.Require().Contains(env, corev1.EnvVar{Name: "CLIENT_PUSHER_PORT", Value: "443"})
				s.Require().Contains(env, corev1.EnvVar{Name: "CLIENT_PUSHER_PATH", Value: "/modeler-ws"})
				s.Require().Contains(env, corev1.EnvVar{Name: "CLIENT_PUSHER_FORCE_TLS", Value: "true"})
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
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(s.T(), output, &deployment)

				// then
				env := deployment.Spec.Template.Spec.Containers[0].Env
				s.Require().Contains(env, corev1.EnvVar{Name: "CLIENT_PUSHER_HOST", Value: "c8.example.com"})
				s.Require().Contains(env, corev1.EnvVar{Name: "CLIENT_PUSHER_PORT", Value: "80"})
				s.Require().Contains(env, corev1.EnvVar{Name: "CLIENT_PUSHER_PATH", Value: "/modeler-ws"})
				s.Require().Contains(env, corev1.EnvVar{Name: "CLIENT_PUSHER_FORCE_TLS", Value: "false"})
			},
		}, {
			Name: "TestContainerShouldSetServerHttpsOnly",
			Values: map[string]string{
				"webModeler.enabled":                          "true",
				"webModeler.restapi.mail.fromAddress":         "example@example.com",
				"global.identity.auth.webModeler.redirectUrl": "https://modeler.example.com",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(s.T(), output, &deployment)

				// then
				env := deployment.Spec.Template.Spec.Containers[0].Env
				s.Require().Contains(env, corev1.EnvVar{Name: "SERVER_HTTPS_ONLY", Value: "true"})
			},
		}, {
			Name: "TestContainerStartupProbe",
			Values: map[string]string{
				"webModeler.enabled":                       "true",
				"webModeler.restapi.mail.fromAddress":      "example@example.com",
				"webModeler.webapp.startupProbe.enabled":   "true",
				"webModeler.webapp.startupProbe.probePath": "/healthz",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(s.T(), output, &deployment)

				// then
				probe := deployment.Spec.Template.Spec.Containers[0].StartupProbe

				s.Require().Equal("/healthz", probe.HTTPGet.Path)
				s.Require().Equal("http-management", probe.HTTPGet.Port.StrVal)
			},
		}, {
			Name: "TestContainerReadinessProbe",
			Values: map[string]string{
				"webModeler.enabled":                         "true",
				"webModeler.restapi.mail.fromAddress":        "example@example.com",
				"webModeler.webapp.readinessProbe.enabled":   "true",
				"webModeler.webapp.readinessProbe.probePath": "/healthz",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(s.T(), output, &deployment)

				// then
				probe := deployment.Spec.Template.Spec.Containers[0].ReadinessProbe

				s.Require().Equal("/healthz", probe.HTTPGet.Path)
				s.Require().Equal("http-management", probe.HTTPGet.Port.StrVal)
			},
		}, {
			Name: "TestContainerLivenessProbe",
			Values: map[string]string{
				"webModeler.enabled":                        "true",
				"webModeler.restapi.mail.fromAddress":       "example@example.com",
				"webModeler.webapp.livenessProbe.enabled":   "true",
				"webModeler.webapp.livenessProbe.probePath": "/healthz",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(s.T(), output, &deployment)

				// then
				probe := deployment.Spec.Template.Spec.Containers[0].LivenessProbe

				s.Require().Equal("/healthz", probe.HTTPGet.Path)
				s.Require().Equal("http-management", probe.HTTPGet.Port.StrVal)
			},
		}, {
			Name:                 "TestContainerProbesWithContextPath",
			HelmOptionsExtraArgs: map[string][]string{"install": {"--debug"}},
			Values: map[string]string{
				"webModeler.enabled":                         "true",
				"webModeler.restapi.mail.fromAddress":        "example@example.com",
				"webModeler.contextPath":                     "/test",
				"webModeler.webapp.startupProbe.enabled":     "true",
				"webModeler.webapp.startupProbe.probePath":   "/start",
				"webModeler.webapp.readinessProbe.enabled":   "true",
				"webModeler.webapp.readinessProbe.probePath": "/ready",
				"webModeler.webapp.livenessProbe.enabled":    "true",
				"webModeler.webapp.livenessProbe.probePath":  "/live",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(s.T(), output, &deployment)

				// then
				probe := deployment.Spec.Template.Spec.Containers[0]

				s.Require().Equal("/start", probe.StartupProbe.HTTPGet.Path)
				s.Require().Equal("/ready", probe.ReadinessProbe.HTTPGet.Path)
				s.Require().Equal("/live", probe.LivenessProbe.HTTPGet.Path)
			},
		}, {
			// Web-Modeler WebApp doesn't support contextPath for health endpoints.
			Name: "TestContainerSetSidecar",
			Values: map[string]string{
				"webModeler.enabled":                                   "true",
				"webModeler.restapi.mail.fromAddress":                  "example@example.com",
				"webModeler.webapp.sidecars[0].name":                   "nginx",
				"webModeler.webapp.sidecars[0].image":                  "nginx:latest",
				"webModeler.webapp.sidecars[0].ports[0].containerPort": "80",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(s.T(), output, &deployment)

				// then
				podContainers := deployment.Spec.Template.Spec.Containers
				expectedContainer := corev1.Container{
					Name:  "nginx",
					Image: "nginx:latest",
					Ports: []corev1.ContainerPort{
						{
							ContainerPort: 80,
						},
					},
				}

				s.Require().Contains(podContainers, expectedContainer)
			},
		}, {
			Name: "TestContainerSetInitContainer",
			Values: map[string]string{
				"webModeler.enabled":                                         "true",
				"webModeler.restapi.mail.fromAddress":                        "example@example.com",
				"webModeler.webapp.initContainers[0].name":                   "nginx",
				"webModeler.webapp.initContainers[0].image":                  "nginx:latest",
				"webModeler.webapp.initContainers[0].ports[0].containerPort": "80",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(s.T(), output, &deployment)

				// then
				podContainers := deployment.Spec.Template.Spec.InitContainers
				expectedContainer := corev1.Container{
					Name:  "nginx",
					Image: "nginx:latest",
					Ports: []corev1.ContainerPort{
						{
							ContainerPort: 80,
						},
					},
				}

				s.Require().Contains(podContainers, expectedContainer)
			},
		},
	}

	testhelpers.RunTestCasesE(s.T(), s.chartPath, s.release, s.namespace, s.templates, testCases)
}
