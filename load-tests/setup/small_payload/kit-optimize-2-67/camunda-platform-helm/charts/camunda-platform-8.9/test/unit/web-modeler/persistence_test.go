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
	"camunda-platform/test/unit/testhelpers"
	"path/filepath"
	"strings"
	"testing"

	corev1 "k8s.io/api/core/v1"

	"github.com/gruntwork-io/terratest/modules/helm"
	"github.com/gruntwork-io/terratest/modules/random"
	"github.com/stretchr/testify/require"
	"github.com/stretchr/testify/suite"
	appsv1 "k8s.io/api/apps/v1"
)

type PersistenceTemplateTest struct {
	suite.Suite
	chartPath string
	release   string
	namespace string
	templates []string
}

func TestPersistenceTemplate(t *testing.T) {
	t.Parallel()

	chartPath, err := filepath.Abs("../../../")
	require.NoError(t, err)

	suite.Run(t, &PersistenceTemplateTest{
		chartPath: chartPath,
		release:   "camunda-platform-test",
		namespace: "camunda-platform-" + strings.ToLower(random.UniqueId()),
		templates: []string{
			"templates/web-modeler/deployment-restapi.yaml",
		},
	})
}

func (s *PersistenceTemplateTest) TestPersistenceConfiguration() {
	testCases := []testhelpers.TestCase{
		{
			Name: "TestPersistenceDisabledUsesEmptyDir",
			Values: map[string]string{
				"identity.enabled":                    "true",
				"webModeler.enabled":                  "true",
				"webModeler.restapi.mail.fromAddress": "example@example.com",
				// persistence.enabled defaults to false
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(s.T(), output, &deployment)

				// Find the tmp volume
				var tmpVolume *corev1.Volume
				for i := range deployment.Spec.Template.Spec.Volumes {
					if deployment.Spec.Template.Spec.Volumes[i].Name == "tmp" {
						tmpVolume = &deployment.Spec.Template.Spec.Volumes[i]
						break
					}
				}

				// then
				s.Require().NotNil(tmpVolume, "tmp volume should exist")
				s.Require().NotNil(tmpVolume.EmptyDir, "should use emptyDir when persistence is disabled")
				s.Require().Nil(tmpVolume.PersistentVolumeClaim, "should not use PVC when persistence is disabled")
			},
		},
		{
			Name: "TestPersistenceEnabledCreatesVolume",
			Values: map[string]string{
				"identity.enabled":                      "true",
				"webModeler.enabled":                    "true",
				"webModeler.restapi.mail.fromAddress":   "example@example.com",
				"webModeler.persistence.enabled":        "true",
				"webModeler.persistence.size":           "5Gi",
				"webModeler.persistence.accessModes[0]": "ReadWriteOnce",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(s.T(), output, &deployment)

				// Find the tmp volume
				var tmpVolume *corev1.Volume
				for i := range deployment.Spec.Template.Spec.Volumes {
					if deployment.Spec.Template.Spec.Volumes[i].Name == "tmp" {
						tmpVolume = &deployment.Spec.Template.Spec.Volumes[i]
						break
					}
				}

				// then
				s.Require().NotNil(tmpVolume, "tmp volume should exist")
				s.Require().NotNil(tmpVolume.PersistentVolumeClaim, "should use PVC when persistence is enabled")
				s.Require().Nil(tmpVolume.EmptyDir, "should not use emptyDir when persistence is enabled")
				s.Require().Equal("camunda-platform-test-webModeler-data", tmpVolume.PersistentVolumeClaim.ClaimName)
			},
		},
		{
			Name: "TestPersistenceWithExistingClaimCreatesVolume",
			Values: map[string]string{
				"identity.enabled":                     "true",
				"webModeler.enabled":                   "true",
				"webModeler.restapi.mail.fromAddress":  "example@example.com",
				"webModeler.persistence.enabled":       "true",
				"webModeler.persistence.existingClaim": "my-existing-pvc",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(s.T(), output, &deployment)

				// Find the tmp volume
				var tmpVolume *corev1.Volume
				for i := range deployment.Spec.Template.Spec.Volumes {
					if deployment.Spec.Template.Spec.Volumes[i].Name == "tmp" {
						tmpVolume = &deployment.Spec.Template.Spec.Volumes[i]
						break
					}
				}

				// then
				s.Require().NotNil(tmpVolume, "tmp volume should exist")
				s.Require().NotNil(tmpVolume.PersistentVolumeClaim, "should use PVC when persistence is enabled")
				s.Require().Equal("my-existing-pvc", tmpVolume.PersistentVolumeClaim.ClaimName)
			},
		},
		{
			Name: "TestPersistenceDisabledWhenComponentDisabled",
			Values: map[string]string{
				"identity.enabled":                    "true",
				"webModeler.enabled":                  "false",
				"webModeler.restapi.mail.fromAddress": "example@example.com",
				"webModeler.persistence.enabled":      "true",
				"webModeler.persistence.size":         "5Gi",
			},
			Verifier: func(t *testing.T, output string, err error) {
				// When component is disabled, no deployment should be created
				s.Require().Empty(output, "no deployment should be created when component is disabled")
			},
		},
	}

	for _, testCase := range testCases {
		testCase := testCase
		s.Run(testCase.Name, func() {
			s.T().Parallel()
			helmChartPath, err := filepath.Abs(s.chartPath)
			s.Require().NoError(err)

			output, err := helm.RenderTemplateE(
				s.T(),
				&helm.Options{
					SetValues: testCase.Values,
				},
				helmChartPath,
				s.release,
				s.templates,
			)

			testCase.Verifier(s.T(), output, err)
		})
	}
}

func TestPVCManifestCreated(t *testing.T) {
	t.Parallel()

	chartPath, err := filepath.Abs("../../../")
	require.NoError(t, err)

	testCase := testhelpers.TestCase{
		Name: "TestPVCManifestCreated",
		Values: map[string]string{
			"identity.enabled":                      "true",
			"webModeler.enabled":                    "true",
			"webModeler.restapi.mail.fromAddress":   "test@test.com",
			"webModeler.persistence.enabled":        "true",
			"webModeler.persistence.size":           "5Gi",
			"webModeler.persistence.accessModes[0]": "ReadWriteOnce",
		},
		Verifier: func(t *testing.T, output string, err error) {
			var pvc corev1.PersistentVolumeClaim
			helm.UnmarshalK8SYaml(t, output, &pvc)
			require.Equal(t, "camunda-platform-test-webmodeler-data", pvc.Name)
			require.Equal(t, "5Gi", pvc.Spec.Resources.Requests.Storage().String())
			require.Equal(t, corev1.ReadWriteOnce, pvc.Spec.AccessModes[0])
		},
	}

	testhelpers.RunTestCasesE(t, chartPath, "camunda-platform-test", "camunda-platform-webmodeler", []string{"templates/web-modeler/persistentvolumeclaim-restapi.yaml"}, []testhelpers.TestCase{testCase})
}
