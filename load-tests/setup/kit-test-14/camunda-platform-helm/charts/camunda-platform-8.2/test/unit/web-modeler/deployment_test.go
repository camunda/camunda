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

	corev1 "k8s.io/api/core/v1"

	"github.com/gruntwork-io/terratest/modules/helm"
	"github.com/gruntwork-io/terratest/modules/k8s"
	"github.com/gruntwork-io/terratest/modules/random"
	"github.com/stretchr/testify/require"
	"github.com/stretchr/testify/suite"
	appsv1 "k8s.io/api/apps/v1"
)

type deploymentTemplateTest struct {
	suite.Suite
	chartPath string
	release   string
	namespace string
	component string
	templates []string
}

func TestDeploymentTemplate(t *testing.T) {
	t.Parallel()

	chartPath, err := filepath.Abs("../../../")
	require.NoError(t, err)
	components := []string{"restapi", "webapp", "websockets"}

	for _, component := range components {
		suite.Run(t, &deploymentTemplateTest{
			chartPath: chartPath,
			release:   "camunda-platform-test",
			namespace: "camunda-platform-" + strings.ToLower(random.UniqueId()),
			component: component,
			templates: []string{"templates/web-modeler/deployment-" + component + ".yaml"},
		})
	}
}

func (s *deploymentTemplateTest) TestContainerOverrideAppName() {
	// given
	options := &helm.Options{
		SetValues: map[string]string{
			"webModeler.enabled":                  "true",
			"webModeler.restapi.mail.fromAddress": "example@example.com",
			"webModeler.nameOverride":             "foo",
		},
		KubectlOptions: k8s.NewKubectlOptions("", "", s.namespace),
	}

	// when
	output := helm.RenderTemplate(s.T(), options, s.chartPath, s.release, s.templates)
	var deployment appsv1.Deployment
	helm.UnmarshalK8SYaml(s.T(), output, &deployment)

	// then
	s.Require().Equal("camunda-platform-test-foo-"+s.component, deployment.ObjectMeta.Name)
}

func (s *deploymentTemplateTest) TestContainerOverrideAppFullname() {
	// given
	options := &helm.Options{
		SetValues: map[string]string{
			"webModeler.enabled":                  "true",
			"webModeler.restapi.mail.fromAddress": "example@example.com",
			"webModeler.fullnameOverride":         "foo",
		},
		KubectlOptions: k8s.NewKubectlOptions("", "", s.namespace),
	}

	// when
	output := helm.RenderTemplate(s.T(), options, s.chartPath, s.release, s.templates)
	var deployment appsv1.Deployment
	helm.UnmarshalK8SYaml(s.T(), output, &deployment)

	// then
	s.Require().Equal("foo-"+s.component, deployment.ObjectMeta.Name)
}

func (s *deploymentTemplateTest) TestContainerSetPodLabels() {
	// given
	options := &helm.Options{
		SetValues: map[string]string{
			"webModeler.enabled":                           "true",
			"webModeler.restapi.mail.fromAddress":          "example@example.com",
			"webModeler." + s.component + ".podLabels.foo": "bar",
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
			"webModeler.enabled":                                "true",
			"webModeler.restapi.mail.fromAddress":               "example@example.com",
			"webModeler." + s.component + ".podAnnotations.foo": "bar",
			"webModeler." + s.component + ".podAnnotations.foz": "baz",
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
			"webModeler.enabled":                  "true",
			"webModeler.restapi.mail.fromAddress": "example@example.com",
			"global.annotations.foo":              "bar",
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
			"webModeler.enabled":                              "true",
			"webModeler.restapi.mail.fromAddress":             "example@example.com",
			"global.image.registry":                           "global.custom.registry.io",
			"global.image.tag":                                "8.x.x",
			"webModeler.image.registry":                       "subchart.custom.registry.io",
			"webModeler.image.tag":                            "snapshot",
			"webModeler." + s.component + ".image.repository": "web-modeler/modeler-" + s.component,
		},
		KubectlOptions: k8s.NewKubectlOptions("", "", s.namespace),
	}

	// when
	output := helm.RenderTemplate(s.T(), options, s.chartPath, s.release, s.templates)
	var deployment appsv1.Deployment
	helm.UnmarshalK8SYaml(s.T(), output, &deployment)

	// then
	container := deployment.Spec.Template.Spec.Containers[0]
	s.Require().Equal("subchart.custom.registry.io/web-modeler/modeler-"+s.component+":snapshot", container.Image)
}

