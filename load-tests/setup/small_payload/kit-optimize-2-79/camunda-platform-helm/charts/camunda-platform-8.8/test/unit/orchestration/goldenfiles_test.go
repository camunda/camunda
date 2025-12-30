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

package orchestration

import (
	"camunda-platform/test/unit/utils"
	"path/filepath"
	"strings"
	"testing"

	"github.com/gruntwork-io/terratest/modules/random"
	"github.com/stretchr/testify/require"
	"github.com/stretchr/testify/suite"
)

func TestGoldenDefaultsTemplateOrchestration(t *testing.T) {
	t.Parallel()

	chartPath, err := filepath.Abs("../../../")
	require.NoError(t, err)
	templateNames := []string{
		"service",
		"service-headless",
		"serviceaccount",
		"statefulset",
		"configmap-unified",
	}

	for _, name := range templateNames {
		suite.Run(t, &utils.TemplateGoldenTest{
			ChartPath:      chartPath,
			Release:        "camunda-platform-test",
			Namespace:      "camunda-platform-" + strings.ToLower(random.UniqueId()),
			GoldenFileName: name,
			Templates:      []string{"templates/orchestration/" + name + ".yaml"},
			IgnoredLines: []string{
				`\s+checksum/.+?:\s+.*`, // ignore configmap checksum.
			},
		})
	}
}

func TestGoldenDefaultsTemplateOrchestrationMigrationData(t *testing.T) {
	t.Parallel()

	chartPath, err := filepath.Abs("../../../")
	require.NoError(t, err)
	templateNames := []string{
		"migration-data-configmap-env-vars",
		"migration-data-job",
		"importer-configmap-env-vars",
		"importer-deployment",
	}

	for _, name := range templateNames {
		suite.Run(t, &utils.TemplateGoldenTest{
			ChartPath:      chartPath,
			Release:        "camunda-platform-test",
			Namespace:      "camunda-platform-" + strings.ToLower(random.UniqueId()),
			GoldenFileName: name,
			Templates:      []string{"templates/orchestration/" + name + ".yaml"},
			SetValues: map[string]string{
				"orchestration.migration.data.enabled": "true",
			},
			IgnoredLines: []string{
				`\s+checksum/.+?:\s+.*`, // ignore configmap checksum.
			},
		})
	}
}

func TestGoldenDefaultsTemplateOrchestrationMigrationIdentity(t *testing.T) {
	t.Parallel()

	chartPath, err := filepath.Abs("../../../")
	require.NoError(t, err)
	templateNames := []string{
		"migration-identity-configmap",
		"migration-identity-job",
	}

	for _, name := range templateNames {
		suite.Run(t, &utils.TemplateGoldenTest{
			ChartPath:      chartPath,
			Release:        "camunda-platform-test",
			Namespace:      "camunda-platform-" + strings.ToLower(random.UniqueId()),
			GoldenFileName: name,
			Templates:      []string{"templates/orchestration/" + name + ".yaml"},
			SetValues: map[string]string{
				"orchestration.migration.identity.enabled": "true",
				"orchestration.migration.identity.secret.inlineSecret": "very-secret-thus-plaintext",
			},
			IgnoredLines: []string{
				`\s+checksum/.+?:\s+.*`, // ignore configmap checksum.
			},
		})
	}
}
