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
package io.camunda.spring.client.annotation;

import static io.camunda.spring.client.testsupport.ClassInfoUtil.classInfo;
import static io.camunda.spring.client.testsupport.ClassInfoUtil.parameterInfo;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.api.response.DocumentReferenceResponse;
import io.camunda.spring.client.annotation.value.DeploymentValue;
import io.camunda.spring.client.annotation.value.DocumentValue;
import io.camunda.spring.client.annotation.value.VariableValue;
import io.camunda.spring.client.bean.ClassInfo;
import io.camunda.spring.client.bean.ParameterInfo;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

public class AnnotationUtilTest {

  public static class ComplexType {}

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
      assertThat(variableValue.getBeanInfo().getParameterInfo().getType())
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
      assertThat(variableValue.getBeanInfo().getParameterInfo().getType())
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
      assertThat(variableValue.getBeanInfo().getParameterInfo().getType())
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
      assertThat(variableValue.getBeanInfo().getParameterInfo().getType())
          .isEqualTo(ComplexType.class);
    }

    public void testNoName(@io.camunda.spring.client.annotation.Variable final ComplexType var1) {}

    public void testNotOptional(
        @io.camunda.spring.client.annotation.Variable(optional = false) final ComplexType var1) {}

    public void testName(
        @io.camunda.spring.client.annotation.Variable(name = "var2") final ComplexType var1) {}

    public void testValue(
        @io.camunda.spring.client.annotation.Variable("var2") final ComplexType var1) {}
  }

  @Nested
  class Deployment {

    @Test
    void shouldFindDeploymentValue() {
      // given
      final ClassInfo classInfo = classInfo(new DeployingBean());
      // when
      final DeploymentValue deploymentValue = AnnotationUtil.getDeploymentValue(classInfo).get();
      // then
      assertThat(deploymentValue.getResources()).hasSize(1);
      assertThat(deploymentValue.getResources().get(0)).isEqualTo("classpath*:*.bpmn");
    }

    @io.camunda.spring.client.annotation.Deployment(resources = "classpath*:*.bpmn")
    static class DeployingBean {}
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
        @io.camunda.spring.client.annotation.Document
            final List<DocumentReferenceResponse> document) {}

    public void testNotOptional(
        @io.camunda.spring.client.annotation.Document(optional = false)
            final List<DocumentReferenceResponse> document) {}

    public void testName(
        @io.camunda.spring.client.annotation.Document(name = "anotherDocument")
            final List<DocumentReferenceResponse> document) {}

    public void testValue(
        @io.camunda.spring.client.annotation.Document("anotherDocument")
            final List<DocumentReferenceResponse> document) {}

    public void testSingle(
        @io.camunda.spring.client.annotation.Document final DocumentReferenceResponse document) {}
  }
}
