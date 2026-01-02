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
