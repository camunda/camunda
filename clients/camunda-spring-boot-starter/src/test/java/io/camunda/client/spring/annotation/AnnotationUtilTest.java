/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.client.spring.annotation;

import static io.camunda.client.spring.testsupport.BeanInfoUtil.beanInfo;
import static io.camunda.client.spring.testsupport.BeanInfoUtil.methodInfo;
import static io.camunda.client.spring.testsupport.BeanInfoUtil.parameterInfo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.camunda.client.annotation.AnnotationUtil;
import io.camunda.client.annotation.ClusterVariables;
import io.camunda.client.annotation.Deployment.Deployments;
import io.camunda.client.annotation.ElementInstanceKey;
import io.camunda.client.annotation.JobKey;
import io.camunda.client.annotation.ProcessDefinitionKey;
import io.camunda.client.annotation.ProcessInstanceKey;
import io.camunda.client.annotation.RootProcessInstanceKey;
import io.camunda.client.annotation.VariablesAsType;
import io.camunda.client.annotation.value.ClusterVariablesValue;
import io.camunda.client.annotation.value.DeploymentValue;
import io.camunda.client.annotation.value.DocumentValue;
import io.camunda.client.annotation.value.JobWorkerValue;
import io.camunda.client.annotation.value.MethodClusterVariablesValue;
import io.camunda.client.annotation.value.ResourceClusterVariablesValue;
import io.camunda.client.annotation.value.SourceAware.GeneratedFromMethodInfo;
import io.camunda.client.annotation.value.VariableValue;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.response.DocumentReferenceResponse;
import io.camunda.client.bean.BeanInfo;
import io.camunda.client.bean.MethodInfo;
import io.camunda.client.bean.ParameterInfo;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

public class AnnotationUtilTest {

  public static class ComplexType {}

  @Nested
  class JobWorker {
    @Test
    void shouldExtractVariableFromJsonAnnotation() {
      // given
      final MethodInfo methodInfo = methodInfo(this, "test", "sampleWorkerWithJsonProperty");
      final Optional<JobWorkerValue> jobWorkerValue = AnnotationUtil.getJobWorkerValue(methodInfo);
      assertThat(jobWorkerValue).isPresent();
      final JobWorkerValue value = jobWorkerValue.get();
      assertThat(value.getFetchVariables())
          .containsExactly(new GeneratedFromMethodInfo<>("some_name"));
    }

    @Test
    void shouldExtractVariableName() {
      final MethodInfo methodInfo = methodInfo(this, "test", "sampleWorker");
      final Optional<JobWorkerValue> jobWorkerValue = AnnotationUtil.getJobWorkerValue(methodInfo);
      assertThat(jobWorkerValue).isPresent();
      final JobWorkerValue value = jobWorkerValue.get();
      assertThat(value.getFetchVariables())
          .containsExactly(new GeneratedFromMethodInfo<>("helloWorld"));
    }

    @Test
    void shouldExtractVariableAsTypeNames() {
      final MethodInfo methodInfo = methodInfo(this, "test", "sampleWorkerWithVariablesType");
      final Optional<JobWorkerValue> jobWorkerValue = AnnotationUtil.getJobWorkerValue(methodInfo);
      assertThat(jobWorkerValue).isPresent();
      final JobWorkerValue value = jobWorkerValue.get();
      assertThat(value.getFetchVariables())
          .containsExactly(
              new GeneratedFromMethodInfo<>("hello"), new GeneratedFromMethodInfo<>("world"));
    }

    @Test
    void shouldExtractVariableFromEmptyJsonAnnotation() {
      // given
      final MethodInfo methodInfo = methodInfo(this, "test", "sampleWorkerWithEmptyJsonProperty");
      final Optional<JobWorkerValue> jobWorkerValue = AnnotationUtil.getJobWorkerValue(methodInfo);
      assertThat(jobWorkerValue).isPresent();
      final JobWorkerValue value = jobWorkerValue.get();
      assertThat(value.getFetchVariables()).containsExactly(new GeneratedFromMethodInfo<>("value"));
    }

    @io.camunda.client.annotation.JobWorker
    public void sampleWorkerWithEmptyJsonProperty(
        @VariablesAsType final PropertyAnnotatedClassEmptyValue annotatedClass) {}

    @io.camunda.client.annotation.JobWorker
    public void sampleWorker(@io.camunda.client.annotation.Variable final String helloWorld) {}

