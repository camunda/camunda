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

package web_modeler

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

type serviceTest struct {
	suite.Suite
	chartPath string
	release   string
	namespace string
	component string
	templates []string
}

func TestServiceTemplate(t *testing.T) {
	t.Parallel()

	chartPath, err := filepath.Abs("../../../")
	require.NoError(t, err)
	components := []string{"restapi", "webapp", "websockets"}

	for _, component := range components {
		suite.Run(t, &serviceTest{
			chartPath: chartPath,
			release:   "camunda-platform-test",
			namespace: "camunda-platform-" + strings.ToLower(random.UniqueId()),
			component: component,
			templates: []string{"templates/web-modeler/service-" + component + ".yaml"},
		})
	}

}

func (s *serviceTest) TestContainerSetGlobalAnnotations() {
	// given
	options := &helm.Options{
		SetValues: map[string]string{
			"webModeler.enabled":                  "true",
			"webModeler.restapi.mail.fromAddress": "example@example.com",
			"global.annotations.foo":              "bar",
		},
		KubectlOptions: k8s.NewKubectlOptions("", "", s.namespace),
	}

	// when
	output := helm.RenderTemplate(s.T(), options, s.chartPath, s.release, s.templates)
	var service coreV1.Service
	helm.UnmarshalK8SYaml(s.T(), output, &service)

	// then
	s.Require().Equal("bar", service.ObjectMeta.Annotations["foo"])
}

func (s *serviceTest) TestContainerServiceAnnotations() {
	// given
	options := &helm.Options{
		SetValues: map[string]string{
			"webModeler.enabled":                                     "true",
			"webModeler.restapi.mail.fromAddress":                    "example@example.com",
			"webModeler." + s.component + ".service.annotations.foo": "bar",
		},
		KubectlOptions: k8s.NewKubectlOptions("", "", s.namespace),
	}

	// when
	output := helm.RenderTemplate(s.T(), options, s.chartPath, s.release, s.templates)

	var service coreV1.Service
	helm.UnmarshalK8SYaml(s.T(), output, &service)

	// then
	s.Require().Equal("bar", service.ObjectMeta.Annotations["foo"])
}