func (s *deploymentTemplateTest) TestContainerSetImageNameGlobalRegistry() {
	// given
	options := &helm.Options{
		SetValues: map[string]string{
			"webModeler.enabled":                              "true",
			"webModeler.restapi.mail.fromAddress":             "example@example.com",
			"global.image.registry":                           "global.custom.registry.io",
			"webModeler.image.registry":                       "",
			"webModeler.image.tag":                            "snapshot",
			"webModeler." + s.component + ".image.repository": "web-modeler/modeler-" + s.component,
		},
		KubectlOptions: k8s.NewKubectlOptions("", "", s.namespace),
	}

	// when
	output := helm.RenderTemplate(s.T(), options, s.chartPath, s.release, s.templates)
	var deployment appsv1.Deployment
	helm.UnmarshalK8SYaml(s.T(), output, &deployment)

	// then
	container := deployment.Spec.Template.Spec.Containers[0]
	s.Require().Equal("global.custom.registry.io/web-modeler/modeler-"+s.component+":snapshot", container.Image)
}

func (s *deploymentTemplateTest) TestContainerSetImagePullSecretsGlobal() {
	// given
	options := &helm.Options{
		SetValues: map[string]string{
			"webModeler.enabled":                  "true",
			"webModeler.restapi.mail.fromAddress": "example@example.com",
			"global.image.pullSecrets[0].name":    "SecretName",
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
			"webModeler.enabled":                   "true",
			"webModeler.restapi.mail.fromAddress":  "example@example.com",
			"global.image.pullSecrets[0].name":     "SecretName",
			"webModeler.image.pullSecrets[0].name": "SecretNameSubChart",
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
			"webModeler.enabled":                  "true",
			"webModeler.restapi.mail.fromAddress": "example@example.com",
			"webModeler.image.tag":                "a.b.c",
		},
		KubectlOptions: k8s.NewKubectlOptions("", "", s.namespace),
	}

	// when
	output := helm.RenderTemplate(s.T(), options, s.chartPath, s.release, s.templates)
	var deployment appsv1.Deployment
	helm.UnmarshalK8SYaml(s.T(), output, &deployment)

	// then
	expectedContainerImage := "registry.camunda.cloud/web-modeler-ee/modeler-" + s.component + ":a.b.c"
	containers := deployment.Spec.Template.Spec.Containers
	s.Require().Equal(1, len(containers))
	s.Require().Equal(expectedContainerImage, containers[0].Image)
}

func (s *deploymentTemplateTest) TestContainerOverwriteGlobalImageTag() {
	// given
	options := &helm.Options{
		SetValues: map[string]string{
			"webModeler.enabled":                  "true",
			"webModeler.restapi.mail.fromAddress": "example@example.com",
			"webModeler.image.tag":                "",
			"global.image.tag":                    "a.b.c",
		},
		KubectlOptions: k8s.NewKubectlOptions("", "", s.namespace),
	}

	// when
	output := helm.RenderTemplate(s.T(), options, s.chartPath, s.release, s.templates)
	var deployment appsv1.Deployment
	helm.UnmarshalK8SYaml(s.T(), output, &deployment)

	// then
	expectedContainerImage := "registry.camunda.cloud/web-modeler-ee/modeler-" + s.component + ":a.b.c"
	containers := deployment.Spec.Template.Spec.Containers
	s.Require().Equal(1, len(containers))
	s.Require().Equal(expectedContainerImage, containers[0].Image)
}

