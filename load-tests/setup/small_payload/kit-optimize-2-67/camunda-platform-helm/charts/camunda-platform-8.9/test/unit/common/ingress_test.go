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
		templates: []string{"templates/common/ingress-http.yaml"},
	})
}

func (s *IngressTemplateTest) TestDifferentValuesInputs() {
	testCases := []testhelpers.TestCase{
		{
			Skip:                 true,
			Name:                 "TestIngressEnabledWithKeycloakCustomContextPathIngress",
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
				helm.UnmarshalK8SYaml(t, output, &ingress)

				// then
				path := ingress.Spec.Rules[0].HTTP.Paths[0]
				require.Equal(t, "/custom/", path.Path)
				require.Equal(t, "camunda-platform-test-keycloak", path.Backend.Service.Name)
			},
		},
		{
			Skip:                 true,
			Name:                 "TestIngressEnabledWithKeycloakCustomContextPathSts",
			HelmOptionsExtraArgs: map[string][]string{"install": {"--debug"}},
			CaseTemplates: &testhelpers.CaseTemplate{
				Templates: nil,
			},
			RenderTemplateExtraArgs: []string{"--show-only", "charts/identityKeycloak/templates/statefulset.yaml"},
			Values: map[string]string{
				"global.ingress.enabled":               "true",
				"global.identity.keycloak.contextPath": "/custom",
				"identityKeycloak.enabled":             "true",
				"identityKeycloak.httpRelativePath":    "/custom",
				"identity.contextPath":                 "/identity",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var statefulSet appsv1.StatefulSet
				helm.UnmarshalK8SYaml(t, output, &statefulSet)

				// then
				env := statefulSet.Spec.Template.Spec.Containers[0].Env
				require.Contains(t, env,
					corev1.EnvVar{
						Name:  "KC_HTTP_RELATIVE_PATH",
						Value: "/custom",
					})
			},
		},
		{
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
				// then
				// TODO: Instead of using plain text search, unmarshal the output in an ingress struct and assert the values.
				require.NotContains(t, output, "keycloak")
				require.NotContains(t, output, "path: /auth")
				require.NotContains(t, output, "number: 8443")
			},
		},
		{
			Skip:                 true,
			Name:                 "TestIngressWithContextPath",
			HelmOptionsExtraArgs: map[string][]string{"install": {"--debug"}},
			Values: map[string]string{
				"global.ingress.enabled":              "true",
				"identity.contextPath":                "/identity",
				"optimize.contextPath":                "/optimize",
				"webModeler.enabled":                  "true",
				"webModeler.restapi.mail.fromAddress": "example@example.com",
				"webModeler.contextPath":              "/modeler",
				"orchestration.contextPath":                    "/orchestration",
			},
			Verifier: func(t *testing.T, output string, err error) {
				// then
				s.Require().Contains(output, "kind: Ingress")
				s.Require().Contains(output, "path: /auth")
				s.Require().Contains(output, "path: /identity")
				s.Require().Contains(output, "path: /optimize")
				s.Require().Contains(output, "path: /modeler")
				s.Require().Contains(output, "path: /modeler-ws")
				s.Require().Contains(output, "path: /orchestration")
			},
		},
		{
			Name:                 "TestIngressComponentWithNoContextPath",
			HelmOptionsExtraArgs: map[string][]string{"install": {"--debug"}},
			Values: map[string]string{
				"global.ingress.enabled":              "true",
				"identity.contextPath":                "",
				"optimize.contextPath":                "",
				"webModeler.enabled":                  "true",
				"webModeler.restapi.mail.fromAddress": "example@example.com",
				"webModeler.contextPath":              "",
				"orchestration.contextPath":                    "",
			},
			Verifier: func(t *testing.T, output string, err error) {
				// then
				s.Require().NotContains(output, "name: camunda-platform-test-identity")
				s.Require().NotContains(output, "name: camunda-platform-test-optimize")
				s.Require().NotContains(output, "name: camunda-platform-test-web-modeler-webapp")
				s.Require().NotContains(output, "name: camunda-platform-test-web-modeler-websockets")
				s.Require().NotContains(output, "name: camunda-platform-test-zeebe")
			},
		},
		{
			Name:                 "TestIngressComponentDisabled",
			HelmOptionsExtraArgs: map[string][]string{"install": {"--debug"}},
			Values: map[string]string{
				"global.ingress.enabled": "true",
				"optimize.enabled":       "false",
				"webModeler.enabled":     "false",
				"orchestration.enabled":           "false",
			},
			Verifier: func(t *testing.T, output string, err error) {
				// then
				s.Require().NotContains(output, "name: camunda-platform-test-identity")
				s.Require().NotContains(output, "name: camunda-platform-test-optimize")
				s.Require().NotContains(output, "name: camunda-platform-test-web-modeler-webapp")
				s.Require().NotContains(output, "name: camunda-platform-test-web-modeler-websockets")
				s.Require().NotContains(output, "name: camunda-platform-test-zeebe")
			},
		},
		{
			Name:                 "TestIngressExternal",
			HelmOptionsExtraArgs: map[string][]string{"install": {"--debug"}},
			Values: map[string]string{
				"global.ingress.enabled":  "true",
				"global.ingress.external": "true",
			},
			Verifier: func(t *testing.T, output string, err error) {
				// then
				require.NotContains(t, output, "kind: Ingress")
			},
		},
		{
			Name:                 "TestIngressWithGlobalLabels",
			HelmOptionsExtraArgs: map[string][]string{"install": {"--debug"}},
			Values: map[string]string{
				"global.ingress.enabled":                        "true",
				"global.ingress.labels.test-label":              "test-value",
				"global.ingress.labels.external-dns":            "enabled",
				"global.ingress.labels.nginx\\.ingress\\.class": "public",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var ingress netv1.Ingress
				helm.UnmarshalK8SYaml(t, output, &ingress)

				// then
				s.Require().Equal("test-value", ingress.Labels["test-label"])
				s.Require().Equal("enabled", ingress.Labels["external-dns"])
				s.Require().Equal("public", ingress.Labels["nginx.ingress.class"])
			},
		},
		{
			Name:                 "TestIngressWithoutLabels",
			HelmOptionsExtraArgs: map[string][]string{"install": {"--debug"}},
			Values: map[string]string{
				"global.ingress.enabled": "true",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var ingress netv1.Ingress
				helm.UnmarshalK8SYaml(t, output, &ingress)

				// then - should only have default chart labels, not custom labels
				s.Require().NotContains(ingress.Labels, "test-label")
				s.Require().NotContains(ingress.Labels, "external-dns")
				// But should still have standard chart labels
				s.Require().Contains(ingress.Labels, "app")
				s.Require().Contains(ingress.Labels, "app.kubernetes.io/name")
			},
		},
		{
			Name:                 "TestHttpIngressLabelMergeOverwrite",
			HelmOptionsExtraArgs: map[string][]string{"install": {"--debug"}},
			Values: map[string]string{
				"global.ingress.enabled":              "true",
				"global.commonLabels.app":             "common-override",
				"global.commonLabels.environment":     "common-env",
				"global.ingress.labels.app":           "ingress-override",
				"global.ingress.labels.team":          "ingress-team",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var ingress netv1.Ingress
				helm.UnmarshalK8SYaml(t, output, &ingress)

				// then - ingress labels should override common labels for same keys
				s.Require().Equal("ingress-override", ingress.Labels["app"], "ingress labels should override common labels for same key")
				// and common labels should be present when no ingress label conflicts
				s.Require().Equal("common-env", ingress.Labels["environment"], "common labels should be present when not overridden")
				// and ingress-specific labels should be present
				s.Require().Equal("ingress-team", ingress.Labels["team"], "ingress labels should be present")
				// standard chart labels should still be there for non-conflicting keys
				s.Require().Contains(ingress.Labels, "app.kubernetes.io/name")
			},
		},
	}

	testhelpers.RunTestCasesE(s.T(), s.chartPath, s.release, s.namespace, s.templates, testCases)
}

