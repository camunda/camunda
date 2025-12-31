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

package optimize

import (
	"path/filepath"
	"strings"
	"testing"

	"github.com/gruntwork-io/terratest/modules/helm"
	"github.com/gruntwork-io/terratest/modules/k8s"
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
		templates: []string{"templates/optimize/deployment.yaml"},
	})
}

func (s *deploymentTemplateTest) TestContainerSetPodLabels() {
	// given
	options := &helm.Options{
		SetValues: map[string]string{
			"optimize.podLabels.foo": "bar",
		},
		KubectlOptions: k8s.NewKubectlOptions("", "", s.namespace),
	}

	// when
	output := helm.RenderTemplate(s.T(), options, s.chartPath, s.release, s.templates)
	var deployment appsv1.Deployment
	helm.UnmarshalK8SYaml(s.T(), output, &deployment)

	// then
	s.Require().Equal("bar", deployment.Spec.Template.Labels["foo"])
}

func (s *deploymentTemplateTest) TestContainerSetPodAnnotations() {
	// given
	options := &helm.Options{
		SetValues: map[string]string{
			"optimize.podAnnotations.foo": "bar",
			"optimize.podAnnotations.foz": "baz",
		},
		KubectlOptions: k8s.NewKubectlOptions("", "", s.namespace),
	}

	// when
	output := helm.RenderTemplate(s.T(), options, s.chartPath, s.release, s.templates)
	var deployment appsv1.Deployment
	helm.UnmarshalK8SYaml(s.T(), output, &deployment)

	// then
	s.Require().Equal("bar", deployment.Spec.Template.Annotations["foo"])
	s.Require().Equal("baz", deployment.Spec.Template.Annotations["foz"])
}

func (s *deploymentTemplateTest) TestContainerSetGlobalAnnotations() {
	// given
	options := &helm.Options{
		SetValues: map[string]string{
			"global.annotations.foo": "bar",
		},
		KubectlOptions: k8s.NewKubectlOptions("", "", s.namespace),
	}

	// when
	output := helm.RenderTemplate(s.T(), options, s.chartPath, s.release, s.templates)
	var deployment appsv1.Deployment
	helm.UnmarshalK8SYaml(s.T(), output, &deployment)

	// then
	s.Require().Equal("bar", deployment.ObjectMeta.Annotations["foo"])
}

func (s *deploymentTemplateTest) TestContainerSetImageNameSubChart() {
	// given
	options := &helm.Options{
		SetValues: map[string]string{
			"global.image.registry":     "global.custom.registry.io",
			"global.image.tag":          "8.x.x",
			"optimize.image.registry":   "subchart.custom.registry.io",
			"optimize.image.repository": "camunda/optimize-test",
			"optimize.image.tag":        "snapshot",
		},
		KubectlOptions: k8s.NewKubectlOptions("", "", s.namespace),
	}

	// when
	output := helm.RenderTemplate(s.T(), options, s.chartPath, s.release, s.templates)
	var deployment appsv1.Deployment
	helm.UnmarshalK8SYaml(s.T(), output, &deployment)

	// then
	container := deployment.Spec.Template.Spec.Containers[0]
	s.Require().Equal(container.Image, "subchart.custom.registry.io/camunda/optimize-test:snapshot")
}

func (s *deploymentTemplateTest) TestContainerSetImagePullSecretsGlobal() {
	// given
	options := &helm.Options{
		SetValues: map[string]string{
			"global.image.pullSecrets[0].name": "SecretName",
		},
		KubectlOptions: k8s.NewKubectlOptions("", "", s.namespace),
	}

	// when
	output := helm.RenderTemplate(s.T(), options, s.chartPath, s.release, s.templates)
	var deployment appsv1.Deployment
	helm.UnmarshalK8SYaml(s.T(), output, &deployment)

	// then
	s.Require().Equal("SecretName", deployment.Spec.Template.Spec.ImagePullSecrets[0].Name)
}

