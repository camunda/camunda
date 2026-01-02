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

package test

import (
	"path/filepath"
	"strings"
	"testing"

	"github.com/gruntwork-io/terratest/modules/helm"
	"github.com/gruntwork-io/terratest/modules/k8s"
	"github.com/gruntwork-io/terratest/modules/random"
	"github.com/stretchr/testify/require"
	"github.com/stretchr/testify/suite"
)

type deploymentTemplateTest struct {
	suite.Suite
	chartPath string
	release   string
	namespace string
	templates []string
}

func TestDeploymentTemplate(t *testing.T) {
	t.Parallel()

	chartPath, err := filepath.Abs("../../")
	require.NoError(t, err)

	suite.Run(t, &deploymentTemplateTest{
		chartPath: chartPath,
		release:   "camunda-platform-test",
		namespace: "camunda-platform-" + strings.ToLower(random.UniqueId()),
	})
}

func (s *deploymentTemplateTest) TestContainerShouldNotRenderOptimizeIfDisabled() {
	// given
	options := &helm.Options{
		SetValues: map[string]string{
			"optimize.enabled": "false",
		},
		KubectlOptions: k8s.NewKubectlOptions("", "", s.namespace),
		ExtraArgs:      map[string][]string{"template": {"--debug"}, "install": {"--debug"}},
	}

	// when
	output := helm.RenderTemplate(s.T(), options, s.chartPath, s.release, s.templates)

	// then
	s.Require().NotContains(output, "charts/optimize")
}

func (s *deploymentTemplateTest) TestContainerShouldNotRenderOperateIfDisabled() {
	// given
	options := &helm.Options{
		SetValues: map[string]string{
			"operate.enabled": "false",
		},
		KubectlOptions: k8s.NewKubectlOptions("", "", s.namespace),
		ExtraArgs:      map[string][]string{"template": {"--debug"}, "install": {"--debug"}},
	}

	// when
	output := helm.RenderTemplate(s.T(), options, s.chartPath, s.release, s.templates)

	// then
	s.Require().NotContains(output, "charts/operate")
}

func (s *deploymentTemplateTest) TestContainerShouldNotRenderTasklistIfDisabled() {
	// given
	options := &helm.Options{
		SetValues: map[string]string{
			"tasklist.enabled": "false",
		},
		KubectlOptions: k8s.NewKubectlOptions("", "", s.namespace),
		ExtraArgs:      map[string][]string{"template": {"--debug"}, "install": {"--debug"}},
	}

	// when
	output := helm.RenderTemplate(s.T(), options, s.chartPath, s.release, s.templates)

	// then
	s.Require().NotContains(output, "charts/tasklist")
}

func (s *deploymentTemplateTest) TestContainerShouldNotRenderIdentityIfDisabled() {
	// given
	options := &helm.Options{
		SetValues: map[string]string{
			"identity.enabled":             "false",
			"global.identity.auth.enabled": "false",
		},
		KubectlOptions: k8s.NewKubectlOptions("", "", s.namespace),
		ExtraArgs:      map[string][]string{"template": {"--debug"}, "install": {"--debug"}},
	}

	// when
	output := helm.RenderTemplate(s.T(), options, s.chartPath, s.release, s.templates)

	// then
	s.Require().NotContains(output, "charts/identity")
}

func (s *deploymentTemplateTest) TestContainerShouldNotRenderWebModelerIfDisabled() {
	// given
	options := &helm.Options{
		SetValues: map[string]string{
			"webModeler.enabled": "false",
		},
		KubectlOptions: k8s.NewKubectlOptions("", "", s.namespace),
		ExtraArgs:      map[string][]string{"template": {"--debug"}, "install": {"--debug"}},
	}

	// when
	output := helm.RenderTemplate(s.T(), options, s.chartPath, s.release, s.templates)

	// then
	s.Require().NotContains(output, "templates/web-modeler")
}

func (s *deploymentTemplateTest) TestContainerSetImageNameGlobal() {
	// given
	options := &helm.Options{
		SetValues: map[string]string{
			"global.image.registry":  "global.custom.registry.io",
			"global.image.tag":       "8.x.x",
			"connectors.image.tag":   "",
			"identity.image.tag":     "",
			"operate.image.tag":      "",
			"optimize.image.tag":     "",
			"tasklist.image.tag":     "",
			"zeebe.image.tag":        "",
			"zeebeGateway.image.tag": "",
		},
		KubectlOptions: k8s.NewKubectlOptions("", "", s.namespace),
	}

	// when
	output := helm.RenderTemplate(s.T(), options, s.chartPath, s.release, s.templates)

	// then
	s.Require().Contains(output, "image: global.custom.registry.io/camunda/connectors-bundle:8.x.x")
	s.Require().Contains(output, "image: global.custom.registry.io/camunda/identity:8.x.x")
	s.Require().Contains(output, "image: global.custom.registry.io/camunda/operate:8.x.x")
	s.Require().Contains(output, "image: global.custom.registry.io/camunda/optimize:8.x.x")
	s.Require().Contains(output, "image: global.custom.registry.io/camunda/tasklist:8.x.x")
	s.Require().Contains(output, "image: global.custom.registry.io/camunda/zeebe:8.x.x")
}
