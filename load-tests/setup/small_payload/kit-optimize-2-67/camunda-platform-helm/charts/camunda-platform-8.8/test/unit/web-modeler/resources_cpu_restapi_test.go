// Copyright 2025 Camunda Services GmbH
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

	"github.com/gruntwork-io/terratest/modules/helm"
	"github.com/gruntwork-io/terratest/modules/k8s"
	"github.com/gruntwork-io/terratest/modules/random"
	"github.com/stretchr/testify/require"
	"github.com/stretchr/testify/suite"
	appsv1 "k8s.io/api/apps/v1"
	"k8s.io/apimachinery/pkg/api/resource"
)

type RestapiResourcesCPUTemplateTest struct {
	suite.Suite
	chartPath string
	release   string
	namespace string
	templates []string
}

func TestRestapiResourcesCPUTemplate(t *testing.T) {
	t.Parallel()

	chartPath, err := filepath.Abs("../../../")
	require.NoError(t, err)

	suite.Run(t, &RestapiResourcesCPUTemplateTest{
		chartPath: chartPath,
		release:   "camunda-platform-test",
		namespace: "camunda-platform-" + strings.ToLower(random.UniqueId()),
		templates: []string{"templates/web-modeler/deployment-restapi.yaml"},
	})
}

func (s *RestapiResourcesCPUTemplateTest) TestCPUResourcesAsString() {
	// given
	options := &helm.Options{
		SetValues: map[string]string{
			"webModeler.enabled":                       "true",
			"identity.enabled":                         "true",
			"webModeler.restapi.mail.fromAddress":      "test@example.com",
			"webModeler.restapi.resources.requests.cpu": "300m",
			"webModeler.restapi.resources.limits.cpu":   "1.5",
			"webModeler.restapi.resources.requests.memory": "256Mi",
			"webModeler.restapi.resources.limits.memory":   "512Mi",
		},
		KubectlOptions: k8s.NewKubectlOptions("", "", s.namespace),
	}

	// when
	output := helm.RenderTemplate(s.T(), options, s.chartPath, s.release, s.templates)
	var deployment appsv1.Deployment
	helm.UnmarshalK8SYaml(s.T(), output, &deployment)

	// then
	container := deployment.Spec.Template.Spec.Containers[0]
	
	cpuRequest := container.Resources.Requests.Cpu()
	cpuLimit := container.Resources.Limits.Cpu()
	
	require.NotNil(s.T(), cpuRequest, "CPU request should be set")
	require.NotNil(s.T(), cpuLimit, "CPU limit should be set")
	
	expectedRequest := resource.MustParse("300m")
	expectedLimit := resource.MustParse("1.5")
	
	require.True(s.T(), cpuRequest.Equal(expectedRequest), 
		"CPU request should be 300m, got %s", cpuRequest.String())
	require.True(s.T(), cpuLimit.Equal(expectedLimit), 
		"CPU limit should be 1.5, got %s", cpuLimit.String())
}

func (s *RestapiResourcesCPUTemplateTest) TestCPUResourcesBackwardCompatibility() {
	// given
	options := &helm.Options{
		SetValues: map[string]string{
			"webModeler.enabled":                      "true",
			"identity.enabled":                        "true",
			"webModeler.restapi.mail.fromAddress":     "test@example.com",
			"webModeler.restapi.resources.requests.cpu":    "500m",
			"webModeler.restapi.resources.limits.cpu":      "1000m",
			"webModeler.restapi.resources.requests.memory": "1Gi",
			"webModeler.restapi.resources.limits.memory":   "2Gi",
		},
		KubectlOptions: k8s.NewKubectlOptions("", "", s.namespace),
	}

	// when
	output := helm.RenderTemplate(s.T(), options, s.chartPath, s.release, s.templates)
	var deployment appsv1.Deployment
	helm.UnmarshalK8SYaml(s.T(), output, &deployment)

	// then
	container := deployment.Spec.Template.Spec.Containers[0]
	
	cpuRequest := container.Resources.Requests.Cpu()
	cpuLimit := container.Resources.Limits.Cpu()
	
	require.NotNil(s.T(), cpuRequest, "CPU request should be set")
	require.NotNil(s.T(), cpuLimit, "CPU limit should be set")
	
	expectedRequest := resource.MustParse("500m")
	expectedLimit := resource.MustParse("1000m")
	
	require.True(s.T(), cpuRequest.Equal(expectedRequest), 
		"CPU request should be 500m, got %s", cpuRequest.String())
	require.True(s.T(), cpuLimit.Equal(expectedLimit), 
		"CPU limit should be 1000m, got %s", cpuLimit.String())
}
