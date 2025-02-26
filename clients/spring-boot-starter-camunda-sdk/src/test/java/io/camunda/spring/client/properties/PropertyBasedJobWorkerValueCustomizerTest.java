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
package io.camunda.spring.client.properties;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.spring.client.annotation.JobWorker;
import io.camunda.spring.client.annotation.Variable;
import io.camunda.spring.client.annotation.VariablesAsType;
import io.camunda.spring.client.annotation.value.JobWorkerValue;
import io.camunda.spring.client.bean.ClassInfo;
import io.camunda.spring.client.bean.MethodInfo;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

public class PropertyBasedJobWorkerValueCustomizerTest {

  private static MethodInfo methodInfo(
      final Object bean, final String beanName, final String methodName) {
    try {
      return MethodInfo.builder()
          .classInfo(ClassInfo.builder().beanName(beanName).bean(bean).build())
          .method(
              Arrays.stream(PropertyBasedJobWorkerValueCustomizerTest.class.getDeclaredMethods())
                  .filter(m -> m.getName().equals(methodName))
                  .findFirst()
                  .orElseThrow(
                      () -> new IllegalStateException("No method present with name " + methodName)))
          .build();
    } catch (final Exception e) {
      throw new RuntimeException("Error while constructing methodInfo for method " + methodName, e);
    }
  }

  private static CamundaClientProperties properties() {
    return new CamundaClientProperties();
  }

  @JobWorker
  void sampleWorker(
      @Variable final String var1, @VariablesAsType final ComplexProcessVariable var2) {}

  @JobWorker
  void activatedJobWorker(@Variable final String var1, final ActivatedJob activatedJob) {}

  @JobWorker
  void sampleWorkerWithJsonProperty(@VariablesAsType final PropertyAnnotatedClass annotatedClass) {}

  @JobWorker
  void sampleWorkerWithEmptyJsonProperty(
      @VariablesAsType final PropertyAnnotatedClassEmptyValue annotatedClass) {}

  @Test
  void shouldNotAdjustVariableFilterVariablesAsActivatedJobIsInjectedLegacy() {
    // given
    final PropertyBasedJobWorkerValueCustomizer customizer =
        new PropertyBasedJobWorkerValueCustomizer(properties());
    final JobWorkerValue jobWorkerValue = new JobWorkerValue();
    jobWorkerValue.setFetchVariables(List.of("a", "var1", "b"));
    jobWorkerValue.setMethodInfo(methodInfo(this, "testBean", "activatedJobWorker"));
    // when
    customizer.customize(jobWorkerValue);
    // then
    assertThat(jobWorkerValue.getFetchVariables()).containsExactly("a", "var1", "b");
  }

  @Test
  void shouldNotAdjustVariableFilterVariablesAsActivatedJobIsInjected() {
    // given
    final PropertyBasedJobWorkerValueCustomizer customizer =
        new PropertyBasedJobWorkerValueCustomizer(properties());
    final JobWorkerValue jobWorkerValue = new JobWorkerValue();
    jobWorkerValue.setFetchVariables(List.of("a", "var1", "b"));
    jobWorkerValue.setMethodInfo(methodInfo(this, "testBean", "activatedJobWorker"));
    // when
    customizer.customize(jobWorkerValue);
    // then
    assertThat(jobWorkerValue.getFetchVariables()).containsExactly("a", "var1", "b");
  }

  @Test
  void shouldSetDefaultName() {
    // given
    final CamundaClientProperties properties = properties();
    properties.getWorker().getDefaults().setName("defaultName");
    final PropertyBasedJobWorkerValueCustomizer customizer =
        new PropertyBasedJobWorkerValueCustomizer(properties);
    final JobWorkerValue jobWorkerValue = new JobWorkerValue();
    jobWorkerValue.setMethodInfo(methodInfo(this, "testBean", "sampleWorker"));
    // when
    customizer.customize(jobWorkerValue);
    // then
    assertThat(jobWorkerValue.getName()).isEqualTo("defaultName");
  }

  @Test
  void shouldSetGeneratedNameLegacy() {
    // given
    final PropertyBasedJobWorkerValueCustomizer customizer =
        new PropertyBasedJobWorkerValueCustomizer(properties());
    final JobWorkerValue jobWorkerValue = new JobWorkerValue();
    jobWorkerValue.setMethodInfo(methodInfo(this, "testBean", "sampleWorker"));
    // when
    customizer.customize(jobWorkerValue);
    // then
    assertThat(jobWorkerValue.getName()).isEqualTo("testBean#sampleWorker");
  }

