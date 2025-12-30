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

package optimize

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
			"templates/optimize/deployment.yaml",
		},
	})
}

func (s *PersistenceTemplateTest) TestPersistenceConfiguration() {
	testCases := []testhelpers.TestCase{
		{
			Name: "TestPersistenceDisabledUsesEmptyDir",
			Values: map[string]string{
				"identity.enabled": "true",
				"optimize.enabled": "true",
				// persistence.enabled defaults to false
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(s.T(), output, &deployment)

				// Find the tmp and camunda volumes
				var tmpVolume, camundaVolume *corev1.Volume
				for i := range deployment.Spec.Template.Spec.Volumes {
					if deployment.Spec.Template.Spec.Volumes[i].Name == "tmp" {
						tmpVolume = &deployment.Spec.Template.Spec.Volumes[i]
					}
					if deployment.Spec.Template.Spec.Volumes[i].Name == "camunda" {
						camundaVolume = &deployment.Spec.Template.Spec.Volumes[i]
					}
				}

				// then
				s.Require().NotNil(tmpVolume, "tmp volume should exist")
				s.Require().NotNil(tmpVolume.EmptyDir, "tmp should use emptyDir when persistence is disabled")
				s.Require().Nil(tmpVolume.PersistentVolumeClaim, "tmp should not use PVC when persistence is disabled")

				s.Require().NotNil(camundaVolume, "camunda volume should exist")
				s.Require().NotNil(camundaVolume.EmptyDir, "camunda should use emptyDir when persistence is disabled")
				s.Require().Nil(camundaVolume.PersistentVolumeClaim, "camunda should not use PVC when persistence is disabled")
			},
		},
		{
			Name: "TestPersistenceEnabledCreatesVolume",
			Values: map[string]string{
				"identity.enabled":                    "true",
				"optimize.enabled":                    "true",
				"optimize.persistence.enabled":        "true",
				"optimize.persistence.size":           "5Gi",
				"optimize.persistence.accessModes[0]": "ReadWriteOnce",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(s.T(), output, &deployment)

				// Find the tmp and camunda volumes
				var tmpVolume, camundaVolume *corev1.Volume
				for i := range deployment.Spec.Template.Spec.Volumes {
					if deployment.Spec.Template.Spec.Volumes[i].Name == "tmp" {
						tmpVolume = &deployment.Spec.Template.Spec.Volumes[i]
					}
					if deployment.Spec.Template.Spec.Volumes[i].Name == "camunda" {
						camundaVolume = &deployment.Spec.Template.Spec.Volumes[i]
					}
				}

				// then
				s.Require().NotNil(tmpVolume, "tmp volume should exist")
				s.Require().NotNil(tmpVolume.PersistentVolumeClaim, "tmp should use PVC when persistence is enabled")
				s.Require().Nil(tmpVolume.EmptyDir, "tmp should not use emptyDir when persistence is enabled")
				s.Require().Equal("camunda-platform-test-optimize-data", tmpVolume.PersistentVolumeClaim.ClaimName)

				s.Require().NotNil(camundaVolume, "camunda volume should exist")
				s.Require().NotNil(camundaVolume.PersistentVolumeClaim, "camunda should use PVC when persistence is enabled")
				s.Require().Nil(camundaVolume.EmptyDir, "camunda should not use emptyDir when persistence is enabled")
				s.Require().Equal("camunda-platform-test-optimize-data", camundaVolume.PersistentVolumeClaim.ClaimName)
			},
		},
		{
			Name: "TestPersistenceWithExistingClaimCreatesVolume",
			Values: map[string]string{
				"identity.enabled":                   "true",
				"optimize.enabled":                   "true",
				"optimize.persistence.enabled":       "true",
				"optimize.persistence.existingClaim": "my-existing-pvc",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(s.T(), output, &deployment)

				// Find the tmp and camunda volumes
				var tmpVolume, camundaVolume *corev1.Volume
				for i := range deployment.Spec.Template.Spec.Volumes {
					if deployment.Spec.Template.Spec.Volumes[i].Name == "tmp" {
						tmpVolume = &deployment.Spec.Template.Spec.Volumes[i]
					}
					if deployment.Spec.Template.Spec.Volumes[i].Name == "camunda" {
						camundaVolume = &deployment.Spec.Template.Spec.Volumes[i]
					}
				}

				// then
				s.Require().NotNil(tmpVolume, "tmp volume should exist")
				s.Require().NotNil(tmpVolume.PersistentVolumeClaim, "tmp should use PVC when persistence is enabled")
				s.Require().Equal("my-existing-pvc", tmpVolume.PersistentVolumeClaim.ClaimName)

				s.Require().NotNil(camundaVolume, "camunda volume should exist")
				s.Require().NotNil(camundaVolume.PersistentVolumeClaim, "camunda should use PVC when persistence is enabled")
				s.Require().Equal("my-existing-pvc", camundaVolume.PersistentVolumeClaim.ClaimName)
			},
		},
		{
			Name: "TestPersistenceDisabledWhenComponentDisabled",
			Values: map[string]string{
				"identity.enabled":             "true",
				"optimize.enabled":             "false",
				"optimize.persistence.enabled": "true",
				"optimize.persistence.size":    "5Gi",
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
			"identity.enabled":                    "true",
			"optimize.enabled":                    "true",
			"optimize.persistence.enabled":        "true",
			"optimize.persistence.size":           "5Gi",
			"optimize.persistence.accessModes[0]": "ReadWriteOnce",
		},
		Verifier: func(t *testing.T, output string, err error) {
			var pvc corev1.PersistentVolumeClaim
			helm.UnmarshalK8SYaml(t, output, &pvc)
			require.Equal(t, "camunda-platform-test-optimize-data", pvc.Name)
			require.Equal(t, "5Gi", pvc.Spec.Resources.Requests.Storage().String())
			require.Equal(t, corev1.ReadWriteOnce, pvc.Spec.AccessModes[0])
		},
	}

	testhelpers.RunTestCasesE(t, chartPath, "camunda-platform-test", "camunda-platform-optimize", []string{"templates/optimize/persistentvolumeclaim.yaml"}, []testhelpers.TestCase{testCase})
}
