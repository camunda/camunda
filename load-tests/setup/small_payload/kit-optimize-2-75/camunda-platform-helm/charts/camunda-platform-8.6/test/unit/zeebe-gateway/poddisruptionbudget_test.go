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

package gateway

import (
	"camunda-platform/test/unit/testhelpers"
	"camunda-platform/test/unit/utils"
	"path/filepath"
	"strings"
	"testing"

	"github.com/gruntwork-io/terratest/modules/helm"
	"github.com/gruntwork-io/terratest/modules/random"
	"github.com/stretchr/testify/require"
	"github.com/stretchr/testify/suite"
	v1 "k8s.io/api/policy/v1"
)

func TestGoldenPodDisruptionBudgetDefaults(t *testing.T) {
	t.Parallel()

	chartPath, err := filepath.Abs("../../../")
	require.NoError(t, err)

	suite.Run(t, &utils.TemplateGoldenTest{
		ChartPath:      chartPath,
		Release:        "camunda-platform-test",
		Namespace:      "camunda-platform-" + strings.ToLower(random.UniqueId()),
		GoldenFileName: "poddisruptionbudget",
		Templates:      []string{"templates/zeebe-gateway/poddisruptionbudget.yaml"},
		SetValues:      map[string]string{"zeebeGateway.podDisruptionBudget.enabled": "true"},
	})
}

type PodDisruptionBudgetTest struct {
	suite.Suite
	chartPath string
	release   string
	namespace string
	templates []string
}

func TestPodDisruptionBudgetTemplate(t *testing.T) {
	t.Parallel()

	chartPath, err := filepath.Abs("../../../")
	require.NoError(t, err)

	suite.Run(t, &PodDisruptionBudgetTest{
		chartPath: chartPath,
		release:   "camunda-platform-test",
		namespace: "camunda-platform-" + strings.ToLower(random.UniqueId()),
		templates: []string{"templates/zeebe-gateway/poddisruptionbudget.yaml"},
	})
}

func (s *PodDisruptionBudgetTest) TestDifferentValuesInputs() {
	testCases := []testhelpers.TestCase{
		{
			Name: "TestContainerMinAvailableMutualExclusiveWithMaxUnavailable",
			Values: map[string]string{
				"zeebeGateway.podDisruptionBudget.enabled":      "true",
				"zeebeGateway.podDisruptionBudget.minAvailable": "1",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var podDisruptionBudget v1.PodDisruptionBudget
				helm.UnmarshalK8SYaml(s.T(), output, &podDisruptionBudget)

				// then
				s.Require().EqualValues(1, podDisruptionBudget.Spec.MinAvailable.IntVal)
				s.Require().Nil(podDisruptionBudget.Spec.MaxUnavailable)
			},
		},
	}

	testhelpers.RunTestCasesE(s.T(), s.chartPath, s.release, s.namespace, s.templates, testCases)
}
