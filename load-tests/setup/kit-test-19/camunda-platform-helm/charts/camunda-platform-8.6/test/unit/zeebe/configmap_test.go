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

package zeebe

import (
	"camunda-platform/test/unit/camunda"
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
		templates: []string{"templates/zeebe/configmap.yaml"},
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
		Templates:      []string{"templates/zeebe/configmap.yaml"},
		SetValues:      map[string]string{"zeebe.log4j2": "<xml>\n</xml>"},
	})
}

func TestGoldenConfigmapWithMultiregionNormal(t *testing.T) {
	t.Parallel()

	chartPath, err := filepath.Abs("../../../")
	require.NoError(t, err)

	suite.Run(t, &utils.TemplateGoldenTest{
		ChartPath:      chartPath,
		Release:        "camunda-platform-test",
		Namespace:      "camunda-platform-" + strings.ToLower(random.UniqueId()),
		GoldenFileName: "configmap",
		Templates:      []string{"templates/zeebe/configmap.yaml"},
		SetValues: map[string]string{
			"global.multiregion.regions":          "1",
			"global.multiregion.regionId":         "0",
			"global.multiregion.installationType": "normal",
		},
	})
}

func TestGoldenConfigmapWithMultiregionFailOver(t *testing.T) {
	t.Parallel()

	chartPath, err := filepath.Abs("../../../")
	require.NoError(t, err)

	suite.Run(t, &utils.TemplateGoldenTest{
		ChartPath:      chartPath,
		Release:        "camunda-platform-test",
		Namespace:      "camunda-platform-" + strings.ToLower(random.UniqueId()),
		GoldenFileName: "configmap-multiregion-failOver",
		Templates:      []string{"templates/zeebe/configmap.yaml"},
		SetValues: map[string]string{
			"global.multiregion.regions":          "1",
			"global.multiregion.regionId":         "0",
			"global.multiregion.installationType": "failOver",
		},
	})
}

func TestGoldenConfigmapWithMultiregionFailBack(t *testing.T) {
	t.Parallel()

	chartPath, err := filepath.Abs("../../../")
	require.NoError(t, err)

	suite.Run(t, &utils.TemplateGoldenTest{
		ChartPath:      chartPath,
		Release:        "camunda-platform-test",
		Namespace:      "camunda-platform-" + strings.ToLower(random.UniqueId()),
		GoldenFileName: "configmap-multiregion-failBack",
		Templates:      []string{"templates/zeebe/configmap.yaml"},
		SetValues: map[string]string{
			"global.multiregion.regions":          "1",
			"global.multiregion.regionId":         "0",
			"global.multiregion.installationType": "failBack",
		},
	})
}

func (s *ConfigmapTemplateTest) TestDifferentValuesInputs() {
	testCases := []testhelpers.TestCase{
		{
			Name:   "TestContainerShouldContainExporterClassPerDefault",
			Values: map[string]string{},
			Verifier: func(t *testing.T, output string, err error) {
				var configmap corev1.ConfigMap
				var configmapApplication camunda.ZeebeApplicationYAML
				helm.UnmarshalK8SYaml(s.T(), output, &configmap)
				helm.UnmarshalK8SYaml(s.T(), configmap.Data["application.yaml"], &configmapApplication)

				// then
				s.Require().Equal("io.camunda.zeebe.exporter.ElasticsearchExporter", configmapApplication.Zeebe.Broker.Exporters.Elasticsearch.ClassName)
			},
		},
	}

	testhelpers.RunTestCasesE(s.T(), s.chartPath, s.release, s.namespace, s.templates, testCases)
}
