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

type ConstraintTemplateTest struct {
	suite.Suite
	chartPath string
	release   string
	namespace string
	templates []string
}

func TestConstraintTemplate(t *testing.T) {
	t.Parallel()

	chartPath, err := filepath.Abs("../../../")
	require.NoError(t, err)

	suite.Run(t, &ConstraintTemplateTest{
		chartPath: chartPath,
		release:   "camunda-platform-test",
		namespace: "camunda-platform-" + strings.ToLower(random.UniqueId()),
		templates: []string{},
	})
}

func (s *ConstraintTemplateTest) TestDifferentValuesInputs() {
	testCases := []testhelpers.TestCase{
		{
			Name: "TestExistingSecretConstraintDisplays",
			Values: map[string]string{
				"global.identity.auth.issuerBackendUrl":                "http://keycloak:80/auth/realms/camunda-platform",
				"global.testDeprecationFlags.existingSecretsMustBeSet": "error",
			},
			Verifier: func(t *testing.T, output string, err error) {
				// then
				s.Require().ErrorContains(err, "the Camunda Helm chart will no longer automatically generate passwords for the Identity component")
			},
		}, {
			Name: "TestExistingSecretConstraintDoesNotDisplayErrorForComponentWithExistingSecret",
			Values: map[string]string{
				"global.identity.auth.issuerBackendUrl":                "http://keycloak:80/auth/realms/camunda-platform",
				"global.testDeprecationFlags.existingSecretsMustBeSet": "error",
				"global.identity.auth.zeebe.existingSecret.name":       "zeebe-secret",
			},
			Verifier: func(t *testing.T, output string, err error) {
				// then
				s.Require().NotContains(err.Error(), "global.identity.auth.zeebe.existingSecret")
			},
		}, {
			Name: "TestExistingSecretConstraintDoesNotDisplayErrorForComponentThatsDisabled",
			Values: map[string]string{
				"global.identity.auth.issuerBackendUrl":                "http://keycloak:80/auth/realms/camunda-platform",
				"global.testDeprecationFlags.existingSecretsMustBeSet": "error",
				"operate.enabled": "false",
			},
			Verifier: func(t *testing.T, output string, err error) {
				// then
				s.Require().NotContains(err.Error(), "global.identity.auth.operate.existingSecret")
			},
		}, {
			Name: "TestExistingSecretConstraintInWarningModeDoesNotPreventInstall",
			Values: map[string]string{
				"global.identity.auth.issuerBackendUrl":                "http://keycloak:80/auth/realms/camunda-platform",
				"global.testDeprecationFlags.existingSecretsMustBeSet": "warning",
			},
			Verifier: func(t *testing.T, output string, err error) {
				// then
				s.Require().Nil(err)
			},
		}, {
			Name: "TestContextPathAndRestPathForZeebeGatewayConstraintBothValuesShouldBeTheSame",
			Values: map[string]string{
				"zeebeGateway.ingress.rest.enabled": "true",
				"zeebeGateway.ingress.rest.path":    "/zeebe",
				"zeebeGateway.contextPath":          "/zeebeRest",
			},
			Verifier: func(t *testing.T, output string, err error) {
				s.Require().ErrorContains(err, "[camunda][error]")
			},
		},
	}

	testhelpers.RunTestCasesE(s.T(), s.chartPath, s.release, s.namespace, s.templates, testCases)
}
