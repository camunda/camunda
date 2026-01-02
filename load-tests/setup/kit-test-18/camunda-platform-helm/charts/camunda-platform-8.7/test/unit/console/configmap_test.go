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

package console

import (
	"camunda-platform/test/unit/testhelpers"
	"path/filepath"
	"strings"
	"testing"

	"github.com/gruntwork-io/terratest/modules/random"
	"github.com/stretchr/testify/require"
	"github.com/stretchr/testify/suite"
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
		templates: []string{"templates/console/configmap.yaml"},
	})
}

func (s *configMapTemplateTest) TestDifferentValuesInputs() {
	testCases := []testhelpers.TestCase{
        {
            Name: "Container should set correct identity type",
            Values: map[string]string{
                "console.enabled":                       "true",
                "global.identity.auth.type":             "MICROSOFT",
                "global.identity.auth.issuer":           "https://example.com",
                "global.identity.auth.issuerBackendUrl": "https://example.com",
            },
            Expected: map[string]string{
                "configmapApplication.camunda.console.oAuth.type": "MICROSOFT",
            },
        },
    }
		
	testhelpers.RunTestCases(s.T(), s.chartPath, s.release, s.namespace, s.templates, testCases)
}

func (s *configMapTemplateTest) TestContextPathRootDoesNotCreateDoubleSlashes() {
	testCases := []testhelpers.TestCase{
		{
			Name: "ContextPathRootShouldNotCauseDoubleSlashesInURLs",
			Values: map[string]string{
				"console.enabled":                       "true",
				"identity.enabled":                      "true",
				"operate.enabled":                       "true",
				"tasklist.enabled":                      "true",
				"optimize.enabled":                      "true",
				"connectors.enabled":                    "true",
				"global.ingress.enabled":                "true",
				"global.ingress.host":                   "camunda.example.com",
				"operate.contextPath":                   "/",
				"tasklist.contextPath":                  "/",
				"optimize.contextPath":                  "/",
				"connectors.contextPath":                "/",
			},
			Verifier: func(t *testing.T, output string, err error) {
				require.NoError(t, err)
				// Verify that URLs don't contain double slashes (except in http://)
				// Looking for patterns like "://hostname//" which indicate double slashes
				require.NotContains(t, output, ".com//", "URLs should not contain double slashes after hostname")
				require.NotContains(t, output, ":80//", "URLs should not contain double slashes after port")
				require.NotContains(t, output, ":82//", "URLs should not contain double slashes after port")
				require.NotContains(t, output, ":8080//", "URLs should not contain double slashes after port")
				require.NotContains(t, output, ":9600//", "URLs should not contain double slashes after port")
			},
		},
	}

	testhelpers.RunTestCasesE(s.T(), s.chartPath, s.release, s.namespace, s.templates, testCases)
}
