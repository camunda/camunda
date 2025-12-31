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

package gateway

import (
	"camunda-platform/test/unit/camunda"
	"camunda-platform/test/unit/testhelpers"
	"camunda-platform/test/unit/utils"
	"path/filepath"
	"strings"
	"testing"

	corev1 "k8s.io/api/core/v1"

	"github.com/gruntwork-io/terratest/modules/helm"
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

func TestConfigmapTemplate(t *testing.T) {
	t.Parallel()

	chartPath, err := filepath.Abs("../../../")
	require.NoError(t, err)

	suite.Run(t, &ConfigmapTemplateTest{
		chartPath: chartPath,
		release:   "camunda-platform-test",
		namespace: "camunda-platform-" + strings.ToLower(random.UniqueId()),
		templates: []string{"templates/zeebe-gateway/configmap.yaml"},
	})
}

func TestGoldenConfigmapWithLog4j2(t *testing.T) {
	t.Parallel()

	chartPath, err := filepath.Abs("../../../")
	require.NoError(t, err)

	suite.Run(t, &utils.TemplateGoldenTest{
		ChartPath:      chartPath,
		Release:        "camunda-platform-test",
		Namespace:      "camunda-platform-" + strings.ToLower(random.UniqueId()),
		GoldenFileName: "configmap-log4j2",
		Templates:      []string{"templates/zeebe-gateway/configmap.yaml"},
		SetValues:      map[string]string{"zeebeGateway.log4j2": "<xml>\n</xml>"},
	})
}

func (s *ConfigmapTemplateTest) TestDifferentValuesInputs() {
	testCases := []testhelpers.TestCase{
		{
			Name: "TestZeebeMultiTenancyEnabled",
			Values: map[string]string{
				"global.multitenancy.enabled": "true",
				"identityPostgresql.enabled":  "true",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var configmap corev1.ConfigMap
				var configmapApplication camunda.ZeebeApplicationYAML
				helm.UnmarshalK8SYaml(s.T(), output, &configmap)
				helm.UnmarshalK8SYaml(s.T(), configmap.Data["application.yaml"], &configmapApplication)

				// then
				s.Require().Equal(true, configmapApplication.Zeebe.Gateway.MultiTenancy.Enabled)
				s.Require().Equal(true, configmapApplication.Zeebe.Broker.Gateway.MultiTenancy.Enabled)
			},
		}, {
			Name: "TestZeebeIdentityAuthenticationEnabled",
			Values: map[string]string{
				"global.identity.auth.enabled": "true",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var configmap corev1.ConfigMap
				var configmapApplication camunda.ZeebeApplicationYAML
				helm.UnmarshalK8SYaml(s.T(), output, &configmap)
				helm.UnmarshalK8SYaml(s.T(), configmap.Data["application.yaml"], &configmapApplication)

				// then
				s.Require().Equal("identity", configmapApplication.Zeebe.Gateway.Security.Authentication.Mode)
				s.Require().Equal("identity-auth", configmapApplication.Spring.Profiles.Active)
				s.Require().Equal("zeebe-api", configmapApplication.Camunda.Identity.Audience)
				s.Require().Equal("http://camunda-platform-test-keycloak:80/auth/realms/camunda-platform", configmapApplication.Camunda.Identity.IssuerBackendUrl)
			},
		},
	}

	testhelpers.RunTestCasesE(s.T(), s.chartPath, s.release, s.namespace, s.templates, testCases)
}