func (s *deploymentTemplateTest) TestContainerSetImagePullSecretsSubChart() {
	// given
	options := &helm.Options{
		SetValues: map[string]string{
			"global.image.pullSecrets[0].name":   "SecretName",
			"optimize.image.pullSecrets[0].name": "SecretNameSubChart",
		},
		KubectlOptions: k8s.NewKubectlOptions("", "", s.namespace),
	}

	// when
	output := helm.RenderTemplate(s.T(), options, s.chartPath, s.release, s.templates)
	var deployment appsv1.Deployment
	helm.UnmarshalK8SYaml(s.T(), output, &deployment)

	// then
	s.Require().Equal("SecretNameSubChart", deployment.Spec.Template.Spec.ImagePullSecrets[0].Name)
}

func (s *deploymentTemplateTest) TestContainerOverwriteImageTag() {
	// given
	options := &helm.Options{
		SetValues: map[string]string{
			"optimize.image.tag": "a.b.c",
		},
		KubectlOptions: k8s.NewKubectlOptions("", "", s.namespace),
		ExtraArgs:      map[string][]string{"install": {"--debug"}},
	}

	// when
	output := helm.RenderTemplate(s.T(), options, s.chartPath, s.release, s.templates)
	var deployment appsv1.Deployment
	helm.UnmarshalK8SYaml(s.T(), output, &deployment)

	// then
	expectedContainerImage := "camunda/optimize:a.b.c"
	containers := deployment.Spec.Template.Spec.Containers
	s.Require().Equal(1, len(containers))
	s.Require().Equal(expectedContainerImage, containers[0].Image)
}

func (s *deploymentTemplateTest) TestContainerNotOverwriteGlobalImageTag() {
	// optimize uses a different version schema which means we shouldn't overwrite the tag via the global value

	// given
	options := &helm.Options{
		SetValues: map[string]string{
			"global.image.tag": "a.b.c",
		},
		KubectlOptions: k8s.NewKubectlOptions("", "", s.namespace),
		ExtraArgs:      map[string][]string{"install": {"--debug"}},
	}

	// when
	output := helm.RenderTemplate(s.T(), options, s.chartPath, s.release, s.templates)
	var deployment appsv1.Deployment
	helm.UnmarshalK8SYaml(s.T(), output, &deployment)

	// then
	expectedContainerImage := "camunda/optimize:a.b.c"
	containers := deployment.Spec.Template.Spec.Containers
	s.Require().Equal(1, len(containers))
	s.Require().NotEqual(expectedContainerImage, containers[0].Image)
}

func (s *deploymentTemplateTest) TestContainerOverwriteImageTagWithChartDirectSetting() {
	// given
	options := &helm.Options{
		SetValues: map[string]string{
			"global.image.tag":   "x.y.z",
			"optimize.image.tag": "a.b.c",
		},
		KubectlOptions: k8s.NewKubectlOptions("", "", s.namespace),
		ExtraArgs:      map[string][]string{"install": {"--debug"}},
	}

	// when
	output := helm.RenderTemplate(s.T(), options, s.chartPath, s.release, s.templates)
	var deployment appsv1.Deployment
	helm.UnmarshalK8SYaml(s.T(), output, &deployment)

	// then
	expectedContainerImage := "camunda/optimize:a.b.c"
	containers := deployment.Spec.Template.Spec.Containers
	s.Require().Equal(1, len(containers))
	s.Require().Equal(expectedContainerImage, containers[0].Image)
}

func (s *deploymentTemplateTest) TestContainerSetContainerCommand() {
	// given
	options := &helm.Options{
		SetValues: map[string]string{
			"optimize.command[0]": "printenv",
		},
		KubectlOptions: k8s.NewKubectlOptions("", "", s.namespace),
		ExtraArgs:      map[string][]string{"install": {"--debug"}},
	}

	// when
	output := helm.RenderTemplate(s.T(), options, s.chartPath, s.release, s.templates)
	var deployment appsv1.Deployment
	helm.UnmarshalK8SYaml(s.T(), output, &deployment)

	// then
	containers := deployment.Spec.Template.Spec.Containers
	s.Require().Equal(1, len(containers))
	s.Require().Equal(1, len(containers[0].Command))
	s.Require().Equal("printenv", containers[0].Command[0])
}