    @io.camunda.client.annotation.JobWorker
    public void sampleWorkerWithVariablesType(
        @VariablesAsType final VariablesType annotatedClass) {}

    @io.camunda.client.annotation.JobWorker
    public void sampleWorkerWithJsonProperty(
        @VariablesAsType final PropertyAnnotatedClass annotatedClass) {}

    private static final class PropertyAnnotatedClass {
      @JsonProperty("some_name")
      private String value;
    }

    private static final class PropertyAnnotatedClassEmptyValue {
      @JsonProperty() private String value;
    }

    private static final class VariablesType {
      private String hello;
      private String world;
    }
  }

  @Nested
  class Variable {
    @Test
    void shouldExtractValueNoName() {
      // given
      final ParameterInfo parameterInfo = parameterInfo(this, "testNoName");
      // when
      final VariableValue variableValue = AnnotationUtil.getVariableValue(parameterInfo).get();
      // then
      assertThat(variableValue.getName()).isEqualTo("var1");
      assertThat(variableValue.isOptional()).isTrue();
      assertThat(variableValue.getParameterInfo().getParameter().getType())
          .isEqualTo(ComplexType.class);
    }

    @Test
    void shouldExtractValueNotOptional() {
      // given
      final ParameterInfo parameterInfo = parameterInfo(this, "testNotOptional");
      // when
      final VariableValue variableValue = AnnotationUtil.getVariableValue(parameterInfo).get();
      // then
      assertThat(variableValue.getName()).isEqualTo("var1");
      assertThat(variableValue.isOptional()).isFalse();
      assertThat(variableValue.getParameterInfo().getParameter().getType())
          .isEqualTo(ComplexType.class);
    }

    @Test
    void shouldExtractValueName() {
      // given
      final ParameterInfo parameterInfo = parameterInfo(this, "testName");
      // when
      final VariableValue variableValue = AnnotationUtil.getVariableValue(parameterInfo).get();
      // then
      assertThat(variableValue.getName()).isEqualTo("var2");
      assertThat(variableValue.isOptional()).isTrue();
      assertThat(variableValue.getParameterInfo().getParameter().getType())
          .isEqualTo(ComplexType.class);
    }

    @Test
    void shouldExtractValueValue() {
      // given
      final ParameterInfo parameterInfo = parameterInfo(this, "testValue");
      // when
      final VariableValue variableValue = AnnotationUtil.getVariableValue(parameterInfo).get();
      // then
      assertThat(variableValue.getName()).isEqualTo("var2");
      assertThat(variableValue.isOptional()).isTrue();
      assertThat(variableValue.getParameterInfo().getParameter().getType())
          .isEqualTo(ComplexType.class);
    }

    public void testNoName(@io.camunda.client.annotation.Variable final ComplexType var1) {}

    public void testNotOptional(
        @io.camunda.client.annotation.Variable(optional = false) final ComplexType var1) {}

    public void testName(
        @io.camunda.client.annotation.Variable(name = "var2") final ComplexType var1) {}

    public void testValue(@io.camunda.client.annotation.Variable("var2") final ComplexType var1) {}
  }

  @Nested
  class Deployment {

    @Test
    void shouldFindDeploymentValue() {
      // given
      final BeanInfo classInfo = beanInfo(new DeploymentBean());
      // when
      final DeploymentValue deploymentValue = AnnotationUtil.getDeploymentValues(classInfo).get(0);
      // then
      assertThat(deploymentValue.getResources()).hasSize(1);
      assertThat(deploymentValue.getResources().get(0)).isEqualTo("classpath*:*.bpmn");
    }

    @Test
    void shouldFindDeploymentValues() {
      // given
      final BeanInfo classInfo = beanInfo(new MultiDeploymentBean());
      // when
      final List<DeploymentValue> deploymentValues = AnnotationUtil.getDeploymentValues(classInfo);
      // then
      assertThat(deploymentValues).hasSize(2);
      final DeploymentValue deploymentValue1 = deploymentValues.get(0);
      final DeploymentValue deploymentValue2 = deploymentValues.get(1);
      assertThat(deploymentValue1.getResources()).hasSize(1);
      assertThat(deploymentValue1.getResources().get(0)).isEqualTo("classpath*:v1/*.bpmn");
      assertThat(deploymentValue2.getResources()).hasSize(1);
      assertThat(deploymentValue2.getResources().get(0)).isEqualTo("classpath*:v2/*.bpmn");
    }

