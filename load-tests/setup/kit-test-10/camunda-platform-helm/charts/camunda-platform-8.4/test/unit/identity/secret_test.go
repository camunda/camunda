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

package identity

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
	testCases := []testhelpers.TestCase{
		{
			Name: "TestSecretExternalDatabaseEnabledWithDefinedPassword",
			CaseTemplates: &testhelpers.CaseTemplate{
				Templates: []string{"charts/identity/templates/postgresql-secret.yaml"},
			},
			Values: map[string]string{
				"identity.postgresql.enabled":        "false",
				"identity.externalDatabase.enabled":  "true",
				"identity.externalDatabase.password": "super-secure-ext",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var secret coreV1.Secret
				helm.UnmarshalK8SYaml(s.T(), output, &secret)

				// then
				s.NotEmpty(secret.Data)
				s.Require().Equal("super-secure-ext", string(secret.Data["password"]))
			},
		},
	}

	testhelpers.RunTestCasesE(s.T(), s.chartPath, s.release, s.namespace, s.templates, testCases)
}