  @Test
  void shouldSetGeneratedName() {
    // given
    final PropertyBasedJobWorkerValueCustomizer customizer =
        new PropertyBasedJobWorkerValueCustomizer(properties());
    final JobWorkerValue jobWorkerValue = new JobWorkerValue();
    jobWorkerValue.setMethodInfo(methodInfo(this, "testBean", "sampleWorker"));
    // when
    customizer.customize(jobWorkerValue);
    // then
    assertThat(jobWorkerValue.getName()).isEqualTo("testBean#sampleWorker");
  }

  @Test
  void shouldSetDefaultTenantIds() {
    // given
    final CamundaClientProperties properties = properties();
    properties.getWorker().getDefaults().setTenantIds(List.of("customTenantId"));

    final PropertyBasedJobWorkerValueCustomizer customizer =
        new PropertyBasedJobWorkerValueCustomizer(properties);

    final JobWorkerValue jobWorkerValue = new JobWorkerValue();
    jobWorkerValue.setMethodInfo(methodInfo(this, "testBean", "sampleWorker"));
    // when
    customizer.customize(jobWorkerValue);
    // then
    assertThat(jobWorkerValue.getTenantIds()).contains("customTenantId");
  }

  @Test
  void shouldSetDefaultType() {
    // given
    final CamundaClientProperties properties = properties();
    properties.getWorker().getDefaults().setType("defaultType");
    final PropertyBasedJobWorkerValueCustomizer customizer =
        new PropertyBasedJobWorkerValueCustomizer(properties);
    final JobWorkerValue jobWorkerValue = new JobWorkerValue();
    jobWorkerValue.setMethodInfo(methodInfo(this, "testBean", "sampleWorker"));
    // when
    customizer.customize(jobWorkerValue);
    // then
    assertThat(jobWorkerValue.getType()).isEqualTo("defaultType");
  }

  @Test
  void shouldSetGeneratedTypeLegacy() {
    // given
    final PropertyBasedJobWorkerValueCustomizer customizer =
        new PropertyBasedJobWorkerValueCustomizer(properties());
    final JobWorkerValue jobWorkerValue = new JobWorkerValue();
    jobWorkerValue.setMethodInfo(methodInfo(this, "testBean", "sampleWorker"));
    // when
    customizer.customize(jobWorkerValue);
    // then
    assertThat(jobWorkerValue.getType()).isEqualTo("sampleWorker");
  }

  @Test
  void shouldSetGeneratedType() {
    // given
    final PropertyBasedJobWorkerValueCustomizer customizer =
        new PropertyBasedJobWorkerValueCustomizer(properties());
    final JobWorkerValue jobWorkerValue = new JobWorkerValue();
    jobWorkerValue.setMethodInfo(methodInfo(this, "testBean", "sampleWorker"));
    // when
    customizer.customize(jobWorkerValue);
    // then
    assertThat(jobWorkerValue.getType()).isEqualTo("sampleWorker");
  }

  @Test
  void shouldSetVariablesFromVariableAnnotation() {
    // given
    final PropertyBasedJobWorkerValueCustomizer customizer =
        new PropertyBasedJobWorkerValueCustomizer(properties());
    final JobWorkerValue jobWorkerValue = new JobWorkerValue();
    jobWorkerValue.setMethodInfo(methodInfo(this, "testBean", "sampleWorker"));
    // when
    customizer.customize(jobWorkerValue);
    // then
    assertThat(jobWorkerValue.getFetchVariables()).contains("var1");
  }

  @Test
  void shouldSetVariablesFromVariablesAsTypeAnnotation() {
    // given
    final PropertyBasedJobWorkerValueCustomizer customizer =
        new PropertyBasedJobWorkerValueCustomizer(properties());
    final JobWorkerValue jobWorkerValue = new JobWorkerValue();
    jobWorkerValue.setMethodInfo(methodInfo(this, "testBean", "sampleWorker"));
    // when
    customizer.customize(jobWorkerValue);
    // then
    assertThat(jobWorkerValue.getFetchVariables()).contains("var3", "var4");
  }

