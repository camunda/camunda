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

type WebsocketsResourcesCPUTemplateTest struct {
	suite.Suite
	chartPath string
	release   string
	namespace string
	templates []string
}

func TestWebsocketsResourcesCPUTemplate(t *testing.T) {
	t.Parallel()

	chartPath, err := filepath.Abs("../../../")
	require.NoError(t, err)

	suite.Run(t, &WebsocketsResourcesCPUTemplateTest{
		chartPath: chartPath,
		release:   "camunda-platform-test",
		namespace: "camunda-platform-" + strings.ToLower(random.UniqueId()),
		templates: []string{"templates/web-modeler/deployment-websockets.yaml"},
	})
}

func (s *WebsocketsResourcesCPUTemplateTest) TestCPUResourcesAsString() {
	// given
	options := &helm.Options{
		SetValues: map[string]string{
			"webModeler.enabled":                          "true",
			"identity.enabled":                            "true",
			"webModeler.restapi.mail.fromAddress":         "test@example.com",
			"webModeler.websockets.resources.requests.cpu":    "50m",
			"webModeler.websockets.resources.limits.cpu":      "0.3",
			"webModeler.websockets.resources.requests.memory": "32Mi",
			"webModeler.websockets.resources.limits.memory":   "64Mi",
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
	
	expectedRequest := resource.MustParse("50m")
	expectedLimit := resource.MustParse("0.3")
	
	require.True(s.T(), cpuRequest.Equal(expectedRequest), 
		"CPU request should be 50m, got %s", cpuRequest.String())
	require.True(s.T(), cpuLimit.Equal(expectedLimit), 
		"CPU limit should be 0.3, got %s", cpuLimit.String())
}

func (s *WebsocketsResourcesCPUTemplateTest) TestCPUResourcesBackwardCompatibility() {
	// given
	options := &helm.Options{
		SetValues: map[string]string{
			"webModeler.enabled":                          "true",
			"identity.enabled":                            "true",
			"webModeler.restapi.mail.fromAddress":         "test@example.com",
			"webModeler.websockets.resources.requests.cpu":    "100m",
			"webModeler.websockets.resources.limits.cpu":      "200m",
			"webModeler.websockets.resources.requests.memory": "64Mi",
			"webModeler.websockets.resources.limits.memory":   "128Mi",
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
	
	expectedRequest := resource.MustParse("100m")
	expectedLimit := resource.MustParse("200m")
	
	require.True(s.T(), cpuRequest.Equal(expectedRequest), 
		"CPU request should be 100m, got %s", cpuRequest.String())
	require.True(s.T(), cpuLimit.Equal(expectedLimit), 
		"CPU limit should be 200m, got %s", cpuLimit.String())
}
