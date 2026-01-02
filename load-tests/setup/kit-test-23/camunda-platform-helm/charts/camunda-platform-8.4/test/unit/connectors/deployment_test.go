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

package connectors

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
		templates: []string{"templates/connectors/deployment.yaml"},
	})
}

// TODO/FIXME: currently Connectors don't support name override

func (s *DeploymentTemplateTest) TestDifferentValuesInputs() {
	testCases := []testhelpers.TestCase{
		{
			Name: "TestContainerSetPodLabels",
			Values: map[string]string{
				"connectors.enabled":       "true",
				"connectors.podLabels.foo": "bar",
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
				"connectors.enabled":            "true",
				"connectors.podAnnotations.foo": "bar",
				"connectors.podAnnotations.foz": "baz",
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
				"connectors.enabled":     "true",
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
				"connectors.enabled":          "true",
				"global.image.registry":       "global.custom.registry.io",
				"global.image.tag":            "999.999.1",
				"connectors.image.registry":   "subchart.custom.registry.io",
				"connectors.image.tag":        "snapshot",
				"connectors.image.repository": "connectors/connectors-bundle",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(s.T(), output, &deployment)

				// then
				container := deployment.Spec.Template.Spec.Containers[0]
				s.Require().Equal("subchart.custom.registry.io/connectors/connectors-bundle:snapshot", container.Image)
			},
		}, {
			Name: "TestContainerSetImageNameGlobalRegistry",
			Values: map[string]string{
				"connectors.enabled":          "true",
				"global.image.registry":       "global.custom.registry.io",
				"connectors.image.registry":   "",
				"connectors.image.tag":        "snapshot",
				"connectors.image.repository": "connectors/connectors-bundle",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(s.T(), output, &deployment)

				// then
				container := deployment.Spec.Template.Spec.Containers[0]
				s.Require().Equal("global.custom.registry.io/connectors/connectors-bundle:snapshot", container.Image)
			},
		}, {
			Name: "TestContainerSetImagePullSecretsGlobal",
			Values: map[string]string{
				"connectors.enabled":               "true",
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
				"connectors.enabled":                   "true",
				"global.image.pullSecrets[0].name":     "SecretName",
				"connectors.image.pullSecrets[0].name": "SecretNameSubChart",
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
				"connectors.enabled":   "true",
				"connectors.image.tag": "a.b.c",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(s.T(), output, &deployment)

				// then
				expectedContainerImage := "camunda/connectors-bundle:a.b.c"
				containers := deployment.Spec.Template.Spec.Containers
				s.Require().Equal(1, len(containers))
				s.Require().Equal(expectedContainerImage, containers[0].Image)
			},
		}, {
			Name: "TestContainerOverwriteGlobalImageTag",
			Values: map[string]string{
				"connectors.enabled":   "true",
				"connectors.image.tag": "",
				"global.image.tag":     "a.b.c",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(s.T(), output, &deployment)

				// then
				expectedContainerImage := "camunda/connectors-bundle:a.b.c"
				containers := deployment.Spec.Template.Spec.Containers
				s.Require().Equal(1, len(containers))
				s.Require().Equal(expectedContainerImage, containers[0].Image)
			},
		}, {
			Name: "TestContainerOverwriteImageTagWithChartDirectSetting",
			Values: map[string]string{
				"connectors.enabled":   "true",
				"connectors.image.tag": "a.b.c",
				"global.image.tag":     "x.y.z",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(s.T(), output, &deployment)

				// then
				expectedContainerImage := "camunda/connectors-bundle:a.b.c"
				containers := deployment.Spec.Template.Spec.Containers
				s.Require().Equal(1, len(containers))
				s.Require().Equal(expectedContainerImage, containers[0].Image)
			},
		}, {
			Name: "TestContainerSetContainerCommand",
			Values: map[string]string{
				"connectors.enabled":    "true",
				"connectors.command[0]": "printenv",
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
			Name: "TestContainerSetServiceAccountName",
			Values: map[string]string{
				"connectors.enabled":             "true",
				"connectors.serviceAccount.name": "accName",
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
				"connectors.enabled":                      "true",
				"connectors.podSecurityContext.runAsUser": "1000",
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
				"connectors.enabled":                                      "true",
				"connectors.containerSecurityContext.privileged":          "true",
				"connectors.containerSecurityContext.capabilities.add[0]": "NET_ADMIN",
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
				"connectors.enabled":               "true",
				"connectors.nodeSelector.disktype": "ssd",
				"connectors.nodeSelector.cputype":  "arm",
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
				"connectors.enabled": "true",
				"connectors.affinity.nodeAffinity.requiredDuringSchedulingIgnoredDuringExecution.nodeSelectorTerms[0].matchexpressions[0].key":       "kubernetes.io/e2e-az-name",
				"connectors.affinity.nodeAffinity.requiredDuringSchedulingIgnoredDuringExecution.nodeSelectorTerms[0].matchexpressions[0].operator":  "In",
				"connectors.affinity.nodeAffinity.requiredDuringSchedulingIgnoredDuringExecution.nodeSelectorTerms[0].matchexpressions[0].values[0]": "e2e-a1",
				"connectors.affinity.nodeAffinity.requiredDuringSchedulingIgnoredDuringExecution.nodeSelectorTerms[0].matchexpressions[0].values[1]": "e2e-a2",
				"connectors.affinity.nodeAffinity.preferredDuringSchedulingIgnoredDuringExecution[0].weight":                                         "1",
				"connectors.affinity.nodeAffinity.preferredDuringSchedulingIgnoredDuringExecution[0].preference.matchExpressions[0].key":             "another-node-label-key",
				"connectors.affinity.nodeAffinity.preferredDuringSchedulingIgnoredDuringExecution[0].preference.matchExpressions[0].operator":        "In",
				"connectors.affinity.nodeAffinity.preferredDuringSchedulingIgnoredDuringExecution[0].preference.matchExpressions[0].values[0]":       "another-node-label-value",
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
				"connectors.enabled":                 "true",
				"connectors.tolerations[0].key":      "key1",
				"connectors.tolerations[0].operator": "Equal",
				"connectors.tolerations[0].value":    "Value1",
				"connectors.tolerations[0].effect":   "NoSchedule",
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
			Name: "TestContainerShouldOverwriteGlobalImagePullPolicy",
			Values: map[string]string{
				"connectors.enabled":      "true",
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
			Name: "TestContainerStartupProbe",
			Values: map[string]string{
				"connectors.enabled":                          "true",
				"connectors.startupProbe.enabled":             "true",
				"connectors.startupProbe.initialDelaySeconds": "5",
				"connectors.startupProbe.periodSeconds":       "10",
				"connectors.startupProbe.successThreshold":    "1",
				"connectors.startupProbe.failureThreshold":    "5",
				"connectors.startupProbe.timeoutSeconds":      "1",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(s.T(), output, &deployment)

				// then
				probe := deployment.Spec.Template.Spec.Containers[0].StartupProbe

				s.Require().EqualValues(5, probe.InitialDelaySeconds)
				s.Require().EqualValues(10, probe.PeriodSeconds)
				s.Require().EqualValues(1, probe.SuccessThreshold)
				s.Require().EqualValues(5, probe.FailureThreshold)
				s.Require().EqualValues(1, probe.TimeoutSeconds)
			},
		}, {
			Name: "TestContainerReadinessProbe",
			Values: map[string]string{
				"connectors.enabled":                            "true",
				"connectors.readinessProbe.enabled":             "true",
				"connectors.readinessProbe.initialDelaySeconds": "5",
				"connectors.readinessProbe.periodSeconds":       "10",
				"connectors.readinessProbe.successThreshold":    "1",
				"connectors.readinessProbe.failureThreshold":    "5",
				"connectors.readinessProbe.timeoutSeconds":      "1",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(s.T(), output, &deployment)

				// then
				probe := deployment.Spec.Template.Spec.Containers[0].ReadinessProbe

				s.Require().EqualValues(5, probe.InitialDelaySeconds)
				s.Require().EqualValues(10, probe.PeriodSeconds)
				s.Require().EqualValues(1, probe.SuccessThreshold)
				s.Require().EqualValues(5, probe.FailureThreshold)
				s.Require().EqualValues(1, probe.TimeoutSeconds)
			},
		}, {
			Name: "TestContainerLivenessProbe",
			Values: map[string]string{
				"connectors.enabled":                           "true",
				"connectors.livenessProbe.enabled":             "true",
				"connectors.livenessProbe.initialDelaySeconds": "5",
				"connectors.livenessProbe.periodSeconds":       "10",
				"connectors.livenessProbe.successThreshold":    "1",
				"connectors.livenessProbe.failureThreshold":    "5",
				"connectors.livenessProbe.timeoutSeconds":      "1",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(s.T(), output, &deployment)

				// then
				probe := deployment.Spec.Template.Spec.Containers[0].LivenessProbe

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
				"connectors.contextPath":              "/test",
				"connectors.startupProbe.enabled":     "true",
				"connectors.startupProbe.probePath":   "/start",
				"connectors.readinessProbe.enabled":   "true",
				"connectors.readinessProbe.probePath": "/ready",
				"connectors.livenessProbe.enabled":    "true",
				"connectors.livenessProbe.probePath":  "/live",
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
				"connectors.contextPath":              "/test/",
				"connectors.startupProbe.enabled":     "true",
				"connectors.startupProbe.probePath":   "/start",
				"connectors.readinessProbe.enabled":   "true",
				"connectors.readinessProbe.probePath": "/ready",
				"connectors.livenessProbe.enabled":    "true",
				"connectors.livenessProbe.probePath":  "/live",
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
			Name: "TestContainerExtraVolumeMounts",
			Values: map[string]string{
				"connectors.enabled":                        "true",
				"connectors.extraVolumeMounts[0].name":      "someConfig",
				"connectors.extraVolumeMounts[0].mountPath": "/usr/local/config",
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
				s.Require().Equal("someConfig", extraVolumeMount.Name)
				s.Require().Equal("/usr/local/config", extraVolumeMount.MountPath)
			},
		}, {
			Name: "TestContainerExtraVolumes",
			Values: map[string]string{
				"connectors.enabled":                               "true",
				"connectors.extraVolumes[0].name":                  "myExtraVolume",
				"connectors.extraVolumes[0].configMap.name":        "otherConfigMap",
				"connectors.extraVolumes[0].configMap.defaultMode": "744",
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
				s.Require().Equal("myExtraVolume", extraVolume.Name)
				s.Require().NotNil(*extraVolume.ConfigMap)
				s.Require().Equal("otherConfigMap", extraVolume.ConfigMap.Name)
				s.Require().EqualValues(744, *extraVolume.ConfigMap.DefaultMode)
			},
		}, {
			Name: "TestContainerSetInboundModeDisabled",
			Values: map[string]string{
				"connectors.enabled":      "true",
				"connectors.inbound.mode": "disabled",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(s.T(), output, &deployment)

				// then
				env := deployment.Spec.Template.Spec.Containers[0].Env

				for _, envvar := range env {
					s.Require().NotEqual("CAMUNDA_OPERATE_CLIENT_KEYCLOAK-TOKEN-URL", envvar.Name)
					s.Require().NotEqual("CAMUNDA_OPERATE_CLIENT_URL", envvar.Name)
					s.Require().NotEqual("CAMUNDA_OPERATE_CLIENT_USERNAME", envvar.Name)
					s.Require().NotEqual("CAMUNDA_OPERATE_CLIENT_PASSWORD", envvar.Name)
					s.Require().NotEqual("CAMUNDA_OPERATE_CLIENT_CLIENT-ID", envvar.Name)
					s.Require().NotEqual("CAMUNDA_OPERATE_CLIENT_CLIENT-SECRET", envvar.Name)
				}

				s.Require().Contains(env, corev1.EnvVar{Name: "ZEEBE_CLIENT_BROKER_GATEWAY-ADDRESS", Value: "camunda-platform-test-zeebe-gateway:26500"})
				s.Require().Contains(env, corev1.EnvVar{Name: "ZEEBE_CLIENT_SECURITY_PLAINTEXT", Value: "true"})
				s.Require().Contains(env, corev1.EnvVar{Name: "CAMUNDA_CONNECTOR_POLLING_ENABLED", Value: "false"})
				s.Require().Contains(env, corev1.EnvVar{Name: "CAMUNDA_CONNECTOR_WEBHOOK_ENABLED", Value: "false"})
			},
		}, {
			Name: "TestContainerSetInboundModeCredentials",
			Values: map[string]string{
				"connectors.enabled":           "true",
				"connectors.inbound.mode":      "credentials",
				"global.identity.auth.enabled": "false",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(s.T(), output, &deployment)

				// then
				env := deployment.Spec.Template.Spec.Containers[0].Env

				for _, envvar := range env {
					s.Require().NotEqual("CAMUNDA_CONNECTOR_POLLING_ENABLED", envvar.Name)
					s.Require().NotEqual("CAMUNDA_CONNECTOR_WEBHOOK_ENABLED", envvar.Name)
					s.Require().NotEqual("CAMUNDA_OPERATE_CLIENT_KEYCLOAK-TOKEN-URL", envvar.Name)
					s.Require().NotEqual("CAMUNDA_OPERATE_CLIENT_CLIENT-ID", envvar.Name)
					s.Require().NotEqual("CAMUNDA_OPERATE_CLIENT_CLIENT-SECRET", envvar.Name)
					s.Require().NotEqual("SPRING_MAIN_WEB-APPLICATION-TYPE", envvar.Name)
				}

				s.Require().Contains(env, corev1.EnvVar{Name: "ZEEBE_CLIENT_BROKER_GATEWAY-ADDRESS", Value: "camunda-platform-test-zeebe-gateway:26500"})
				s.Require().Contains(env, corev1.EnvVar{Name: "ZEEBE_CLIENT_SECURITY_PLAINTEXT", Value: "true"})
				s.Require().Contains(env, corev1.EnvVar{Name: "CAMUNDA_OPERATE_CLIENT_URL", Value: "http://camunda-platform-test-operate:80"})
				s.Require().Contains(env, corev1.EnvVar{Name: "CAMUNDA_OPERATE_CLIENT_USERNAME", Value: "connectors"})
				s.Require().Contains(
					env,
					corev1.EnvVar{
						Name:      "CAMUNDA_OPERATE_CLIENT_PASSWORD",
						ValueFrom: &corev1.EnvVarSource{SecretKeyRef: &corev1.SecretKeySelector{LocalObjectReference: corev1.LocalObjectReference{Name: "camunda-platform-test-connectors-auth-credentials"}, Key: "connectors-secret"}},
					})
			},
		}, {
			Name: "TestContainerSetInboundModeOauthIdentity",
			Values: map[string]string{
				"connectors.enabled":           "true",
				"connectors.inbound.mode":      "oauth",
				"global.identity.auth.enabled": "true",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(s.T(), output, &deployment)

				// then
				env := deployment.Spec.Template.Spec.Containers[0].Env

				for _, envvar := range env {
					s.Require().NotEqual("CAMUNDA_CONNECTOR_POLLING_ENABLED", envvar.Name)
					s.Require().NotEqual("CAMUNDA_CONNECTOR_WEBHOOK_ENABLED", envvar.Name)
					s.Require().NotEqual("SPRING_MAIN_WEB-APPLICATION-TYPE", envvar.Name)
					s.Require().NotEqual("CAMUNDA_OPERATE_CLIENT_USERNAME", envvar.Name)
					s.Require().NotEqual("CAMUNDA_OPERATE_CLIENT_PASSWORD", envvar.Name)
				}

				s.Require().Contains(env, corev1.EnvVar{Name: "ZEEBE_CLIENT_BROKER_GATEWAY-ADDRESS", Value: "camunda-platform-test-zeebe-gateway:26500"})
				s.Require().Contains(env, corev1.EnvVar{Name: "ZEEBE_CLIENT_SECURITY_PLAINTEXT", Value: "true"})
				s.Require().Contains(env, corev1.EnvVar{Name: "ZEEBE_CLIENT_ID", Value: "zeebe"})
				s.Require().Contains(
					env,
					corev1.EnvVar{
						Name: "ZEEBE_CLIENT_SECRET",
						ValueFrom: &corev1.EnvVarSource{
							SecretKeyRef: &corev1.SecretKeySelector{
								LocalObjectReference: corev1.LocalObjectReference{Name: "camunda-platform-test-zeebe-identity-secret"},
								Key:                  "zeebe-secret",
							},
						},
					})
				s.Require().Contains(env, corev1.EnvVar{Name: "ZEEBE_AUTHORIZATION_SERVER_URL", Value: "http://camunda-platform-test-keycloak:80/auth/realms/camunda-platform/protocol/openid-connect/token"})
				s.Require().Contains(env, corev1.EnvVar{Name: "ZEEBE_TOKEN_AUDIENCE", Value: "zeebe-api"})
				s.Require().Contains(env, corev1.EnvVar{Name: "CAMUNDA_OPERATE_CLIENT_URL", Value: "http://camunda-platform-test-operate:80"})
				s.Require().Contains(env, corev1.EnvVar{Name: "CAMUNDA_IDENTITY_AUDIENCE", Value: "operate-api"})
				s.Require().Contains(env, corev1.EnvVar{Name: "CAMUNDA_IDENTITY_CLIENT_ID", Value: "connectors"})
				s.Require().Contains(
					env,
					corev1.EnvVar{
						Name: "CAMUNDA_IDENTITY_CLIENT_SECRET",
						ValueFrom: &corev1.EnvVarSource{
							SecretKeyRef: &corev1.SecretKeySelector{
								LocalObjectReference: corev1.LocalObjectReference{Name: "camunda-platform-test-connectors-identity-secret"},
								Key:                  "connectors-secret",
							},
						},
					})
			},
		}, {
			Name: "TestContainerSetContextPath",
			Values: map[string]string{
				"connectors.enabled":     "true",
				"connectors.contextPath": "/connectors",
			},
			Verifier: func(t *testing.T, output string, err error) {
				var deployment appsv1.Deployment
				helm.UnmarshalK8SYaml(s.T(), output, &deployment)

				// then
				env := deployment.Spec.Template.Spec.Containers[0].Env

				s.Require().Contains(env, corev1.EnvVar{Name: "SERVER_SERVLET_CONTEXT_PATH", Value: "/connectors"})
			},
		}, {
			Name: "TestContainerSetSidecar",
			Values: map[string]string{
				"connectors.sidecars[0].name":                   "nginx",
				"connectors.sidecars[0].image":                  "nginx:latest",
				"connectors.sidecars[0].ports[0].containerPort": "80",
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
				"connectors.initContainers[0].name":                   "nginx",
				"connectors.initContainers[0].image":                  "nginx:latest",
				"connectors.initContainers[0].ports[0].containerPort": "80",
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
		},
	}

	testhelpers.RunTestCasesE(s.T(), s.chartPath, s.release, s.namespace, s.templates, testCases)
}
