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
	"camunda-platform/test/unit/utils"
	"path/filepath"
	"testing"

	"github.com/stretchr/testify/require"
	"github.com/stretchr/testify/suite"
)

func TestGoldenDefaultsTemplateConsole(t *testing.T) {
	t.Parallel()

	chartPath, err := filepath.Abs("../../../")
	require.NoError(t, err)
	templateNames := []string{
		"configmap",
		"deployment",
		"service",
		"serviceaccount",
	}

	for _, name := range templateNames {
		suite.Run(t, &utils.TemplateGoldenTest{
			ChartPath:      chartPath,
			Release:        "camunda-platform-test",
			Namespace:      "camunda-platform",
			GoldenFileName: name,
			Templates:      []string{"templates/console/" + name + ".yaml"},
			SetValues: map[string]string{
				"console.enabled":                "true",
				"identity.enabled":               "true",
				"identityKeycloak.enabled":       "true",
				"console.serviceAccount.enabled": "true",
			},
			IgnoredLines: []string{
				`\s+.*-secret:\s+.*`,    // secrets are auto-generated and need to be ignored.
				`\s+checksum/.+?:\s+.*`, // ignore configmap checksum.
				`\s+version:\s+.*`,      // ignore release version in console config.
			},
		})
	}
}
