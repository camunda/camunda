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

type DeploymentTemplateTest struct {
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

	suite.Run(t, &DeploymentTemplateTest{
		chartPath: chartPath,
		release:   "camunda-platform-test",
		namespace: "camunda-platform-" + strings.ToLower(random.UniqueId()),
		templates: []string{"templates/identity/deployment.yaml"},
	})
}

func (s *DeploymentTemplateTest) TestDifferentValuesInputs() {
	testCases := []testhelpers.TestCase{
		{
			Name:                 "TestContainerWithExternalKeycloak",
			HelmOptionsExtraArgs: map[string][]string{"install": {"--debug"}},
			Values: map[string]string{
				"identityKeycloak.enabled":                        "false",
				"global.identity.keycloak.url.protocol":           "https",
				"global.identity.keycloak.url.host":               "keycloak.prod.svc.cluster.local",
				"global.identity.keycloak.url.port":               "8443",
				"global.identity.keycloak.auth.adminUser":         "testAdmin",
				"global.identity.keycloak.auth.existingSecret":    "ownExistingSecretKeycloak",
				"global.identity.keycloak.auth.existingSecretKey": "test-admin",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(s.T(), output, &deployment)

				// then
				env := deployment.Spec.Template.Spec.Containers[0].Env
				s.Require().Contains(env,
					corev1.EnvVar{
						Name:  "KEYCLOAK_URL",
						Value: "https://keycloak.prod.svc.cluster.local:8443/auth",
					})
				s.Require().Contains(env,
					corev1.EnvVar{
						Name:  "KEYCLOAK_SETUP_USER",
						Value: "testAdmin",
					})
				s.Require().Contains(env,
					corev1.EnvVar{
						Name: "KEYCLOAK_SETUP_PASSWORD",
						ValueFrom: &corev1.EnvVarSource{
							SecretKeyRef: &corev1.SecretKeySelector{
								LocalObjectReference: corev1.LocalObjectReference{Name: "ownExistingSecretKeycloak"},
								Key:                  "test-admin",
							},
						},
					})
			},
		}, {
			Name: "TestContainerSetPodLabels",
			Values: map[string]string{
				"identity.podLabels.foo": "bar",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(s.T(), output, &deployment)

				// then
				s.Require().Equal("bar", deployment.Spec.Template.Labels["foo"])
			},
		}, {
			Name: "TestContainerSetPodAnnotations",
			Values: map[string]string{
				"identity.podAnnotations.foo": "bar",
				"identity.podAnnotations.foz": "baz",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(s.T(), output, &deployment)

				// then
				s.Require().Equal("bar", deployment.Spec.Template.Annotations["foo"])
				s.Require().Equal("baz", deployment.Spec.Template.Annotations["foz"])
			},
		}, {
			Name: "TestContainerSetGlobalAnnotations",
			Values: map[string]string{
				"global.annotations.foo": "bar",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(s.T(), output, &deployment)

				// then
				s.Require().Equal("bar", deployment.ObjectMeta.Annotations["foo"])
			},
		}, {
			Name: "TestContainerSetImageNameSubChart",
			Values: map[string]string{
				"global.image.registry":     "global.custom.registry.io",
				"global.image.tag":          "8.x.x",
				"identity.image.registry":   "subchart.custom.registry.io",
				"identity.image.repository": "camunda/identity-test",
				"identity.image.tag":        "snapshot",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(s.T(), output, &deployment)

				// then
				container := deployment.Spec.Template.Spec.Containers[0]
				s.Require().Equal(container.Image, "subchart.custom.registry.io/camunda/identity-test:snapshot")
			},
		}, {
			Name: "TestContainerSetImagePullSecretsGlobal",
			Values: map[string]string{
				"global.image.pullSecrets[0].name": "SecretName",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(s.T(), output, &deployment)

				// then
				s.Require().Equal("SecretName", deployment.Spec.Template.Spec.ImagePullSecrets[0].Name)
			},
		}, {
			Name: "TestContainerSetImagePullSecretsSubChart",
			Values: map[string]string{
				"global.image.pullSecrets[0].name":   "SecretName",
				"identity.image.pullSecrets[0].name": "SecretNameSubChart",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(s.T(), output, &deployment)

				// then
				s.Require().Equal("SecretNameSubChart", deployment.Spec.Template.Spec.ImagePullSecrets[0].Name)
			},
		}, {
			Name: "TestContainerOverwriteImageTag",
			Values: map[string]string{
				"identity.image.tag": "a.b.c",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(s.T(), output, &deployment)

				// then
				expectedContainerImage := "camunda/identity:a.b.c"
				containers := deployment.Spec.Template.Spec.Containers
				s.Require().Equal(1, len(containers))
				s.Require().Equal(expectedContainerImage, containers[0].Image)
			},
		}, {
			Name: "TestContainerOverwriteGlobalImageTag",
			Values: map[string]string{
				"global.image.tag":   "a.b.c",
				"identity.image.tag": "",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(s.T(), output, &deployment)

				// then
				expectedContainerImage := "camunda/identity:a.b.c"
				containers := deployment.Spec.Template.Spec.Containers
				s.Require().Equal(1, len(containers))
				s.Require().Equal(expectedContainerImage, containers[0].Image)
			},
		}, {
			Name: "TestContainerOverwriteImageTagWithChartDirectSetting",
			Values: map[string]string{
				"global.image.tag":   "x.y.z",
				"identity.image.tag": "a.b.c",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(s.T(), output, &deployment)

				// then
				expectedContainerImage := "camunda/identity:a.b.c"
				containers := deployment.Spec.Template.Spec.Containers
				s.Require().Equal(1, len(containers))
				s.Require().Equal(expectedContainerImage, containers[0].Image)
			},
		}, {
			Name: "TestContainerSetContainerCommand",
			Values: map[string]string{
				"identity.command[0]": "printenv",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(s.T(), output, &deployment)

				// then
				containers := deployment.Spec.Template.Spec.Containers
				s.Require().Equal(1, len(containers))
				s.Require().Equal(1, len(containers[0].Command))
				s.Require().Equal("printenv", containers[0].Command[0])
			},
		}, {
			Name: "TestContainerSetExtraVolumes",
			Values: map[string]string{
				"identity.extraVolumes[0].name":                  "extraVolume",
				"identity.extraVolumes[0].configMap.name":        "otherConfigMap",
				"identity.extraVolumes[0].configMap.defaultMode": "744",
			},
			Verifier: func(t *testing.T, output string, err error) {
				// finding out the length of volumes array before addition of new volume
				var deploymentBefore appsv1.Deployment
				before := helm.RenderTemplate(s.T(), &helm.Options{}, s.chartPath, s.release, s.templates)
				helm.UnmarshalK8SYaml(s.T(), before, &deploymentBefore)
				volumeLenBefore := len(deploymentBefore.Spec.Template.Spec.Volumes)
				// given
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(s.T(), output, &deployment)

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
			Name: "TestContainerSetExtraVolumeMounts",
			Values: map[string]string{
				"identity.extraVolumeMounts[0].name":      "otherConfigMap",
				"identity.extraVolumeMounts[0].mountPath": "/usr/local/config",
			},
			Verifier: func(t *testing.T, output string, err error) {
				// finding out the length of containers and volumeMounts array before addition of new volumeMount
				var deploymentBefore appsv1.Deployment
				before := helm.RenderTemplate(s.T(), &helm.Options{}, s.chartPath, s.release, s.templates)
				helm.UnmarshalK8SYaml(s.T(), before, &deploymentBefore)
				containerLenBefore := len(deploymentBefore.Spec.Template.Spec.Containers)
				volumeMountLenBefore := len(deploymentBefore.Spec.Template.Spec.Containers[0].VolumeMounts)
				// given
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(s.T(), output, &deployment)

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
			Name: "TestContainerSetExtraVolumesAndMounts",
			Values: map[string]string{
				"identity.extraVolumeMounts[0].name":             "otherConfigMap",
				"identity.extraVolumeMounts[0].mountPath":        "/usr/local/config",
				"identity.extraVolumes[0].name":                  "extraVolume",
				"identity.extraVolumes[0].configMap.name":        "otherConfigMap",
				"identity.extraVolumes[0].configMap.defaultMode": "744",
			},
			Verifier: func(t *testing.T, output string, err error) {
				// finding out the length of volumes, volumemounts array before addition of new volume
				var deploymentBefore appsv1.Deployment
				before := helm.RenderTemplate(s.T(), &helm.Options{}, s.chartPath, s.release, s.templates)
				helm.UnmarshalK8SYaml(s.T(), before, &deploymentBefore)
				volumeLenBefore := len(deploymentBefore.Spec.Template.Spec.Volumes)
				volumeMountLenBefore := len(deploymentBefore.Spec.Template.Spec.Containers[0].VolumeMounts)
				containerLenBefore := len(deploymentBefore.Spec.Template.Spec.Containers)
				// given
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(s.T(), output, &deployment)

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
			Name: "TestContainerSetServiceAccountName",
			Values: map[string]string{
				"identity.serviceAccount.name": "accName",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(s.T(), output, &deployment)

				// then
				serviceAccName := deployment.Spec.Template.Spec.ServiceAccountName
				s.Require().Equal("accName", serviceAccName)
			},
		}, {
			Name: "TestPodSetSecurityContext",
			Values: map[string]string{
				"identity.podSecurityContext.runAsUser": "1000",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(s.T(), output, &deployment)

				// then
				securityContext := deployment.Spec.Template.Spec.SecurityContext
				s.Require().EqualValues(1000, *securityContext.RunAsUser)
			},
		}, {
			Name: "TestContainerSetSecurityContext",
			Values: map[string]string{
				"identity.containerSecurityContext.privileged":          "true",
				"identity.containerSecurityContext.capabilities.add[0]": "NET_ADMIN",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(s.T(), output, &deployment)

				// then
				securityContext := deployment.Spec.Template.Spec.Containers[0].SecurityContext
				s.Require().True(*securityContext.Privileged)
				s.Require().EqualValues("NET_ADMIN", securityContext.Capabilities.Add[0])
			},
		}, {
			// https://kubernetes.io/docs/concepts/scheduling-eviction/assign-pod-node/#nodeselector
			Name: "TestContainerSetNodeSelector",
			Values: map[string]string{
				"identity.nodeSelector.disktype": "ssd",
				"identity.nodeSelector.cputype":  "arm",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(s.T(), output, &deployment)

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
			Name: "TestContainerSetAffinity",
			Values: map[string]string{
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
				helm.UnmarshalK8SYaml(s.T(), output, &deployment)

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
			Name: "TestContainerSetTolerations",
			Values: map[string]string{
				"identity.tolerations[0].key":      "key1",
				"identity.tolerations[0].operator": "Equal",
				"identity.tolerations[0].value":    "Value1",
				"identity.tolerations[0].effect":   "NoSchedule",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(s.T(), output, &deployment)

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
			Name:                 "TestContainerShouldSetTemplateEnvVars",
			HelmOptionsExtraArgs: map[string][]string{"install": {"--debug"}},
			Values: map[string]string{
				"identity.env[0].name":  "RELEASE_NAME",
				"identity.env[0].value": "test-{{ .Release.Name }}",
				"identity.env[1].name":  "OTHER_ENV",
				"identity.env[1].value": "nothingToSeeHere",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(s.T(), output, &deployment)

				// then
				env := deployment.Spec.Template.Spec.Containers[0].Env
				s.Require().Contains(env, corev1.EnvVar{Name: "RELEASE_NAME", Value: "test-camunda-platform-test"})
				s.Require().Contains(env, corev1.EnvVar{Name: "OTHER_ENV", Value: "nothingToSeeHere"})
			},
		}, {
			Name:                 "TestContainerShouldSetCorrectSecret",
			HelmOptionsExtraArgs: map[string][]string{"install": {"--debug"}},
			Values: map[string]string{
				"identityKeycloak.enabled":                "true",
				"identityKeycloak.auth.existingSecret":    "ownExistingSecret",
				"identityKeycloak.auth.passwordSecretKey": "test-admin",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(s.T(), output, &deployment)

				// then
				env := deployment.Spec.Template.Spec.Containers[0].Env
				s.Require().Contains(env,
					corev1.EnvVar{
						Name: "KEYCLOAK_SETUP_PASSWORD",
						ValueFrom: &corev1.EnvVarSource{
							SecretKeyRef: &corev1.SecretKeySelector{
								LocalObjectReference: corev1.LocalObjectReference{Name: "ownExistingSecret"},
								Key:                  "test-admin",
							},
						},
					})
			},
		}, {
			Name:                 "TestContainerShouldDisableIntegration",
			HelmOptionsExtraArgs: map[string][]string{"install": {"--debug"}},
			Values: map[string]string{
				"global.identity.auth.enabled": "false",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(s.T(), output, &deployment)

				// then
				env := deployment.Spec.Template.Spec.Containers[0].Env

				for _, envvar := range env {
					s.Require().NotEqual("KEYCLOAK_INIT_OPERATE_ROOT_URL", envvar.Name)
					s.Require().NotEqual("KEYCLOAK_INIT_OPERATE_SECRET", envvar.Name)
					s.Require().NotEqual("KEYCLOAK_INIT_TASKLIST_ROOT_URL", envvar.Name)
					s.Require().NotEqual("KEYCLOAK_INIT_TASKLIST_SECRET", envvar.Name)
				}
			},
		}, {
			Name:                 "TestContainerShouldSetOperateIdentitySecretValue",
			HelmOptionsExtraArgs: map[string][]string{"install": {"--debug"}},
			Values: map[string]string{
				"global.identity.auth.operate.existingSecret": "secretValue",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(s.T(), output, &deployment)

				// then
				env := deployment.Spec.Template.Spec.Containers[0].Env
				s.Require().Contains(env,
					corev1.EnvVar{
						Name: "KEYCLOAK_INIT_OPERATE_SECRET",
						ValueFrom: &corev1.EnvVarSource{
							SecretKeyRef: &corev1.SecretKeySelector{
								LocalObjectReference: corev1.LocalObjectReference{Name: "camunda-platform-test-operate-identity-secret"},
								Key:                  "operate-secret",
							},
						},
					})
			},
		}, {
			Name:                 "TestContainerShouldSetOperateIdentitySecretViaReference",
			HelmOptionsExtraArgs: map[string][]string{"install": {"--debug"}},
			Values: map[string]string{
				"global.identity.auth.operate.existingSecret.name": "ownExistingSecret",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(s.T(), output, &deployment)

				// then
				env := deployment.Spec.Template.Spec.Containers[0].Env
				s.Require().Contains(env,
					corev1.EnvVar{
						Name: "KEYCLOAK_INIT_OPERATE_SECRET",
						ValueFrom: &corev1.EnvVarSource{
							SecretKeyRef: &corev1.SecretKeySelector{
								LocalObjectReference: corev1.LocalObjectReference{Name: "ownExistingSecret"},
								Key:                  "operate-secret",
							},
						},
					})
			},
		}, {
			Name:                 "TestContainerShouldSetTasklistIdentitySecretValue",
			HelmOptionsExtraArgs: map[string][]string{"install": {"--debug"}},
			Values: map[string]string{
				"global.identity.auth.tasklist.existingSecret": "secretValue",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(s.T(), output, &deployment)

				// then
				env := deployment.Spec.Template.Spec.Containers[0].Env
				s.Require().Contains(env,
					corev1.EnvVar{
						Name: "KEYCLOAK_INIT_TASKLIST_SECRET",
						ValueFrom: &corev1.EnvVarSource{
							SecretKeyRef: &corev1.SecretKeySelector{
								LocalObjectReference: corev1.LocalObjectReference{Name: "camunda-platform-test-tasklist-identity-secret"},
								Key:                  "tasklist-secret",
							},
						},
					})
			},
		}, {
			Name:                 "TestContainerShouldSetTasklistIdentitySecretViaReference",
			HelmOptionsExtraArgs: map[string][]string{"install": {"--debug"}},
			Values: map[string]string{
				"global.identity.auth.tasklist.existingSecret.name": "ownExistingSecret",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(s.T(), output, &deployment)

				// then
				env := deployment.Spec.Template.Spec.Containers[0].Env
				s.Require().Contains(env,
					corev1.EnvVar{
						Name: "KEYCLOAK_INIT_TASKLIST_SECRET",
						ValueFrom: &corev1.EnvVarSource{
							SecretKeyRef: &corev1.SecretKeySelector{
								LocalObjectReference: corev1.LocalObjectReference{Name: "ownExistingSecret"},
								Key:                  "tasklist-secret",
							},
						},
					})
			},
		}, {
			Name:                 "TestContainerShouldSetOptimizeIdentitySecretValue",
			HelmOptionsExtraArgs: map[string][]string{"install": {"--debug"}},
			Values: map[string]string{
				"global.identity.auth.optimize.existingSecret": "secretValue",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(s.T(), output, &deployment)

				// then
				env := deployment.Spec.Template.Spec.Containers[0].Env
				s.Require().Contains(env,
					corev1.EnvVar{
						Name: "KEYCLOAK_INIT_OPTIMIZE_SECRET",
						ValueFrom: &corev1.EnvVarSource{
							SecretKeyRef: &corev1.SecretKeySelector{
								LocalObjectReference: corev1.LocalObjectReference{Name: "camunda-platform-test-optimize-identity-secret"},
								Key:                  "optimize-secret",
							},
						},
					})
			},
		}, {
			Name:                 "TestContainerShouldSetOptimizeIdentitySecretViaReference",
			HelmOptionsExtraArgs: map[string][]string{"install": {"--debug"}},
			Values: map[string]string{
				"global.identity.auth.optimize.existingSecret.name": "ownExistingSecret",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(s.T(), output, &deployment)

				// then
				env := deployment.Spec.Template.Spec.Containers[0].Env
				s.Require().Contains(env,
					corev1.EnvVar{
						Name: "KEYCLOAK_INIT_OPTIMIZE_SECRET",
						ValueFrom: &corev1.EnvVarSource{
							SecretKeyRef: &corev1.SecretKeySelector{
								LocalObjectReference: corev1.LocalObjectReference{Name: "ownExistingSecret"},
								Key:                  "optimize-secret",
							},
						},
					})
			},
		}, {
			Name: "TestContainerShouldOverwriteGlobalImagePullPolicy",
			Values: map[string]string{
				"global.image.pullPolicy": "Always",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(s.T(), output, &deployment)

				// then
				expectedPullPolicy := corev1.PullAlways
				containers := deployment.Spec.Template.Spec.Containers
				s.Require().Equal(1, len(containers))
				pullPolicy := containers[0].ImagePullPolicy
				s.Require().Equal(expectedPullPolicy, pullPolicy)
			},
		}, {
			Name:                 "TestContainerStartupProbe",
			HelmOptionsExtraArgs: map[string][]string{"install": {"--debug"}},
			Values: map[string]string{
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
				helm.UnmarshalK8SYaml(s.T(), output, &deployment)

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
			// readinessProbe is enabled by default so it's tested by golden files.
			Name:                 "TestContainerLivenessProbe",
			HelmOptionsExtraArgs: map[string][]string{"install": {"--debug"}},
			Values: map[string]string{
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
				helm.UnmarshalK8SYaml(s.T(), output, &deployment)

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
			Name:                 "TestContainerProbesWithContextPath",
			HelmOptionsExtraArgs: map[string][]string{"install": {"--debug"}},
			Values: map[string]string{
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
				"identity.sidecars[0].name":                   "nginx",
				"identity.sidecars[0].image":                  "nginx:latest",
				"identity.sidecars[0].ports[0].containerPort": "80",
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
			Name: "TestInitContainers",
			Values: map[string]string{
				"identity.initContainers[0].name":                   "nginx",
				"identity.initContainers[0].image":                  "nginx:latest",
				"identity.initContainers[0].ports[0].containerPort": "80",
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
			Name:                 "TestContainerShouldSetFirstUserExistingSecretValue",
			HelmOptionsExtraArgs: map[string][]string{"install": {"--debug"}},
			Values: map[string]string{
				"identity.firstUser.existingSecret": "identityFirstUserSecret",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(s.T(), output, &deployment)

				// then
				env := deployment.Spec.Template.Spec.Containers[0].Env
				s.Require().Contains(env,
					corev1.EnvVar{
						Name: "KEYCLOAK_USERS_0_PASSWORD",
						ValueFrom: &corev1.EnvVarSource{
							SecretKeyRef: &corev1.SecretKeySelector{
								LocalObjectReference: corev1.LocalObjectReference{Name: "identityFirstUserSecret"},
								Key:                  "identity-firstuser-password",
							},
						},
					})
			},
		}, {
			Name: "TestContainerShouldSetExternalDatabaseExistingSecret",
			Values: map[string]string{
				"identityPostgresql.enabled":                          "false",
				"identity.externalDatabase.enabled":                   "true",
				"identity.externalDatabase.existingSecret":            "postgres-secret-ext",
				"identity.externalDatabase.existingSecretPasswordKey": "identity-password-ext",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(s.T(), output, &deployment)

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
			Name: "TestSetDnsPolicyAndDnsConfig",
			Values: map[string]string{
				"identity.dnsPolicy":                "ClusterFirst",
				"identity.dnsConfig.nameservers[0]": "8.8.8.8",
				"identity.dnsConfig.searches[0]":    "example.com",
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
		}, {
			Name: "TestClientSecretPlaintextWhenAuthTypeGeneric",
			Values: map[string]string{
				"global.identity.auth.type":                    "GENERIC",
				"global.identity.auth.issuerBackendUrl":        "https://my.idp.org",
				"global.identity.auth.issuer":                  "https://my.idp.org",
				"global.identity.auth.identity.audience":       "identity-client",
				"global.identity.auth.identity.clientId":       "identity-client",
				"global.identity.auth.identity.existingSecret": "superSecret",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(s.T(), output, &deployment)

				// then
				env := deployment.Spec.Template.Spec.Containers[0].Env
				s.Require().Contains(env,
					corev1.EnvVar{
						Name:  "CAMUNDA_IDENTITY_CLIENT_SECRET",
						Value: "superSecret",
					})
			},
		}, {
			Name: "TestClientSecretRefWhenAuthTypeGeneric",
			Values: map[string]string{
				"global.identity.auth.type":                         "GENERIC",
				"global.identity.auth.issuerBackendUrl":             "https://my.idp.org",
				"global.identity.auth.issuer":                       "https://my.idp.org",
				"global.identity.auth.identity.audience":            "identity-client",
				"global.identity.auth.identity.clientId":            "identity-client",
				"global.identity.auth.identity.existingSecret.name": "super-secret",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(s.T(), output, &deployment)

				// then
				env := deployment.Spec.Template.Spec.Containers[0].Env
				s.Require().Contains(env,
					corev1.EnvVar{
						Name: "CAMUNDA_IDENTITY_CLIENT_SECRET",
						ValueFrom: &corev1.EnvVarSource{
							SecretKeyRef: &corev1.SecretKeySelector{
								LocalObjectReference: corev1.LocalObjectReference{Name: "super-secret"},
								Key:                  "identity-secret",
							},
						},
					})
			},
		}, {
			Name: "TestClientSecretPlaintextWhenAuthTypeMicrosoft",
			Values: map[string]string{
				"global.identity.auth.type":                    "MICROSOFT",
				"global.identity.auth.issuerBackendUrl":        "https://my.idp.org",
				"global.identity.auth.issuer":                  "https://my.idp.org",
				"global.identity.auth.identity.audience":       "identity-client",
				"global.identity.auth.identity.clientId":       "identity-client",
				"global.identity.auth.identity.existingSecret": "superSecret",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(s.T(), output, &deployment)

				// then
				env := deployment.Spec.Template.Spec.Containers[0].Env
				s.Require().Contains(env,
					corev1.EnvVar{
						Name:  "CAMUNDA_IDENTITY_CLIENT_SECRET",
						Value: "superSecret",
					})
			},
		}, {
			Name: "TestClientSecretRefWhenAuthTypeMicrosoft",
			Values: map[string]string{
				"global.identity.auth.type":                         "MICROSOFT",
				"global.identity.auth.issuerBackendUrl":             "https://my.idp.org",
				"global.identity.auth.issuer":                       "https://my.idp.org",
				"global.identity.auth.identity.audience":            "identity-client",
				"global.identity.auth.identity.clientId":            "identity-client",
				"global.identity.auth.identity.existingSecret.name": "super-secret",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(s.T(), output, &deployment)

				// then
				env := deployment.Spec.Template.Spec.Containers[0].Env
				s.Require().Contains(env,
					corev1.EnvVar{
						Name: "CAMUNDA_IDENTITY_CLIENT_SECRET",
						ValueFrom: &corev1.EnvVarSource{
							SecretKeyRef: &corev1.SecretKeySelector{
								LocalObjectReference: corev1.LocalObjectReference{Name: "super-secret"},
								Key:                  "identity-secret",
							},
						},
					})
			},
		},
	}

	testhelpers.RunTestCasesE(s.T(), s.chartPath, s.release, s.namespace, s.templates, testCases)
}
