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
	batchv1 "k8s.io/api/batch/v1"
)

type MigrationIdentityJobTest struct {
	suite.Suite
	chartPath string
	release   string
	namespace string
	templates []string
}

func TestMigrationIdentityJobTemplate(t *testing.T) {
	t.Parallel()

	chartPath, err := filepath.Abs("../../../")
	require.NoError(t, err)

	suite.Run(t, &MigrationIdentityJobTest{
		chartPath: chartPath,
		release:   "camunda-platform-test",
		namespace: "camunda-platform-" + strings.ToLower(random.UniqueId()),
		templates: []string{"templates/orchestration/migration-identity-job.yaml"},
	})
}

func (s *MigrationIdentityJobTest) TestDifferentValuesInputs() {
	testCases := []testhelpers.TestCase{
		{
			Name: "TestContainerSetInitContainerImageCustomTag",
			Values: map[string]string{
				"orchestration.migration.identity.enabled":                 "true",
				"orchestration.migration.identity.secret.inlineSecret":     "very-secret-thus-plaintext",
				"orchestration.migration.identity.waitContainer.image.tag": "9.16.0",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var job batchv1.Job
				helm.UnmarshalK8SYaml(s.T(), output, &job)

				// then
				initContainers := job.Spec.Template.Spec.InitContainers
				s.Require().Equal(1, len(initContainers))
				s.Require().Equal("curlimages/curl:9.16.0", initContainers[0].Image)
			},
		}, {
			Name: "TestContainerSetInitContainerImageCustomRepository",
			Values: map[string]string{
				"orchestration.migration.identity.enabled":                        "true",
				"orchestration.migration.identity.secret.inlineSecret":            "very-secret-thus-plaintext",
				"orchestration.migration.identity.waitContainer.image.repository": "mycustom/curl",
				"orchestration.migration.identity.waitContainer.image.tag":        "latest",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var job batchv1.Job
				helm.UnmarshalK8SYaml(s.T(), output, &job)

				// then
				initContainers := job.Spec.Template.Spec.InitContainers
				s.Require().Equal(1, len(initContainers))
				s.Require().Equal("mycustom/curl:latest", initContainers[0].Image)
			},
		}, {
			Name: "TestContainerSetInitContainerImageCustomRegistry",
			Values: map[string]string{
				"orchestration.migration.identity.enabled":                        "true",
				"orchestration.migration.identity.secret.inlineSecret":            "very-secret-thus-plaintext",
				"orchestration.migration.identity.waitContainer.image.registry":   "my.custom.registry.io",
				"orchestration.migration.identity.waitContainer.image.repository": "curlimages/curl",
				"orchestration.migration.identity.waitContainer.image.tag":        "custom-tag-123",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var job batchv1.Job
				helm.UnmarshalK8SYaml(s.T(), output, &job)

				// then
				initContainers := job.Spec.Template.Spec.InitContainers
				s.Require().Equal(1, len(initContainers))
				s.Require().Equal("my.custom.registry.io/curlimages/curl:custom-tag-123", initContainers[0].Image)
			},
		}, {
			Name: "TestContainerSetInitContainerImageWithDigest",
			Values: map[string]string{
				"orchestration.migration.identity.enabled":                    "true",
				"orchestration.migration.identity.secret.inlineSecret":        "very-secret-thus-plaintext",
				"orchestration.migration.identity.waitContainer.image.digest": "sha256:abcd1234efgh5678",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var job batchv1.Job
				helm.UnmarshalK8SYaml(s.T(), output, &job)

				// then
				initContainers := job.Spec.Template.Spec.InitContainers
				s.Require().Equal(1, len(initContainers))
				s.Require().Equal("curlimages/curl@sha256:abcd1234efgh5678", initContainers[0].Image)
			},
		}, {
			Name: "TestContainerSetInitContainerImageViaGlobalRegistry",
			Values: map[string]string{
				"orchestration.migration.identity.enabled":             "true",
				"orchestration.migration.identity.secret.inlineSecret": "very-secret-thus-plaintext",
				"global.image.registry":                                "global.registry.io",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var job batchv1.Job
				helm.UnmarshalK8SYaml(s.T(), output, &job)

				// then
				initContainers := job.Spec.Template.Spec.InitContainers
				s.Require().Equal(1, len(initContainers))
				s.Require().Contains(initContainers[0].Image, "global.registry.io/curlimages/curl:")
				s.Require().NotContains(initContainers[0].Image, "@")
			},
		}, {
			Name: "TestContainerInitContainerImageOverrideGlobalRegistry",
			Values: map[string]string{
				"orchestration.migration.identity.enabled":                      "true",
				"orchestration.migration.identity.secret.inlineSecret":          "very-secret-thus-plaintext",
				"global.image.registry":                                         "global.registry.io",
				"orchestration.migration.identity.waitContainer.image.registry": "override.registry.io",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var job batchv1.Job
				helm.UnmarshalK8SYaml(s.T(), output, &job)

				// then
				initContainers := job.Spec.Template.Spec.InitContainers
				s.Require().Equal(1, len(initContainers))
				s.Require().Contains(initContainers[0].Image, "override.registry.io/curlimages/curl:")
				s.Require().NotContains(initContainers[0].Image, "global.registry.io")
			},
		},
	}

	testhelpers.RunTestCasesE(s.T(), s.chartPath, s.release, s.namespace, s.templates, testCases)
}
