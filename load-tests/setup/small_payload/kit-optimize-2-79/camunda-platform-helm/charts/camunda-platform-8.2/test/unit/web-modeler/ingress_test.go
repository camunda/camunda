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

	"camunda-platform/test/unit/golden"

	"github.com/gruntwork-io/terratest/modules/random"
	"github.com/stretchr/testify/require"
	"github.com/stretchr/testify/suite"
)

func TestGoldenIngressDefaultTemplate(t *testing.T) {
	t.Parallel()

	chartPath, err := filepath.Abs("../../../")
	require.NoError(t, err)

	suite.Run(t, &golden.TemplateGoldenTest{
		ChartPath:      chartPath,
		Release:        "camunda-platform-test",
		Namespace:      "camunda-platform-" + strings.ToLower(random.UniqueId()),
		GoldenFileName: "ingress",
		Templates:      []string{"templates/web-modeler/ingress.yaml"},
		SetValues: map[string]string{
			"webModeler.enabled":                  "true",
			"webModeler.restapi.mail.fromAddress": "example@example.com",
			"webModeler.ingress.enabled":          "true",
			"webModeler.ingress.webapp.host":      "modeler.example.com",
			"webModeler.ingress.websockets.host":  "modeler-ws.example.com",
		},
	})
}

func TestGoldenIngressAllEnabledTemplate(t *testing.T) {
	t.Parallel()

	chartPath, err := filepath.Abs("../../../")
	require.NoError(t, err)

	suite.Run(t, &golden.TemplateGoldenTest{
		ChartPath:      chartPath,
		Release:        "camunda-platform-test",
		Namespace:      "camunda-platform-" + strings.ToLower(random.UniqueId()),
		GoldenFileName: "ingress-all-enabled",
		Templates:      []string{"templates/web-modeler/ingress.yaml"},
		SetValues: map[string]string{
			"webModeler.enabled":                           "true",
			"webModeler.restapi.mail.fromAddress":          "example@example.com",
			"webModeler.ingress.enabled":                   "true",
			"webModeler.ingress.webapp.host":               "modeler.example.com",
			"webModeler.ingress.websockets.host":           "modeler-ws.example.com",
			"webModeler.ingress.webapp.tls.enabled":        "true",
			"webModeler.ingress.webapp.tls.secretName":     "webapp-tls-secret",
			"webModeler.ingress.websockets.tls.enabled":    "true",
			"webModeler.ingress.websockets.tls.secretName": "websockets-tls-secret",
		},
	})
}