func (s *deploymentTemplateTest) TestContainerOverwriteImageTagWithChartDirectSetting() {
	// given
	options := &helm.Options{
		SetValues: map[string]string{
			"webModeler.enabled":                  "true",
			"webModeler.restapi.mail.fromAddress": "example@example.com",
			"webModeler.image.tag":                "a.b.c",
			"global.image.tag":                    "x.y.z",
		},
		KubectlOptions: k8s.NewKubectlOptions("", "", s.namespace),
	}

	// when
	output := helm.RenderTemplate(s.T(), options, s.chartPath, s.release, s.templates)
	var deployment appsv1.Deployment
	helm.UnmarshalK8SYaml(s.T(), output, &deployment)

	// then
	expectedContainerImage := "registry.camunda.cloud/web-modeler-ee/modeler-" + s.component + ":a.b.c"
	containers := deployment.Spec.Template.Spec.Containers
	s.Require().Equal(1, len(containers))
	s.Require().Equal(expectedContainerImage, containers[0].Image)
}

func (s *deploymentTemplateTest) TestContainerSetContainerCommand() {
	// given
	options := &helm.Options{
		SetValues: map[string]string{
			"webModeler.enabled":                     "true",
			"webModeler.restapi.mail.fromAddress":    "example@example.com",
			"webModeler." + s.component + ".command": "[printenv]",
		},
		KubectlOptions: k8s.NewKubectlOptions("", "", s.namespace),
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
	// given
	options := &helm.Options{
		SetValues: map[string]string{
			"webModeler.enabled":                                                   "true",
			"webModeler.restapi.mail.fromAddress":                                  "example@example.com",
			"webModeler." + s.component + ".extraVolumes[0].name":                  "extraVolume",
			"webModeler." + s.component + ".extraVolumes[0].configMap.name":        "otherConfigMap",
			"webModeler." + s.component + ".extraVolumes[0].configMap.defaultMode": "744",
		},
		KubectlOptions: k8s.NewKubectlOptions("", "", s.namespace),
	}

	// when
	output := helm.RenderTemplate(s.T(), options, s.chartPath, s.release, s.templates)
	var deployment appsv1.Deployment
	helm.UnmarshalK8SYaml(s.T(), output, &deployment)

	// then
	volumes := deployment.Spec.Template.Spec.Volumes
	s.Require().Equal(1, len(volumes))

	extraVolume := volumes[0]
	s.Require().Equal("extraVolume", extraVolume.Name)
	s.Require().NotNil(*extraVolume.ConfigMap)
	s.Require().Equal("otherConfigMap", extraVolume.ConfigMap.Name)
	s.Require().EqualValues(744, *extraVolume.ConfigMap.DefaultMode)
}

func (s *deploymentTemplateTest) TestContainerSetExtraVolumeMounts() {
	// given
	options := &helm.Options{
		SetValues: map[string]string{
			"webModeler.enabled":                                            "true",
			"webModeler.restapi.mail.fromAddress":                           "example@example.com",
			"webModeler." + s.component + ".extraVolumeMounts[0].name":      "otherConfigMap",
			"webModeler." + s.component + ".extraVolumeMounts[0].mountPath": "/usr/local/config",
		},
		KubectlOptions: k8s.NewKubectlOptions("", "", s.namespace),
	}

	// when
	output := helm.RenderTemplate(s.T(), options, s.chartPath, s.release, s.templates)
	var deployment appsv1.Deployment
	helm.UnmarshalK8SYaml(s.T(), output, &deployment)

	// then
	containers := deployment.Spec.Template.Spec.Containers
	s.Require().Equal(1, len(containers))

	volumeMounts := deployment.Spec.Template.Spec.Containers[0].VolumeMounts
	s.Require().Equal(1, len(volumeMounts))
	extraVolumeMount := volumeMounts[0]
	s.Require().Equal("otherConfigMap", extraVolumeMount.Name)
	s.Require().Equal("/usr/local/config", extraVolumeMount.MountPath)
}

func (s *deploymentTemplateTest) TestContainerSetServiceAccountName() {
	// given
	options := &helm.Options{
		SetValues: map[string]string{
			"webModeler.enabled":                  "true",
			"webModeler.restapi.mail.fromAddress": "example@example.com",
			"webModeler.serviceAccount.name":      "accName",
		},
		KubectlOptions: k8s.NewKubectlOptions("", "", s.namespace),
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
			"webModeler.enabled":                                          "true",
			"webModeler.restapi.mail.fromAddress":                         "example@example.com",
			"webModeler." + s.component + ".podSecurityContext.runAsUser": "1000",
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
			"webModeler.enabled":                                                          "true",
			"webModeler.restapi.mail.fromAddress":                                         "example@example.com",
			"webModeler." + s.component + ".containerSecurityContext.privileged":          "true",
			"webModeler." + s.component + ".containerSecurityContext.capabilities.add[0]": "NET_ADMIN",
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
			"webModeler.enabled":                                   "true",
			"webModeler.restapi.mail.fromAddress":                  "example@example.com",
			"webModeler." + s.component + ".nodeSelector.disktype": "ssd",
			"webModeler." + s.component + ".nodeSelector.cputype":  "arm",
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
			"webModeler.enabled":                  "true",
			"webModeler.restapi.mail.fromAddress": "example@example.com",
			"webModeler." + s.component + ".affinity.nodeAffinity.requiredDuringSchedulingIgnoredDuringExecution.nodeSelectorTerms[0].matchexpressions[0].key":       "kubernetes.io/e2e-az-name",
			"webModeler." + s.component + ".affinity.nodeAffinity.requiredDuringSchedulingIgnoredDuringExecution.nodeSelectorTerms[0].matchexpressions[0].operator":  "In",
			"webModeler." + s.component + ".affinity.nodeAffinity.requiredDuringSchedulingIgnoredDuringExecution.nodeSelectorTerms[0].matchexpressions[0].values[0]": "e2e-a1",
			"webModeler." + s.component + ".affinity.nodeAffinity.requiredDuringSchedulingIgnoredDuringExecution.nodeSelectorTerms[0].matchexpressions[0].values[1]": "e2e-a2",
			"webModeler." + s.component + ".affinity.nodeAffinity.preferredDuringSchedulingIgnoredDuringExecution[0].weight":                                         "1",
			"webModeler." + s.component + ".affinity.nodeAffinity.preferredDuringSchedulingIgnoredDuringExecution[0].preference.matchExpressions[0].key":             "another-node-label-key",
			"webModeler." + s.component + ".affinity.nodeAffinity.preferredDuringSchedulingIgnoredDuringExecution[0].preference.matchExpressions[0].operator":        "In",
			"webModeler." + s.component + ".affinity.nodeAffinity.preferredDuringSchedulingIgnoredDuringExecution[0].preference.matchExpressions[0].values[0]":       "another-node-label-value",
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
			"webModeler.enabled":                                     "true",
			"webModeler.restapi.mail.fromAddress":                    "example@example.com",
			"webModeler." + s.component + ".tolerations[0].key":      "key1",
			"webModeler." + s.component + ".tolerations[0].operator": "Equal",
			"webModeler." + s.component + ".tolerations[0].value":    "Value1",
			"webModeler." + s.component + ".tolerations[0].effect":   "NoSchedule",
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

func (s *deploymentTemplateTest) TestContainerShouldOverwriteGlobalImagePullPolicy() {
	// given
	options := &helm.Options{
		SetValues: map[string]string{
			"webModeler.enabled":                  "true",
			"webModeler.restapi.mail.fromAddress": "example@example.com",
			"global.image.pullPolicy":             "Always",
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

func (s *deploymentTemplateTest) TestContainerStartupProbe() {
	// given
	options := &helm.Options{
		SetValues: map[string]string{
			"webModeler.enabled":                                              "true",
			"webModeler.restapi.mail.fromAddress":                             "example@example.com",
			"webModeler." + s.component + ".startupProbe.enabled":             "true",
			"webModeler." + s.component + ".startupProbe.initialDelaySeconds": "5",
			"webModeler." + s.component + ".startupProbe.periodSeconds":       "10",
			"webModeler." + s.component + ".startupProbe.successThreshold":    "1",
			"webModeler." + s.component + ".startupProbe.failureThreshold":    "5",
			"webModeler." + s.component + ".startupProbe.timeoutSeconds":      "1",
		},
		KubectlOptions: k8s.NewKubectlOptions("", "", s.namespace),
	}

	// when
	output := helm.RenderTemplate(s.T(), options, s.chartPath, s.release, s.templates)
	var deployment appsv1.Deployment
	helm.UnmarshalK8SYaml(s.T(), output, &deployment)

	// then
	probe := deployment.Spec.Template.Spec.Containers[0].StartupProbe

	s.Require().EqualValues(5, probe.InitialDelaySeconds)
	s.Require().EqualValues(10, probe.PeriodSeconds)
	s.Require().EqualValues(1, probe.SuccessThreshold)
	s.Require().EqualValues(5, probe.FailureThreshold)
	s.Require().EqualValues(1, probe.TimeoutSeconds)
}

func (s *deploymentTemplateTest) TestContainerReadinessProbe() {
	// given
	options := &helm.Options{
		SetValues: map[string]string{
			"webModeler.enabled":                                                "true",
			"webModeler.restapi.mail.fromAddress":                               "example@example.com",
			"webModeler." + s.component + ".readinessProbe.enabled":             "true",
			"webModeler." + s.component + ".readinessProbe.initialDelaySeconds": "5",
			"webModeler." + s.component + ".readinessProbe.periodSeconds":       "10",
			"webModeler." + s.component + ".readinessProbe.successThreshold":    "1",
			"webModeler." + s.component + ".readinessProbe.failureThreshold":    "5",
			"webModeler." + s.component + ".readinessProbe.timeoutSeconds":      "1",
		},
		KubectlOptions: k8s.NewKubectlOptions("", "", s.namespace),
	}

	// when
	output := helm.RenderTemplate(s.T(), options, s.chartPath, s.release, s.templates)
	var deployment appsv1.Deployment
	helm.UnmarshalK8SYaml(s.T(), output, &deployment)

	// then
	probe := deployment.Spec.Template.Spec.Containers[0].ReadinessProbe

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
			"webModeler.enabled":                                               "true",
			"webModeler.restapi.mail.fromAddress":                              "example@example.com",
			"webModeler." + s.component + ".livenessProbe.enabled":             "true",
			"webModeler." + s.component + ".livenessProbe.initialDelaySeconds": "5",
			"webModeler." + s.component + ".livenessProbe.periodSeconds":       "10",
			"webModeler." + s.component + ".livenessProbe.successThreshold":    "1",
			"webModeler." + s.component + ".livenessProbe.failureThreshold":    "5",
			"webModeler." + s.component + ".livenessProbe.timeoutSeconds":      "1",
		},
		KubectlOptions: k8s.NewKubectlOptions("", "", s.namespace),
	}

	// when
	output := helm.RenderTemplate(s.T(), options, s.chartPath, s.release, s.templates)
	var deployment appsv1.Deployment
	helm.UnmarshalK8SYaml(s.T(), output, &deployment)

	// then
	probe := deployment.Spec.Template.Spec.Containers[0].LivenessProbe

	s.Require().EqualValues(5, probe.InitialDelaySeconds)
	s.Require().EqualValues(10, probe.PeriodSeconds)
	s.Require().EqualValues(1, probe.SuccessThreshold)
	s.Require().EqualValues(5, probe.FailureThreshold)
	s.Require().EqualValues(1, probe.TimeoutSeconds)
}
