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
	appsv1 "k8s.io/api/apps/v1"
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
				Templates: []string{"templates/identity/postgresql-secret.yaml"},
			},
			Values: map[string]string{
				"identityPostgresql.enabled":         "false",
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
		}, {
			Name: "TestExternalIdentityPostgresqlSecretRenderedOnCompatibilityPostgresqlEnabledViaChart",
			CaseTemplates: &testhelpers.CaseTemplate{
				Templates: []string{"charts/identityPostgresql/templates/secrets.yaml"},
			},
			Values: map[string]string{
				// note how it's not identityPostgresql.enabled so we can reproduce SUPPORT-21601
				"identity.postgresql.enabled":       "true",
				"identity.externalDatabase.enabled": "false",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var secret coreV1.Secret

				helm.UnmarshalK8SYaml(s.T(), output, &secret)

				// I expect Secret to be rendered via templates/identity/postgresql-secret.yaml
				s.Require().Equal("camunda-platform-test-identity-postgresql", secret.ObjectMeta.Name)
				s.Require().NotEmpty(string(secret.Data["password"]))
			},
		}, {
			Name: "TestExternalIdentityPostgresqlSecretRenderedOnCompatibilityPostgresqlEnabledViaTemplate",
			CaseTemplates: &testhelpers.CaseTemplate{
				Templates: []string{"templates/identity/postgresql-secret.yaml"},
			},
			Values: map[string]string{
				// note how it's not identityPostgresql.enabled so we can reproduce SUPPORT-21601
				"identity.postgresql.enabled":       "true",
				"identity.externalDatabase.enabled": "false",
			},
			Verifier: func(t *testing.T, output string, err error) {
				// I expect Secret to NOT be rendered via charts/identityPostgresql/templates/secrets.yaml
				s.Require().ErrorContains(err, "could not find template templates/identity/postgresql-secret.yaml in chart")
			},
		}, {
			Name: "TestExternalIdentityPostgresqlSecretRenderedOnCompatibilityPostgresqlEnabledDeployment",
			CaseTemplates: &testhelpers.CaseTemplate{
				Templates: []string{"templates/identity/deployment.yaml"},
			},
			Values: map[string]string{
				// note how it's not identityPostgresql.enabled so we can reproduce SUPPORT-21601
				"identity.postgresql.enabled":       "true",
				"identity.externalDatabase.enabled": "false",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment

				helm.UnmarshalK8SYaml(s.T(), output, &deployment)

				envVars := deployment.Spec.Template.Spec.Containers[0].Env
				var identityDatabasePassword coreV1.EnvVar
				for _, envVar := range envVars {
					if envVar.Name == "IDENTITY_DATABASE_PASSWORD" {
						identityDatabasePassword = envVar
					}
				}

				s.Require().Equal("IDENTITY_DATABASE_PASSWORD", identityDatabasePassword.Name)
				// I expect Deployment environment variable to reference the secret that is rendered
				s.Require().Equal("camunda-platform-test-identity-postgresql", identityDatabasePassword.ValueFrom.SecretKeyRef.Name)
				// I expect Secret to be rendered via templates/identity/postgresql-secret.yaml
			},
		},
	}

	testhelpers.RunTestCasesE(s.T(), s.chartPath, s.release, s.namespace, s.templates, testCases)
}
