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

package optimize

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

type ResourcesCPUTemplateTest struct {
	suite.Suite
	chartPath string
	release   string
	namespace string
	templates []string
}

func TestResourcesCPUTemplate(t *testing.T) {
	t.Parallel()

	chartPath, err := filepath.Abs("../../../")
	require.NoError(t, err)

	suite.Run(t, &ResourcesCPUTemplateTest{
		chartPath: chartPath,
		release:   "camunda-platform-test",
		namespace: "camunda-platform-" + strings.ToLower(random.UniqueId()),
		templates: []string{"templates/optimize/deployment.yaml"},
	})
}

// Test that CPU resources can be set as string (e.g., "200m", "1", "1.5")
func (s *ResourcesCPUTemplateTest) TestCPUResourcesAsString() {
	// given
	options := &helm.Options{
		SetValues: map[string]string{
			"optimize.enabled":                   "true",
			"identity.enabled":                   "true",
			"optimize.resources.requests.cpu":    "500m",
			"optimize.resources.limits.cpu":      "1.5",
			"optimize.resources.requests.memory": "512Mi",
			"optimize.resources.limits.memory":   "1Gi",
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
	
	// Verify the values match what we set
	expectedRequest := resource.MustParse("500m")
	expectedLimit := resource.MustParse("1.5")
	
	require.True(s.T(), cpuRequest.Equal(expectedRequest), 
		"CPU request should be 500m, got %s", cpuRequest.String())
	require.True(s.T(), cpuLimit.Equal(expectedLimit), 
		"CPU limit should be 1.5, got %s", cpuLimit.String())
}

// Test backward compatibility with millicore notation
func (s *ResourcesCPUTemplateTest) TestCPUResourcesBackwardCompatibility() {
	// given
	options := &helm.Options{
		SetValues: map[string]string{
			"optimize.enabled":                   "true",
			"identity.enabled":                   "true",
			"optimize.resources.requests.cpu":    "600m",
			"optimize.resources.limits.cpu":      "2000m",
			"optimize.resources.requests.memory": "1Gi",
			"optimize.resources.limits.memory":   "2Gi",
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
	
	// Verify the values match what we set
	expectedRequest := resource.MustParse("600m")
	expectedLimit := resource.MustParse("2000m")
	
	require.True(s.T(), cpuRequest.Equal(expectedRequest), 
		"CPU request should be 600m, got %s", cpuRequest.String())
	require.True(s.T(), cpuLimit.Equal(expectedLimit), 
		"CPU limit should be 2000m, got %s", cpuLimit.String())
}
