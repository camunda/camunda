// documentstore_configmap_test.go
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

type documentStoreConfigMapTest struct {
	suite.Suite
	chartPath string
	release   string
	namespace string
	templates []string
}

func TestDocumentStoreConfigMapTemplate(t *testing.T) {
	t.Parallel()

	chartPath, err := filepath.Abs("../../../")
	require.NoError(t, err)

	suite.Run(t, &documentStoreConfigMapTest{
		chartPath: chartPath,
		release:   "camunda-platform-test",
		namespace: "camunda-platform-" + strings.ToLower(random.UniqueId()),
		templates: []string{"templates/common/configmap-documentstore.yaml"},
	})
}

func (s *documentStoreConfigMapTest) TestDifferentValuesInputs() {
	testCases := []testhelpers.TestCase{
		{
			Name: "Document Handling: AWS",
			Values: map[string]string{
				"global.documentStore.activeStoreId":                  "aws",
				"global.documentStore.type.aws.enabled":               "true",
				"global.documentStore.type.aws.storeId":               "AWS",
				"global.documentStore.type.aws.class":                 "io.camunda.document.store.aws.AwsDocumentStoreProvider",
				"global.documentStore.type.aws.bucket":                    "aws-bucket",
				"global.documentStore.type.aws.bucketPath":             "/aws/path",
				"identity.enabled":                                     "true",
				"connectors.security.authentication.oidc.existingSecret.name":  "foo",
				"orchestration.security.authentication.oidc.existingSecret.name": "bar",
			},
			Expected: map[string]string{
				"DOCUMENT_DEFAULT_STORE_ID":      "aws",
				"DOCUMENT_STORE_AWS_CLASS":       "io.camunda.document.store.aws.AwsDocumentStoreProvider",
				"DOCUMENT_STORE_AWS_BUCKET":      "aws-bucket",
				"DOCUMENT_STORE_AWS_BUCKET_PATH": "/aws/path",
			},
		},
		{
			Name: "Document Handling: GCP",
			Values: map[string]string{
				"global.documentStore.activeStoreId":                  "gcp",
				"global.documentStore.type.gcp.enabled":               "true",
				"global.documentStore.type.gcp.storeId":               "GCP",
				"global.documentStore.type.gcp.class":                 "io.camunda.document.store.gcp.GcpDocumentStoreProvider",
				"global.documentStore.type.gcp.bucket":                    "gcp-bucket",
				"identity.enabled":                                     "true",
				"connectors.security.authentication.oidc.existingSecret.name":  "foo",
				"orchestration.security.authentication.oidc.existingSecret.name": "bar",
			},
			Expected: map[string]string{
				"DOCUMENT_DEFAULT_STORE_ID": "gcp",
				"DOCUMENT_STORE_GCP_CLASS":  "io.camunda.document.store.gcp.GcpDocumentStoreProvider",
				"DOCUMENT_STORE_GCP_BUCKET": "gcp-bucket",
			},
		},
		{
			Name: "Document Handling: In Memory",
			Values: map[string]string{
				"global.documentStore.activeStoreId":                  "inmemory",
				"global.documentStore.type.inmemory.enabled":              "true",
				"global.documentStore.type.inmemory.storeId":           "INMEMORY",
				"global.documentStore.type.inmemory.class":             "io.camunda.document.store.inmemory.InMemoryDocumentStoreProvider",
				"identity.enabled":                                     "true",
				"connectors.security.authentication.oidc.existingSecret.name":  "foo",
				"orchestration.security.authentication.oidc.existingSecret.name": "bar",
			},
			Expected: map[string]string{
				"DOCUMENT_DEFAULT_STORE_ID":     "inmemory",
				"DOCUMENT_STORE_INMEMORY_CLASS": "io.camunda.document.store.inmemory.InMemoryDocumentStoreProvider",
			},
		},
	}

	testhelpers.RunTestCases(s.T(), s.chartPath, s.release, s.namespace, s.templates, testCases)
}
