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
	"path/filepath"
	"strings"
	"testing"

	corev1 "k8s.io/api/core/v1"

	"github.com/gruntwork-io/terratest/modules/helm"
	"github.com/gruntwork-io/terratest/modules/k8s"
	"github.com/gruntwork-io/terratest/modules/random"
	"github.com/stretchr/testify/require"
	"github.com/stretchr/testify/suite"
	appsv1 "k8s.io/api/apps/v1"
)

type webappDeploymentTemplateTest struct {
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

	suite.Run(t, &webappDeploymentTemplateTest{
		chartPath: chartPath,
		release:   "camunda-platform-test",
		namespace: "camunda-platform-" + strings.ToLower(random.UniqueId()),
		templates: []string{"templates/web-modeler/deployment-webapp.yaml"},
	})
}

func (s *webappDeploymentTemplateTest) TestContainerShouldSetCorrectKeycloakClientConfigWithRootPath() {
	// given
	options := &helm.Options{
		SetValues: map[string]string{
			"webModeler.enabled":                   "true",
			"webModeler.restapi.mail.fromAddress":  "example@example.com",
			"global.identity.auth.publicIssuerUrl": "http://localhost:18080/realms/test-realm",
			"global.identity.keycloak.contextPath": "/",
			"global.identity.keycloak.realm":       "/realms/test-realm",
		},
		KubectlOptions: k8s.NewKubectlOptions("", "", s.namespace),
	}

	// when
	output := helm.RenderTemplate(s.T(), options, s.chartPath, s.release, s.templates)
	var deployment appsv1.Deployment
	helm.UnmarshalK8SYaml(s.T(), output, &deployment)

	// then
	env := deployment.Spec.Template.Spec.Containers[0].Env
	s.Require().Contains(env,
		corev1.EnvVar{
			Name:  "KEYCLOAK_BASE_URL",
			Value: "http://localhost:18080",
		},
		corev1.EnvVar{
			Name:  "KEYCLOAK_CONTEXT_PATH",
			Value: "/",
		},
		corev1.EnvVar{
			Name:  "KEYCLOAK_REALM",
			Value: "test-realm",
		},
	)
}

func (s *webappDeploymentTemplateTest) TestContainerShouldSetCorrectKeycloakClientConfigWithCustomPath() {
	// given
	options := &helm.Options{
		SetValues: map[string]string{
			"webModeler.enabled":                   "true",
			"webModeler.restapi.mail.fromAddress":  "example@example.com",
			"global.identity.auth.publicIssuerUrl": "http://localhost:18080/test-path/realms/test-realm",
			"global.identity.keycloak.contextPath": "/test-path",
			"global.identity.keycloak.realm":       "/realms/test-realm",
		},
		KubectlOptions: k8s.NewKubectlOptions("", "", s.namespace),
	}

	// when
	output := helm.RenderTemplate(s.T(), options, s.chartPath, s.release, s.templates)
	var deployment appsv1.Deployment
	helm.UnmarshalK8SYaml(s.T(), output, &deployment)

	// then
	env := deployment.Spec.Template.Spec.Containers[0].Env
	s.Require().Contains(env,
		corev1.EnvVar{
			Name:  "KEYCLOAK_BASE_URL",
			Value: "http://localhost:18080",
		},
		corev1.EnvVar{
			Name:  "KEYCLOAK_CONTEXT_PATH",
			Value: "/test-path",
		},
		corev1.EnvVar{
			Name:  "KEYCLOAK_REALM",
			Value: "test-realm",
		},
	)
}

func (s *webappDeploymentTemplateTest) TestContainerShouldSetCorrectKeycloakServiceUrl() {
	// given
	options := &helm.Options{
		SetValues: map[string]string{
			"webModeler.enabled":                    "true",
			"webModeler.restapi.mail.fromAddress":   "example@example.com",
			"global.identity.keycloak.url.protocol": "http",
			"global.identity.keycloak.url.host":     "keycloak",
			"global.identity.keycloak.url.port":     "80",
		},
		KubectlOptions: k8s.NewKubectlOptions("", "", s.namespace),
	}

	// when
	output := helm.RenderTemplate(s.T(), options, s.chartPath, s.release, s.templates)
	var deployment appsv1.Deployment
	helm.UnmarshalK8SYaml(s.T(), output, &deployment)

	// then
	env := deployment.Spec.Template.Spec.Containers[0].Env
	s.Require().Contains(env,
		corev1.EnvVar{
			Name:  "KEYCLOAK_JWKS_URL",
			Value: "http://keycloak:80/auth/realms/camunda-platform/protocol/openid-connect/certs",
		})
}

