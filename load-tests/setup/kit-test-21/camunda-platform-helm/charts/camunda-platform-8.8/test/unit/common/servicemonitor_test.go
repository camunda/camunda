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
	"camunda-platform/test/unit/utils"
	"path/filepath"
	"strings"
	"testing"

	"github.com/gruntwork-io/terratest/modules/random"
	"github.com/stretchr/testify/require"
	"github.com/stretchr/testify/suite"
)

func TestGoldenServiceMonitorOptimizeDefaults(t *testing.T) {
	t.Parallel()

	chartPath, err := filepath.Abs("../../../")
	require.NoError(t, err)

	suite.Run(t, &utils.TemplateGoldenTest{
		ChartPath:      chartPath,
		Release:        "camunda-platform-test",
		Namespace:      "camunda-platform-" + strings.ToLower(random.UniqueId()),
		GoldenFileName: "optimize-service-monitor",
		Templates:      []string{"templates/service-monitor/optimize-service-monitor.yaml"},
		SetValues: map[string]string{
			"optimize.enabled":                 "true",
			"prometheusServiceMonitor.enabled": "true",
			"identity.enabled":                 "true",
		},
	})
}

func TestGoldenServiceMonitorModelerDefaults(t *testing.T) {
	t.Parallel()

	chartPath, err := filepath.Abs("../../../")
	require.NoError(t, err)

	suite.Run(t, &utils.TemplateGoldenTest{
		ChartPath:      chartPath,
		Release:        "camunda-platform-test",
		Namespace:      "camunda-platform-" + strings.ToLower(random.UniqueId()),
		GoldenFileName: "web-modeler-service-monitor",
		Templates:      []string{"templates/service-monitor/web-modeler-service-monitor.yaml"},
		SetValues: map[string]string{"prometheusServiceMonitor.enabled": "true",
			"webModeler.enabled":                  "true",
			"webModeler.restapi.mail.fromAddress": "test@test.com",
			"identity.enabled":                    "true",
		},
	})
}

func TestGoldenServiceMonitorConnectorsDefaults(t *testing.T) {
	t.Parallel()

	chartPath, err := filepath.Abs("../../../")
	require.NoError(t, err)

	suite.Run(t, &utils.TemplateGoldenTest{
		ChartPath:      chartPath,
		Release:        "camunda-platform-test",
		Namespace:      "camunda-platform-" + strings.ToLower(random.UniqueId()),
		GoldenFileName: "connectors-service-monitor",
		Templates:      []string{"templates/service-monitor/connectors-service-monitor.yaml"},
		SetValues:      map[string]string{"prometheusServiceMonitor.enabled": "true"},
	})
}

func TestGoldenServiceMonitorIdentityDefaults(t *testing.T) {
	t.Parallel()

	chartPath, err := filepath.Abs("../../../")
	require.NoError(t, err)

	suite.Run(t, &utils.TemplateGoldenTest{
		ChartPath:      chartPath,
		Release:        "camunda-platform-test",
		Namespace:      "camunda-platform-" + strings.ToLower(random.UniqueId()),
		GoldenFileName: "identity-service-monitor",
		Templates:      []string{"templates/service-monitor/identity-service-monitor.yaml"},
		SetValues: map[string]string{
			"global.identity.auth.enabled":     "true",
			"prometheusServiceMonitor.enabled": "true",
		},
	})
}

func TestGoldenServiceMonitorOrchestrationDefaults(t *testing.T) {
	t.Parallel()

	chartPath, err := filepath.Abs("../../../")
	require.NoError(t, err)

	suite.Run(t, &utils.TemplateGoldenTest{
		ChartPath:      chartPath,
		Release:        "camunda-platform-test",
		Namespace:      "camunda-platform-" + strings.ToLower(random.UniqueId()),
		GoldenFileName: "orchestration-service-monitor",
		Templates:      []string{"templates/service-monitor/orchestration-service-monitor.yaml"},
		SetValues:      map[string]string{"prometheusServiceMonitor.enabled": "true"},
	})
}
