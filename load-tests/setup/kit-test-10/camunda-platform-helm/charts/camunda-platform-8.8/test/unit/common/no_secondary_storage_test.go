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

type NoSecondaryStorageTemplateTest struct {
	suite.Suite
	chartPath string
	release   string
	namespace string
	templates []string
}

func TestNoSecondaryStorageTemplate(t *testing.T) {
	t.Parallel()

	chartPath, err := filepath.Abs("../../../")
	require.NoError(t, err)

	suite.Run(t, &NoSecondaryStorageTemplateTest{
		chartPath: chartPath,
		release:   "camunda-platform-test",
		namespace: "camunda-platform-" + strings.ToLower(random.UniqueId()),
	})
}

// TODO: Move this test to each component config instead of this file.
func (s *NoSecondaryStorageTemplateTest) TestNoSecondaryStorageGlobalValue() {
	testCases := []testhelpers.TestCase{
		{
			Name:                 "TestGlobalNoSecondaryStorageTogglesAllExpectedValues",
			HelmOptionsExtraArgs: map[string][]string{"install": {"--debug"}},
			Values: map[string]string{
				"global.noSecondaryStorage":                     "true",
				"global.elasticsearch.enabled":                 "false",
				"global.opensearch.enabled":                    "false",
				"elasticsearch.enabled":                        "false",
				"orchestration.security.authentication.method": "oidc",
			},
			Verifier: func(t *testing.T, output string, err error) {
				require.NoError(t, err)
				// Secondary storage type should be none
				require.Contains(t, output, "secondary-storage:\n          autoconfigure-camunda-exporter: true\n          type: \"none\"")
				// Persistent sessions should be disabled
				require.Contains(t, output, "persistentSessionsEnabled: false")
				// Agentic AI and inbound connectors should be disabled
				require.Contains(t, output, "webhook:\n          enabled: false")
				require.Contains(t, output, "polling:\n          enabled: false")
				require.Contains(t, output, "agenticai:\n          enabled: false")
				// Optimize should not be rendered
				require.NotContains(t, output, "templates/optimize")
			},
		},
	}

	testhelpers.RunTestCasesE(s.T(), s.chartPath, s.release, s.namespace, s.templates, testCases)
}
