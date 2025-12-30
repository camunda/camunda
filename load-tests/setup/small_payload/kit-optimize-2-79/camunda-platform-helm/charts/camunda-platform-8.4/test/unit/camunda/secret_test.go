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

	"github.com/gruntwork-io/terratest/modules/helm"
	"github.com/gruntwork-io/terratest/modules/random"
	"github.com/stretchr/testify/require"
	"github.com/stretchr/testify/suite"
	coreV1 "k8s.io/api/core/v1"
)

type SecretTest struct {
	suite.Suite
	chartPath  string
	release    string
	namespace  string
	templates  []string
	secretName []string
}

func TestSecretTemplate(t *testing.T) {
	t.Parallel()

	chartPath, err := filepath.Abs("../../../")
	require.NoError(t, err)

	suite.Run(t, &SecretTest{
		chartPath:  chartPath,
		release:    "camunda-platform-test",
		namespace:  "camunda-platform-" + strings.ToLower(random.UniqueId()),
		templates:  []string{},
		secretName: []string{},
	})
}

func (s *SecretTest) TestDifferentValuesInputs() {
	// Define components that need secret tests
	components := []string{
		"Connectors",
		"Console",
		"Operate",
		"Optimize",
		"TaskList",
		"Zeebe",
	}

	require.Equal(s.T(), len(components), 6, "Expected 6 components to be tested")

	// Create test cases for each component
	testCases := make([]testhelpers.TestCase, 0, len(components)+1)
	for _, component := range components {
		testCases = append(testCases, s.createSecretTestCase(component))
	}

	testhelpers.RunTestCasesE(s.T(), s.chartPath, s.release, s.namespace, s.templates, testCases)
}

func (s *SecretTest) createSecretTestCase(componentName string) testhelpers.TestCase {
	secretKey := strings.ToLower(componentName) + "-secret"
	templatePath := "templates/camunda/secret-" + strings.ToLower(componentName) + ".yaml"

	return testhelpers.TestCase{
		Name: "TestContainerGenerateSecret" + componentName,
		CaseTemplates: &testhelpers.CaseTemplate{
			Templates: []string{templatePath},
		},
		Verifier: func(t *testing.T, output string, err error) {
			s.verifySecretData(t, output, secretKey)
		},
	}
}

func (s *SecretTest) verifySecretData(t *testing.T, output string, secretName string) {
	var secret coreV1.Secret
	helm.UnmarshalK8SYaml(t, output, &secret)

	require.NotNil(t, secret.Data)
	require.NotNil(t, secret.Data[secretName])
	require.NotEmpty(t, secret.Data[secretName])
}
