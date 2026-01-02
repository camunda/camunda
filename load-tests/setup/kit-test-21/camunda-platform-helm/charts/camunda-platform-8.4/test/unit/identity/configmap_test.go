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
	corev1 "k8s.io/api/core/v1"
)

type ConfigMapTemplateTest struct {
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

	suite.Run(t, &ConfigMapTemplateTest{
		chartPath: chartPath,
		release:   "camunda-platform-test",
		namespace: "camunda-platform-" + strings.ToLower(random.UniqueId()),
		templates: []string{"charts/identity/templates/configmap-env-vars.yaml"},
	})
}

func (s *ConfigMapTemplateTest) TestDifferentValuesInputs() {
	testCases := []testhelpers.TestCase{
		{
			Name: "TestConfigMapBuiltinDatabaseEnabled",
			Values: map[string]string{
				"global.multitenancy.enabled": "true",
				"identity.postgresql.enabled": "true",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var configmap corev1.ConfigMap
				helm.UnmarshalK8SYaml(s.T(), output, &configmap)

				// then
				s.NotEmpty(configmap.Data)
				s.Require().Equal("true", configmap.Data["MULTITENANCY_ENABLED"])
				s.Require().Equal("camunda-platform-test-identity-postgresql", configmap.Data["IDENTITY_DATABASE_HOST"])
				s.Require().Equal("5432", configmap.Data["IDENTITY_DATABASE_PORT"])
				s.Require().Equal("identity", configmap.Data["IDENTITY_DATABASE_NAME"])
				s.Require().Equal("identity", configmap.Data["IDENTITY_DATABASE_USERNAME"])
			},
		}, {
			Name: "TestConfigMapExternalDatabaseEnabled",
			Values: map[string]string{
				"global.multitenancy.enabled":        "true",
				"identity.postgresql.enabled":        "false",
				"identity.externalDatabase.enabled":  "true",
				"identity.externalDatabase.host":     "my-database-host",
				"identity.externalDatabase.port":     "2345",
				"identity.externalDatabase.database": "my-database-name",
				"identity.externalDatabase.username": "my-database-username",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var configmap corev1.ConfigMap
				helm.UnmarshalK8SYaml(s.T(), output, &configmap)

				// then
				s.NotEmpty(configmap.Data)
				s.Require().Equal("true", configmap.Data["MULTITENANCY_ENABLED"])
				s.Require().Equal("my-database-host", configmap.Data["IDENTITY_DATABASE_HOST"])
				s.Require().Equal("2345", configmap.Data["IDENTITY_DATABASE_PORT"])
				s.Require().Equal("my-database-name", configmap.Data["IDENTITY_DATABASE_NAME"])
				s.Require().Equal("my-database-username", configmap.Data["IDENTITY_DATABASE_USERNAME"])
			},
		},
	}

	testhelpers.RunTestCasesE(s.T(), s.chartPath, s.release, s.namespace, s.templates, testCases)
}