func (s *deploymentTemplateTest) TestContainerSetExtraVolumes() {
	//finding out the length of volumes array before addition of new volume
	var deploymentBefore appsv1.Deployment
	before := helm.RenderTemplate(s.T(), &helm.Options{}, s.chartPath, s.release, s.templates)
	helm.UnmarshalK8SYaml(s.T(), before, &deploymentBefore)
	volumeLenBefore := len(deploymentBefore.Spec.Template.Spec.Volumes)
	// given
	options := &helm.Options{
		SetValues: map[string]string{
			"optimize.extraVolumes[0].name":                  "extraVolume",
			"optimize.extraVolumes[0].configMap.name":        "otherConfigMap",
			"optimize.extraVolumes[0].configMap.defaultMode": "744",
		},
		KubectlOptions: k8s.NewKubectlOptions("", "", s.namespace),
		ExtraArgs:      map[string][]string{"install": {"--debug"}},
	}

	// when
	output := helm.RenderTemplate(s.T(), options, s.chartPath, s.release, s.templates)
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
}

func (s *deploymentTemplateTest) TestContainerSetExtraVolumeMounts() {
	//finding out the length of containers and volumeMounts array before addition of new volumeMount
	var deploymentBefore appsv1.Deployment
	before := helm.RenderTemplate(s.T(), &helm.Options{}, s.chartPath, s.release, s.templates)
	helm.UnmarshalK8SYaml(s.T(), before, &deploymentBefore)
	containerLenBefore := len(deploymentBefore.Spec.Template.Spec.Containers)
	volumeMountLenBefore := len(deploymentBefore.Spec.Template.Spec.Containers[0].VolumeMounts)
	// given
	options := &helm.Options{
		SetValues: map[string]string{
			"optimize.extraVolumeMounts[0].name":      "otherConfigMap",
			"optimize.extraVolumeMounts[0].mountPath": "/usr/local/config",
		},
		KubectlOptions: k8s.NewKubectlOptions("", "", s.namespace),
		ExtraArgs:      map[string][]string{"install": {"--debug"}},
	}

	// when
	output := helm.RenderTemplate(s.T(), options, s.chartPath, s.release, s.templates)
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
}

func (s *deploymentTemplateTest) TestContainerSetExtraVolumesAndMounts() {
	//finding out the length of volumes, volumemounts array before addition of new volume
	var deploymentBefore appsv1.Deployment
	before := helm.RenderTemplate(s.T(), &helm.Options{}, s.chartPath, s.release, s.templates)
	helm.UnmarshalK8SYaml(s.T(), before, &deploymentBefore)
	volumeLenBefore := len(deploymentBefore.Spec.Template.Spec.Volumes)
	volumeMountLenBefore := len(deploymentBefore.Spec.Template.Spec.Containers[0].VolumeMounts)
	containerLenBefore := len(deploymentBefore.Spec.Template.Spec.Containers)
	// given
	options := &helm.Options{
		SetValues: map[string]string{
			"optimize.extraVolumeMounts[0].name":             "otherConfigMap",
			"optimize.extraVolumeMounts[0].mountPath":        "/usr/local/config",
			"optimize.extraVolumes[0].name":                  "extraVolume",
			"optimize.extraVolumes[0].configMap.name":        "otherConfigMap",
			"optimize.extraVolumes[0].configMap.defaultMode": "744",
		},
		KubectlOptions: k8s.NewKubectlOptions("", "", s.namespace),
		ExtraArgs:      map[string][]string{"install": {"--debug"}},
	}

	// when
	output := helm.RenderTemplate(s.T(), options, s.chartPath, s.release, s.templates)
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
}

func (s *deploymentTemplateTest) TestContainerSetServiceAccountName() {
	// given
	options := &helm.Options{
		SetValues: map[string]string{
			"optimize.serviceAccount.name": "accName",
		},
		KubectlOptions: k8s.NewKubectlOptions("", "", s.namespace),
		ExtraArgs:      map[string][]string{"install": {"--debug"}},
	}

	// when
	output := helm.RenderTemplate(s.T(), options, s.chartPath, s.release, s.templates)
	var deployment appsv1.Deployment
	helm.UnmarshalK8SYaml(s.T(), output, &deployment)

	// then
	serviceAccName := deployment.Spec.Template.Spec.ServiceAccountName
	s.Require().Equal("accName", serviceAccName)
}

