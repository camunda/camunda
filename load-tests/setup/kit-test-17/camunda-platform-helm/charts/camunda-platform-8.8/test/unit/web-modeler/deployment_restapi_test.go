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

type RestapiDeploymentTemplateTest struct {
	suite.Suite
	chartPath string
	release   string
	namespace string
	templates []string
}

func TestRestapiDeploymentTemplate(t *testing.T) {
	t.Parallel()

	chartPath, err := filepath.Abs("../../../")
	require.NoError(t, err)

	suite.Run(t, &RestapiDeploymentTemplateTest{
		chartPath: chartPath,
		release:   "camunda-platform-test",
		namespace: "camunda-platform-" + strings.ToLower(random.UniqueId()),
		templates: []string{"templates/web-modeler/deployment-restapi.yaml"},
	})
}

func (s *RestapiDeploymentTemplateTest) TestDifferentValuesInputs() {
	testCases := []testhelpers.TestCase{
		{
			Name: "TestContainerExternalDatabasePasswordSecretRefForGivenPassword",
			Values: map[string]string{
				"identity.enabled":                                             "true",
				"webModeler.enabled":                                           "true",
				"webModeler.restapi.mail.fromAddress":                          "example@example.com",
				"webModelerPostgresql.enabled":                                 "false",
				"webModeler.restapi.externalDatabase.url":                      "jdbc:postgresql://postgres.example.com:65432/modeler-database",
				"webModeler.restapi.externalDatabase.user":                     "modeler-user",
				"webModeler.restapi.externalDatabase.secret.existingSecret":    "camunda-platform-test-web-modeler-restapi",
				"webModeler.restapi.externalDatabase.secret.existingSecretKey": "database-password",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(s.T(), output, &deployment)

				// then
				env := deployment.Spec.Template.Spec.Containers[0].Env
				s.Require().Contains(env,
					corev1.EnvVar{
						Name: "SPRING_DATASOURCE_PASSWORD",
						ValueFrom: &corev1.EnvVarSource{
							SecretKeyRef: &corev1.SecretKeySelector{
								LocalObjectReference: corev1.LocalObjectReference{Name: "camunda-platform-test-web-modeler-restapi"},
								Key:                  "database-password",
							},
						},
					})
			},
		}, {
			Name: "TestContainerExternalDatabasePasswordSecretRefForExistingSecretAndDefaultKey",
			Values: map[string]string{
				"identity.enabled":                                        "true",
				"webModeler.enabled":                                      "true",
				"webModeler.restapi.mail.fromAddress":                     "example@example.com",
				"webModelerPostgresql.enabled":                            "false",
				"webModeler.restapi.externalDatabase.url":                 "jdbc:postgresql://postgres.example.com:65432/modeler-database",
				"webModeler.restapi.externalDatabase.user":                "modeler-user",
				"webModeler.restapi.externalDatabase.existingSecret.name": "my-secret",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(s.T(), output, &deployment)

				// then
				env := deployment.Spec.Template.Spec.Containers[0].Env
				s.Require().Contains(env,
					corev1.EnvVar{
						Name: "SPRING_DATASOURCE_PASSWORD",
						ValueFrom: &corev1.EnvVarSource{
							SecretKeyRef: &corev1.SecretKeySelector{
								LocalObjectReference: corev1.LocalObjectReference{Name: "my-secret"},
								Key:                  "database-password",
							},
						},
					})
			},
		}, {
			Name: "TestContainerExternalDatabasePasswordSecretRefForExistingSecretAndCustomKey",
			Values: map[string]string{
				"identity.enabled":                                              "true",
				"webModeler.enabled":                                            "true",
				"webModeler.restapi.mail.fromAddress":                           "example@example.com",
				"webModelerPostgresql.enabled":                                  "false",
				"webModeler.restapi.externalDatabase.url":                       "jdbc:postgresql://postgres.example.com:65432/modeler-database",
				"webModeler.restapi.externalDatabase.user":                      "modeler-user",
				"webModeler.restapi.externalDatabase.existingSecret.name":       "my-secret",
				"webModeler.restapi.externalDatabase.existingSecretPasswordKey": "my-database-password-key",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(s.T(), output, &deployment)

				// then
				env := deployment.Spec.Template.Spec.Containers[0].Env
				s.Require().Contains(env,
					corev1.EnvVar{
						Name: "SPRING_DATASOURCE_PASSWORD",
						ValueFrom: &corev1.EnvVarSource{
							SecretKeyRef: &corev1.SecretKeySelector{
								LocalObjectReference: corev1.LocalObjectReference{Name: "my-secret"},
								Key:                  "my-database-password-key",
							},
						},
					})
			},
		}, {
			Name: "TestContainerExternalDatabasePasswordExplicitlyDefinedStillReferencesInternalSecret",
			Values: map[string]string{
				"identity.enabled":                                             "true",
				"webModeler.enabled":                                           "true",
				"webModeler.restapi.mail.fromAddress":                          "example@example.com",
				"webModelerPostgresql.enabled":                                 "false",
				"webModeler.restapi.externalDatabase.url":                      "jdbc:postgresql://postgres.example.com:65432/modeler-database",
				"webModeler.restapi.externalDatabase.user":                     "modeler-user",
				"webModeler.restapi.externalDatabase.secret.existingSecret":    "camunda-platform-test-web-modeler-restapi",
				"webModeler.restapi.externalDatabase.secret.existingSecretKey": "database-password",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(s.T(), output, &deployment)

				// then
				env := deployment.Spec.Template.Spec.Containers[0].Env

				s.Require().Contains(env,
					corev1.EnvVar{
						Name: "SPRING_DATASOURCE_PASSWORD",
						ValueFrom: &corev1.EnvVarSource{
							SecretKeyRef: &corev1.SecretKeySelector{
								LocalObjectReference: corev1.LocalObjectReference{Name: "camunda-platform-test-web-modeler-restapi"},
								Key:                  "database-password",
							},
						},
					})
			},
		}, {
			Name: "TestContainerInternalDatabasePasswordSecretRefForExistingSecretAndDefaultKey",
			Values: map[string]string{
				"identity.enabled":                         "true",
				"webModeler.enabled":                       "true",
				"webModeler.restapi.mail.fromAddress":      "example@example.com",
				"webModelerPostgresql.enabled":             "true",
				"webModelerPostgresql.auth.existingSecret": "my-secret",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(s.T(), output, &deployment)

				// then
				env := deployment.Spec.Template.Spec.Containers[0].Env
				s.Require().Contains(env,
					corev1.EnvVar{
						Name: "SPRING_DATASOURCE_PASSWORD",
						ValueFrom: &corev1.EnvVarSource{
							SecretKeyRef: &corev1.SecretKeySelector{
								LocalObjectReference: corev1.LocalObjectReference{Name: "my-secret"},
								Key:                  "web-modeler-postgresql-user-password",
							},
						},
					})
			},
		}, {
			Name: "TestContainerInternalDatabasePasswordSecretRefForExistingSecretAndCustomKey",
			Values: map[string]string{
				"identity.enabled":                                     "true",
				"webModeler.enabled":                                   "true",
				"webModeler.restapi.mail.fromAddress":                  "example@example.com",
				"webModelerPostgresql.enabled":                         "true",
				"webModelerPostgresql.auth.existingSecret":             "my-secret",
				"webModelerPostgresql.auth.secretKeys.userPasswordKey": "my-database-password-key",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(s.T(), output, &deployment)

				// then
				env := deployment.Spec.Template.Spec.Containers[0].Env
				s.Require().Contains(env,
					corev1.EnvVar{
						Name: "SPRING_DATASOURCE_PASSWORD",
						ValueFrom: &corev1.EnvVarSource{
							SecretKeyRef: &corev1.SecretKeySelector{
								LocalObjectReference: corev1.LocalObjectReference{Name: "my-secret"},
								Key:                  "my-database-password-key",
							},
						},
					})
			},
		}, {
			Name: "TestContainerSmtpPasswordSecretRefForGivenPassword",
			Values: map[string]string{
				"identity.enabled":                                 "true",
				"webModeler.enabled":                               "true",
				"webModeler.restapi.mail.fromAddress":              "example@example.com",
				"webModeler.restapi.mail.smtpUser":                 "modeler-user",
				"webModeler.restapi.mail.secret.existingSecret":    "camunda-platform-test-web-modeler-restapi",
				"webModeler.restapi.mail.secret.existingSecretKey": "smtp-password",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(s.T(), output, &deployment)

				// then
				env := deployment.Spec.Template.Spec.Containers[0].Env
				s.Require().Contains(env,
					corev1.EnvVar{
						Name: "RESTAPI_MAIL_PASSWORD",
						ValueFrom: &corev1.EnvVarSource{
							SecretKeyRef: &corev1.SecretKeySelector{
								LocalObjectReference: corev1.LocalObjectReference{Name: "camunda-platform-test-web-modeler-restapi"},
								Key:                  "smtp-password",
							},
						},
					})
			},
		}, {
			Name: "TestContainerSmtpPasswordSecretRefForExistingSecretAndDefaultKey",
			Values: map[string]string{
				"identity.enabled":                            "true",
				"webModeler.enabled":                          "true",
				"webModeler.restapi.mail.fromAddress":         "example@example.com",
				"webModeler.restapi.mail.smtpUser":            "modeler-user",
				"webModeler.restapi.mail.existingSecret.name": "my-secret",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(s.T(), output, &deployment)

				// then
				env := deployment.Spec.Template.Spec.Containers[0].Env
				s.Require().Contains(env,
					corev1.EnvVar{
						Name: "RESTAPI_MAIL_PASSWORD",
						ValueFrom: &corev1.EnvVarSource{
							SecretKeyRef: &corev1.SecretKeySelector{
								LocalObjectReference: corev1.LocalObjectReference{Name: "my-secret"},
								Key:                  "smtp-password",
							},
						},
					})
			},
		}, {
			Name: "TestContainerSmtpPasswordSecretRefForExistingSecretAndCustomKey",
			Values: map[string]string{
				"identity.enabled":                                  "true",
				"webModeler.enabled":                                "true",
				"webModeler.restapi.mail.fromAddress":               "example@example.com",
				"webModeler.restapi.mail.smtpUser":                  "modeler-user",
				"webModeler.restapi.mail.existingSecret.name":       "my-secret",
				"webModeler.restapi.mail.existingSecretPasswordKey": "my-smtp-password-key",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(s.T(), output, &deployment)

				// then
				env := deployment.Spec.Template.Spec.Containers[0].Env
				s.Require().Contains(env,
					corev1.EnvVar{
						Name: "RESTAPI_MAIL_PASSWORD",
						ValueFrom: &corev1.EnvVarSource{
							SecretKeyRef: &corev1.SecretKeySelector{
								LocalObjectReference: corev1.LocalObjectReference{Name: "my-secret"},
								Key:                  "my-smtp-password-key",
							},
						},
					})
			},
		}, {
			Name: "TestContainerStartupProbe",
			Values: map[string]string{
				"identity.enabled":                          "true",
				"webModeler.enabled":                        "true",
				"webModeler.restapi.mail.fromAddress":       "example@example.com",
				"webModeler.restapi.startupProbe.enabled":   "true",
				"webModeler.restapi.startupProbe.probePath": "/healthz",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(s.T(), output, &deployment)

				// then
				probe := deployment.Spec.Template.Spec.Containers[0].StartupProbe

				s.Require().Equal("/healthz", probe.HTTPGet.Path)
				s.Require().Equal("http-management", probe.HTTPGet.Port.StrVal)
			},
		}, {
			Name: "TestContainerReadinessProbe",
			Values: map[string]string{
				"identity.enabled":                            "true",
				"webModeler.enabled":                          "true",
				"webModeler.restapi.mail.fromAddress":         "example@example.com",
				"webModeler.restapi.readinessProbe.enabled":   "true",
				"webModeler.restapi.readinessProbe.probePath": "/healthz",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(s.T(), output, &deployment)

				// then
				probe := deployment.Spec.Template.Spec.Containers[0].ReadinessProbe

				s.Require().Equal("/healthz", probe.HTTPGet.Path)
				s.Require().Equal("http-management", probe.HTTPGet.Port.StrVal)
			},
		}, {
			Name: "TestContainerLivenessProbe",
			Values: map[string]string{
				"identity.enabled":                           "true",
				"webModeler.enabled":                         "true",
				"webModeler.restapi.mail.fromAddress":        "example@example.com",
				"webModeler.restapi.livenessProbe.enabled":   "true",
				"webModeler.restapi.livenessProbe.probePath": "/healthz",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(s.T(), output, &deployment)

				// then
				probe := deployment.Spec.Template.Spec.Containers[0].LivenessProbe

				s.Require().Equal("/healthz", probe.HTTPGet.Path)
				s.Require().Equal("http-management", probe.HTTPGet.Port.StrVal)
			},
		}, {
			// Web-Modeler REST API doesn't use contextPath for health endpoints.
			Name: "TestContainerProbesWithContextPath",
			Values: map[string]string{
				"identity.enabled":                            "true",
				"webModeler.enabled":                          "true",
				"webModeler.restapi.mail.fromAddress":         "example@example.com",
				"webModeler.contextPath":                      "/test",
				"webModeler.restapi.startupProbe.enabled":     "true",
				"webModeler.restapi.startupProbe.probePath":   "/start",
				"webModeler.restapi.readinessProbe.enabled":   "true",
				"webModeler.restapi.readinessProbe.probePath": "/ready",
				"webModeler.restapi.livenessProbe.enabled":    "true",
				"webModeler.restapi.livenessProbe.probePath":  "/live",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(s.T(), output, &deployment)

				// then
				probe := deployment.Spec.Template.Spec.Containers[0]

				s.Require().Equal("/start", probe.StartupProbe.HTTPGet.Path)
				s.Require().Equal("/ready", probe.ReadinessProbe.HTTPGet.Path)
				s.Require().Equal("/live", probe.LivenessProbe.HTTPGet.Path)
			},
		}, {
			Name: "TestContainerSetSidecar",
			Values: map[string]string{
				"identity.enabled":                                      "true",
				"webModeler.enabled":                                    "true",
				"webModeler.restapi.mail.fromAddress":                   "example@example.com",
				"webModeler.restapi.sidecars[0].name":                   "nginx",
				"webModeler.restapi.sidecars[0].image":                  "nginx:latest",
				"webModeler.restapi.sidecars[0].ports[0].containerPort": "80",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(s.T(), output, &deployment)

				// then
				podContainers := deployment.Spec.Template.Spec.Containers
				expectedContainer := corev1.Container{
					Name:  "nginx",
					Image: "nginx:latest",
					Ports: []corev1.ContainerPort{
						{
							ContainerPort: 80,
						},
					},
				}

				s.Require().Contains(podContainers, expectedContainer)
			},
		}, {
			Name: "TestContainerSetInitContainer",
			Values: map[string]string{
				"identity.enabled":                                            "true",
				"webModeler.enabled":                                          "true",
				"webModeler.restapi.mail.fromAddress":                         "example@example.com",
				"webModeler.restapi.initContainers[0].name":                   "nginx",
				"webModeler.restapi.initContainers[0].image":                  "nginx:latest",
				"webModeler.restapi.initContainers[0].ports[0].containerPort": "80",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(s.T(), output, &deployment)

				// then
				podContainers := deployment.Spec.Template.Spec.InitContainers
				expectedContainer := corev1.Container{
					Name:  "nginx",
					Image: "nginx:latest",
					Ports: []corev1.ContainerPort{
						{
							ContainerPort: 80,
						},
					},
				}

				s.Require().Contains(podContainers, expectedContainer)
			},
		}, {
			Name: "TestSetDnsPolicyAndDnsConfig",
			Values: map[string]string{
				"identity.enabled":                            "true",
				"webModeler.enabled":                          "true",
				"webModeler.restapi.mail.fromAddress":         "example@example.com",
				"webModeler.restapi.dnsPolicy":                "ClusterFirst",
				"webModeler.restapi.dnsConfig.nameservers[0]": "8.8.8.8",
				"webModeler.restapi.dnsConfig.searches[0]":    "example.com",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(s.T(), output, &deployment)

				// then
				// Check if dnsPolicy is set
				require.NotEmpty(s.T(), deployment.Spec.Template.Spec.DNSPolicy, "dnsPolicy should not be empty")

				// Check if dnsConfig is set
				require.NotNil(s.T(), deployment.Spec.Template.Spec.DNSConfig, "dnsConfig should not be nil")

				expectedDNSConfig := &corev1.PodDNSConfig{
					Nameservers: []string{"8.8.8.8"},
					Searches:    []string{"example.com"},
				}

				require.Equal(s.T(), expectedDNSConfig, deployment.Spec.Template.Spec.DNSConfig, "dnsConfig should match the expected configuration")
			},
		},
	}

	testhelpers.RunTestCasesE(s.T(), s.chartPath, s.release, s.namespace, s.templates, testCases)
}
