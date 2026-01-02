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

	"github.com/gruntwork-io/terratest/modules/random"
	"github.com/stretchr/testify/require"
	"github.com/stretchr/testify/suite"
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
		templates: []string{"templates/camunda/configmap-identity-auth.yaml"},
	})
}

func (c *ConfigMapTemplateTest) TestDifferentValuesInputs() {
	testCases := []testhelpers.TestCase{
		{
			Name: "Identity Provider URL",
			Values: map[string]string{
				"global.identity.auth.issuerBackendUrl": "http://foo-keycloak:80/auth/realms/camunda-platform",
			},
			Expected: map[string]string{
				"CAMUNDA_IDENTITY_ISSUER_BACKEND_URL": "http://foo-keycloak:80/auth/realms/camunda-platform",
			},
		},
		{
			Name: "Identity Provider URL with Keycloak URL syntax",
			Values: map[string]string{
				"global.identity.keycloak.url.protocol": "http",
				"global.identity.keycloak.url.host":     "keycloak",
				"global.identity.keycloak.url.port":     "80",
				"global.identity.keycloak.contextPath":  "/auth/realms/",
				"global.identity.keycloak.realm":        "camunda-platform",
			},
			Expected: map[string]string{
				"CAMUNDA_IDENTITY_ISSUER_BACKEND_URL": "http://keycloak:80/auth/realms/camunda-platform",
			},
		},
	}

	testhelpers.RunTestCases(c.T(), c.chartPath, c.release, c.namespace, c.templates, testCases)
}