  @Test
  void shouldNotSetNameOfVariablesAsTypeAnnotatedFieldLegacy() {
    // given
    final PropertyBasedJobWorkerValueCustomizer customizer =
        new PropertyBasedJobWorkerValueCustomizer(properties());
    final JobWorkerValue jobWorkerValue = new JobWorkerValue();
    jobWorkerValue.setMethodInfo(methodInfo(this, "testBean", "sampleWorker"));
    // when
    customizer.customize(jobWorkerValue);
    // then
    assertThat(jobWorkerValue.getFetchVariables()).doesNotContain("var2");
  }

  @Test
  void shouldNotSetNameOfVariablesAsTypeAnnotatedField() {
    // given
    final PropertyBasedJobWorkerValueCustomizer customizer =
        new PropertyBasedJobWorkerValueCustomizer(properties());
    final JobWorkerValue jobWorkerValue = new JobWorkerValue();
    jobWorkerValue.setMethodInfo(methodInfo(this, "testBean", "sampleWorker"));
    // when
    customizer.customize(jobWorkerValue);
    // then
    assertThat(jobWorkerValue.getFetchVariables()).doesNotContain("var2");
  }

  @Test
  void shouldApplyOverrides() {
    // given
    final CamundaClientProperties properties = properties();
    final JobWorkerValue override = new JobWorkerValue();
    override.setEnabled(false);
    properties.getWorker().getOverride().put("sampleWorker", override);
    final PropertyBasedJobWorkerValueCustomizer customizer =
        new PropertyBasedJobWorkerValueCustomizer(properties);
    final JobWorkerValue jobWorkerValue = new JobWorkerValue();
    jobWorkerValue.setMethodInfo(methodInfo(this, "testBean", "sampleWorker"));
    assertThat(jobWorkerValue.getEnabled()).isNull();
    // when
    customizer.customize(jobWorkerValue);
    // then
    assertThat(jobWorkerValue.getEnabled()).isFalse();
  }

  @Test
  void shouldApplyGlobalOverride() {
    // given
    final CamundaClientProperties properties = properties();
    final JobWorkerValue override = new JobWorkerValue();
    override.setEnabled(false);
    properties.getWorker().setDefaults(override);
    final PropertyBasedJobWorkerValueCustomizer customizer =
        new PropertyBasedJobWorkerValueCustomizer(properties);
    final JobWorkerValue jobWorkerValue = new JobWorkerValue();
    jobWorkerValue.setMethodInfo(methodInfo(this, "testBean", "sampleWorker"));
    assertThat(jobWorkerValue.getEnabled()).isNull();
    // when
    customizer.customize(jobWorkerValue);
    // then
    assertThat(jobWorkerValue.getEnabled()).isFalse();
  }

  @Test
  void shouldApplyWorkerOverridesOverGlobalOverrides() {
    // given
    final CamundaClientProperties properties = properties();
    properties.getWorker().getDefaults().setEnabled(true);
    final JobWorkerValue override = new JobWorkerValue();
    override.setEnabled(false);
    properties.getWorker().getOverride().put("sampleWorker", override);
    final PropertyBasedJobWorkerValueCustomizer customizer =
        new PropertyBasedJobWorkerValueCustomizer(properties);
    final JobWorkerValue jobWorkerValue = new JobWorkerValue();
    jobWorkerValue.setMethodInfo(methodInfo(this, "testBean", "sampleWorker"));
    assertThat(jobWorkerValue.getEnabled()).isNull();
    // when
    customizer.customize(jobWorkerValue);
    // then
    assertThat(jobWorkerValue.getEnabled()).isFalse();
  }

  @Test
  void shouldApplyPropertyAnnotationOnVariableFiltering() {
    // given
    final PropertyBasedJobWorkerValueCustomizer customizer =
        new PropertyBasedJobWorkerValueCustomizer(properties());
    final JobWorkerValue jobWorkerValue = new JobWorkerValue();
    jobWorkerValue.setMethodInfo(methodInfo(this, "testBean", "sampleWorkerWithJsonProperty"));

    // when
    customizer.customize(jobWorkerValue);

    // then
    assertThat(jobWorkerValue.getFetchVariables()).containsExactly("some_name");
  }