type GrpcIngressTemplateTest struct {
	suite.Suite
	chartPath string
	release   string
	namespace string
	templates []string
	extraArgs []string
}

func TestGrpcIngressTemplate(t *testing.T) {
	t.Parallel()

	chartPath, err := filepath.Abs("../../../")
	require.NoError(t, err)

	suite.Run(t, &GrpcIngressTemplateTest{
		chartPath: chartPath,
		release:   "camunda-platform-test",
		namespace: "camunda-platform-" + strings.ToLower(random.UniqueId()),
		templates: []string{"templates/common/ingress-grpc.yaml"},
	})
}

func (s *GrpcIngressTemplateTest) TestDifferentValuesInputs() {
	testCases := []testhelpers.TestCase{
		{
			Name:                 "TestGrpcIngressWithLabels",
			HelmOptionsExtraArgs: map[string][]string{"install": {"--debug"}},
			Values: map[string]string{
				"orchestration.enabled":                           "true",
				"orchestration.ingress.grpc.enabled":              "true",
				"orchestration.ingress.grpc.labels.test-label":    "grpc-test-value",
				"orchestration.ingress.grpc.labels.external-dns":  "grpc-enabled",
				"orchestration.ingress.grpc.labels.grpc-service":  "zeebe-gateway",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var ingress netv1.Ingress
				helm.UnmarshalK8SYaml(t, output, &ingress)

				// then
				s.Require().Equal("grpc-test-value", ingress.Labels["test-label"])
				s.Require().Equal("grpc-enabled", ingress.Labels["external-dns"])
				s.Require().Equal("zeebe-gateway", ingress.Labels["grpc-service"])
			},
		},
		{
			Name:                 "TestGrpcIngressWithoutLabels",
			HelmOptionsExtraArgs: map[string][]string{"install": {"--debug"}},
			Values: map[string]string{
				"orchestration.enabled":              "true",
				"orchestration.ingress.grpc.enabled": "true",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var ingress netv1.Ingress
				helm.UnmarshalK8SYaml(t, output, &ingress)

				// then - should only have default chart labels, not custom labels
				s.Require().NotContains(ingress.Labels, "test-label")
				s.Require().NotContains(ingress.Labels, "external-dns")
				// But should still have standard chart labels
				s.Require().Contains(ingress.Labels, "app")
				s.Require().Contains(ingress.Labels, "app.kubernetes.io/name")
			},
		},
		{
			Name:                 "TestGrpcIngressDisabled",
			HelmOptionsExtraArgs: map[string][]string{"install": {"--debug"}},
			Values: map[string]string{
				"orchestration.enabled":                           "true",
				"orchestration.ingress.grpc.enabled":              "false",
				"orchestration.ingress.grpc.labels.test-label":    "should-not-appear",
			},
			Verifier: func(t *testing.T, output string, err error) {
				// then - no ingress should be rendered
				require.NotContains(t, output, "kind: Ingress")
			},
		},
		{
			Name:                 "TestGrpcIngressExternal",
			HelmOptionsExtraArgs: map[string][]string{"install": {"--debug"}},
			Values: map[string]string{
				"orchestration.enabled":                           "true",
				"orchestration.ingress.grpc.enabled":              "true",
				"orchestration.ingress.grpc.external":             "true",
				"orchestration.ingress.grpc.labels.test-label":    "should-not-appear",
			},
			Verifier: func(t *testing.T, output string, err error) {
				// then - no ingress should be rendered when external is true
				require.NotContains(t, output, "kind: Ingress")
			},
		},
		{
			Name:                 "TestGrpcIngressLabelMergeOverwrite",
			HelmOptionsExtraArgs: map[string][]string{"install": {"--debug"}},
			Values: map[string]string{
				"orchestration.enabled":                           "true",
				"orchestration.ingress.grpc.enabled":              "true",
				"global.commonLabels.app":                         "common-grpc-override",
				"global.commonLabels.environment":                 "common-grpc-env",
				"orchestration.ingress.grpc.labels.app":           "grpc-specific-override",
				"orchestration.ingress.grpc.labels.protocol":      "grpc",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var ingress netv1.Ingress
				helm.UnmarshalK8SYaml(t, output, &ingress)

				// then - grpc-specific labels should override common labels for same keys
				s.Require().Equal("grpc-specific-override", ingress.Labels["app"], "grpc labels should override common labels for same key")
				// and common labels should be present when no grpc label conflicts
				s.Require().Equal("common-grpc-env", ingress.Labels["environment"], "common labels should be present when not overridden")
				// and grpc-specific labels should be present
				s.Require().Equal("grpc", ingress.Labels["protocol"], "grpc-specific labels should be present")
				// standard chart labels should still be there for non-conflicting keys
				s.Require().Contains(ingress.Labels, "app.kubernetes.io/name")
			},
		},
	}

	testhelpers.RunTestCasesE(s.T(), s.chartPath, s.release, s.namespace, s.templates, testCases)
}
