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

package web_modeler

import (
	"path/filepath"
	"strings"
	"testing"

	"camunda-platform/test/unit/golden"

	"github.com/gruntwork-io/terratest/modules/random"
	"github.com/stretchr/testify/require"
	"github.com/stretchr/testify/suite"
)

func TestGoldenDefaultsTemplate(t *testing.T) {
	t.Parallel()

	chartPath, err := filepath.Abs("../../../")
	require.NoError(t, err)
	templateNames := []string{
		"configmap-shared",
		"deployment-restapi",
		"deployment-webapp",
		"deployment-websockets",
		"secret-shared",
		"service-restapi",
		"service-webapp",
		"service-websockets",
		"serviceaccount",
	}

	for _, name := range templateNames {
		suite.Run(t, &golden.TemplateGoldenTest{
			ChartPath:      chartPath,
			Release:        "camunda-platform-test",
			Namespace:      "camunda-platform-" + strings.ToLower(random.UniqueId()),
			GoldenFileName: name,
			IgnoredLines:   []string{`\s+pusher-app-key:\s+.*`, `\s+pusher-app-secret:\s+.*`}, // secrets are auto-generated and need to be ignored
			Templates:      []string{"templates/web-modeler/" + name + ".yaml"},
			SetValues: map[string]string{
				"webModeler.enabled":                  "true",
				"webModeler.restapi.mail.fromAddress": "example@example.com",
				"postgresql.enabled":                  "true",
			},
		})
	}
}
