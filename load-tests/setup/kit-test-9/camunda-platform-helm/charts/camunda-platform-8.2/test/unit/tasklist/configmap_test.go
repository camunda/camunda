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

package tasklist

import (
	"path/filepath"
	"strings"
	"testing"

	"github.com/gruntwork-io/terratest/modules/helm"
	"github.com/gruntwork-io/terratest/modules/k8s"
	"github.com/gruntwork-io/terratest/modules/random"
	"github.com/stretchr/testify/require"
	"github.com/stretchr/testify/suite"
	corev1 "k8s.io/api/core/v1"
)

type configMapTemplateTest struct {
	suite.Suite
	chartPath string
	release   string
	namespace string
	templates []string
}

func TestConfigMapTemplate(t *testing.T) {
	t.Parallel()

	chartPath, err := filepath.Abs("../../../")
	require.NoError(t, err)

	suite.Run(t, &configMapTemplateTest{
		chartPath: chartPath,
		release:   "camunda-platform-test",
		namespace: "camunda-platform-" + strings.ToLower(random.UniqueId()),
		templates: []string{"charts/tasklist/templates/configmap.yaml"},
	})
}

func (s *configMapTemplateTest) TestConfigMapElasticsearchURL() {
	// given
	options := &helm.Options{
		SetValues: map[string]string{
			"global.elasticsearch.url": "elasticsearch-master-test",
		},
		KubectlOptions: k8s.NewKubectlOptions("", "", s.namespace),
	}

	// when
	output := helm.RenderTemplate(s.T(), options, s.chartPath, s.release, s.templates)
	var configmap corev1.ConfigMap
	var configmapApplication map[string]interface{}
	helm.UnmarshalK8SYaml(s.T(), output, &configmap)
	helm.UnmarshalK8SYaml(s.T(), configmap.Data["application.yml"], &configmapApplication)

	// TODO: Move Tasklist config to its own struct when we have more tests.
	elasticsearchURL := configmapApplication["camunda.tasklist"].(map[string]interface{})["elasticsearch"].(map[string]interface{})["url"]

	// then
	s.Require().Equal("elasticsearch-master-test", elasticsearchURL)
}