func (s *webappDeploymentTemplateTest) TestContainerShouldSetCorrectIdentityServiceUrlWithFullnameOverride() {
	// given
	options := &helm.Options{
		SetValues: map[string]string{
			"webModeler.enabled":                  "true",
			"webModeler.restapi.mail.fromAddress": "example@example.com",
			"identity.fullnameOverride":           "custom-identity-fullname",
		},
		KubectlOptions: k8s.NewKubectlOptions("", "", s.namespace),
	}

	// when
	output := helm.RenderTemplate(s.T(), options, s.chartPath, s.release, s.templates)
	var deployment appsv1.Deployment
	helm.UnmarshalK8SYaml(s.T(), output, &deployment)

	// then
	env := deployment.Spec.Template.Spec.Containers[0].Env
	s.Require().Contains(env, corev1.EnvVar{Name: "IDENTITY_BASE_URL", Value: "http://custom-identity-fullname:80"})
}

func (s *webappDeploymentTemplateTest) TestContainerShouldSetCorrectIdentityServiceUrlWithNameOverride() {
	// given
	options := &helm.Options{
		SetValues: map[string]string{
			"webModeler.enabled":                  "true",
			"webModeler.restapi.mail.fromAddress": "example@example.com",
			"identity.nameOverride":               "custom-identity",
		},
		KubectlOptions: k8s.NewKubectlOptions("", "", s.namespace),
	}

	// when
	output := helm.RenderTemplate(s.T(), options, s.chartPath, s.release, s.templates)
	var deployment appsv1.Deployment
	helm.UnmarshalK8SYaml(s.T(), output, &deployment)

	// then
	env := deployment.Spec.Template.Spec.Containers[0].Env
	s.Require().Contains(env,
		corev1.EnvVar{Name: "IDENTITY_BASE_URL", Value: "http://camunda-platform-test-custom-identity:80"})
}

func (s *webappDeploymentTemplateTest) TestContainerShouldSetCorrectClientPusherConfigurationWithIngressTlsEnabled() {
	// given
	options := &helm.Options{
		SetValues: map[string]string{
			"webModeler.enabled":                        "true",
			"webModeler.restapi.mail.fromAddress":       "example@example.com",
			"webModeler.ingress.enabled":                "true",
			"webModeler.ingress.websockets.host":        "modeler-ws.example.com",
			"webModeler.ingress.websockets.tls.enabled": "true",
		},
		KubectlOptions: k8s.NewKubectlOptions("", "", s.namespace),
	}

	// when
	output := helm.RenderTemplate(s.T(), options, s.chartPath, s.release, s.templates)
	var deployment appsv1.Deployment
	helm.UnmarshalK8SYaml(s.T(), output, &deployment)

	// then
	env := deployment.Spec.Template.Spec.Containers[0].Env
	s.Require().Contains(env, corev1.EnvVar{Name: "CLIENT_PUSHER_HOST", Value: "modeler-ws.example.com"})
	s.Require().Contains(env, corev1.EnvVar{Name: "CLIENT_PUSHER_PORT", Value: "443"})
	s.Require().Contains(env, corev1.EnvVar{Name: "CLIENT_PUSHER_FORCE_TLS", Value: "true"})
}

func (s *webappDeploymentTemplateTest) TestContainerShouldSetCorrectClientPusherConfigurationWithIngressTlsDisabled() {
	// given
	options := &helm.Options{
		SetValues: map[string]string{
			"webModeler.enabled":                        "true",
			"webModeler.restapi.mail.fromAddress":       "example@example.com",
			"webModeler.ingress.enabled":                "true",
			"webModeler.ingress.websockets.host":        "modeler-ws.example.com",
			"webModeler.ingress.websockets.tls.enabled": "false",
		},
		KubectlOptions: k8s.NewKubectlOptions("", "", s.namespace),
	}

	// when
	output := helm.RenderTemplate(s.T(), options, s.chartPath, s.release, s.templates)
	var deployment appsv1.Deployment
	helm.UnmarshalK8SYaml(s.T(), output, &deployment)

	// then
	env := deployment.Spec.Template.Spec.Containers[0].Env
	s.Require().Contains(env, corev1.EnvVar{Name: "CLIENT_PUSHER_HOST", Value: "modeler-ws.example.com"})
	s.Require().Contains(env, corev1.EnvVar{Name: "CLIENT_PUSHER_PORT", Value: "80"})
	s.Require().Contains(env, corev1.EnvVar{Name: "CLIENT_PUSHER_FORCE_TLS", Value: "false"})
}

