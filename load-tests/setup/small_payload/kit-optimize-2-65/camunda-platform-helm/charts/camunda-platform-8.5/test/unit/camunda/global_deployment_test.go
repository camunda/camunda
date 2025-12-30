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

	"github.com/gruntwork-io/terratest/modules/random"
	"github.com/stretchr/testify/require"
	"github.com/stretchr/testify/suite"
)

type DeploymentTemplateTest struct {
	suite.Suite
	chartPath string
	release   string
	namespace string
	templates []string
}

func TestDeploymentTemplate(t *testing.T) {
	t.Parallel()

	chartPath, err := filepath.Abs("../../../")
	require.NoError(t, err)

	suite.Run(t, &DeploymentTemplateTest{
		chartPath: chartPath,
		release:   "camunda-platform-test",
		namespace: "camunda-platform-" + strings.ToLower(random.UniqueId()),
		templates: []string{},
	})
}

func (s *DeploymentTemplateTest) TestDifferentValuesInputs() {
	testCases := []testhelpers.TestCase{
		{
			Name:                 "TestContainerShouldNotRenderOptimizeIfDisabled",
			HelmOptionsExtraArgs: map[string][]string{"install": {"--debug"}},
			Values:               map[string]string{"optimize.enabled": "false"},
			Verifier: func(t *testing.T, output string, err error) {
				require.NotContains(t, output, "charts/optimize")
			},
		}, {
			Name:                 "TestContainerShouldNotRenderOperateIfDisabled",
			HelmOptionsExtraArgs: map[string][]string{"install": {"--debug"}},
			Values:               map[string]string{"operate.enabled": "false"},
			Verifier: func(t *testing.T, output string, err error) {
				require.NotContains(t, output, "charts/operate")
			},
		}, {
			Name:                 "TestContainerShouldNotRenderTasklistIfDisabled",
			HelmOptionsExtraArgs: map[string][]string{"install": {"--debug"}},
			Values:               map[string]string{"tasklist.enabled": "false"},
			Verifier: func(t *testing.T, output string, err error) {
				require.NotContains(t, output, "charts/tasklist")
			},
		}, {
			Name:                 "TestContainerShouldNotRenderIdentityIfDisabled",
			HelmOptionsExtraArgs: map[string][]string{"install": {"--debug"}},
			Values: map[string]string{
				"optimize.enabled":             "true",
				"identity.enabled":             "false",
				"global.identity.auth.enabled": "false",
			},
			Verifier: func(t *testing.T, output string, err error) {
				require.NotContains(t, output, "charts/identity")
			},
		}, {
			Name:                 "ContainerShouldNotRenderWebModelerIfDisabled",
			HelmOptionsExtraArgs: map[string][]string{"install": {"--debug"}},
			Values: map[string]string{
				"webModeler.enabled": "false",
			},
			Verifier: func(t *testing.T, output string, err error) {
				require.NotContains(t, output, "templates/web-modeler")
			},
		}, {
			Name: "TestContainerSetImageNameGlobal",
			Values: map[string]string{
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
			Verifier: func(t *testing.T, output string, err error) {
				require.Contains(t, output, "image: global.custom.registry.io/camunda/connectors-bundle:8.x.x")
				require.Contains(t, output, "image: global.custom.registry.io/camunda/identity:8.x.x")
				require.Contains(t, output, "image: global.custom.registry.io/camunda/operate:8.x.x")
				require.Contains(t, output, "image: global.custom.registry.io/camunda/optimize:8.x.x")
				require.Contains(t, output, "image: global.custom.registry.io/camunda/tasklist:8.x.x")
				require.Contains(t, output, "image: global.custom.registry.io/camunda/zeebe:8.x.x")
			},
		}, {
			Name: "TestComponentDigestOverridesTag",
			Values: map[string]string{
				// leave tags empty to force each component to use its own digest
				"connectors.image.tag":   "",
				"identity.image.tag":     "",
				"operate.image.tag":      "",
				"optimize.image.tag":     "",
				"tasklist.image.tag":     "",
				"zeebe.image.tag":        "",
				"zeebeGateway.image.tag": "",
				// set component‐level digests
				"connectors.image.digest":   "sha256:aaa111",
				"identity.image.digest":     "sha256:bbb222",
				"operate.image.digest":      "sha256:ccc333",
				"optimize.image.digest":     "sha256:ddd444",
				"tasklist.image.digest":     "sha256:eee555",
				"zeebe.image.digest":        "sha256:fff666",
				"zeebeGateway.image.digest": "sha256:fff666",
			},
			HelmOptionsExtraArgs: map[string][]string{
				"install":  {"--debug"},
				"template": {"--debug"},
			},
			Verifier: func(t *testing.T, output string, err error) {
				require.NoError(t, err)
				require.Contains(t, output, "image: camunda/connectors-bundle@sha256:aaa111")
				require.Contains(t, output, "image: camunda/identity@sha256:bbb222")
				require.Contains(t, output, "image: camunda/operate@sha256:ccc333")
				require.Contains(t, output, "image: camunda/optimize@sha256:ddd444")
				require.Contains(t, output, "image: camunda/tasklist@sha256:eee555")
				require.Contains(t, output, "image: camunda/zeebe@sha256:fff666")
			},
		},
		{
			Name: "TestDigestFallsBackToTagWhenNoDigest",
			Values: map[string]string{
				"connectors.image.tag": "8.x.x",
				"identity.image.tag":   "8.x.x",
			},
			Verifier: func(t *testing.T, output string, err error) {
				require.NoError(t, err)
				require.Contains(t, output, "image: camunda/connectors-bundle:8.x.x")
				require.Contains(t, output, "image: camunda/identity:8.x.x")
			},
		},
	}

	testhelpers.RunTestCasesE(s.T(), s.chartPath, s.release, s.namespace, s.templates, testCases)
}