    @Test
    void shouldFindDeploymentsValues() {
      // given
      final BeanInfo classInfo = beanInfo(new DeploymentsBean());
      // when
      final List<DeploymentValue> deploymentValues = AnnotationUtil.getDeploymentValues(classInfo);
      // then
      assertThat(deploymentValues).hasSize(2);
      final DeploymentValue deploymentValue1 = deploymentValues.get(0);
      final DeploymentValue deploymentValue2 = deploymentValues.get(1);
      assertThat(deploymentValue1.getResources()).hasSize(1);
      assertThat(deploymentValue1.getResources().get(0)).isEqualTo("classpath*:v1/*.bpmn");
      assertThat(deploymentValue2.getResources()).hasSize(1);
      assertThat(deploymentValue2.getResources().get(0)).isEqualTo("classpath*:v2/*.bpmn");
    }

    @io.camunda.client.annotation.Deployment(resources = "classpath*:*.bpmn")
    static class DeploymentBean {}

    @io.camunda.client.annotation.Deployment(resources = "classpath*:v1/*.bpmn")
    @io.camunda.client.annotation.Deployment(resources = "classpath*:v2/*.bpmn")
    static class MultiDeploymentBean {}

    @Deployments({
      @io.camunda.client.annotation.Deployment(resources = "classpath*:v1/*.bpmn"),
      @io.camunda.client.annotation.Deployment(resources = "classpath*:v2/*.bpmn")
    })
    static class DeploymentsBean {}
  }

  @Nested
  class Document {
    @Test
    void shouldExtractValueNoName() {
      // given
      final ParameterInfo parameterInfo = parameterInfo(this, "testNoName");
      // when
      final DocumentValue documentValue = AnnotationUtil.getDocumentValue(parameterInfo).get();
      // then
      assertThat(documentValue.getName()).isEqualTo("document");
      assertThat(documentValue.isOptional()).isTrue();
    }

    @Test
    void shouldExtractValueNotOptional() {
      // given
      final ParameterInfo parameterInfo = parameterInfo(this, "testNotOptional");
      // when
      final DocumentValue documentValue = AnnotationUtil.getDocumentValue(parameterInfo).get();
      // then
      assertThat(documentValue.getName()).isEqualTo("document");
      assertThat(documentValue.isOptional()).isFalse();
    }

    @Test
    void shouldExtractValueName() {
      // given
      final ParameterInfo parameterInfo = parameterInfo(this, "testName");
      // when
      final DocumentValue documentValue = AnnotationUtil.getDocumentValue(parameterInfo).get();
      // then
      assertThat(documentValue.getName()).isEqualTo("anotherDocument");
      assertThat(documentValue.isOptional()).isTrue();
    }

    @Test
    void shouldExtractValueValue() {
      // given
      final ParameterInfo parameterInfo = parameterInfo(this, "testValue");
      // when
      final DocumentValue documentValue = AnnotationUtil.getDocumentValue(parameterInfo).get();
      // then
      assertThat(documentValue.getName()).isEqualTo("anotherDocument");
      assertThat(documentValue.isOptional()).isTrue();
    }

    @Test
    void shouldExtractSingleValue() {
      // given
      final ParameterInfo parameterInfo = parameterInfo(this, "testSingle");
      // when
      final DocumentValue documentValue = AnnotationUtil.getDocumentValue(parameterInfo).get();
      // then
      assertThat(documentValue.getName()).isEqualTo("document");
      assertThat(documentValue.isOptional()).isTrue();
    }

    public void testNoName(
        @io.camunda.client.annotation.Document final List<DocumentReferenceResponse> document) {}

    public void testNotOptional(
        @io.camunda.client.annotation.Document(optional = false)
            final List<DocumentReferenceResponse> document) {}

    public void testName(
        @io.camunda.client.annotation.Document(name = "anotherDocument")
            final List<DocumentReferenceResponse> document) {}

    public void testValue(
        @io.camunda.client.annotation.Document("anotherDocument")
            final List<DocumentReferenceResponse> document) {}

    public void testSingle(
        @io.camunda.client.annotation.Document final DocumentReferenceResponse document) {}
  }

  @Nested
  class KeyResolver {
    @Test
    void shouldExtractProcessInstanceKeyResolver() {
      // given
      final ParameterInfo parameterInfo = parameterInfo(this, "testProcessInstanceKey");
      final ActivatedJob job = mock(ActivatedJob.class);
      when(job.getProcessInstanceKey()).thenReturn(123L);
      // when
      final Function<ActivatedJob, Long> keyResolver =
          AnnotationUtil.getKeyResolver(parameterInfo).get();
      // then
      assertThat(keyResolver.apply(job)).isEqualTo(123L);
    }