func (s *deploymentTemplateTest) TestPodSetSecurityContext() {
	// given
	options := &helm.Options{
		SetValues: map[string]string{
			"optimize.podSecurityContext.runAsUser": "1000",
		},
		KubectlOptions: k8s.NewKubectlOptions("", "", s.namespace),
	}

	// when
	output := helm.RenderTemplate(s.T(), options, s.chartPath, s.release, s.templates)
	var deployment appsv1.Deployment
	helm.UnmarshalK8SYaml(s.T(), output, &deployment)

	// then
	securityContext := deployment.Spec.Template.Spec.SecurityContext
	s.Require().EqualValues(1000, *securityContext.RunAsUser)
}

func (s *deploymentTemplateTest) TestContainerSetSecurityContext() {
	// given
	options := &helm.Options{
		SetValues: map[string]string{
			"optimize.containerSecurityContext.privileged":          "true",
			"optimize.containerSecurityContext.capabilities.add[0]": "NET_ADMIN",
		},
		KubectlOptions: k8s.NewKubectlOptions("", "", s.namespace),
	}

	// when
	output := helm.RenderTemplate(s.T(), options, s.chartPath, s.release, s.templates)
	var deployment appsv1.Deployment
	helm.UnmarshalK8SYaml(s.T(), output, &deployment)

	// then
	securityContext := deployment.Spec.Template.Spec.Containers[0].SecurityContext
	s.Require().True(*securityContext.Privileged)
	s.Require().EqualValues("NET_ADMIN", securityContext.Capabilities.Add[0])
}

// https://kubernetes.io/docs/concepts/scheduling-eviction/assign-pod-node/#nodeselector
func (s *deploymentTemplateTest) TestContainerSetNodeSelector() {
	// given
	options := &helm.Options{
		SetValues: map[string]string{
			"optimize.nodeSelector.disktype": "ssd",
			"optimize.nodeSelector.cputype":  "arm",
		},
		KubectlOptions: k8s.NewKubectlOptions("", "", s.namespace),
	}

	// when
	output := helm.RenderTemplate(s.T(), options, s.chartPath, s.release, s.templates)
	var deployment appsv1.Deployment
	helm.UnmarshalK8SYaml(s.T(), output, &deployment)

	// then
	s.Require().Equal("ssd", deployment.Spec.Template.Spec.NodeSelector["disktype"])
	s.Require().Equal("arm", deployment.Spec.Template.Spec.NodeSelector["cputype"])
}

// https://kubernetes.io/docs/concepts/scheduling-eviction/assign-pod-node/#node-affinity
func (s *deploymentTemplateTest) TestContainerSetAffinity() {
	// given

	//affinity:
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

	options := &helm.Options{
		SetValues: map[string]string{
			"optimize.affinity.nodeAffinity.requiredDuringSchedulingIgnoredDuringExecution.nodeSelectorTerms[0].matchexpressions[0].key":       "kubernetes.io/e2e-az-name",
			"optimize.affinity.nodeAffinity.requiredDuringSchedulingIgnoredDuringExecution.nodeSelectorTerms[0].matchexpressions[0].operator":  "In",
			"optimize.affinity.nodeAffinity.requiredDuringSchedulingIgnoredDuringExecution.nodeSelectorTerms[0].matchexpressions[0].values[0]": "e2e-a1",
			"optimize.affinity.nodeAffinity.requiredDuringSchedulingIgnoredDuringExecution.nodeSelectorTerms[0].matchexpressions[0].values[1]": "e2e-a2",
			"optimize.affinity.nodeAffinity.preferredDuringSchedulingIgnoredDuringExecution[0].weight":                                         "1",
			"optimize.affinity.nodeAffinity.preferredDuringSchedulingIgnoredDuringExecution[0].preference.matchExpressions[0].key":             "another-node-label-key",
			"optimize.affinity.nodeAffinity.preferredDuringSchedulingIgnoredDuringExecution[0].preference.matchExpressions[0].operator":        "In",
			"optimize.affinity.nodeAffinity.preferredDuringSchedulingIgnoredDuringExecution[0].preference.matchExpressions[0].values[0]":       "another-node-label-value",
		},
		KubectlOptions: k8s.NewKubectlOptions("", "", s.namespace),
	}

	// when
	output := helm.RenderTemplate(s.T(), options, s.chartPath, s.release, s.templates)
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
}

