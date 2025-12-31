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

package orchestration

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

type ServiceTest struct {
	suite.Suite
	chartPath string
	release   string
	namespace string
	templates []string
}

func TestServiceTemplate(t *testing.T) {
	t.Parallel()

	chartPath, err := filepath.Abs("../../../")
	require.NoError(t, err)

	suite.Run(t, &ServiceTest{
		chartPath: chartPath,
		release:   "camunda-platform-test",
		namespace: "camunda-platform-" + strings.ToLower(random.UniqueId()),
		templates: []string{"templates/orchestration/service.yaml"},
	})
}

func (s *ServiceTest) TestDifferentValuesInputs() {
	testCases := []testhelpers.TestCase{
		{
			Name: "TestContainerSetGlobalAnnotations",
			Values: map[string]string{
				"global.annotations.foo": "bar-global-annotation",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var service coreV1.Service
				helm.UnmarshalK8SYaml(s.T(), output, &service)

				// then
				s.Require().Equal("bar-global-annotation", service.ObjectMeta.Annotations["foo"])
			},
		}, {
			Name: "TestExtraPorts",
			Values: map[string]string{
				"orchestration.service.extraPorts[0].name":       "hazelcast",
				"orchestration.service.extraPorts[0].protocol":   "TCP",
				"orchestration.service.extraPorts[0].port":       "5701",
				"orchestration.service.extraPorts[0].targetPort": "5701",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var service coreV1.Service
				helm.UnmarshalK8SYaml(s.T(), output, &service)

				// then
				expectedPort := int32(5701)
				expectedName := "hazelcast"
				expectedTargetPort := int32(5701)
				ports := service.Spec.Ports
				var hazelcastPort coreV1.ServicePort
				found := false
				for _, port := range ports {
					if port.Name == expectedName {
						hazelcastPort = port
						found = true
						continue
					}
				}
				if !found {
					s.Fail("hazelcast port not found")
				}

				s.Require().Equal(expectedPort, hazelcastPort.Port)
				s.Require().Equal(expectedTargetPort, hazelcastPort.TargetPort.IntVal)
			},
		}, {
			Name: "TestContainerServiceAnnotations",
			Values: map[string]string{
				"orchestration.service.annotations.foo": "bar-service-annotation",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var service coreV1.Service
				helm.UnmarshalK8SYaml(s.T(), output, &service)

				// then
				s.Require().Equal("bar-service-annotation", service.ObjectMeta.Annotations["foo"])
			},
		},
	}

	testhelpers.RunTestCasesE(s.T(), s.chartPath, s.release, s.namespace, s.templates, testCases)
}

