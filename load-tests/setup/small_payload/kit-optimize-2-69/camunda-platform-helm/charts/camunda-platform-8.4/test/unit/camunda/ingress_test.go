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

package camunda

import (
	"camunda-platform/test/unit/testhelpers"
	"path/filepath"
	"strings"
	"testing"

	"github.com/gruntwork-io/terratest/modules/helm"
	"github.com/gruntwork-io/terratest/modules/random"
	"github.com/stretchr/testify/require"
	"github.com/stretchr/testify/suite"
	appsv1 "k8s.io/api/apps/v1"
	corev1 "k8s.io/api/core/v1"
	netv1 "k8s.io/api/networking/v1"
)

type IngressTemplateTest struct {
	suite.Suite
	chartPath string
	release   string
	namespace string
	templates []string
	extraArgs []string
}

func TestIngressTemplate(t *testing.T) {
	t.Parallel()

	chartPath, err := filepath.Abs("../../../")
	require.NoError(t, err)

	suite.Run(t, &IngressTemplateTest{
		chartPath: chartPath,
		release:   "camunda-platform-test",
		namespace: "camunda-platform-" + strings.ToLower(random.UniqueId()),
		templates: []string{"templates/camunda/ingress.yaml"},
	})
}

func (s *IngressTemplateTest) TestDifferentValuesInputs() {
	testCases := []testhelpers.TestCase{
		{
			Name:                 "TestIngressEnabledAndKeycloakChartProxyForwardingEnabled",
			HelmOptionsExtraArgs: map[string][]string{"install": {"--debug"}},
			// NOTE: helm.Options.ExtraArgs doesn't support passing args to Helm "template" command.
			// TODO: Remove "template" from all helm.Options.ExtraArgs since it doesn't have any effect.
			RenderTemplateExtraArgs: []string{"--show-only", "charts/identity/charts/keycloak/templates/statefulset.yaml"},
			CaseTemplates: &testhelpers.CaseTemplate{
				Templates: nil,
			},
			Values: map[string]string{
				"global.ingress.tls.enabled": "true",
				"identity.contextPath":       "/identity",
				"identity.keycloak.enabled":  "true",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var statefulSet appsv1.StatefulSet
				helm.UnmarshalK8SYaml(s.T(), output, &statefulSet)

				// then
				env := statefulSet.Spec.Template.Spec.Containers[0].Env
				s.Require().Contains(env,
					corev1.EnvVar{
						Name:  "KEYCLOAK_PROXY_ADDRESS_FORWARDING",
						Value: "true",
					})
			},
		}, {
			Name:                 "TestIngressEnabledWithKeycloakCustomContextPath",
			HelmOptionsExtraArgs: map[string][]string{"install": {"--debug"}},
			Values: map[string]string{
				"global.ingress.enabled":               "true",
				"global.identity.keycloak.contextPath": "/custom",
				"identity.keycloak.enabled":            "true",
				"identity.keycloak.httpRelativePath":   "/custom",
				"identity.contextPath":                 "/identity",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var ingress netv1.Ingress
				helm.UnmarshalK8SYaml(s.T(), output, &ingress)

				// then
				path := ingress.Spec.Rules[0].HTTP.Paths[0]
				s.Require().Equal("/custom", path.Path)
				s.Require().Equal("camunda-platform-test-keycloak", path.Backend.Service.Name)
			},
		}, {
			Name:                 "TestIngressEnabledWithKeycloakCustomContextPathStatefulSetsOnly",
			HelmOptionsExtraArgs: map[string][]string{"install": {"--debug"}},
			Values: map[string]string{
				"global.ingress.enabled":               "true",
				"global.identity.keycloak.contextPath": "/custom",
				"identity.keycloak.enabled":            "true",
				"identity.keycloak.httpRelativePath":   "/custom",
				"identity.contextPath":                 "/identity",
			},
			RenderTemplateExtraArgs: []string{"--show-only", "charts/identity/charts/keycloak/templates/statefulset.yaml"},
			CaseTemplates: &testhelpers.CaseTemplate{
				Templates: nil,
			},
			Verifier: func(t *testing.T, output string, err error) {
				var statefulSet appsv1.StatefulSet
				helm.UnmarshalK8SYaml(s.T(), output, &statefulSet)

				// then
				env := statefulSet.Spec.Template.Spec.Containers[0].Env
				s.Require().Contains(env,
					corev1.EnvVar{
						Name:  "KEYCLOAK_HTTP_RELATIVE_PATH",
						Value: "/custom",
					})
			},
		}, {
			Name: "TestIngressWithKeycloakChartIsDisabled",
			Values: map[string]string{
				"global.ingress.enabled": "true",
				"identity.contextPath":   "/identity",
				// Disable Identity Keycloak chart.
				"identity.keycloak.enabled": "false",
				// Set vars to use existing Keycloak.
				"global.identity.keycloak.url.protocol": "https",
				"global.identity.keycloak.url.host":     "keycloak.prod.svc.cluster.local",
				"global.identity.keycloak.url.port":     "8443",
			},
			HelmOptionsExtraArgs: map[string][]string{"install": {"--debug"}},
			Verifier: func(t *testing.T, output string, err error) {
				// then
				// TODO: Instead of using plain text search, unmarshal the output in an ingress struct and assert the values.
				s.Require().NotContains(output, "keycloak")
				s.Require().NotContains(output, "path: /auth")
				s.Require().NotContains(output, "number: 8443")
			},
		}, {
			Name: "TestIngressWithContextPath",
			Values: map[string]string{
				"global.ingress.enabled":              "true",
				"identity.contextPath":                "/identity",
				"operate.contextPath":                 "/operate",
				"optimize.contextPath":                "/optimize",
				"tasklist.contextPath":                "/tasklist",
				"webModeler.enabled":                  "true",
				"webModeler.restapi.mail.fromAddress": "example@example.com",
				"webModeler.contextPath":              "/modeler",
			},
			HelmOptionsExtraArgs: map[string][]string{"install": {"--debug"}},
			Verifier: func(t *testing.T, output string, err error) {
				// then
				s.Require().Contains(output, "kind: Ingress")
				s.Require().Contains(output, "path: /auth")
				s.Require().Contains(output, "path: /identity")
				s.Require().Contains(output, "path: /operate")
				s.Require().Contains(output, "path: /optimize")
				s.Require().Contains(output, "path: /tasklist")
				s.Require().Contains(output, "path: /modeler")
				s.Require().Contains(output, "path: /modeler-ws")
			},
		}, {
			Name: "TestIngressComponentWithNoContextPath",
			Values: map[string]string{
				"global.ingress.enabled":              "true",
				"identity.contextPath":                "",
				"operate.contextPath":                 "",
				"optimize.contextPath":                "",
				"tasklist.contextPath":                "",
				"webModeler.enabled":                  "true",
				"webModeler.restapi.mail.fromAddress": "example@example.com",
				"webModeler.contextPath":              "",
			},
			HelmOptionsExtraArgs: map[string][]string{"install": {"--debug"}},
			Verifier: func(t *testing.T, output string, err error) {
				// then
				s.Require().NotContains(output, "name: camunda-platform-test-identity")
				s.Require().NotContains(output, "name: camunda-platform-test-operate")
				s.Require().NotContains(output, "name: camunda-platform-test-optimize")
				s.Require().NotContains(output, "name: camunda-platform-test-tasklist")
				s.Require().NotContains(output, "name: camunda-platform-test-web-modeler-webapp")
				s.Require().NotContains(output, "name: camunda-platform-test-web-modeler-websockets")
			},
		}, {
			Name: "TestIngressComponentDisabled",
			Values: map[string]string{
				"global.ingress.enabled": "true",
				"operate.identity":       "false",
				"operate.enabled":        "false",
				"optimize.enabled":       "false",
				"tasklist.enabled":       "false",
				"webModeler.enabled":     "false",
			},
			HelmOptionsExtraArgs: map[string][]string{"install": {"--debug"}},
			Verifier: func(t *testing.T, output string, err error) {
				// then
				s.Require().NotContains(output, "name: camunda-platform-test-identity")
				s.Require().NotContains(output, "name: camunda-platform-test-operate")
				s.Require().NotContains(output, "name: camunda-platform-test-optimize")
				s.Require().NotContains(output, "name: camunda-platform-test-tasklist")
				s.Require().NotContains(output, "name: camunda-platform-test-web-modeler-webapp")
				s.Require().NotContains(output, "name: camunda-platform-test-web-modeler-websockets")
			},
		}, {
			Name:                 "TestIngressExternal",
			HelmOptionsExtraArgs: map[string][]string{"install": {"--debug"}},
			Values: map[string]string{
				"global.ingress.enabled":  "true",
				"global.ingress.external": "true",
			},
			Verifier: func(t *testing.T, output string, err error) {
				// then
				s.Require().NotContains(output, "kind: Ingress")
			},
		},
	}

	testhelpers.RunTestCasesE(s.T(), s.chartPath, s.release, s.namespace, s.templates, testCases)
}