// https://kubernetes.io/docs/concepts/scheduling-eviction/taint-and-toleration
func (s *deploymentTemplateTest) TestContainerSetTolerations() {
	// given

	//tolerations:
	//- key: "key1"
	//  operator: "Equal"
	//  value: "value1"
	//  effect: "NoSchedule"

	options := &helm.Options{
		SetValues: map[string]string{
			"optimize.tolerations[0].key":      "key1",
			"optimize.tolerations[0].operator": "Equal",
			"optimize.tolerations[0].value":    "Value1",
			"optimize.tolerations[0].effect":   "NoSchedule",
		},
		KubectlOptions: k8s.NewKubectlOptions("", "", s.namespace),
	}

	// when
	output := helm.RenderTemplate(s.T(), options, s.chartPath, s.release, s.templates)
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
}

func (s *deploymentTemplateTest) TestContainerShouldSetOptimizeIdentitySecretValue() {
	// given
	options := &helm.Options{
		SetValues: map[string]string{
			"global.identity.auth.optimize.existingSecret": "secretValue",
		},
		KubectlOptions: k8s.NewKubectlOptions("", "", s.namespace),
		ExtraArgs:      map[string][]string{"install": {"--debug"}},
	}

	// when
	output := helm.RenderTemplate(s.T(), options, s.chartPath, s.release, s.templates)
	var deployment appsv1.Deployment
	helm.UnmarshalK8SYaml(s.T(), output, &deployment)

	// then
	env := deployment.Spec.Template.Spec.Containers[0].Env
	s.Require().Contains(env,
		corev1.EnvVar{
			Name: "CAMUNDA_OPTIMIZE_IDENTITY_CLIENTSECRET",
			ValueFrom: &corev1.EnvVarSource{
				SecretKeyRef: &corev1.SecretKeySelector{
					LocalObjectReference: corev1.LocalObjectReference{Name: "camunda-platform-test-optimize-identity-secret"},
					Key:                  "optimize-secret",
				},
			},
		})
}

func (s *deploymentTemplateTest) TestContainerShouldSetOptimizeIdentitySecretViaReference() {
	// given
	options := &helm.Options{
		SetValues: map[string]string{
			"global.identity.auth.optimize.existingSecret.name": "ownExistingSecret",
		},
		KubectlOptions: k8s.NewKubectlOptions("", "", s.namespace),
		ExtraArgs:      map[string][]string{"install": {"--debug"}},
	}

	// when
	output := helm.RenderTemplate(s.T(), options, s.chartPath, s.release, s.templates)
	var deployment appsv1.Deployment
	helm.UnmarshalK8SYaml(s.T(), output, &deployment)

	// then
	env := deployment.Spec.Template.Spec.Containers[0].Env
	s.Require().Contains(env,
		corev1.EnvVar{
			Name: "CAMUNDA_OPTIMIZE_IDENTITY_CLIENTSECRET",
			ValueFrom: &corev1.EnvVarSource{
				SecretKeyRef: &corev1.SecretKeySelector{
					LocalObjectReference: corev1.LocalObjectReference{Name: "ownExistingSecret"},
					Key:                  "optimize-secret",
				},
			},
		})
}