    @Test
    void shouldExtractProcessDefinitionKeyResolver() {
      // given
      final ParameterInfo parameterInfo = parameterInfo(this, "testProcessDefinitionKey");
      final ActivatedJob job = mock(ActivatedJob.class);
      when(job.getProcessDefinitionKey()).thenReturn(123L);
      // when
      final Function<ActivatedJob, Long> keyResolver =
          AnnotationUtil.getKeyResolver(parameterInfo).get();
      // then
      assertThat(keyResolver.apply(job)).isEqualTo(123L);
    }

    @Test
    void shouldExtractJobKeyResolver() {
      // given
      final ParameterInfo parameterInfo = parameterInfo(this, "testJobKey");
      final ActivatedJob job = mock(ActivatedJob.class);
      when(job.getKey()).thenReturn(123L);
      // when
      final Function<ActivatedJob, Long> keyResolver =
          AnnotationUtil.getKeyResolver(parameterInfo).get();
      // then
      assertThat(keyResolver.apply(job)).isEqualTo(123L);
    }

    @Test
    void shouldExtractElementInstanceKeyResolver() {
      // given
      final ParameterInfo parameterInfo = parameterInfo(this, "testElementInstanceKey");
      final ActivatedJob job = mock(ActivatedJob.class);
      when(job.getElementInstanceKey()).thenReturn(123L);
      // when
      final Function<ActivatedJob, Long> keyResolver =
          AnnotationUtil.getKeyResolver(parameterInfo).get();
      // then
      assertThat(keyResolver.apply(job)).isEqualTo(123L);
    }

    @Test
    void shouldExtractRootProcessInstanceKeyResolver() {
      // given
      final ParameterInfo parameterInfo = parameterInfo(this, "testRootProcessInstanceKey");
      final ActivatedJob job = mock(ActivatedJob.class);
      when(job.getRootProcessInstanceKey()).thenReturn(123L);
      // when
      final Function<ActivatedJob, Long> keyResolver =
          AnnotationUtil.getKeyResolver(parameterInfo).get();
      // then
      assertThat(keyResolver.apply(job)).isEqualTo(123L);
    }

    public void testProcessInstanceKey(@ProcessInstanceKey final String key) {}

    public void testProcessDefinitionKey(@ProcessDefinitionKey final String key) {}

    public void testJobKey(@JobKey final String key) {}

    public void testElementInstanceKey(@ElementInstanceKey final String key) {}

    public void testRootProcessInstanceKey(@RootProcessInstanceKey final String key) {}
  }

  @Nested
  class ClusterVariablesAnnotation {

    @Test
    void shouldDetectClassAnnotation() {
      // given
      final BeanInfo beanInfo = beanInfo(new SingleResourceBean());
      // when / then
      assertThat(AnnotationUtil.isClusterVariables(beanInfo)).isTrue();
    }

    @Test
    void shouldDetectMethodAnnotation() {
      // given
      final BeanInfo beanInfo = beanInfo(new MethodVariablesBean());
      // when / then
      assertThat(AnnotationUtil.isClusterVariables(beanInfo)).isTrue();
    }

    @Test
    void shouldNotDetectUnannotatedBean() {
      // given
      final BeanInfo beanInfo = beanInfo(new UnannotatedBean());
      // when / then
      assertThat(AnnotationUtil.isClusterVariables(beanInfo)).isFalse();
    }

    @Test
    void shouldExtractResourceValueFromClass() {
      // given
      final BeanInfo beanInfo = beanInfo(new SingleResourceBean());
      // when
      final List<ResourceClusterVariablesValue> values =
          AnnotationUtil.getClusterVariablesValuesFromClass(beanInfo);
      // then
      assertThat(values).hasSize(1);
      assertThat(values.get(0).getResources()).containsExactly("classpath:variables.json");
      assertThat(values.get(0).getTenantId()).isNull();
    }

    @Test
    void shouldExtractResourceValueWithTenantFromClass() {
      // given
      final BeanInfo beanInfo = beanInfo(new TenantScopedResourceBean());
      // when
      final List<ResourceClusterVariablesValue> values =
          AnnotationUtil.getClusterVariablesValuesFromClass(beanInfo);
      // then
      assertThat(values).hasSize(1);
      assertThat(values.get(0).getResources()).containsExactly("classpath:vars.json");
      assertThat(values.get(0).getTenantId()).isEqualTo("my-tenant");
    }

