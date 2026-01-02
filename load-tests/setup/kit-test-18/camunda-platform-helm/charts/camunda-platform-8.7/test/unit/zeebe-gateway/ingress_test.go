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

package gateway

import (
	"camunda-platform/test/unit/utils"
	"path/filepath"
	"strings"
	"testing"

	"github.com/gruntwork-io/terratest/modules/random"
	"github.com/stretchr/testify/require"
	"github.com/stretchr/testify/suite"
)

func TestGoldenIngressGrpcDefaultTemplate(t *testing.T) {
	t.Parallel()

	chartPath, err := filepath.Abs("../../../")
	require.NoError(t, err)

	suite.Run(t, &utils.TemplateGoldenTest{
		ChartPath:      chartPath,
		Release:        "camunda-platform-test",
		Namespace:      "camunda-platform-" + strings.ToLower(random.UniqueId()),
		GoldenFileName: "ingress-grpc",
		Templates:      []string{"templates/zeebe-gateway/ingress-grpc.yaml"},
		SetValues:      map[string]string{"zeebeGateway.ingress.grpc.enabled": "true"},
	})
}

func TestGoldenIngressGrpcAllEnabledTemplate(t *testing.T) {
	t.Parallel()

	chartPath, err := filepath.Abs("../../../")
	require.NoError(t, err)

	suite.Run(t, &utils.TemplateGoldenTest{
		ChartPath:      chartPath,
		Release:        "camunda-platform-test",
		Namespace:      "camunda-platform-" + strings.ToLower(random.UniqueId()),
		GoldenFileName: "ingress-grpc-all-enabled",
		Templates:      []string{"templates/zeebe-gateway/ingress-grpc.yaml"},
		SetValues: map[string]string{
			"zeebeGateway.ingress.grpc.enabled":        "true",
			"zeebeGateway.ingress.grpc.host":           "local",
			"zeebeGateway.ingress.grpc.tls.enabled":    "true",
			"zeebeGateway.ingress.grpc.tls.secretName": "my-secret",
		},
	})
}

func TestGoldenIngressRestDefaultTemplate(t *testing.T) {
	t.Parallel()

	chartPath, err := filepath.Abs("../../../")
	require.NoError(t, err)

	suite.Run(t, &utils.TemplateGoldenTest{
		ChartPath:      chartPath,
		Release:        "camunda-platform-test",
		Namespace:      "camunda-platform-" + strings.ToLower(random.UniqueId()),
		GoldenFileName: "ingress-rest",
		Templates:      []string{"templates/zeebe-gateway/ingress-rest.yaml"},
		SetValues: map[string]string{
			"zeebeGateway.contextPath":          "/",
			"zeebeGateway.ingress.rest.enabled": "true",
			"zeebeGateway.ingress.rest.path":    "/",
		},
	})
}

func TestGoldenIngressRestAllEnabledTemplate(t *testing.T) {
	t.Parallel()

	chartPath, err := filepath.Abs("../../../")
	require.NoError(t, err)

	suite.Run(t, &utils.TemplateGoldenTest{
		ChartPath:      chartPath,
		Release:        "camunda-platform-test",
		Namespace:      "camunda-platform-" + strings.ToLower(random.UniqueId()),
		GoldenFileName: "ingress-rest-all-enabled",
		Templates:      []string{"templates/zeebe-gateway/ingress-rest.yaml"},
		SetValues: map[string]string{
			"zeebeGateway.contextPath":                 "/",
			"zeebeGateway.ingress.rest.enabled":        "true",
			"zeebeGateway.ingress.rest.path":           "/",
			"zeebeGateway.ingress.rest.host":           "local",
			"zeebeGateway.ingress.rest.tls.enabled":    "true",
			"zeebeGateway.ingress.rest.tls.secretName": "my-secret",
		},
	})
}