func (s *deploymentTemplateTest) TestContainerShouldSetTheRightKeycloakServiceUrl() {
	// given
	options := &helm.Options{
		SetValues: map[string]string{
			"global.identity.keycloak.url.protocol": "http",
			"global.identity.keycloak.url.host":     "keycloak",
			"global.identity.keycloak.url.port":     "80",
		},
		KubectlOptions: k8s.NewKubectlOptions("", "", s.namespace),
		ExtraArgs:      map[string][]string{"install": {"--debug"}},
	}

	// when
	output := helm.RenderTemplate(s.T(), options, s.chartPath, s.release, s.templates)
	var deployment appsv1.Deployment
	helm.UnmarshalK8SYaml(s.T(), output, &deployment)

	// then
	env := deployment.Spec.Template.Spec.Containers[0].Env
	s.Require().Contains(env,
		corev1.EnvVar{
			Name:  "CAMUNDA_OPTIMIZE_IDENTITY_ISSUER_BACKEND_URL",
			Value: "http://keycloak:80/auth/realms/camunda-platform",
		})
}

func (s *deploymentTemplateTest) TestContainerShouldOverwriteGlobalImagePullPolicy() {
	// given
	options := &helm.Options{
		SetValues: map[string]string{
			"global.image.pullPolicy": "Always",
		},
		KubectlOptions: k8s.NewKubectlOptions("", "", s.namespace),
	}

	// when
	output := helm.RenderTemplate(s.T(), options, s.chartPath, s.release, s.templates)
	var deployment appsv1.Deployment
	helm.UnmarshalK8SYaml(s.T(), output, &deployment)

	// then
	expectedPullPolicy := corev1.PullAlways
	containers := deployment.Spec.Template.Spec.Containers
	s.Require().Equal(1, len(containers))
	pullPolicy := containers[0].ImagePullPolicy
	s.Require().Equal(expectedPullPolicy, pullPolicy)
}

func (s *deploymentTemplateTest) TestContainerShouldAddContextPath() {
	// given
	options := &helm.Options{
		SetValues: map[string]string{
			"optimize.contextPath": "/optimize",
		},
		KubectlOptions: k8s.NewKubectlOptions("", "", s.namespace),
		ExtraArgs:      map[string][]string{"install": {"--debug"}},
	}

	// when
	output := helm.RenderTemplate(s.T(), options, s.chartPath, s.release, s.templates)
	var deployment appsv1.Deployment
	helm.UnmarshalK8SYaml(s.T(), output, &deployment)

	// then
	env := deployment.Spec.Template.Spec.Containers[0].Env
	s.Require().Contains(env,
		corev1.EnvVar{
			Name:  "CAMUNDA_OPTIMIZE_CONTEXT_PATH",
			Value: "/optimize",
		},
	)
}

// readinessProbe is enabled by default so it's tested by golden files.

func (s *deploymentTemplateTest) TestContainerStartupProbe() {
	// given
	options := &helm.Options{
		SetValues: map[string]string{
			"optimize.startupProbe.enabled":             "true",
			"optimize.startupProbe.probePath":           "/healthz",
			"optimize.startupProbe.initialDelaySeconds": "5",
			"optimize.startupProbe.periodSeconds":       "10",
			"optimize.startupProbe.successThreshold":    "1",
			"optimize.startupProbe.failureThreshold":    "5",
			"optimize.startupProbe.timeoutSeconds":      "1",
		},
		KubectlOptions: k8s.NewKubectlOptions("", "", s.namespace),
		ExtraArgs:      map[string][]string{"install": {"--debug"}},
	}

	// when
	output := helm.RenderTemplate(s.T(), options, s.chartPath, s.release, s.templates)
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
}

func (s *deploymentTemplateTest) TestContainerLivenessProbe() {
	// given
	options := &helm.Options{
		SetValues: map[string]string{
			"optimize.livenessProbe.enabled":             "true",
			"optimize.livenessProbe.probePath":           "/healthz",
			"optimize.livenessProbe.initialDelaySeconds": "5",
			"optimize.livenessProbe.periodSeconds":       "10",
			"optimize.livenessProbe.successThreshold":    "1",
			"optimize.livenessProbe.failureThreshold":    "5",
			"optimize.livenessProbe.timeoutSeconds":      "1",
		},
		KubectlOptions: k8s.NewKubectlOptions("", "", s.namespace),
		ExtraArgs:      map[string][]string{"install": {"--debug"}},
	}

	// when
	output := helm.RenderTemplate(s.T(), options, s.chartPath, s.release, s.templates)
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
}

