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
	"camunda-platform/test/unit/utils"
	"path/filepath"
	"strings"
	"testing"

	"github.com/gruntwork-io/terratest/modules/random"
	"github.com/stretchr/testify/require"
	"github.com/stretchr/testify/suite"
)

func TestGoldenDefaultsTemplateWebModeler(t *testing.T) {
	t.Parallel()

	chartPath, err := filepath.Abs("../../../")
	require.NoError(t, err)
	templateNames := []string{
		"configmap-restapi",
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
		suite.Run(t, &utils.TemplateGoldenTest{
			ChartPath:      chartPath,
			Release:        "camunda-platform-test",
			Namespace:      "camunda-platform-" + strings.ToLower(random.UniqueId()),
			GoldenFileName: name,
			Templates:      []string{"templates/web-modeler/" + name + ".yaml"},
			SetValues: map[string]string{
				"webModeler.enabled":                                             "true",
				"webModeler.restapi.mail.fromAddress":                            "example@example.com",
				"webModelerPostgresql.enabled":                                   "true",
				"webModelerPostgresql.auth.existingSecret":                       "camunda-platform-test-postgresql-web-modeler",
				"connectors.security.authentication.oidc.existingSecret.name":    "foo",
				"orchestration.security.authentication.oidc.existingSecret.name": "foo",
				"global.identity.auth.enabled":                                   "true",
				"identity.enabled":                                               "true",
				"identityKeycloak.enabled":                                       "true",
			},
			IgnoredLines: []string{
				`\s+pusher-app-key:\s+.*`,    // Auto-generated and need to be ignored.
				`\s+pusher-app-secret:\s+.*`, // Auto-generated and need to be ignored.
				`\s+.*-secret:\s+.*`,         // secrets are auto-generated and need to be ignored.
				`\s+checksum/.+?:\s+.*`,      // ignore configmap checksum.
			},
		})
	}
}