  @Test
  void shouldNotOverrideTypeAndNameAndFetchVariablesFromGlobalsIfSet() {
    final CamundaClientProperties properties = properties();
    properties.getWorker().getDefaults().setType("globalOverride");
    properties.getWorker().getDefaults().setName("globalName");
    properties.getWorker().getDefaults().setFetchVariables(List.of("overrideVariable"));
    final PropertyBasedJobWorkerValueCustomizer customizer =
        new PropertyBasedJobWorkerValueCustomizer(properties);
    final JobWorkerValue jobWorkerValue = new JobWorkerValue();
    jobWorkerValue.setMethodInfo(methodInfo(this, "testBean", "sampleWorkerWithJsonProperty"));
    jobWorkerValue.setType("initialValue");
    jobWorkerValue.setName("someName");
    jobWorkerValue.setFetchVariables(List.of("initialVariable"));
    customizer.customize(jobWorkerValue);
    assertThat(jobWorkerValue.getType()).isEqualTo("initialValue");
    assertThat(jobWorkerValue.getName()).isEqualTo("someName");
    assertThat(jobWorkerValue.getFetchVariables()).contains("initialVariable");
  }

  @Test
  void shouldOverrideTypeAndNameFromGlobalsIfNotSet() {
    final CamundaClientProperties properties = properties();
    properties.getWorker().getDefaults().setType("globalOverride");
    properties.getWorker().getDefaults().setName("globalName");
    final PropertyBasedJobWorkerValueCustomizer customizer =
        new PropertyBasedJobWorkerValueCustomizer(properties);
    final JobWorkerValue jobWorkerValue = new JobWorkerValue();
    jobWorkerValue.setMethodInfo(methodInfo(this, "testBean", "sampleWorkerWithJsonProperty"));
    customizer.customize(jobWorkerValue);
    assertThat(jobWorkerValue.getType()).isEqualTo("globalOverride");
    assertThat(jobWorkerValue.getName()).isEqualTo("globalName");
  }

  @Test
  void shouldOverrideTypeAndNameAndFetchVariablesFromLocalsIfSet() {
    final CamundaClientProperties properties = properties();
    final JobWorkerValue override = new JobWorkerValue();
    override.setType("localOverride");
    override.setName("localName");
    override.setFetchVariables(List.of("overrideVariable"));
    properties.getWorker().getOverride().put("initialValue", override);
    final PropertyBasedJobWorkerValueCustomizer customizer =
        new PropertyBasedJobWorkerValueCustomizer(properties);
    final JobWorkerValue jobWorkerValue = new JobWorkerValue();
    jobWorkerValue.setMethodInfo(methodInfo(this, "testBean", "sampleWorkerWithJsonProperty"));
    jobWorkerValue.setType("initialValue");
    jobWorkerValue.setName("someName");
    jobWorkerValue.setFetchVariables(List.of("initialVariable"));
    customizer.customize(jobWorkerValue);
    assertThat(jobWorkerValue.getType()).isEqualTo("localOverride");
    assertThat(jobWorkerValue.getName()).isEqualTo("localName");
    assertThat(jobWorkerValue.getFetchVariables()).contains("overrideVariable");
  }

  @Test
  void shouldNotApplyPropertyAnnotationOnEmptyValue() {
    // given
    final PropertyBasedJobWorkerValueCustomizer customizer =
        new PropertyBasedJobWorkerValueCustomizer(properties());
    final JobWorkerValue jobWorkerValue = new JobWorkerValue();
    jobWorkerValue.setMethodInfo(methodInfo(this, "testBean", "sampleWorkerWithEmptyJsonProperty"));

    // when
    customizer.customize(jobWorkerValue);

    // then
    assertThat(jobWorkerValue.getFetchVariables()).containsExactly("value");
  }

  private static final class ComplexProcessVariable {
    private String var3;
    private String var4;

    public String getVar3() {
      return var3;
    }

    public void setVar3(final String var3) {
      this.var3 = var3;
    }

    public String getVar4() {
      return var4;
    }

    public void setVar4(final String var4) {
      this.var4 = var4;
    }
  }

  private static final class PropertyAnnotatedClass {
    @JsonProperty("some_name")
    private String value;
  }

  private static final class PropertyAnnotatedClassEmptyValue {
    @JsonProperty() private String value;
  }
}
