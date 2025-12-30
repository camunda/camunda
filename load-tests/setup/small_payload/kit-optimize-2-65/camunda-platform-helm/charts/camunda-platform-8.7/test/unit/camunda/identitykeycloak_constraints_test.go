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
	appsv1 "k8s.io/api/apps/v1"
)

type ConstraintsTemplateTest struct {
	suite.Suite
	chartPath string
	release   string
	namespace string
	templates []string
}

func TestConstraintsTemplate(t *testing.T) {
	t.Parallel()

	chartPath, err := filepath.Abs("../../../")
	require.NoError(t, err)

	suite.Run(t, &ConstraintsTemplateTest{
		chartPath: chartPath,
		release:   "camunda-platform-test",
		namespace: "camunda-platform-" + strings.ToLower(random.UniqueId()),
		templates: []string{},
	})
}

func (s *ConstraintsTemplateTest) TestDifferentValuesInputs() {
	testCases := []testhelpers.TestCase{
		{
			Name: "TestIdentityKeycloakConstraintFailure",
			Values: map[string]string{
				"identity.enabled":         "false",
				"identityKeycloak.enabled": "true",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				unmarshalErr := helm.UnmarshalK8SYamlE(s.T(), output, &deployment)

				s.Require().Error(err, "[camunda][error] Identity is disabled but identityKeycloak is enabled")
				s.Require().Error(unmarshalErr, "[camunda][error] Identity is disabled but identityKeycloak is enabled")
			},
		}, {
			Name: "TestIdentityKeycloakConstraintSuccess",
			Values: map[string]string{
				"identity.enabled":                      "true",
				"identityKeycloak.enabled":              "false",
				"global.identity.keycloak.url.protocol": "https",
				"global.identity.keycloak.url.host":     "keycloak.prod.svc.cluster.local",
				"global.identity.keycloak.url.port":     "8443",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(s.T(), output, &deployment)

				s.Require().NoError(err, "[camunda][error] Identity is disabled but identityKeycloak is enabled")
			},
		},
	}

	testhelpers.RunTestCasesE(s.T(), s.chartPath, s.release, s.namespace, s.templates, testCases)
}
