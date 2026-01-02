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
	"path/filepath"
	"strings"
	"testing"

	"github.com/gruntwork-io/terratest/modules/helm"
	"github.com/gruntwork-io/terratest/modules/k8s"
	"github.com/gruntwork-io/terratest/modules/random"
	"github.com/stretchr/testify/require"
	"github.com/stretchr/testify/suite"
	coreV1 "k8s.io/api/core/v1"
)

type secretTest struct {
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

	suite.Run(t, &secretTest{
		chartPath:  chartPath,
		release:    "camunda-platform-test",
		namespace:  "camunda-platform-" + strings.ToLower(random.UniqueId()),
		templates:  []string{},
		secretName: []string{},
	})
}

func (s *secretTest) TestSecretExternalDatabaseEnabledWithDefinedPassword() {
	// given
	options := &helm.Options{
		SetValues: map[string]string{
			"identity.enabled":                   "true",
			"identityPostgresql.enabled":         "false",
			"identity.externalDatabase.enabled":  "true",
			"identity.externalDatabase.password": "super-secure-ext",
		},
		KubectlOptions: k8s.NewKubectlOptions("", "", s.namespace),
	}

	s.templates = []string{
		"templates/identity/postgresql-secret.yaml",
	}

	// when
	output := helm.RenderTemplate(s.T(), options, s.chartPath, s.release, s.templates)
	var secret coreV1.Secret
	helm.UnmarshalK8SYaml(s.T(), output, &secret)

	// then
	s.NotEmpty(secret.Data)
	s.Require().Equal("super-secure-ext", string(secret.Data["password"]))
}
