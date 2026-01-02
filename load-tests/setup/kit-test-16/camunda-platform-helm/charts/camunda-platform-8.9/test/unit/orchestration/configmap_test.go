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

package orchestration

import (
	camunda "camunda-platform/test/unit/common"
	"camunda-platform/test/unit/testhelpers"
	"camunda-platform/test/unit/utils"
	"path/filepath"
	"strings"
	"testing"

	"github.com/gruntwork-io/terratest/modules/helm"
	corev1 "k8s.io/api/core/v1"

	"github.com/gruntwork-io/terratest/modules/random"
	"github.com/stretchr/testify/require"
	"github.com/stretchr/testify/suite"
)

type ConfigmapLegacyTemplateTest struct {
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

	suite.Run(t, &ConfigmapLegacyTemplateTest{
		chartPath: chartPath,
		release:   "camunda-platform-test",
		namespace: "camunda-platform-" + strings.ToLower(random.UniqueId()),
		templates: []string{"templates/orchestration/configmap-unified.yaml"},
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
		Templates:      []string{"templates/orchestration/configmap-unified.yaml"},
		SetValues:      map[string]string{"orchestration.log4j2": "<xml>\n</xml>"},
	})
}

func TestGoldenConfigmapWithAuthorizationsEnabled(t *testing.T) {
	t.Parallel()

	chartPath, err := filepath.Abs("../../../")
	require.NoError(t, err)

	suite.Run(t, &utils.TemplateGoldenTest{
		ChartPath:      chartPath,
		Release:        "camunda-platform-test",
		Namespace:      "camunda-platform-" + strings.ToLower(random.UniqueId()),
		GoldenFileName: "configmap-authorizations",
		Templates:      []string{"templates/orchestration/configmap-unified.yaml"},
		SetValues:      map[string]string{"global.authorizations.enabled": "true"},
	})
}


func TestGoldenConfigmapWithHistoryRetentionEnabled(t *testing.T) {
	t.Parallel()

	chartPath, err := filepath.Abs("../../../")
	require.NoError(t, err)

	suite.Run(t, &utils.TemplateGoldenTest{
		ChartPath:      chartPath,
		Release:        "camunda-platform-test",
		Namespace:      "camunda-platform-" + strings.ToLower(random.UniqueId()),
		GoldenFileName: "configmap-retention",
		Templates:      []string{"templates/orchestration/configmap-unified.yaml"},
		SetValues:      map[string]string{"orchestration.history.retention.enabled": "true"},
	})
}

func (s *ConfigmapLegacyTemplateTest) TestDifferentValuesInputs() {
	testCases := []testhelpers.TestCase{
		{
			Name:   "TestContainerShouldContainExporterClassPerDefault",
			Values: map[string]string{},
			Verifier: func(t *testing.T, output string, err error) {
				var configmap corev1.ConfigMap
				var configmapApplication camunda.OrchestrationApplicationYAML
				helm.UnmarshalK8SYaml(s.T(), output, &configmap)
				helm.UnmarshalK8SYaml(s.T(), configmap.Data["application.yaml"], &configmapApplication)


				// then
				s.Require().Equal("io.camunda.exporter.CamundaExporter", configmapApplication.Zeebe.Broker.Exporters.CamundaExporter.ClassName)
			},
		},
	}

	testhelpers.RunTestCasesE(s.T(), s.chartPath, s.release, s.namespace, s.templates, testCases)
}
