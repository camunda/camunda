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

func TestGatewayDeploymentTemplate(t *testing.T) {
	t.Parallel()

	chartPath, err := filepath.Abs("../../../")
	require.NoError(t, err)

	suite.Run(t, &DeploymentTemplateTest{
		chartPath: chartPath,
		release:   "camunda-platform-test",
		namespace: "camunda-platform-" + strings.ToLower(random.UniqueId()),
		templates: []string{"templates/zeebe-gateway/deployment.yaml"},
	})
}

func (s *DeploymentTemplateTest) TestDifferentValuesInputs() {
	testCases := []testhelpers.TestCase{
		{
			Name: "TestContainerSetPodLabels",
			Values: map[string]string{
				"zeebe-gateway.podLabels.foo": "bar",
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
				"zeebe-gateway.podAnnotations.foo": "bar",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(s.T(), output, &deployment)

				// then
				s.Require().Equal("bar", deployment.Spec.Template.Annotations["foo"])
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
			Name: "TestContainerSetPriorityClassName",
			Values: map[string]string{
				"zeebe-gateway.priorityClassName": "PRIO",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(s.T(), output, &deployment)

				// then
				s.Require().Equal("PRIO", deployment.Spec.Template.Spec.PriorityClassName)
			},
		}, {
			Name: "TestContainerSetImageNameSubChart",
			Values: map[string]string{
				"global.image.registry":          "global.custom.registry.io",
				"global.image.tag":               "8.x.x",
				"zeebe-gateway.image.registry":   "subchart.custom.registry.io",
				"zeebe-gateway.image.repository": "camunda/zeebe-test",
				"zeebe-gateway.image.tag":        "snapshot",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(s.T(), output, &deployment)

				// then
				container := deployment.Spec.Template.Spec.Containers[0]
				s.Require().Equal(container.Image, "subchart.custom.registry.io/camunda/zeebe-test:snapshot")
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
				"global.image.pullSecrets[0].name":        "SecretName",
				"zeebe-gateway.image.pullSecrets[0].name": "SecretNameSubChart",
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
				"zeebe-gateway.image.tag": "a.b.c",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(s.T(), output, &deployment)

				// then
				expectedContainerImage := "camunda/zeebe:a.b.c"
				containers := deployment.Spec.Template.Spec.Containers
				s.Require().Equal(1, len(containers))
				s.Require().Equal(expectedContainerImage, containers[0].Image)
			},
		}, {
			Name: "TestContainerOverwriteGlobalImageTag",
			Values: map[string]string{
				"global.image.tag":        "a.b.c",
				"zeebe-gateway.image.tag": "",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(s.T(), output, &deployment)

				// then
				expectedContainerImage := "camunda/zeebe:a.b.c"
				containers := deployment.Spec.Template.Spec.Containers
				s.Require().Equal(1, len(containers))
				s.Require().Equal(expectedContainerImage, containers[0].Image)
			},
		}, {
			Name: "TestContainerOverwriteImageTagWithChartDirectSetting",
			Values: map[string]string{
				"global.image.tag":        "x.y.z",
				"zeebe-gateway.image.tag": "a.b.c",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(s.T(), output, &deployment)

				// then
				expectedContainerImage := "camunda/zeebe:a.b.c"
				containers := deployment.Spec.Template.Spec.Containers
				s.Require().Equal(1, len(containers))
				s.Require().Equal(expectedContainerImage, containers[0].Image)
			},
		}, {
			Name: "TestContainerShouldSetTemplateEnvVars",
			Values: map[string]string{
				"zeebe-gateway.env[0].name":  "RELEASE_NAME",
				"zeebe-gateway.env[0].value": "test-{{ .Release.Name }}",
				"zeebe-gateway.env[1].name":  "OTHER_ENV",
				"zeebe-gateway.env[1].value": "nothingToSeeHere",
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
			Name: "TestContainerSetContainerCommand",
			Values: map[string]string{
				"zeebe-gateway.command[0]": "printenv",
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
			Name: "TestContainerSetLog4j2",
			Values: map[string]string{
				"zeebe-gateway.log4j2": "<xml>\n</xml>",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(s.T(), output, &deployment)

				// then
				volumeMounts := deployment.Spec.Template.Spec.Containers[0].VolumeMounts
				s.Require().Equal(2, len(volumeMounts))
				s.Require().Equal("config", volumeMounts[1].Name)
				s.Require().Equal("/usr/local/zeebe/config/log4j2.xml", volumeMounts[1].MountPath)
				s.Require().Equal("gateway-log4j2.xml", volumeMounts[1].SubPath)
			},
		}, {
			Name: "TestContainerSetExtraVolumes",
			Values: map[string]string{
				"zeebe-gateway.extraVolumes[0].name":                  "extraVolume",
				"zeebe-gateway.extraVolumes[0].configMap.name":        "otherConfigMap",
				"zeebe-gateway.extraVolumes[0].configMap.defaultMode": "744",
			},
			Verifier: func(t *testing.T, output string, err error) {
				// finding out the length of volumes, volumemounts array before addition of new volume
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
				"zeebe-gateway.extraVolumeMounts[0].name":      "otherConfigMap",
				"zeebe-gateway.extraVolumeMounts[0].mountPath": "/usr/local/config",
			},
			Verifier: func(t *testing.T, output string, err error) {
				// finding out the length of containers and volumeMounts array before addition of new volumeMount
				var deploymentBefore appsv1.Deployment
				before := helm.RenderTemplate(s.T(), &helm.Options{}, s.chartPath, s.release, s.templates)
				helm.UnmarshalK8SYaml(s.T(), before, &deploymentBefore)
				volumeMountLenBefore := len(deploymentBefore.Spec.Template.Spec.Containers[0].VolumeMounts)
				// given
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(s.T(), output, &deployment)

				// then
				volumeMounts := deployment.Spec.Template.Spec.Containers[0].VolumeMounts
				s.Require().Equal(volumeMountLenBefore+1, len(volumeMounts))
				extraVolumeMount := volumeMounts[volumeMountLenBefore]
				s.Require().Equal("otherConfigMap", extraVolumeMount.Name)
				s.Require().Equal("/usr/local/config", extraVolumeMount.MountPath)
			},
		}, {
			Name: "TestContainerSetExtraVolumesAndMounts",
			Values: map[string]string{
				"zeebe-gateway.extraVolumeMounts[0].name":             "otherConfigMap",
				"zeebe-gateway.extraVolumeMounts[0].mountPath":        "/usr/local/config",
				"zeebe-gateway.extraVolumes[0].name":                  "extraVolume",
				"zeebe-gateway.extraVolumes[0].configMap.name":        "otherConfigMap",
				"zeebe-gateway.extraVolumes[0].configMap.defaultMode": "744",
			},
			Verifier: func(t *testing.T, output string, err error) {
				// finding out the length of volumes, volumemounts array before addition of new volume
				var deploymentBefore appsv1.Deployment
				before := helm.RenderTemplate(s.T(), &helm.Options{}, s.chartPath, s.release, s.templates)
				helm.UnmarshalK8SYaml(s.T(), before, &deploymentBefore)
				volumeLenBefore := len(deploymentBefore.Spec.Template.Spec.Volumes)
				volumeMountLenBefore := len(deploymentBefore.Spec.Template.Spec.Containers[0].VolumeMounts)
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

				volumeMounts := deployment.Spec.Template.Spec.Containers[0].VolumeMounts
				s.Require().Equal(volumeMountLenBefore+1, len(volumeMounts))
				extraVolumeMount := volumeMounts[volumeMountLenBefore]
				s.Require().Equal("otherConfigMap", extraVolumeMount.Name)
				s.Require().Equal("/usr/local/config", extraVolumeMount.MountPath)
			},
		}, {
			Name: "TestPodSetSecurityContext",
			Values: map[string]string{
				"zeebe-gateway.podSecurityContext.runAsUser": "1000",
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
				"zeebe-gateway.containerSecurityContext.privileged":          "true",
				"zeebe-gateway.containerSecurityContext.capabilities.add[0]": "NET_ADMIN",
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
			Name: "TestContainerSetServiceAccountName",
			Values: map[string]string{
				"zeebe-gateway.serviceAccount.name": "serviceaccount",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(s.T(), output, &deployment)

				// then
				s.Require().Equal("serviceaccount", deployment.Spec.Template.Spec.ServiceAccountName)
			},
		}, {
			// https://kubernetes.io/docs/concepts/scheduling-eviction/assign-pod-node/#nodeselector
			Name: "TestContainerSetNodeSelector",
			Values: map[string]string{
				"zeebe-gateway.nodeSelector.disktype": "ssd",
				"zeebe-gateway.nodeSelector.cputype":  "arm",
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
				"zeebe-gateway.affinity.nodeAffinity.requiredDuringSchedulingIgnoredDuringExecution.nodeSelectorTerms[0].matchexpressions[0].key":       "kubernetes.io/e2e-az-name",
				"zeebe-gateway.affinity.nodeAffinity.requiredDuringSchedulingIgnoredDuringExecution.nodeSelectorTerms[0].matchexpressions[0].operator":  "In",
				"zeebe-gateway.affinity.nodeAffinity.requiredDuringSchedulingIgnoredDuringExecution.nodeSelectorTerms[0].matchexpressions[0].values[0]": "e2e-a1",
				"zeebe-gateway.affinity.nodeAffinity.requiredDuringSchedulingIgnoredDuringExecution.nodeSelectorTerms[0].matchexpressions[0].values[1]": "e2e-a2",
				"zeebe-gateway.affinity.nodeAffinity.preferredDuringSchedulingIgnoredDuringExecution[0].weight":                                         "1",
				"zeebe-gateway.affinity.nodeAffinity.preferredDuringSchedulingIgnoredDuringExecution[0].preference.matchExpressions[0].key":             "another-node-label-key",
				"zeebe-gateway.affinity.nodeAffinity.preferredDuringSchedulingIgnoredDuringExecution[0].preference.matchExpressions[0].operator":        "In",
				"zeebe-gateway.affinity.nodeAffinity.preferredDuringSchedulingIgnoredDuringExecution[0].preference.matchExpressions[0].values[0]":       "another-node-label-value",
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
				"zeebe-gateway.tolerations[0].key":      "key1",
				"zeebe-gateway.tolerations[0].operator": "Equal",
				"zeebe-gateway.tolerations[0].value":    "Value1",
				"zeebe-gateway.tolerations[0].effect":   "NoSchedule",
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
			Name: "TestContainerSetExtraInitContainers",
			Values: map[string]string{
				"zeebe-gateway.extraInitContainers[0].name":       "init-container-{{ .Release.Name }}",
				"zeebe-gateway.extraInitContainers[0].image":      "busybox:1.28",
				"zeebe-gateway.extraInitContainers[0].command[0]": "sh",
				"zeebe-gateway.extraInitContainers[0].command[1]": "-c",
				"zeebe-gateway.extraInitContainers[0].command[2]": "top",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(s.T(), output, &deployment)

				// then
				initContainer := deployment.Spec.Template.Spec.InitContainers[0]
				s.Require().Equal("init-container-camunda-platform-test", initContainer.Name)
				s.Require().Equal("busybox:1.28", initContainer.Image)
				s.Require().Equal([]string{"sh", "-c", "top"}, initContainer.Command)
			},
		}, {
			Name: "TestInitContainers",
			Values: map[string]string{
				"zeebe-gateway.initContainers[0].name":                   "nginx",
				"zeebe-gateway.initContainers[0].image":                  "nginx:latest",
				"zeebe-gateway.initContainers[0].ports[0].containerPort": "80",
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
			// readinessProbe is enabled by default so it's tested by golden files.
			Name:                 "TestContainerStartupProbe",
			HelmOptionsExtraArgs: map[string][]string{"install": {"--debug"}},
			Values: map[string]string{
				"zeebe-gateway.startupProbe.enabled":             "true",
				"zeebe-gateway.startupProbe.probePath":           "/healthz",
				"zeebe-gateway.startupProbe.initialDelaySeconds": "5",
				"zeebe-gateway.startupProbe.periodSeconds":       "10",
				"zeebe-gateway.startupProbe.successThreshold":    "1",
				"zeebe-gateway.startupProbe.failureThreshold":    "5",
				"zeebe-gateway.startupProbe.timeoutSeconds":      "1",
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
				"zeebe-gateway.livenessProbe.enabled":             "true",
				"zeebe-gateway.livenessProbe.probePath":           "/healthz",
				"zeebe-gateway.livenessProbe.initialDelaySeconds": "5",
				"zeebe-gateway.livenessProbe.periodSeconds":       "10",
				"zeebe-gateway.livenessProbe.successThreshold":    "1",
				"zeebe-gateway.livenessProbe.failureThreshold":    "5",
				"zeebe-gateway.livenessProbe.timeoutSeconds":      "1",
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
				"zeebe-gateway.contextPath":              "/test",
				"zeebe-gateway.startupProbe.enabled":     "true",
				"zeebe-gateway.startupProbe.probePath":   "/start",
				"zeebe-gateway.readinessProbe.enabled":   "true",
				"zeebe-gateway.readinessProbe.probePath": "/ready",
				"zeebe-gateway.livenessProbe.enabled":    "true",
				"zeebe-gateway.livenessProbe.probePath":  "/live",
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
				"zeebe-gateway.contextPath":              "/test/",
				"zeebe-gateway.startupProbe.enabled":     "true",
				"zeebe-gateway.startupProbe.probePath":   "/start",
				"zeebe-gateway.readinessProbe.enabled":   "true",
				"zeebe-gateway.readinessProbe.probePath": "/ready",
				"zeebe-gateway.livenessProbe.enabled":    "true",
				"zeebe-gateway.livenessProbe.probePath":  "/live",
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
				"zeebe-gateway.sidecars[0].name":                   "nginx",
				"zeebe-gateway.sidecars[0].image":                  "nginx:latest",
				"zeebe-gateway.sidecars[0].ports[0].containerPort": "80",
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
			Name: "TestZeebeMultiTenancyEnabled",
			Values: map[string]string{
				"global.multitenancy.enabled": "true",
				"identity.postgresql.enabled": "true",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var statefulSet appsv1.StatefulSet
				helm.UnmarshalK8SYaml(s.T(), output, &statefulSet)

				// then
				env := statefulSet.Spec.Template.Spec.Containers[0].Env
				s.Require().Contains(env, corev1.EnvVar{Name: "ZEEBE_GATEWAY_MULTITENANCY_ENABLED", Value: "true"})
				s.Require().Contains(env, corev1.EnvVar{Name: "ZEEBE_BROKER_GATEWAY_MULTITENANCY_ENABLED", Value: "true"})
			},
		},
	}

	testhelpers.RunTestCasesE(s.T(), s.chartPath, s.release, s.namespace, s.templates, testCases)
}