func (s *webappDeploymentTemplateTest) TestContainerShouldSetCorrectClientPusherConfigurationWithGlobalIngressTlsEnabled() {
	// given
	options := &helm.Options{
		SetValues: map[string]string{
			"webModeler.enabled":                  "true",
			"webModeler.restapi.mail.fromAddress": "example@example.com",
			"webModeler.ingress.enabled":          "false",
			"webModeler.contextPath":              "/modeler",
			"global.ingress.enabled":              "true",
			"global.ingress.host":                 "c8.example.com",
			"global.ingress.tls.enabled":          "true",
		},
		KubectlOptions: k8s.NewKubectlOptions("", "", s.namespace),
	}

	// when
	output := helm.RenderTemplate(s.T(), options, s.chartPath, s.release, s.templates)
	var deployment appsv1.Deployment
	helm.UnmarshalK8SYaml(s.T(), output, &deployment)

	// then
	env := deployment.Spec.Template.Spec.Containers[0].Env
	s.Require().Contains(env, corev1.EnvVar{Name: "CLIENT_PUSHER_HOST", Value: "c8.example.com"})
	s.Require().Contains(env, corev1.EnvVar{Name: "CLIENT_PUSHER_PORT", Value: "443"})
	s.Require().Contains(env, corev1.EnvVar{Name: "CLIENT_PUSHER_PATH", Value: "/modeler-ws"})
	s.Require().Contains(env, corev1.EnvVar{Name: "CLIENT_PUSHER_FORCE_TLS", Value: "true"})
}

func (s *webappDeploymentTemplateTest) TestContainerShouldSetCorrectClientPusherConfigurationWithGlobalIngressTlsDisabled() {
	// given
	options := &helm.Options{
		SetValues: map[string]string{
			"webModeler.enabled":                  "true",
			"webModeler.restapi.mail.fromAddress": "example@example.com",
			"webModeler.ingress.enabled":          "false",
			"webModeler.contextPath":              "/modeler",
			"global.ingress.enabled":              "true",
			"global.ingress.host":                 "c8.example.com",
			"global.ingress.tls.enabled":          "false",
		},
		KubectlOptions: k8s.NewKubectlOptions("", "", s.namespace),
	}

	// when
	output := helm.RenderTemplate(s.T(), options, s.chartPath, s.release, s.templates)
	var deployment appsv1.Deployment
	helm.UnmarshalK8SYaml(s.T(), output, &deployment)

	// then
	env := deployment.Spec.Template.Spec.Containers[0].Env
	s.Require().Contains(env, corev1.EnvVar{Name: "CLIENT_PUSHER_HOST", Value: "c8.example.com"})
	s.Require().Contains(env, corev1.EnvVar{Name: "CLIENT_PUSHER_PORT", Value: "80"})
	s.Require().Contains(env, corev1.EnvVar{Name: "CLIENT_PUSHER_PATH", Value: "/modeler-ws"})
	s.Require().Contains(env, corev1.EnvVar{Name: "CLIENT_PUSHER_FORCE_TLS", Value: "false"})
}

func (s *webappDeploymentTemplateTest) TestContainerShouldSetServerHttpsOnly() {
	// given
	options := &helm.Options{
		SetValues: map[string]string{
			"webModeler.enabled":                          "true",
			"webModeler.restapi.mail.fromAddress":         "example@example.com",
			"global.identity.auth.webModeler.redirectUrl": "https://modeler.example.com",
		},
		KubectlOptions: k8s.NewKubectlOptions("", "", s.namespace),
	}

	// when
	output := helm.RenderTemplate(s.T(), options, s.chartPath, s.release, s.templates)
	var deployment appsv1.Deployment
	helm.UnmarshalK8SYaml(s.T(), output, &deployment)

	// then
	env := deployment.Spec.Template.Spec.Containers[0].Env
	s.Require().Contains(env, corev1.EnvVar{Name: "SERVER_HTTPS_ONLY", Value: "true"})
}

func (s *webappDeploymentTemplateTest) TestContainerStartupProbe() {
	// given
	options := &helm.Options{
		SetValues: map[string]string{
			"webModeler.enabled":                       "true",
			"webModeler.restapi.mail.fromAddress":      "example@example.com",
			"webModeler.webapp.startupProbe.enabled":   "true",
			"webModeler.webapp.startupProbe.probePath": "/healthz",
		},
		KubectlOptions: k8s.NewKubectlOptions("", "", s.namespace),
	}

	// when
	output := helm.RenderTemplate(s.T(), options, s.chartPath, s.release, s.templates)
	var deployment appsv1.Deployment
	helm.UnmarshalK8SYaml(s.T(), output, &deployment)

	// then
	probe := deployment.Spec.Template.Spec.Containers[0].StartupProbe

	s.Require().Equal("/healthz", probe.HTTPGet.Path)
	s.Require().Equal("http-management", probe.HTTPGet.Port.StrVal)
}

