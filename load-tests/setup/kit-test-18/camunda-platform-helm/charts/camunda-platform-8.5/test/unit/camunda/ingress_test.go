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
			Name:                 "TestIngressEnabledWithKeycloakCustomContextPath",
			HelmOptionsExtraArgs: map[string][]string{"install": {"--debug"}},
			Values: map[string]string{
				"global.ingress.enabled":               "true",
				"global.identity.keycloak.contextPath": "/custom",
				"identityKeycloak.enabled":             "true",
				"identityKeycloak.httpRelativePath":    "/custom",
				"identity.contextPath":                 "/identity",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var ingress netv1.Ingress
				helm.UnmarshalK8SYaml(s.T(), output, &ingress)
				path := ingress.Spec.Rules[0].HTTP.Paths[0]
				require.Equal(t, "/custom/", path.Path)
				require.Equal(t, "camunda-platform-test-keycloak", path.Backend.Service.Name)
			},
		}, {
			Name:                 "TestIngressWithKeycloakChartIsDisabled",
			HelmOptionsExtraArgs: map[string][]string{"install": {"--debug"}},
			Values: map[string]string{
				"global.ingress.enabled": "true",
				"identity.contextPath":   "/identity",
				// Disable Identity Keycloak chart.
				"identityKeycloak.enabled": "false",
				// Set vars to use existing Keycloak.
				"global.identity.keycloak.url.protocol": "https",
				"global.identity.keycloak.url.host":     "keycloak.prod.svc.cluster.local",
				"global.identity.keycloak.url.port":     "8443",
			},
			Verifier: func(t *testing.T, output string, err error) {
				// TODO: Instead of using plain text search, unmarshal the output in an ingress struct and assert the values.
				require.NotContains(t, output, "keycloak")
				require.NotContains(t, output, "path: /auth")
				require.NotContains(t, output, "number: 8443")
			},
		}, {
			Name:                 "TestIngressEnabledAndKeycloakChartProxyForwardingEnabled",
			HelmOptionsExtraArgs: map[string][]string{"install": {"--debug"}},
			CaseTemplates: &testhelpers.CaseTemplate{
				Templates: nil,
			},
			RenderTemplateExtraArgs: []string{"--show-only", "charts/identityKeycloak/templates/statefulset.yaml"},
			Values: map[string]string{
				"global.ingress.tls.enabled": "true",
				"identity.contextPath":       "/identity",
				"identityKeycloak.enabled":   "true",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var statefulSet appsv1.StatefulSet
				helm.UnmarshalK8SYaml(t, output, &statefulSet)

				// then
				env := statefulSet.Spec.Template.Spec.Containers[0].Env
				require.Contains(t, env,
					corev1.EnvVar{
						Name:  "KEYCLOAK_PROXY_ADDRESS_FORWARDING",
						Value: "true",
					})
			},
		}, {
			Name:                    "TestIngressEnabledWithKeycloakCustomContextPathWithTemplateArgs",
			HelmOptionsExtraArgs:    map[string][]string{"install": {"--debug"}},
			RenderTemplateExtraArgs: []string{"--show-only", "charts/identityKeycloak/templates/statefulset.yaml"},
			CaseTemplates: &testhelpers.CaseTemplate{
				Templates: nil,
			},
			Values: map[string]string{
				"global.ingress.enabled":               "true",
				"global.identity.keycloak.contextPath": "/custom",
				"identityKeycloak.enabled":             "true",
				"identityKeycloak.httpRelativePath":    "/custom",
				"identity.contextPath":                 "/identity",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var statefulSet appsv1.StatefulSet
				helm.UnmarshalK8SYaml(s.T(), output, &statefulSet)

				// then
				env := statefulSet.Spec.Template.Spec.Containers[0].Env
				require.Contains(t, env,
					corev1.EnvVar{
						Name:  "KEYCLOAK_HTTP_RELATIVE_PATH",
						Value: "/custom",
					})
			},
		}, {
			Name:                 "TestIngressWithContextPath",
			HelmOptionsExtraArgs: map[string][]string{"install": {"--debug"}},
			Values: map[string]string{
				"global.ingress.enabled":              "true",
				"identity.contextPath":                "/identity",
				"operate.contextPath":                 "/operate",
				"optimize.contextPath":                "/optimize",
				"tasklist.contextPath":                "/tasklist",
				"webModeler.enabled":                  "true",
				"webModeler.restapi.mail.fromAddress": "example@example.com",
				"webModeler.contextPath":              "/modeler",
				"zeebeGateway.contextPath":            "/zeebe",
			},
			Verifier: func(t *testing.T, output string, err error) {
				require.Contains(t, output, "kind: Ingress")
				require.Contains(t, output, "path: /auth")
				require.Contains(t, output, "path: /identity")
				require.Contains(t, output, "path: /operate")
				require.Contains(t, output, "path: /optimize")
				require.Contains(t, output, "path: /tasklist")
				require.Contains(t, output, "path: /modeler")
				require.Contains(t, output, "path: /modeler-ws")
				require.Contains(t, output, "path: /zeebe")
			},
		}, {
			Name:                 "TestIngressComponentWithNoContextPath",
			HelmOptionsExtraArgs: map[string][]string{"install": {"--debug"}},
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
			Verifier: func(t *testing.T, output string, err error) {
				require.NotContains(t, output, "name: camunda-platform-test-identity")
				require.NotContains(t, output, "name: camunda-platform-test-operate")
				require.NotContains(t, output, "name: camunda-platform-test-optimize")
				require.NotContains(t, output, "name: camunda-platform-test-tasklist")
				require.NotContains(t, output, "name: camunda-platform-test-web-modeler-webapp")
				require.NotContains(t, output, "name: camunda-platform-test-web-modeler-websockets")
				require.NotContains(t, output, "name: camunda-platform-test-zeebe-gateway")
			},
		}, {
			Name:                 "TestIngressComponentDisabled",
			HelmOptionsExtraArgs: map[string][]string{"install": {"--debug"}},
			Values: map[string]string{
				"global.ingress.enabled": "true",
				"operate.identity":       "false",
				"operate.enabled":        "false",
				"optimize.enabled":       "false",
				"tasklist.enabled":       "false",
				"webModeler.enabled":     "false",
				"zeebe.enabled":          "false",
			},
			Verifier: func(t *testing.T, output string, err error) {
				require.NotContains(t, output, "name: camunda-platform-test-identity")
				require.NotContains(t, output, "name: camunda-platform-test-operate")
				require.NotContains(t, output, "name: camunda-platform-test-optimize")
				require.NotContains(t, output, "name: camunda-platform-test-tasklist")
				require.NotContains(t, output, "name: camunda-platform-test-web-modeler-webapp")
				require.NotContains(t, output, "name: camunda-platform-test-web-modeler-websockets")
				require.NotContains(t, output, "name: camunda-platform-test-zeebe-gateway")
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
