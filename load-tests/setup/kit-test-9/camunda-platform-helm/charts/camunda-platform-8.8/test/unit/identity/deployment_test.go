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

package identity

import (
	"camunda-platform/test/unit/testhelpers"
	"path/filepath"
	"strings"
	"testing"

	"github.com/gruntwork-io/terratest/modules/helm"
	"github.com/gruntwork-io/terratest/modules/random"
	"github.com/stretchr/testify/require"
	"github.com/stretchr/testify/suite"
	appsv1 "k8s.io/api/apps/v1"
	corev1 "k8s.io/api/core/v1"
)

type deploymentTemplateTest struct {
	suite.Suite
	chartPath string
	release   string
	namespace string
	templates []string
}

func TestDeploymentTemplate(t *testing.T) {
	t.Parallel()

	chartPath, err := filepath.Abs("../../../")
	require.NoError(t, err)

	suite.Run(t, &deploymentTemplateTest{
		chartPath: chartPath,
		release:   "camunda-platform-test",
		namespace: "camunda-platform-" + strings.ToLower(random.UniqueId()),
		templates: []string{"templates/identity/deployment.yaml"},
	})
}

func (s *deploymentTemplateTest) TestDifferentValuesInputs() {
	testCases := []testhelpers.TestCase{
		{
			Skip:                 true,
			Name:                 "TestContainerWithExternalKeycloak",
			HelmOptionsExtraArgs: map[string][]string{"install": {"--debug"}},
			Values: map[string]string{
				"identity.enabled":                                "true",
				"identityKeycloak.enabled":                        "false",
				"global.identity.auth.enabled":                    "true",
				"global.identity.keycloak.url.protocol":           "https",
				"global.identity.keycloak.url.host":               "keycloak.prod.svc.cluster.local",
				"global.identity.keycloak.url.port":               "8443",
				"global.identity.keycloak.auth.adminUser":         "testAdmin",
				"global.identity.keycloak.auth.existingSecret":    "ownExistingSecretKeycloak",
				"global.identity.keycloak.auth.existingSecretKey": "test-admin",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(t, output, &deployment)

				// then
				env := deployment.Spec.Template.Spec.Containers[0].Env
				s.Require().Contains(env,
					corev1.EnvVar{
						Name: "VALUES_KEYCLOAK_SETUP_PASSWORD",
						ValueFrom: &corev1.EnvVarSource{
							SecretKeyRef: &corev1.SecretKeySelector{
								LocalObjectReference: corev1.LocalObjectReference{Name: "ownExistingSecretKeycloak"},
								Key:                  "test-admin",
							},
						},
					})
			},
		}, {
			Skip: true,
			Name: "TestContainerSetPodLabels",
			Values: map[string]string{
				"identity.enabled":       "true",
				"identity.podLabels.foo": "bar",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(t, output, &deployment)

				// then
				s.Require().Equal("bar", deployment.Spec.Template.Labels["foo"])
			},
		}, {
			Skip: true,
			Name: "TestContainerSetPodAnnotations",
			Values: map[string]string{
				"identity.enabled":            "true",
				"identity.podAnnotations.foo": "bar",
				"identity.podAnnotations.foz": "baz",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(t, output, &deployment)

				// then
				s.Require().Equal("bar", deployment.Spec.Template.Annotations["foo"])
				s.Require().Equal("baz", deployment.Spec.Template.Annotations["foz"])
			},
		}, {
			Skip: true,
			Name: "TestContainerSetGlobalAnnotations",
			Values: map[string]string{
				"global.annotations.foo": "bar",
				"identity.enabled":       "true",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(t, output, &deployment)

				// then
				s.Require().Equal("bar", deployment.ObjectMeta.Annotations["foo"])
			},
		}, {
			Skip: true,
			Name: "TestContainerSetImageNameSubChart",
			Values: map[string]string{
				"global.image.registry":     "global.custom.registry.io",
				"global.image.tag":          "8.x.x",
				"identity.enabled":          "true",
				"identity.image.registry":   "subchart.custom.registry.io",
				"identity.image.repository": "camunda/identity-test",
				"identity.image.tag":        "snapshot",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(t, output, &deployment)

				// then
				container := deployment.Spec.Template.Spec.Containers[0]
				s.Require().Equal(container.Image, "subchart.custom.registry.io/camunda/identity-test:snapshot")
			},
		}, {
			Skip: true,
			Name: "TestContainerSetImagePullSecretsGlobal",
			Values: map[string]string{
				"identity.enabled":                 "true",
				"global.image.pullSecrets[0].name": "SecretName",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(t, output, &deployment)

				// then
				s.Require().Equal("SecretName", deployment.Spec.Template.Spec.ImagePullSecrets[0].Name)
			},
		}, {
			Skip: true,
			Name: "TestContainerSetImagePullSecretsSubChart",
			Values: map[string]string{
				"global.image.pullSecrets[0].name":   "SecretName",
				"identity.enabled":                   "true",
				"identity.image.pullSecrets[0].name": "SecretNameSubChart",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(t, output, &deployment)

				// then
				s.Require().Equal("SecretNameSubChart", deployment.Spec.Template.Spec.ImagePullSecrets[0].Name)
			},
		}, {
			Skip: true,
			Name: "TestContainerOverwriteImageTag",
			Values: map[string]string{
				"identity.enabled":   "true",
				"identity.image.tag": "a.b.c",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(t, output, &deployment)

				// then
				expectedContainerImage := "camunda/identity:a.b.c"
				containers := deployment.Spec.Template.Spec.Containers
				s.Require().Equal(1, len(containers))
				s.Require().Equal(expectedContainerImage, containers[0].Image)
			},
		}, {
			Skip: true,
			Name: "TestContainerOverwriteGlobalImageTag",
			Values: map[string]string{
				"global.image.tag":   "a.b.c",
				"identity.enabled":   "true",
				"identity.image.tag": "",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(t, output, &deployment)

				// then
				expectedContainerImage := "camunda/identity:a.b.c"
				containers := deployment.Spec.Template.Spec.Containers
				s.Require().Equal(1, len(containers))
				s.Require().Equal(expectedContainerImage, containers[0].Image)
			},
		}, {
			Skip: true,
			Name: "TestContainerOverwriteImageTagWithChartDirectSetting",
			Values: map[string]string{
				"global.image.tag":   "x.y.z",
				"identity.enabled":   "true",
				"identity.image.tag": "a.b.c",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(t, output, &deployment)

				// then
				expectedContainerImage := "camunda/identity:a.b.c"
				containers := deployment.Spec.Template.Spec.Containers
				s.Require().Equal(1, len(containers))
				s.Require().Equal(expectedContainerImage, containers[0].Image)
			},
		}, {
			Skip: true,
			Name: "TestContainerSetContainerCommand",
			Values: map[string]string{
				"identity.enabled":    "true",
				"identity.command[0]": "printenv",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(t, output, &deployment)

				// then
				containers := deployment.Spec.Template.Spec.Containers
				s.Require().Equal(1, len(containers))
				s.Require().Equal(1, len(containers[0].Command))
				s.Require().Equal("printenv", containers[0].Command[0])
			},
		}, {
			Skip: true,
			Name: "TestContainerSetExtraVolumes",
			Values: map[string]string{
				"identity.enabled":                               "true",
				"identity.extraVolumes[0].name":                  "extraVolume",
				"identity.extraVolumes[0].configMap.name":        "otherConfigMap",
				"identity.extraVolumes[0].configMap.defaultMode": "744",
			},
			Verifier: func(t *testing.T, output string, err error) {
				// finding out the length of volumes array before addition of new volume
				var deploymentBefore appsv1.Deployment
				before := helm.RenderTemplate(s.T(), &helm.Options{}, s.chartPath, s.release, s.templates, "--set", "identity.enabled=true")
				helm.UnmarshalK8SYaml(s.T(), before, &deploymentBefore)
				volumeLenBefore := len(deploymentBefore.Spec.Template.Spec.Volumes)
				// given
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(t, output, &deployment)

				// then
				volumes := deployment.Spec.Template.Spec.Volumes
				s.Require().Equal(volumeLenBefore+1, len(volumes))

				extraVolume := volumes[volumeLenBefore]
				s.Require().Equal("extraVolume", extraVolume.Name)
				s.Require().NotNil(*extraVolume.ConfigMap)
				s.Require().Equal("otherConfigMap", extraVolume.ConfigMap.Name)
				s.Require().EqualValues(744, *extraVolume.ConfigMap.DefaultMode)
			},
		}, {
			Skip: true,
			Name: "TestContainerSetExtraVolumeMounts",
			Values: map[string]string{
				"identity.enabled":                        "true",
				"identity.extraVolumeMounts[0].name":      "otherConfigMap",
				"identity.extraVolumeMounts[0].mountPath": "/usr/local/config",
			},
			Verifier: func(t *testing.T, output string, err error) {
				// finding out the length of containers and volumeMounts array before addition of new volumeMount
				var deploymentBefore appsv1.Deployment
				before := helm.RenderTemplate(s.T(), &helm.Options{}, s.chartPath, s.release, s.templates, "--set", "identity.enabled=true")
				helm.UnmarshalK8SYaml(s.T(), before, &deploymentBefore)
				containerLenBefore := len(deploymentBefore.Spec.Template.Spec.Containers)
				volumeMountLenBefore := len(deploymentBefore.Spec.Template.Spec.Containers[0].VolumeMounts)
				// given
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(t, output, &deployment)

				// then
				containers := deployment.Spec.Template.Spec.Containers
				s.Require().Equal(containerLenBefore, len(containers))

				volumeMounts := deployment.Spec.Template.Spec.Containers[0].VolumeMounts
				s.Require().Equal(volumeMountLenBefore+1, len(volumeMounts))
				extraVolumeMount := volumeMounts[volumeMountLenBefore]
				s.Require().Equal("otherConfigMap", extraVolumeMount.Name)
				s.Require().Equal("/usr/local/config", extraVolumeMount.MountPath)
			},
		}, {
			Skip: true,
			Values: map[string]string{
				"identity.enabled":                               "true",
				"identity.extraVolumeMounts[0].name":             "otherConfigMap",
				"identity.extraVolumeMounts[0].mountPath":        "/usr/local/config",
				"identity.extraVolumes[0].name":                  "extraVolume",
				"identity.extraVolumes[0].configMap.name":        "otherConfigMap",
				"identity.extraVolumes[0].configMap.defaultMode": "744",
			},
			Verifier: func(t *testing.T, output string, err error) {
				// finding out the length of volumes, volumemounts array before addition of new volume
				var deploymentBefore appsv1.Deployment
				before := helm.RenderTemplate(s.T(), &helm.Options{}, s.chartPath, s.release, s.templates, "--set", "identity.enabled=true")
				helm.UnmarshalK8SYaml(s.T(), before, &deploymentBefore)
				volumeLenBefore := len(deploymentBefore.Spec.Template.Spec.Volumes)
				volumeMountLenBefore := len(deploymentBefore.Spec.Template.Spec.Containers[0].VolumeMounts)
				containerLenBefore := len(deploymentBefore.Spec.Template.Spec.Containers)
				// given
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(t, output, &deployment)

				// then
				volumes := deployment.Spec.Template.Spec.Volumes
				s.Require().Equal(volumeLenBefore+1, len(volumes))

				extraVolume := volumes[volumeLenBefore]
				s.Require().Equal("extraVolume", extraVolume.Name)
				s.Require().NotNil(*extraVolume.ConfigMap)
				s.Require().Equal("otherConfigMap", extraVolume.ConfigMap.Name)
				s.Require().EqualValues(744, *extraVolume.ConfigMap.DefaultMode)

				containers := deployment.Spec.Template.Spec.Containers
				s.Require().Equal(containerLenBefore, len(containers))

				volumeMounts := deployment.Spec.Template.Spec.Containers[0].VolumeMounts
				s.Require().Equal(volumeMountLenBefore+1, len(volumeMounts))
				extraVolumeMount := volumeMounts[volumeMountLenBefore]
				s.Require().Equal("otherConfigMap", extraVolumeMount.Name)
				s.Require().Equal("/usr/local/config", extraVolumeMount.MountPath)
			},
		}, {
			Skip: true,
			Name: "TestContainerSetServiceAccountName",
			Values: map[string]string{
				"identity.enabled":             "true",
				"identity.serviceAccount.name": "accName",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(t, output, &deployment)

				// then
				serviceAccName := deployment.Spec.Template.Spec.ServiceAccountName
				s.Require().Equal("accName", serviceAccName)
			},
		}, {
			Skip: true,
			Name: "TestPodSetSecurityContext",
			Values: map[string]string{
				"identity.enabled":                      "true",
				"identity.podSecurityContext.runAsUser": "1000",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(t, output, &deployment)

				// then
				securityContext := deployment.Spec.Template.Spec.SecurityContext
				s.Require().EqualValues(1000, *securityContext.RunAsUser)
			},
		}, {
			Skip: true,
			Name: "TestContainerSetSecurityContext",
			Values: map[string]string{
				"identity.enabled": "true",
				"identity.containerSecurityContext.privileged": "true",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(t, output, &deployment)

				// then
				securityContext := deployment.Spec.Template.Spec.Containers[0].SecurityContext
				s.Require().True(*securityContext.Privileged)
			},
		}, {
			// https://kubernetes.io/docs/concepts/scheduling-eviction/assign-pod-node/#nodeselector
			Skip: true,
			Name: "TestContainerSetNodeSelector",
			Values: map[string]string{
				"identity.enabled":               "true",
				"identity.nodeSelector.disktype": "ssd",
				"identity.nodeSelector.cputype":  "arm",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(t, output, &deployment)

				// then
				s.Require().Equal("ssd", deployment.Spec.Template.Spec.NodeSelector["disktype"])
				s.Require().Equal("arm", deployment.Spec.Template.Spec.NodeSelector["cputype"])
			},
		}, {
			// https://kubernetes.io/docs/concepts/scheduling-eviction/assign-pod-node/#node-affinity
			// affinity:
			//	nodeAffinity:
			//	 requiredDuringSchedulingIgnoredDuringExecution:
			//	   nodeSelectorTerms:
			//	   - matchExpressions:
			//		 - key: kubernetes.io/e2e-az-name
			//		   operator: In
			//		   values:
			//		   - e2e-az1
			//		   - e2e-az2
			//	 preferredDuringSchedulingIgnoredDuringExecution:
			//	 - weight: 1
			//	   preference:
			//		 matchExpressions:
			//		 - key: another-node-label-key
			//		   operator: In
			//		   values:
			//		   - another-node-label-value
			Skip: true,
			Name: "TestContainerSetAffinity",
			Values: map[string]string{
				"identity.enabled": "true",
				"identity.affinity.nodeAffinity.requiredDuringSchedulingIgnoredDuringExecution.nodeSelectorTerms[0].matchexpressions[0].key":       "kubernetes.io/e2e-az-name",
				"identity.affinity.nodeAffinity.requiredDuringSchedulingIgnoredDuringExecution.nodeSelectorTerms[0].matchexpressions[0].operator":  "In",
				"identity.affinity.nodeAffinity.requiredDuringSchedulingIgnoredDuringExecution.nodeSelectorTerms[0].matchexpressions[0].values[0]": "e2e-a1",
				"identity.affinity.nodeAffinity.requiredDuringSchedulingIgnoredDuringExecution.nodeSelectorTerms[0].matchexpressions[0].values[1]": "e2e-a2",
				"identity.affinity.nodeAffinity.preferredDuringSchedulingIgnoredDuringExecution[0].weight":                                         "1",
				"identity.affinity.nodeAffinity.preferredDuringSchedulingIgnoredDuringExecution[0].preference.matchExpressions[0].key":             "another-node-label-key",
				"identity.affinity.nodeAffinity.preferredDuringSchedulingIgnoredDuringExecution[0].preference.matchExpressions[0].operator":        "In",
				"identity.affinity.nodeAffinity.preferredDuringSchedulingIgnoredDuringExecution[0].preference.matchExpressions[0].values[0]":       "another-node-label-value",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(t, output, &deployment)

				// then
				nodeAffinity := deployment.Spec.Template.Spec.Affinity.NodeAffinity
				s.Require().NotNil(nodeAffinity)

				nodeSelectorTerm := nodeAffinity.RequiredDuringSchedulingIgnoredDuringExecution.NodeSelectorTerms[0]
				s.Require().NotNil(nodeSelectorTerm)
				matchExpression := nodeSelectorTerm.MatchExpressions[0]
				s.Require().NotNil(matchExpression)
				s.Require().Equal("kubernetes.io/e2e-az-name", matchExpression.Key)
				s.Require().EqualValues("In", matchExpression.Operator)
				s.Require().Equal([]string{"e2e-a1", "e2e-a2"}, matchExpression.Values)

				preferredSchedulingTerm := nodeAffinity.PreferredDuringSchedulingIgnoredDuringExecution[0]
				s.Require().NotNil(preferredSchedulingTerm)

				matchExpression = preferredSchedulingTerm.Preference.MatchExpressions[0]
				s.Require().NotNil(matchExpression)
				s.Require().Equal("another-node-label-key", matchExpression.Key)
				s.Require().EqualValues("In", matchExpression.Operator)
				s.Require().Equal([]string{"another-node-label-value"}, matchExpression.Values)
			},
		}, {
			// https://kubernetes.io/docs/concepts/scheduling-eviction/taint-and-toleration
			//tolerations:
			//- key: "key1"
			//  operator: "Equal"
			//  value: "value1"
			//  effect: "NoSchedule"
			Skip: true,
			Name: "TestContainerSetTolerations",
			Values: map[string]string{
				"identity.enabled":                 "true",
				"identity.tolerations[0].key":      "key1",
				"identity.tolerations[0].operator": "Equal",
				"identity.tolerations[0].value":    "Value1",
				"identity.tolerations[0].effect":   "NoSchedule",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(t, output, &deployment)

				// then
				tolerations := deployment.Spec.Template.Spec.Tolerations
				s.Require().Equal(1, len(tolerations))

				toleration := tolerations[0]
				s.Require().Equal("key1", toleration.Key)
				s.Require().EqualValues("Equal", toleration.Operator)
				s.Require().Equal("Value1", toleration.Value)
				s.Require().EqualValues("NoSchedule", toleration.Effect)
			},
		}, {
			Skip:                 true,
			Name:                 "TestContainerShouldSetTemplateEnvVars",
			HelmOptionsExtraArgs: map[string][]string{"install": {"--debug"}},
			Values: map[string]string{
				"identity.enabled":      "true",
				"identity.env[0].name":  "RELEASE_NAME",
				"identity.env[0].value": "test-{{ .Release.Name }}",
				"identity.env[1].name":  "OTHER_ENV",
				"identity.env[1].value": "nothingToSeeHere",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(t, output, &deployment)

				// then
				env := deployment.Spec.Template.Spec.Containers[0].Env
				s.Require().Contains(env, corev1.EnvVar{Name: "RELEASE_NAME", Value: "test-camunda-platform-test"})
				s.Require().Contains(env, corev1.EnvVar{Name: "OTHER_ENV", Value: "nothingToSeeHere"})
			},
		}, {
			Skip:                 true,
			Name:                 "TestContainerShouldSetCorrectSecret",
			HelmOptionsExtraArgs: map[string][]string{"install": {"--debug"}},
			Values: map[string]string{
				"global.identity.auth.enabled":            "true",
				"identity.enabled":                        "true",
				"identityKeycloak.enabled":                "true",
				"identityKeycloak.auth.existingSecret":    "ownExistingSecret",
				"identityKeycloak.auth.passwordSecretKey": "test-admin",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(t, output, &deployment)

				// then
				env := deployment.Spec.Template.Spec.Containers[0].Env
				s.Require().Contains(env,
					corev1.EnvVar{
						Name: "VALUES_KEYCLOAK_SETUP_PASSWORD",
						ValueFrom: &corev1.EnvVarSource{
							SecretKeyRef: &corev1.SecretKeySelector{
								LocalObjectReference: corev1.LocalObjectReference{Name: "ownExistingSecret"},
								Key:                  "test-admin",
							},
						},
					})
			},
		}, {
			Skip:                 true,
			Name:                 "TestContainerShouldSetOptimizeIdentitySecretValue",
			HelmOptionsExtraArgs: map[string][]string{"install": {"--debug"}},
			Values: map[string]string{
				"global.identity.auth.enabled":                 "true",
				"global.identity.auth.optimize.existingSecret": "secretValue",
				"identity.enabled":                             "true",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(t, output, &deployment)

				// then
				env := deployment.Spec.Template.Spec.Containers[0].Env
				s.Require().Contains(env,
					corev1.EnvVar{
						Name: "VALUES_KEYCLOAK_INIT_OPTIMIZE_SECRET",
						ValueFrom: &corev1.EnvVarSource{
							SecretKeyRef: &corev1.SecretKeySelector{
								LocalObjectReference: corev1.LocalObjectReference{Name: "camunda-platform-test-optimize-identity-secret"},
								Key:                  "identity-optimize-client-token",
							},
						},
					})
			},
		}, {
			Skip:                 true,
			Name:                 "TestContainerShouldSetOptimizeIdentitySecretViaReference",
			HelmOptionsExtraArgs: map[string][]string{"install": {"--debug"}},
			Values: map[string]string{
				"identity.enabled":                                  "true",
				"global.identity.auth.enabled":                      "true",
				"global.identity.auth.optimize.existingSecret.name": "ownExistingSecret",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(t, output, &deployment)

				// then
				env := deployment.Spec.Template.Spec.Containers[0].Env
				s.Require().Contains(env,
					corev1.EnvVar{
						Name: "VALUES_KEYCLOAK_INIT_OPTIMIZE_SECRET",
						ValueFrom: &corev1.EnvVarSource{
							SecretKeyRef: &corev1.SecretKeySelector{
								LocalObjectReference: corev1.LocalObjectReference{Name: "ownExistingSecret"},
								Key:                  "identity-optimize-client-token",
							},
						},
					})
			},
		}, {
			Skip: true,
			Name: "TestContainerShouldOverwriteGlobalImagePullPolicy",
			Values: map[string]string{
				"global.image.pullPolicy": "Always",
				"identity.enabled":        "true",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(t, output, &deployment)

				// then
				expectedPullPolicy := corev1.PullAlways
				containers := deployment.Spec.Template.Spec.Containers
				s.Require().Equal(1, len(containers))
				pullPolicy := containers[0].ImagePullPolicy
				s.Require().Equal(expectedPullPolicy, pullPolicy)
			},
		}, {
			Skip: true,
			// readinessProbe is enabled by default so it's tested by golden files.
			Name:                 "TestContainerStartupProbe",
			HelmOptionsExtraArgs: map[string][]string{"install": {"--debug"}},
			Values: map[string]string{
				"identity.enabled":                          "true",
				"identity.startupProbe.enabled":             "true",
				"identity.startupProbe.probePath":           "/healthz",
				"identity.startupProbe.initialDelaySeconds": "5",
				"identity.startupProbe.periodSeconds":       "10",
				"identity.startupProbe.successThreshold":    "1",
				"identity.startupProbe.failureThreshold":    "5",
				"identity.startupProbe.timeoutSeconds":      "1",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(t, output, &deployment)

				// then
				probe := deployment.Spec.Template.Spec.Containers[0].StartupProbe

				s.Require().Equal("/healthz", probe.HTTPGet.Path)
				s.Require().EqualValues(5, probe.InitialDelaySeconds)
				s.Require().EqualValues(10, probe.PeriodSeconds)
				s.Require().EqualValues(1, probe.SuccessThreshold)
				s.Require().EqualValues(5, probe.FailureThreshold)
				s.Require().EqualValues(1, probe.TimeoutSeconds)
			},
		}, {
			Skip:                 true,
			Name:                 "TestContainerLivenessProbe",
			HelmOptionsExtraArgs: map[string][]string{"install": {"--debug"}},
			Values: map[string]string{
				"identity.enabled":                           "true",
				"identity.livenessProbe.enabled":             "true",
				"identity.livenessProbe.probePath":           "/healthz",
				"identity.livenessProbe.initialDelaySeconds": "5",
				"identity.livenessProbe.periodSeconds":       "10",
				"identity.livenessProbe.successThreshold":    "1",
				"identity.livenessProbe.failureThreshold":    "5",
				"identity.livenessProbe.timeoutSeconds":      "1",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(t, output, &deployment)

				// then
				probe := deployment.Spec.Template.Spec.Containers[0].LivenessProbe

				s.Require().EqualValues("/healthz", probe.HTTPGet.Path)
				s.Require().EqualValues(5, probe.InitialDelaySeconds)
				s.Require().EqualValues(10, probe.PeriodSeconds)
				s.Require().EqualValues(1, probe.SuccessThreshold)
				s.Require().EqualValues(5, probe.FailureThreshold)
				s.Require().EqualValues(1, probe.TimeoutSeconds)
			},
		}, {
			// Identity doesn't support contextPath for health endpoints.
			Skip:                 true,
			Name:                 "TestContainerProbesWithContextPath",
			HelmOptionsExtraArgs: map[string][]string{"install": {"--debug"}},
			Values: map[string]string{
				"identity.enabled":                  "true",
				"identity.contextPath":              "/test",
				"identity.startupProbe.enabled":     "true",
				"identity.startupProbe.probePath":   "/start",
				"identity.readinessProbe.enabled":   "true",
				"identity.readinessProbe.probePath": "/ready",
				"identity.livenessProbe.enabled":    "true",
				"identity.livenessProbe.probePath":  "/live",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(t, output, &deployment)

				// then
				probe := deployment.Spec.Template.Spec.Containers[0]

				s.Require().Equal("/start", probe.StartupProbe.HTTPGet.Path)
				s.Require().Equal("/ready", probe.ReadinessProbe.HTTPGet.Path)
				s.Require().Equal("/live", probe.LivenessProbe.HTTPGet.Path)
			},
		}, {
			Skip: true,
			Name: "TestContainerSetSidecar",
			Values: map[string]string{
				"identity.enabled":                            "true",
				"identity.sidecars[0].name":                   "nginx",
				"identity.sidecars[0].image":                  "nginx:latest",
				"identity.sidecars[0].ports[0].containerPort": "80",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(t, output, &deployment)

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
			Skip: true,
			Name: "TestInitContainers",
			Values: map[string]string{
				"identity.enabled":                                  "true",
				"identity.initContainers[0].name":                   "nginx",
				"identity.initContainers[0].image":                  "nginx:latest",
				"identity.initContainers[0].ports[0].containerPort": "80",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(t, output, &deployment)

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
			Skip:                 true,
			Name:                 "TestContainerShouldSetFirstUserExistingSecretValue",
			HelmOptionsExtraArgs: map[string][]string{"install": {"--debug"}},
			Values: map[string]string{
				"global.identity.auth.enabled":      "true",
				"identity.enabled":                  "true",
				"identity.firstUser.enabled":        "true",
				"identity.firstUser.existingSecret": "identityFirstUserSecret",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(t, output, &deployment)

				// then
				env := deployment.Spec.Template.Spec.Containers[0].Env
				s.Require().Contains(env,
					corev1.EnvVar{
						Name: "VALUES_IDENTITY_FIRSTUSER_PASSWORD",
						ValueFrom: &corev1.EnvVarSource{
							SecretKeyRef: &corev1.SecretKeySelector{
								LocalObjectReference: corev1.LocalObjectReference{Name: "identityFirstUserSecret"},
								Key:                  "identity-firstuser-password",
							},
						},
					})
			},
		}, {
			Skip: true,
			Name: "TestContainerShouldSetExternalDatabaseExistingSecret",
			Values: map[string]string{
				"identity.enabled":                                    "true",
				"identityPostgresql.enabled":                          "false",
				"identity.externalDatabase.enabled":                   "true",
				"identity.externalDatabase.existingSecret":            "postgres-secret-ext",
				"identity.externalDatabase.existingSecretPasswordKey": "identity-password-ext",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(t, output, &deployment)

				// then
				env := deployment.Spec.Template.Spec.Containers[0].Env
				s.Require().Contains(env,
					corev1.EnvVar{
						Name: "IDENTITY_DATABASE_PASSWORD",
						ValueFrom: &corev1.EnvVarSource{
							SecretKeyRef: &corev1.SecretKeySelector{
								LocalObjectReference: corev1.LocalObjectReference{Name: "postgres-secret-ext"},
								Key:                  "identity-password-ext",
							},
						},
					})
			},
		}, {
			Skip: true,
			Name: "TestSetDnsPolicyAndDnsConfig",
			Values: map[string]string{
				"identity.enabled":                  "true",
				"identity.dnsPolicy":                "ClusterFirst",
				"identity.dnsConfig.nameservers[0]": "8.8.8.8",
				"identity.dnsConfig.searches[0]":    "example.com",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(t, output, &deployment)

				// then
				// Check if dnsPolicy is set
				require.NotEmpty(t, deployment.Spec.Template.Spec.DNSPolicy, "dnsPolicy should not be empty")

				// Check if dnsConfig is set
				require.NotNil(t, deployment.Spec.Template.Spec.DNSConfig, "dnsConfig should not be nil")

				expectedDNSConfig := &corev1.PodDNSConfig{
					Nameservers: []string{"8.8.8.8"},
					Searches:    []string{"example.com"},
				}

				require.Equal(t, expectedDNSConfig, deployment.Spec.Template.Spec.DNSConfig, "dnsConfig should match the expected configuration")
			},
		}, {
			Name: "TestCustomUserInlineSecret",
			Values: map[string]string{
				"identity.enabled":                      "true",
				"identity.users[0].secret.inlineSecret": "secretjeff",
				"identity.users[0].username":            "jeff",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(t, output, &deployment)

				// then
				env := deployment.Spec.Template.Spec.Containers[0].Env
				s.Require().Contains(env,
					corev1.EnvVar{
						Name:  "VALUES_JEFF_USER_PASSWORD",
						Value: "secretjeff",
					})
			},
		}, {
			Name: "TestCustomUserExistingSecret",
			Values: map[string]string{
				"identity.enabled":                           "true",
				"identity.users[0].secret.existingSecret":    "jeff-k8s-secret",
				"identity.users[0].secret.existingSecretKey": "password",
				"identity.users[0].username":                 "jeff",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(t, output, &deployment)

				// then
				env := deployment.Spec.Template.Spec.Containers[0].Env
				s.Require().Contains(env,
					corev1.EnvVar{
						Name: "VALUES_JEFF_USER_PASSWORD",
						ValueFrom: &corev1.EnvVarSource{
							SecretKeyRef: &corev1.SecretKeySelector{
								LocalObjectReference: corev1.LocalObjectReference{Name: "jeff-k8s-secret"},
								Key:                  "password",
							},
						},
					})
			},
		},
		// Hybrid Auth Tests - verify OIDC client secrets are only included for components using OIDC auth
		{
			// Test: When both connectors and orchestration use basic auth, no OIDC secrets should be present
			Name:                 "TestBasicAuthExcludesOidcSecrets",
			HelmOptionsExtraArgs: map[string][]string{"install": {"--debug"}},
			Values: map[string]string{
				"identity.enabled":                               "true",
				"global.identity.auth.enabled":                   "true",
				"connectors.security.authentication.method":      "basic",
				"orchestration.security.authentication.method":   "basic",
				"connectors.enabled":                             "true",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(t, output, &deployment)

				// then - verify neither OIDC secret is present
				env := deployment.Spec.Template.Spec.Containers[0].Env
				for _, envVar := range env {
					s.Require().NotEqual("VALUES_KEYCLOAK_INIT_CONNECTORS_SECRET", envVar.Name,
						"VALUES_KEYCLOAK_INIT_CONNECTORS_SECRET should not be present when connectors use basic auth")
					s.Require().NotEqual("VALUES_KEYCLOAK_INIT_ORCHESTRATION_SECRET", envVar.Name,
						"VALUES_KEYCLOAK_INIT_ORCHESTRATION_SECRET should not be present when orchestration uses basic auth")
				}
			},
		}, {
			// Test: When using global OIDC (default non-hybrid), both secrets should be present (backwards compatibility)
			Name:                 "TestGlobalOidcAuthIncludesBothOidcSecrets",
			HelmOptionsExtraArgs: map[string][]string{"install": {"--debug"}},
			Values: map[string]string{
				"identity.enabled":                                                 "true",
				"global.identity.auth.enabled":                                     "true",
				"global.security.authentication.method":                            "oidc",
				"connectors.security.authentication.oidc.existingSecret.name":      "connectors-oidc-secret",
				"orchestration.security.authentication.oidc.existingSecret.name":   "orchestration-oidc-secret",
				"connectors.enabled":                                               "true",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(t, output, &deployment)

				// then - verify both OIDC secrets ARE present (inherited from global)
				env := deployment.Spec.Template.Spec.Containers[0].Env
				s.Require().Contains(env,
					corev1.EnvVar{
						Name: "VALUES_KEYCLOAK_INIT_CONNECTORS_SECRET",
						ValueFrom: &corev1.EnvVarSource{
							SecretKeyRef: &corev1.SecretKeySelector{
								LocalObjectReference: corev1.LocalObjectReference{Name: "connectors-oidc-secret"},
								Key:                  "identity-connectors-client-token",
							},
						},
					},
					"Connectors OIDC secret should be present when global auth method is OIDC")
				s.Require().Contains(env,
					corev1.EnvVar{
						Name: "VALUES_KEYCLOAK_INIT_ORCHESTRATION_SECRET",
						ValueFrom: &corev1.EnvVarSource{
							SecretKeyRef: &corev1.SecretKeySelector{
								LocalObjectReference: corev1.LocalObjectReference{Name: "orchestration-oidc-secret"},
								Key:                  "identity-orchestration-client-token",
							},
						},
					},
					"Orchestration OIDC secret should be present when global auth method is OIDC")
			},
		}, {
			// Test: Hybrid auth - connectors basic, orchestration OIDC
			Name:                 "TestHybridAuthConnectorsBasicOrchestrationOidc",
			HelmOptionsExtraArgs: map[string][]string{"install": {"--debug"}},
			Values: map[string]string{
				"identity.enabled":                                                 "true",
				"global.identity.auth.enabled":                                     "true",
				"connectors.security.authentication.method":                        "basic",
				"orchestration.security.authentication.method":                     "oidc",
				"orchestration.security.authentication.oidc.existingSecret.name":   "orchestration-oidc-secret",
				"connectors.enabled":                                               "true",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(t, output, &deployment)

				env := deployment.Spec.Template.Spec.Containers[0].Env
				// Connectors secret should NOT be present
				for _, envVar := range env {
					s.Require().NotEqual("VALUES_KEYCLOAK_INIT_CONNECTORS_SECRET", envVar.Name,
						"Connectors OIDC secret should not be present when connectors use basic auth")
				}
				// Orchestration secret SHOULD be present
				s.Require().Contains(env,
					corev1.EnvVar{
						Name: "VALUES_KEYCLOAK_INIT_ORCHESTRATION_SECRET",
						ValueFrom: &corev1.EnvVarSource{
							SecretKeyRef: &corev1.SecretKeySelector{
								LocalObjectReference: corev1.LocalObjectReference{Name: "orchestration-oidc-secret"},
								Key:                  "identity-orchestration-client-token",
							},
						},
					},
					"Orchestration OIDC secret should be present when orchestration uses OIDC auth")
			},
		}, {
			// Test: Connectors disabled with global OIDC should NOT include connectors secret env var
			Name:                 "TestConnectorsDisabledExcludesOidcSecretEnvVar",
			HelmOptionsExtraArgs: map[string][]string{"install": {"--debug"}},
			Values: map[string]string{
				"identity.enabled":                                                 "true",
				"global.identity.auth.enabled":                                     "true",
				"global.security.authentication.method":                            "oidc",
				"connectors.enabled":                                               "false",
				"orchestration.security.authentication.oidc.existingSecret.name":   "orchestration-oidc-secret",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(t, output, &deployment)

				env := deployment.Spec.Template.Spec.Containers[0].Env
				// Connectors secret should NOT be present when connectors is disabled
				for _, envVar := range env {
					s.Require().NotEqual("VALUES_KEYCLOAK_INIT_CONNECTORS_SECRET", envVar.Name,
						"Connectors OIDC secret should not be present when connectors.enabled=false")
				}
				// Orchestration secret SHOULD still be present
				s.Require().Contains(env,
					corev1.EnvVar{
						Name: "VALUES_KEYCLOAK_INIT_ORCHESTRATION_SECRET",
						ValueFrom: &corev1.EnvVarSource{
							SecretKeyRef: &corev1.SecretKeySelector{
								LocalObjectReference: corev1.LocalObjectReference{Name: "orchestration-oidc-secret"},
								Key:                  "identity-orchestration-client-token",
							},
						},
					},
					"Orchestration OIDC secret should be present when orchestration is enabled")
			},
		}, {
			// Test: Orchestration disabled with global OIDC should NOT include orchestration secret env var
			Name:                 "TestOrchestrationDisabledExcludesOidcSecretEnvVar",
			HelmOptionsExtraArgs: map[string][]string{"install": {"--debug"}},
			Values: map[string]string{
				"identity.enabled":                                              "true",
				"global.identity.auth.enabled":                                  "true",
				"global.security.authentication.method":                         "oidc",
				"orchestration.enabled":                                         "false",
				"connectors.enabled":                                            "true",
				"connectors.security.authentication.oidc.existingSecret.name":   "connectors-oidc-secret",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(t, output, &deployment)

				env := deployment.Spec.Template.Spec.Containers[0].Env
				// Orchestration secret should NOT be present when orchestration is disabled
				for _, envVar := range env {
					s.Require().NotEqual("VALUES_KEYCLOAK_INIT_ORCHESTRATION_SECRET", envVar.Name,
						"Orchestration OIDC secret should not be present when orchestration.enabled=false")
				}
				// Connectors secret SHOULD still be present
				s.Require().Contains(env,
					corev1.EnvVar{
						Name: "VALUES_KEYCLOAK_INIT_CONNECTORS_SECRET",
						ValueFrom: &corev1.EnvVarSource{
							SecretKeyRef: &corev1.SecretKeySelector{
								LocalObjectReference: corev1.LocalObjectReference{Name: "connectors-oidc-secret"},
								Key:                  "identity-connectors-client-token",
							},
						},
					},
					"Connectors OIDC secret should be present when connectors is enabled")
			},
		},
	}

	testhelpers.RunTestCasesE(s.T(), s.chartPath, s.release, s.namespace, s.templates, testCases)
}