    @Test
    void shouldExtractMultipleResourceValuesFromClass() {
      // given
      final BeanInfo beanInfo = beanInfo(new RepeatableResourceBean());
      // when
      final List<ResourceClusterVariablesValue> values =
          AnnotationUtil.getClusterVariablesValuesFromClass(beanInfo);
      // then
      assertThat(values).hasSize(2);
      assertThat(values.get(0).getResources()).containsExactly("classpath:v1.json");
      assertThat(values.get(1).getResources()).containsExactly("classpath:v2.json");
    }

    @Test
    void shouldReturnEmptyForUnannotatedClass() {
      // given
      final BeanInfo beanInfo = beanInfo(new UnannotatedBean());
      // when
      final List<ResourceClusterVariablesValue> values =
          AnnotationUtil.getClusterVariablesValuesFromClass(beanInfo);
      // then
      assertThat(values).isEmpty();
    }

    @Test
    void shouldExtractMethodValueAsMethodClusterVariablesValue() {
      // given
      final MethodInfo methodInfo = methodInfo(new MethodVariablesBean(), "test", "variables");
      // when
      final List<ClusterVariablesValue> values =
          AnnotationUtil.getClusterVariablesValuesFromMethods(methodInfo);
      // then
      assertThat(values).hasSize(1);
      assertThat(values.get(0)).isInstanceOf(MethodClusterVariablesValue.class);
      assertThat(values.get(0).getTenantId()).isNull();
      final MethodClusterVariablesValue methodValue = (MethodClusterVariablesValue) values.get(0);
      assertThat(methodValue.getVariableSupplier().get()).isEqualTo(Map.of("key", "value"));
    }

    @Test
    void shouldExtractTenantScopedMethodValue() {
      // given
      final MethodInfo methodInfo =
          methodInfo(new TenantScopedMethodBean(), "test", "tenantVariables");
      // when
      final List<ClusterVariablesValue> values =
          AnnotationUtil.getClusterVariablesValuesFromMethods(methodInfo);
      // then
      assertThat(values).hasSize(1);
      assertThat(values.get(0)).isInstanceOf(MethodClusterVariablesValue.class);
      assertThat(values.get(0).getTenantId()).isEqualTo("my-tenant");
    }

    @Test
    void shouldExtractResourceValueFromMethodAnnotation() {
      // given
      final MethodInfo methodInfo = methodInfo(new MethodWithResourcesBean(), "test", "unused");
      // when
      final List<ClusterVariablesValue> values =
          AnnotationUtil.getClusterVariablesValuesFromMethods(methodInfo);
      // then
      assertThat(values).hasSize(1);
      assertThat(values.get(0)).isInstanceOf(ResourceClusterVariablesValue.class);
      final ResourceClusterVariablesValue resourceValue =
          (ResourceClusterVariablesValue) values.get(0);
      assertThat(resourceValue.getResources()).containsExactly("classpath:from-method.json");
    }

    @Test
    void shouldReturnEmptyForUnannotatedMethod() {
      // given
      final MethodInfo methodInfo = methodInfo(new UnannotatedBean(), "test", "noAnnotation");
      // when
      final List<ClusterVariablesValue> values =
          AnnotationUtil.getClusterVariablesValuesFromMethods(methodInfo);
      // then
      assertThat(values).isEmpty();
    }

    @ClusterVariables(resources = "classpath:variables.json")
    static class SingleResourceBean {}

    @ClusterVariables(resources = "classpath:vars.json", tenantId = "my-tenant")
    static class TenantScopedResourceBean {}

    @ClusterVariables(resources = "classpath:v1.json")
    @ClusterVariables(resources = "classpath:v2.json")
    static class RepeatableResourceBean {}

    // Public to allow reflective method invocation from SpringMethodInfo
    public static class MethodVariablesBean {
      @ClusterVariables
      public Map<String, Object> variables() {
        return Map.of("key", "value");
      }
    }

    // Public to allow reflective method invocation from SpringMethodInfo
    public static class TenantScopedMethodBean {
      @ClusterVariables(tenantId = "my-tenant")
      public Map<String, Object> tenantVariables() {
        return Map.of("key", "value");
      }
    }

    public static class MethodWithResourcesBean {
      @ClusterVariables(resources = "classpath:from-method.json")
      public void unused() {}
    }

    static class UnannotatedBean {
      public void noAnnotation() {}
    }
  }
}
