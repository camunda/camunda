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

package connectors

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
	chartPath string
	release   string
	namespace string
	templates []string
}

func TestSecretRestapiTemplate(t *testing.T) {
	t.Parallel()

	chartPath, err := filepath.Abs("../../../")
	require.NoError(t, err)

	suite.Run(t, &secretTest{
		chartPath: chartPath,
		release:   "camunda-platform-test",
		namespace: "camunda-platform-" + strings.ToLower(random.UniqueId()),
		templates: []string{"templates/connectors/inbound-secret.yaml"},
	})
}

func (s *secretTest) TestContainerCreateConnectorsSecret() {
	// given
	options := &helm.Options{
		SetValues: map[string]string{
			"connectors.enabled":      "true",
			"connectors.inbound.mode": "credentials",
		},
		KubectlOptions: k8s.NewKubectlOptions("", "", s.namespace),
	}

	// when
	output := helm.RenderTemplate(s.T(), options, s.chartPath, s.release, s.templates)
	var secret coreV1.Secret
	helm.UnmarshalK8SYaml(s.T(), output, &secret)

	// then
	s.Require().NotNil(secret.Data)
	s.Require().Regexp("^[a-zA-Z0-9]{10}$", string(secret.Data["connectors-secret"]))
}
