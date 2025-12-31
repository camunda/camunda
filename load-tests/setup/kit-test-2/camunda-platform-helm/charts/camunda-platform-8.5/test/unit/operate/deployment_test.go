// Copyright 2022 Camunda Services GmbH
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//	  http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package operate

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
		templates: []string{"templates/operate/deployment.yaml"},
	})
}

func (s *DeploymentTemplateTest) TestDifferentValuesInputs() {
	testCases := []testhelpers.TestCase{
		{
			Name: "TestContainerSetPodLabels",
			Values: map[string]string{
				"operate.podLabels.foo": "bar",
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
				"operate.podAnnotations.foo": "bar",
				"operate.podAnnotations.foz": "baz",
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
				"global.image.registry":    "global.custom.registry.io",
				"global.image.tag":         "8.x.x",
				"operate.image.registry":   "subchart.custom.registry.io",
				"operate.image.repository": "camunda/operate-test",
				"operate.image.tag":        "snapshot",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(s.T(), output, &deployment)

				// then
				container := deployment.Spec.Template.Spec.Containers[0]
				s.Require().Equal(container.Image, "subchart.custom.registry.io/camunda/operate-test:snapshot")
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
				"global.image.pullSecrets[0].name":  "SecretName",
				"operate.image.pullSecrets[0].name": "SecretNameSubChart",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(s.T(), output, &deployment)

				// then
				s.Require().Equal("SecretNameSubChart", deployment.Spec.Template.Spec.ImagePullSecrets[0].Name)
			},
		}, {
			Name:                 "TestContainerOverwriteImageTag",
			HelmOptionsExtraArgs: map[string][]string{"install": {"--debug"}},
			Values: map[string]string{
				"operate.image.tag": "a.b.c",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(s.T(), output, &deployment)

				// then
				expectedContainerImage := "camunda/operate:a.b.c"
				containers := deployment.Spec.Template.Spec.Containers
				s.Require().Equal(1, len(containers))
				s.Require().Equal(expectedContainerImage, containers[0].Image)
			},
		}, {
			Name:                 "TestContainerOverwriteGlobalImageTag",
			HelmOptionsExtraArgs: map[string][]string{"install": {"--debug"}},
			Values: map[string]string{
				"global.image.tag":  "a.b.c",
				"operate.image.tag": "",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(s.T(), output, &deployment)

				// then
				expectedContainerImage := "camunda/operate:a.b.c"
				containers := deployment.Spec.Template.Spec.Containers
				s.Require().Equal(1, len(containers))
				s.Require().Equal(expectedContainerImage, containers[0].Image)
			},
		}, {
			Name:                 "TestContainerOverwriteImageTagWithChartDirectSetting",
			HelmOptionsExtraArgs: map[string][]string{"install": {"--debug"}},
			Values: map[string]string{
				"global.image.tag":  "x.y.z",
				"operate.image.tag": "a.b.c",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(s.T(), output, &deployment)

				// then
				expectedContainerImage := "camunda/operate:a.b.c"
				containers := deployment.Spec.Template.Spec.Containers
				s.Require().Equal(1, len(containers))
				s.Require().Equal(expectedContainerImage, containers[0].Image)
			},
		}, {
			Name:                 "TestContainerSetContainerCommand",
			HelmOptionsExtraArgs: map[string][]string{"install": {"--debug"}},
			Values: map[string]string{
				"operate.command[0]": "printenv",
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
			Name:                 "TestContainerSetExtraVolumes",
			HelmOptionsExtraArgs: map[string][]string{"install": {"--debug"}},
			Values: map[string]string{
				"operate.extraVolumes[0].name":                  "extraVolume",
				"operate.extraVolumes[0].configMap.name":        "otherConfigMap",
				"operate.extraVolumes[0].configMap.defaultMode": "744",
			},
			Verifier: func(t *testing.T, output string, err error) {
				// finding out the length of volumes array before addition of new volume
				var deploymentBefore appsv1.Deployment
				before := helm.RenderTemplate(s.T(), &helm.Options{}, s.chartPath, s.release, s.templates)
				helm.UnmarshalK8SYaml(s.T(), before, &deploymentBefore)
				volumeLenBefore := len(deploymentBefore.Spec.Template.Spec.Volumes)
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
			Name:                 "TestContainerSetExtraVolumeMounts",
			HelmOptionsExtraArgs: map[string][]string{"install": {"--debug"}},
			Values: map[string]string{
				"operate.extraVolumeMounts[0].name":      "otherConfigMap",
				"operate.extraVolumeMounts[0].mountPath": "/usr/local/config",
			},
			Verifier: func(t *testing.T, output string, err error) {
				// finding out the length of containers and volumeMounts array before addition of new volumeMount
				var deploymentBefore appsv1.Deployment
				before := helm.RenderTemplate(s.T(), &helm.Options{}, s.chartPath, s.release, s.templates)
				helm.UnmarshalK8SYaml(s.T(), before, &deploymentBefore)
				containerLenBefore := len(deploymentBefore.Spec.Template.Spec.Containers)
				volumeMountLenBefore := len(deploymentBefore.Spec.Template.Spec.Containers[0].VolumeMounts)
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
			Name:                 "TestContainerSetExtraVolumesAndMounts",
			HelmOptionsExtraArgs: map[string][]string{"install": {"--debug"}},
			Values: map[string]string{
				"operate.extraVolumeMounts[0].name":             "otherConfigMap",
				"operate.extraVolumeMounts[0].mountPath":        "/usr/local/config",
				"operate.extraVolumes[0].name":                  "extraVolume",
				"operate.extraVolumes[0].configMap.name":        "otherConfigMap",
				"operate.extraVolumes[0].configMap.defaultMode": "744",
			},
			Verifier: func(t *testing.T, output string, err error) {
				// finding out the length of volumes, volumemounts array before addition of new volume
				var deploymentBefore appsv1.Deployment
				before := helm.RenderTemplate(s.T(), &helm.Options{}, s.chartPath, s.release, s.templates)
				helm.UnmarshalK8SYaml(s.T(), before, &deploymentBefore)
				volumeLenBefore := len(deploymentBefore.Spec.Template.Spec.Volumes)
				volumeMountLenBefore := len(deploymentBefore.Spec.Template.Spec.Containers[0].VolumeMounts)
				containerLenBefore := len(deploymentBefore.Spec.Template.Spec.Containers)
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
			Name:                 "TestContainerSetServiceAccountName",
			HelmOptionsExtraArgs: map[string][]string{"install": {"--debug"}},
			Values: map[string]string{
				"operate.serviceAccount.name": "accName",
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
				"operate.podSecurityContext.runAsUser": "1000",
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
				"operate.containerSecurityContext.privileged":          "true",
				"operate.containerSecurityContext.capabilities.add[0]": "NET_ADMIN",
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
				"operate.nodeSelector.disktype": "ssd",
				"operate.nodeSelector.cputype":  "arm",
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
				"operate.affinity.nodeAffinity.requiredDuringSchedulingIgnoredDuringExecution.nodeSelectorTerms[0].matchexpressions[0].key":       "kubernetes.io/e2e-az-name",
				"operate.affinity.nodeAffinity.requiredDuringSchedulingIgnoredDuringExecution.nodeSelectorTerms[0].matchexpressions[0].operator":  "In",
				"operate.affinity.nodeAffinity.requiredDuringSchedulingIgnoredDuringExecution.nodeSelectorTerms[0].matchexpressions[0].values[0]": "e2e-a1",
				"operate.affinity.nodeAffinity.requiredDuringSchedulingIgnoredDuringExecution.nodeSelectorTerms[0].matchexpressions[0].values[1]": "e2e-a2",
				"operate.affinity.nodeAffinity.preferredDuringSchedulingIgnoredDuringExecution[0].weight":                                         "1",
				"operate.affinity.nodeAffinity.preferredDuringSchedulingIgnoredDuringExecution[0].preference.matchExpressions[0].key":             "another-node-label-key",
				"operate.affinity.nodeAffinity.preferredDuringSchedulingIgnoredDuringExecution[0].preference.matchExpressions[0].operator":        "In",
				"operate.affinity.nodeAffinity.preferredDuringSchedulingIgnoredDuringExecution[0].preference.matchExpressions[0].values[0]":       "another-node-label-value",
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
				"operate.tolerations[0].key":      "key1",
				"operate.tolerations[0].operator": "Equal",
				"operate.tolerations[0].value":    "Value1",
				"operate.tolerations[0].effect":   "NoSchedule",
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
						Name: "CAMUNDA_IDENTITY_CLIENT_SECRET",
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
						Name: "CAMUNDA_IDENTITY_CLIENT_SECRET",
						ValueFrom: &corev1.EnvVarSource{
							SecretKeyRef: &corev1.SecretKeySelector{
								LocalObjectReference: corev1.LocalObjectReference{Name: "ownExistingSecret"},
								Key:                  "operate-secret",
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
				"operate.startupProbe.enabled":             "true",
				"operate.startupProbe.probePath":           "/healthz",
				"operate.startupProbe.initialDelaySeconds": "5",
				"operate.startupProbe.periodSeconds":       "10",
				"operate.startupProbe.successThreshold":    "1",
				"operate.startupProbe.failureThreshold":    "5",
				"operate.startupProbe.timeoutSeconds":      "1",
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
			Name:                 "TestContainerLivenessProbe",
			HelmOptionsExtraArgs: map[string][]string{"install": {"--debug"}},
			Values: map[string]string{
				"operate.livenessProbe.enabled":             "true",
				"operate.livenessProbe.probePath":           "/healthz",
				"operate.livenessProbe.initialDelaySeconds": "5",
				"operate.livenessProbe.periodSeconds":       "10",
				"operate.livenessProbe.successThreshold":    "1",
				"operate.livenessProbe.failureThreshold":    "5",
				"operate.livenessProbe.timeoutSeconds":      "1",
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
			Name:                 "TestContainerProbesWithContextPath",
			HelmOptionsExtraArgs: map[string][]string{"install": {"--debug"}},
			Values: map[string]string{
				"operate.contextPath":              "/test",
				"operate.startupProbe.enabled":     "true",
				"operate.startupProbe.probePath":   "/start",
				"operate.readinessProbe.enabled":   "true",
				"operate.readinessProbe.probePath": "/ready",
				"operate.livenessProbe.enabled":    "true",
				"operate.livenessProbe.probePath":  "/live",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(s.T(), output, &deployment)

				// then
				probe := deployment.Spec.Template.Spec.Containers[0]

				s.Require().Equal("/test/start", probe.StartupProbe.HTTPGet.Path)
				s.Require().Equal("/test/ready", probe.ReadinessProbe.HTTPGet.Path)
				s.Require().Equal("/test/live", probe.LivenessProbe.HTTPGet.Path)
			},
		}, {
			Name:                 "TestContainerProbesWithContextPathWithTrailingSlash",
			HelmOptionsExtraArgs: map[string][]string{"install": {"--debug"}},
			Values: map[string]string{
				"operate.contextPath":              "/test/",
				"operate.startupProbe.enabled":     "true",
				"operate.startupProbe.probePath":   "/start",
				"operate.readinessProbe.enabled":   "true",
				"operate.readinessProbe.probePath": "/ready",
				"operate.livenessProbe.enabled":    "true",
				"operate.livenessProbe.probePath":  "/live",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(s.T(), output, &deployment)

				// then
				probe := deployment.Spec.Template.Spec.Containers[0]

				s.Require().Equal("/test/start", probe.StartupProbe.HTTPGet.Path)
				s.Require().Equal("/test/ready", probe.ReadinessProbe.HTTPGet.Path)
				s.Require().Equal("/test/live", probe.LivenessProbe.HTTPGet.Path)
			},
		}, {
			Name: "TestContainerSetSidecar",
			Values: map[string]string{
				"operate.sidecars[0].name":                   "nginx",
				"operate.sidecars[0].image":                  "nginx:latest",
				"operate.sidecars[0].ports[0].containerPort": "80",
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
				"operate.initContainers[0].name":                   "nginx",
				"operate.initContainers[0].image":                  "nginx:latest",
				"operate.initContainers[0].ports[0].containerPort": "80",
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
			// readinessProbe is enabled by default so it's tested by golden files.
			Name: "TestOperateWithConfiguration",
			Values: map[string]string{
				"operate.configuration": `
camunda.operate:
  elasticsearch:
    numberOfShards: 3
			`,
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(s.T(), output, &deployment)

				// then
				volumeMounts := deployment.Spec.Template.Spec.Containers[0].VolumeMounts
				volumes := deployment.Spec.Template.Spec.Volumes

				// find the volume named config
				var volume corev1.Volume
				for _, candidateVolume := range volumes {
					if candidateVolume.Name == "config" {
						volume = candidateVolume
						break
					}
				}

				// find the volumeMount named environment-config
				var volumeMount corev1.VolumeMount
				for _, candidateVolumeMount := range volumeMounts {
					if candidateVolumeMount.Name == "config" && strings.Contains(candidateVolumeMount.MountPath, "application") {
						volumeMount = candidateVolumeMount
						break
					}
				}
				s.Require().Equal("config", volumeMount.Name)
				s.Require().Equal("/usr/local/operate/config/application.yml", volumeMount.MountPath)
				s.Require().Equal("application.yml", volumeMount.SubPath)

				s.Require().Equal("config", volume.Name)
				s.Require().Equal("camunda-platform-test-operate-configuration", volume.ConfigMap.Name)
			},
		}, {
			Name: "TestOperateWithLog4j2Configuration",
			Values: map[string]string{
				// unfortunately, this testing library does not accept periods in the keys so I had to leave
				// out a period here.
				"operate.extraConfiguration.log4j2xml": `
<configuration></configuration>
			`,
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(s.T(), output, &deployment)

				// then
				volumeMounts := deployment.Spec.Template.Spec.Containers[0].VolumeMounts
				volumes := deployment.Spec.Template.Spec.Volumes

				// find the volume named environment-config
				var volume corev1.Volume
				for _, candidateVolume := range volumes {
					if candidateVolume.Name == "config" {
						volume = candidateVolume
						break
					}
				}

				// find the volumeMount named environment-config
				var volumeMount corev1.VolumeMount
				for _, candidateVolumeMount := range volumeMounts {
					if candidateVolumeMount.Name == "config" && strings.Contains(candidateVolumeMount.MountPath, "log4j2") {
						volumeMount = candidateVolumeMount
						break
					}
				}
				s.Require().Equal("config", volumeMount.Name)
				s.Require().Equal("/usr/local/operate/config/log4j2xml", volumeMount.MountPath)
				s.Require().Equal("log4j2xml", volumeMount.SubPath)

				s.Require().Equal("config", volume.Name)
				s.Require().Equal("camunda-platform-test-operate-configuration", volume.ConfigMap.Name)
			},
		}, {
			Name: "TestOperateDoesNotSetElasticsearchPasswordIfNoneProvidedAndExternal",
			Values: map[string]string{
				"global.elasticsearch.external":     "true",
				"global.elasticsearch.url.protocol": "http",
				"global.elasticsearch.url.host":     "elasticexternal",
				"global.elasticsearch.url.port":     "9200",
				"elasticsearch.enabled":             "false",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(s.T(), output, &deployment)

				// then
				envVars := deployment.Spec.Template.Spec.Containers[0].Env

				for _, envVar := range envVars {
					if envVar.Name == "CAMUNDA_OPERATE_ELASTICSEARCH_PASSWORD" || envVar.Name == "CAMUNDA_OPERATE_ZEEBE_ELASTICSEARCH_PASSWORD" {
						s.Fail("The elasticsearch password vars should not be set when external elasticsearch is unauthenticated")
					}
				}
			},
		}, {
			Name: "TestOperateSetsElasticsearchPasswordIfProvidedByExplicitValueAndExternal",
			Values: map[string]string{
				"global.elasticsearch.external":      "true",
				"global.elasticsearch.url.protocol":  "http",
				"global.elasticsearch.url.host":      "elasticexternal",
				"global.elasticsearch.url.port":      "9200",
				"elasticsearch.enabled":              "false",
				"global.elasticsearch.auth.password": "supersecret",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(s.T(), output, &deployment)

				// then
				envVars := deployment.Spec.Template.Spec.Containers[0].Env

				var camundaOperateElasticPassword corev1.EnvVar
				var camundaOperateZeebeElasticPassword corev1.EnvVar
				for _, envVar := range envVars {
					if envVar.Name == "CAMUNDA_OPERATE_ELASTICSEARCH_PASSWORD" {
						camundaOperateElasticPassword = envVar
						continue
					}
					if envVar.Name == "CAMUNDA_OPERATE_ZEEBE_ELASTICSEARCH_PASSWORD" {
						camundaOperateZeebeElasticPassword = envVar
					}
				}

				s.Require().Equal(camundaOperateElasticPassword.ValueFrom.SecretKeyRef.Name, "camunda-platform-test-elasticsearch")
				s.Require().Equal(camundaOperateZeebeElasticPassword.ValueFrom.SecretKeyRef.Name, "camunda-platform-test-elasticsearch")
			},
		}, {
			Name: "TestOperateSetsElasticsearchPasswordIfProvidedBySecretNameAndExternal",
			Values: map[string]string{
				"global.elasticsearch.external":            "true",
				"global.elasticsearch.url.protocol":        "http",
				"global.elasticsearch.url.host":            "elasticexternal",
				"global.elasticsearch.url.port":            "9200",
				"elasticsearch.enabled":                    "false",
				"global.elasticsearch.auth.existingSecret": "supersecret",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(s.T(), output, &deployment)

				// then
				envVars := deployment.Spec.Template.Spec.Containers[0].Env

				var camundaOperateElasticPassword corev1.EnvVar
				var camundaOperateZeebeElasticPassword corev1.EnvVar
				for _, envVar := range envVars {
					if envVar.Name == "CAMUNDA_OPERATE_ELASTICSEARCH_PASSWORD" {
						camundaOperateElasticPassword = envVar
						continue
					}
					if envVar.Name == "CAMUNDA_OPERATE_ZEEBE_ELASTICSEARCH_PASSWORD" {
						camundaOperateZeebeElasticPassword = envVar
					}
				}

				s.Require().Equal(camundaOperateElasticPassword.ValueFrom.SecretKeyRef.Name, "supersecret")
				s.Require().Equal(camundaOperateZeebeElasticPassword.ValueFrom.SecretKeyRef.Name, "supersecret")
			},
		}, {
			Name: "TestSetDnsPolicyAndDnsConfig",
			Values: map[string]string{
				"operate.dnsPolicy":                "ClusterFirst",
				"operate.dnsConfig.nameservers[0]": "8.8.8.8",
				"operate.dnsConfig.searches[0]":    "example.com",
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