func (s *webappDeploymentTemplateTest) TestContainerReadinessProbe() {
	// given
	options := &helm.Options{
		SetValues: map[string]string{
			"webModeler.enabled":                         "true",
			"webModeler.restapi.mail.fromAddress":        "example@example.com",
			"webModeler.webapp.readinessProbe.enabled":   "true",
			"webModeler.webapp.readinessProbe.probePath": "/healthz",
		},
		KubectlOptions: k8s.NewKubectlOptions("", "", s.namespace),
	}

	// when
	output := helm.RenderTemplate(s.T(), options, s.chartPath, s.release, s.templates)
	var deployment appsv1.Deployment
	helm.UnmarshalK8SYaml(s.T(), output, &deployment)

	// then
	probe := deployment.Spec.Template.Spec.Containers[0].ReadinessProbe

	s.Require().Equal("/healthz", probe.HTTPGet.Path)
	s.Require().Equal("http-management", probe.HTTPGet.Port.StrVal)
}

func (s *webappDeploymentTemplateTest) TestContainerLivenessProbe() {
	// given
	options := &helm.Options{
		SetValues: map[string]string{
			"webModeler.enabled":                        "true",
			"webModeler.restapi.mail.fromAddress":       "example@example.com",
			"webModeler.webapp.livenessProbe.enabled":   "true",
			"webModeler.webapp.livenessProbe.probePath": "/healthz",
		},
		KubectlOptions: k8s.NewKubectlOptions("", "", s.namespace),
	}

	// when
	output := helm.RenderTemplate(s.T(), options, s.chartPath, s.release, s.templates)
	var deployment appsv1.Deployment
	helm.UnmarshalK8SYaml(s.T(), output, &deployment)

	// then
	probe := deployment.Spec.Template.Spec.Containers[0].LivenessProbe

	s.Require().Equal("/healthz", probe.HTTPGet.Path)
	s.Require().Equal("http-management", probe.HTTPGet.Port.StrVal)
}

// Web-Modeler WebApp doesn't support contextPath for health endpoints.
func (s *webappDeploymentTemplateTest) TestContainerProbesWithContextPath() {
	// given
	options := &helm.Options{
		SetValues: map[string]string{
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
		KubectlOptions: k8s.NewKubectlOptions("", "", s.namespace),
		ExtraArgs:      map[string][]string{"install": {"--debug"}},
	}

	// when
	output := helm.RenderTemplate(s.T(), options, s.chartPath, s.release, s.templates)
	var deployment appsv1.Deployment
	helm.UnmarshalK8SYaml(s.T(), output, &deployment)

	// then
	probe := deployment.Spec.Template.Spec.Containers[0]

	s.Require().Equal("/start", probe.StartupProbe.HTTPGet.Path)
	s.Require().Equal("/ready", probe.ReadinessProbe.HTTPGet.Path)
	s.Require().Equal("/live", probe.LivenessProbe.HTTPGet.Path)
}

func (s *webappDeploymentTemplateTest) TestContainerSetSidecar() {
	// given
	options := &helm.Options{
		SetValues: map[string]string{
			"webModeler.enabled":                                   "true",
			"webModeler.restapi.mail.fromAddress":                  "example@example.com",
			"webModeler.webapp.sidecars[0].name":                   "nginx",
			"webModeler.webapp.sidecars[0].image":                  "nginx:latest",
			"webModeler.webapp.sidecars[0].ports[0].containerPort": "80",
		},
		KubectlOptions: k8s.NewKubectlOptions("", "", s.namespace),
	}

	// when
	output := helm.RenderTemplate(s.T(), options, s.chartPath, s.release, s.templates)
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
}

func (s *webappDeploymentTemplateTest) TestContainerSetInitContainer() {
	// given
	options := &helm.Options{
		SetValues: map[string]string{
			"webModeler.enabled":                                         "true",
			"webModeler.restapi.mail.fromAddress":                        "example@example.com",
			"webModeler.webapp.initContainers[0].name":                   "nginx",
			"webModeler.webapp.initContainers[0].image":                  "nginx:latest",
			"webModeler.webapp.initContainers[0].ports[0].containerPort": "80",
		},
		KubectlOptions: k8s.NewKubectlOptions("", "", s.namespace),
	}

	// when
	output := helm.RenderTemplate(s.T(), options, s.chartPath, s.release, s.templates)
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
}