func (s *deploymentTemplateTest) TestContainerProbesWithContextPath() {
	// given
	options := &helm.Options{
		SetValues: map[string]string{
			"optimize.contextPath":              "/test",
			"optimize.startupProbe.enabled":     "true",
			"optimize.startupProbe.probePath":   "/start",
			"optimize.readinessProbe.enabled":   "true",
			"optimize.readinessProbe.probePath": "/ready",
			"optimize.livenessProbe.enabled":    "true",
			"optimize.livenessProbe.probePath":  "/live",
		},
		KubectlOptions: k8s.NewKubectlOptions("", "", s.namespace),
		ExtraArgs:      map[string][]string{"install": {"--debug"}},
	}

	// when
	output := helm.RenderTemplate(s.T(), options, s.chartPath, s.release, s.templates)
	var deployment appsv1.Deployment
	helm.UnmarshalK8SYaml(s.T(), output, &deployment)

	// then
	probe := deployment.Spec.Template.Spec.Containers[0]

	s.Require().Equal("/test/start", probe.StartupProbe.HTTPGet.Path)
	s.Require().Equal("/test/ready", probe.ReadinessProbe.HTTPGet.Path)
	s.Require().Equal("/test/live", probe.LivenessProbe.HTTPGet.Path)
}

func (s *deploymentTemplateTest) TestContainerSetSidecar() {
	// given
	options := &helm.Options{
		SetValues: map[string]string{
			"optimize.sidecars[0].name":                   "nginx",
			"optimize.sidecars[0].image":                  "nginx:latest",
			"optimize.sidecars[0].ports[0].containerPort": "80",
		},
		KubectlOptions: k8s.NewKubectlOptions("", "", s.namespace),
	}

	// when
	output := helm.RenderTemplate(s.T(), options, s.chartPath, s.release, s.templates)
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
}

func (s *deploymentTemplateTest) TestInitContainers() {
	// given
	options := &helm.Options{
		SetValues: map[string]string{
			"optimize.initContainers[0].name":                   "nginx",
			"optimize.initContainers[0].image":                  "nginx:latest",
			"optimize.initContainers[0].ports[0].containerPort": "80",
		},
		KubectlOptions: k8s.NewKubectlOptions("", "", s.namespace),
	}

	// when
	output := helm.RenderTemplate(s.T(), options, s.chartPath, s.release, s.templates)
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
}

func (s *deploymentTemplateTest) TestOptimizeMultiTenancyEnabled() {
	// given
	options := &helm.Options{
		SetValues: map[string]string{
			"global.multitenancy.enabled": "true",
			"identity.postgresql.enabled": "true",
		},
		KubectlOptions: k8s.NewKubectlOptions("", "", s.namespace),
	}

	// when
	output := helm.RenderTemplate(s.T(), options, s.chartPath, s.release, s.templates)
	var deployment appsv1.Deployment
	helm.UnmarshalK8SYaml(s.T(), output, &deployment)

	// then
	env := deployment.Spec.Template.Spec.Containers[0].Env
	s.Require().Contains(env, corev1.EnvVar{Name: "CAMUNDA_OPTIMIZE_MULTITENANCY_ENABLED", Value: "true"})
}

func (s *deploymentTemplateTest) TestOptimizeCustomZeebeName() {
	// given
	options := &helm.Options{
		SetValues: map[string]string{
			"global.elasticsearch.prefix": "custom-prefix",
		},
		KubectlOptions: k8s.NewKubectlOptions("", "", s.namespace),
	}

	// when
	output := helm.RenderTemplate(s.T(), options, s.chartPath, s.release, s.templates)
	var deployment appsv1.Deployment
	helm.UnmarshalK8SYaml(s.T(), output, &deployment)

	// then
	env := deployment.Spec.Template.Spec.Containers[0].Env
	s.Require().Contains(env, corev1.EnvVar{Name: "CAMUNDA_OPTIMIZE_ZEEBE_NAME", Value: "custom-prefix"})
}