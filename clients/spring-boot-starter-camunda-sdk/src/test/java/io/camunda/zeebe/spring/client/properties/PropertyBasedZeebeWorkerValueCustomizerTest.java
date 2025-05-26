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
package io.camunda.zeebe.spring.client.properties;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.spring.client.annotation.JobWorker;
import io.camunda.zeebe.spring.client.annotation.Variable;
import io.camunda.zeebe.spring.client.annotation.VariablesAsType;
import io.camunda.zeebe.spring.client.annotation.value.ZeebeWorkerValue;
import io.camunda.zeebe.spring.client.bean.ClassInfo;
import io.camunda.zeebe.spring.client.bean.MethodInfo;
import io.camunda.zeebe.spring.client.properties.common.ZeebeClientProperties;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class PropertyBasedZeebeWorkerValueCustomizerTest {

  private static MethodInfo methodInfo(
      final Object bean, final String beanName, final String methodName) {
    try {
      return MethodInfo.builder()
          .classInfo(ClassInfo.builder().beanName(beanName).bean(bean).build())
          .method(
              Arrays.stream(PropertyBasedZeebeWorkerValueCustomizerTest.class.getDeclaredMethods())
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
    final PropertyBasedZeebeWorkerValueCustomizer customizer =
        new PropertyBasedZeebeWorkerValueCustomizer(properties());
    final ZeebeWorkerValue zeebeWorkerValue = new ZeebeWorkerValue();
    zeebeWorkerValue.setFetchVariables(List.of("a", "var1", "b"));
    zeebeWorkerValue.setMethodInfo(methodInfo(this, "testBean", "activatedJobWorker"));
    // when
    customizer.customize(zeebeWorkerValue);
    // then
    assertThat(zeebeWorkerValue.getFetchVariables()).containsExactly("a", "var1", "b");
  }

  @Test
  void shouldNotAdjustVariableFilterVariablesAsActivatedJobIsInjected() {
    // given
    final PropertyBasedZeebeWorkerValueCustomizer customizer =
        new PropertyBasedZeebeWorkerValueCustomizer(properties());
    final ZeebeWorkerValue zeebeWorkerValue = new ZeebeWorkerValue();
    zeebeWorkerValue.setFetchVariables(List.of("a", "var1", "b"));
    zeebeWorkerValue.setMethodInfo(methodInfo(this, "testBean", "activatedJobWorker"));
    // when
    customizer.customize(zeebeWorkerValue);
    // then
    assertThat(zeebeWorkerValue.getFetchVariables()).containsExactly("a", "var1", "b");
  }

  @Test
  void shouldSetDefaultName() {
    // given
    final CamundaClientProperties properties = properties();
    final ZeebeClientProperties zeebeClientProperties = new ZeebeClientProperties();
    zeebeClientProperties.setDefaults(new ZeebeWorkerValue());
    properties.setZeebe(zeebeClientProperties);
    properties.getZeebe().getDefaults().setName("defaultName");
    final PropertyBasedZeebeWorkerValueCustomizer customizer =
        new PropertyBasedZeebeWorkerValueCustomizer(properties);
    final ZeebeWorkerValue zeebeWorkerValue = new ZeebeWorkerValue();
    zeebeWorkerValue.setMethodInfo(methodInfo(this, "testBean", "sampleWorker"));
    // when
    customizer.customize(zeebeWorkerValue);
    // then
    assertThat(zeebeWorkerValue.getName()).isEqualTo("defaultName");
  }

  @Test
  void shouldSetGeneratedNameLegacy() {
    // given
    final PropertyBasedZeebeWorkerValueCustomizer customizer =
        new PropertyBasedZeebeWorkerValueCustomizer(properties());
    final ZeebeWorkerValue zeebeWorkerValue = new ZeebeWorkerValue();
    zeebeWorkerValue.setMethodInfo(methodInfo(this, "testBean", "sampleWorker"));
    // when
    customizer.customize(zeebeWorkerValue);
    // then
    assertThat(zeebeWorkerValue.getName()).isEqualTo("testBean#sampleWorker");
  }

  @Test
  void shouldSetGeneratedName() {
    // given
    final PropertyBasedZeebeWorkerValueCustomizer customizer =
        new PropertyBasedZeebeWorkerValueCustomizer(properties());
    final ZeebeWorkerValue zeebeWorkerValue = new ZeebeWorkerValue();
    zeebeWorkerValue.setMethodInfo(methodInfo(this, "testBean", "sampleWorker"));
    // when
    customizer.customize(zeebeWorkerValue);
    // then
    assertThat(zeebeWorkerValue.getName()).isEqualTo("testBean#sampleWorker");
  }

  @Test
  void shouldSetDefaultType() {
    // given
    final CamundaClientProperties properties = properties();
    final ZeebeClientProperties zeebeClientProperties = new ZeebeClientProperties();
    zeebeClientProperties.setDefaults(new ZeebeWorkerValue());
    properties.setZeebe(zeebeClientProperties);
    properties.getZeebe().getDefaults().setType("defaultType");
    final PropertyBasedZeebeWorkerValueCustomizer customizer =
        new PropertyBasedZeebeWorkerValueCustomizer(properties);
    final ZeebeWorkerValue zeebeWorkerValue = new ZeebeWorkerValue();
    zeebeWorkerValue.setMethodInfo(methodInfo(this, "testBean", "sampleWorker"));
    // when
    customizer.customize(zeebeWorkerValue);
    // then
    assertThat(zeebeWorkerValue.getType()).isEqualTo("defaultType");
  }

  @Test
  void shouldSetGeneratedTypeLegacy() {
    // given
    final PropertyBasedZeebeWorkerValueCustomizer customizer =
        new PropertyBasedZeebeWorkerValueCustomizer(properties());
    final ZeebeWorkerValue zeebeWorkerValue = new ZeebeWorkerValue();
    zeebeWorkerValue.setMethodInfo(methodInfo(this, "testBean", "sampleWorker"));
    // when
    customizer.customize(zeebeWorkerValue);
    // then
    assertThat(zeebeWorkerValue.getType()).isEqualTo("sampleWorker");
  }

  @Test
  void shouldSetGeneratedType() {
    // given
    final PropertyBasedZeebeWorkerValueCustomizer customizer =
        new PropertyBasedZeebeWorkerValueCustomizer(properties());
    final ZeebeWorkerValue zeebeWorkerValue = new ZeebeWorkerValue();
    zeebeWorkerValue.setMethodInfo(methodInfo(this, "testBean", "sampleWorker"));
    // when
    customizer.customize(zeebeWorkerValue);
    // then
    assertThat(zeebeWorkerValue.getType()).isEqualTo("sampleWorker");
  }

  @Test
  void shouldSetVariablesFromVariableAnnotation() {
    // given
    final PropertyBasedZeebeWorkerValueCustomizer customizer =
        new PropertyBasedZeebeWorkerValueCustomizer(properties());
    final ZeebeWorkerValue zeebeWorkerValue = new ZeebeWorkerValue();
    zeebeWorkerValue.setMethodInfo(methodInfo(this, "testBean", "sampleWorker"));
    // when
    customizer.customize(zeebeWorkerValue);
    // then
    assertThat(zeebeWorkerValue.getFetchVariables()).contains("var1");
  }

  @Test
  void shouldSetVariablesFromVariablesAsTypeAnnotation() {
    // given
    final PropertyBasedZeebeWorkerValueCustomizer customizer =
        new PropertyBasedZeebeWorkerValueCustomizer(properties());
    final ZeebeWorkerValue zeebeWorkerValue = new ZeebeWorkerValue();
    zeebeWorkerValue.setMethodInfo(methodInfo(this, "testBean", "sampleWorker"));
    // when
    customizer.customize(zeebeWorkerValue);
    // then
    assertThat(zeebeWorkerValue.getFetchVariables()).contains("var3", "var4");
  }

  @Test
  void shouldNotSetNameOfVariablesAsTypeAnnotatedFieldLegacy() {
    // given
    final PropertyBasedZeebeWorkerValueCustomizer customizer =
        new PropertyBasedZeebeWorkerValueCustomizer(properties());
    final ZeebeWorkerValue zeebeWorkerValue = new ZeebeWorkerValue();
    zeebeWorkerValue.setMethodInfo(methodInfo(this, "testBean", "sampleWorker"));
    // when
    customizer.customize(zeebeWorkerValue);
    // then
    assertThat(zeebeWorkerValue.getFetchVariables()).doesNotContain("var2");
  }

  @Test
  void shouldNotSetNameOfVariablesAsTypeAnnotatedField() {
    // given
    final PropertyBasedZeebeWorkerValueCustomizer customizer =
        new PropertyBasedZeebeWorkerValueCustomizer(properties());
    final ZeebeWorkerValue zeebeWorkerValue = new ZeebeWorkerValue();
    zeebeWorkerValue.setMethodInfo(methodInfo(this, "testBean", "sampleWorker"));
    // when
    customizer.customize(zeebeWorkerValue);
    // then
    assertThat(zeebeWorkerValue.getFetchVariables()).doesNotContain("var2");
  }

  @Test
  void shouldApplyOverrides() {
    // given
    final CamundaClientProperties properties = properties();
    final ZeebeClientProperties zeebeClientProperties = new ZeebeClientProperties();
    final ZeebeWorkerValue override = new ZeebeWorkerValue();
    override.setEnabled(false);
    final Map<String, ZeebeWorkerValue> overrideMap = new HashMap<>();
    overrideMap.put("sampleWorker", override);
    zeebeClientProperties.setOverride(overrideMap);
    properties.setZeebe(zeebeClientProperties);
    final PropertyBasedZeebeWorkerValueCustomizer customizer =
        new PropertyBasedZeebeWorkerValueCustomizer(properties);
    final ZeebeWorkerValue zeebeWorkerValue = new ZeebeWorkerValue();
    zeebeWorkerValue.setMethodInfo(methodInfo(this, "testBean", "sampleWorker"));
    assertThat(zeebeWorkerValue.getEnabled()).isNull();
    // when
    customizer.customize(zeebeWorkerValue);
    // then
    assertThat(zeebeWorkerValue.getEnabled()).isFalse();
  }

  @Test
  void shouldApplyGlobalOverride() {
    // given
    final CamundaClientProperties properties = properties();
    final ZeebeClientProperties zeebeClientProperties = new ZeebeClientProperties();
    final ZeebeWorkerValue override = new ZeebeWorkerValue();
    override.setEnabled(false);
    zeebeClientProperties.setDefaults(override);
    properties.setZeebe(zeebeClientProperties);
    final PropertyBasedZeebeWorkerValueCustomizer customizer =
        new PropertyBasedZeebeWorkerValueCustomizer(properties);
    final ZeebeWorkerValue zeebeWorkerValue = new ZeebeWorkerValue();
    zeebeWorkerValue.setMethodInfo(methodInfo(this, "testBean", "sampleWorker"));
    assertThat(zeebeWorkerValue.getEnabled()).isNull();
    // when
    customizer.customize(zeebeWorkerValue);
    // then
    assertThat(zeebeWorkerValue.getEnabled()).isFalse();
  }

  @Test
  void shouldApplyWorkerOverridesOverGlobalOverrides() {
    // given
    final CamundaClientProperties properties = properties();
    final ZeebeClientProperties zeebeClientProperties = new ZeebeClientProperties();
    final ZeebeWorkerValue override = new ZeebeWorkerValue();
    override.setEnabled(false);
    final Map<String, ZeebeWorkerValue> overrideMap = new HashMap<>();
    overrideMap.put("sampleWorker", override);
    zeebeClientProperties.setOverride(overrideMap);
    final ZeebeWorkerValue globalOverride = new ZeebeWorkerValue();
    globalOverride.setEnabled(true);
    zeebeClientProperties.setDefaults(globalOverride);
    properties.setZeebe(zeebeClientProperties);
    final PropertyBasedZeebeWorkerValueCustomizer customizer =
        new PropertyBasedZeebeWorkerValueCustomizer(properties);
    final ZeebeWorkerValue zeebeWorkerValue = new ZeebeWorkerValue();
    zeebeWorkerValue.setMethodInfo(methodInfo(this, "testBean", "sampleWorker"));
    assertThat(zeebeWorkerValue.getEnabled()).isNull();
    // when
    customizer.customize(zeebeWorkerValue);
    // then
    assertThat(zeebeWorkerValue.getEnabled()).isFalse();
  }

  @Test
  void shouldApplyPropertyAnnotationOnVariableFiltering() {
    // given
    final PropertyBasedZeebeWorkerValueCustomizer customizer =
        new PropertyBasedZeebeWorkerValueCustomizer(properties());
    final ZeebeWorkerValue zeebeWorkerValue = new ZeebeWorkerValue();
    zeebeWorkerValue.setMethodInfo(methodInfo(this, "testBean", "sampleWorkerWithJsonProperty"));

    // when
    customizer.customize(zeebeWorkerValue);

    // then
    assertThat(zeebeWorkerValue.getFetchVariables()).containsExactly("some_name");
  }

  @Test
  void shouldNotApplyPropertyAnnotationOnEmptyValue() {
    // given
    final PropertyBasedZeebeWorkerValueCustomizer customizer =
        new PropertyBasedZeebeWorkerValueCustomizer(properties());
    final ZeebeWorkerValue zeebeWorkerValue = new ZeebeWorkerValue();
    zeebeWorkerValue.setMethodInfo(
        methodInfo(this, "testBean", "sampleWorkerWithEmptyJsonProperty"));

    // when
    customizer.customize(zeebeWorkerValue);

    // then
    assertThat(zeebeWorkerValue.getFetchVariables()).containsExactly("value");
  }

  @Test
  void shouldNotOverrideTypeAndNameAndFetchVariablesFromGlobalsIfSet() {
    final CamundaClientProperties properties = properties();
    properties.getZeebe().getDefaults().setType("globalOverride");
    properties.getZeebe().getDefaults().setName("globalName");
    properties.getZeebe().getDefaults().setFetchVariables(List.of("overrideVariable"));
    final PropertyBasedZeebeWorkerValueCustomizer customizer =
        new PropertyBasedZeebeWorkerValueCustomizer(properties);
    final ZeebeWorkerValue jobWorkerValue = new ZeebeWorkerValue();
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
    properties.getZeebe().getDefaults().setType("globalOverride");
    properties.getZeebe().getDefaults().setName("globalName");
    final PropertyBasedZeebeWorkerValueCustomizer customizer =
        new PropertyBasedZeebeWorkerValueCustomizer(properties);
    final ZeebeWorkerValue jobWorkerValue = new ZeebeWorkerValue();
    jobWorkerValue.setMethodInfo(methodInfo(this, "testBean", "sampleWorkerWithJsonProperty"));
    customizer.customize(jobWorkerValue);
    assertThat(jobWorkerValue.getType()).isEqualTo("globalOverride");
    assertThat(jobWorkerValue.getName()).isEqualTo("globalName");
  }

  @Test
  void shouldOverrideTypeAndNameAndFetchVariablesFromLocalsIfSet() {
    final CamundaClientProperties properties = properties();
    final ZeebeWorkerValue override = new ZeebeWorkerValue();
    override.setType("localOverride");
    override.setName("localName");
    override.setFetchVariables(List.of("overrideVariable"));
    properties.getZeebe().getOverride().put("initialValue", override);
    final PropertyBasedZeebeWorkerValueCustomizer customizer =
        new PropertyBasedZeebeWorkerValueCustomizer(properties);
    final ZeebeWorkerValue jobWorkerValue = new ZeebeWorkerValue();
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
  void shouldApplyWorkerDefaultTenantIds() {
    // given
    final CamundaClientProperties properties = properties();
    properties.getZeebe().getDefaults().setTenantIds(List.of("customTenantId"));

    final PropertyBasedZeebeWorkerValueCustomizer customizer =
        new PropertyBasedZeebeWorkerValueCustomizer(properties);

    final ZeebeWorkerValue zeebeWorkerValue = new ZeebeWorkerValue();
    zeebeWorkerValue.setMethodInfo(methodInfo(this, "testBean", "sampleWorker"));
    // when
    customizer.customize(zeebeWorkerValue);
    // then
    assertThat(zeebeWorkerValue.getTenantIds()).contains("customTenantId");
  }

  @Test
  void shouldMergeWorkerDefaultTenantIdsAndWorkerAnnotationTenantIds() {
    // given
    final CamundaClientProperties properties = properties();
    properties.getZeebe().getDefaults().setTenantIds(List.of("customTenantId"));

    final PropertyBasedZeebeWorkerValueCustomizer customizer =
        new PropertyBasedZeebeWorkerValueCustomizer(properties);

    final ZeebeWorkerValue jobWorkerValue = new ZeebeWorkerValue();
    jobWorkerValue.setMethodInfo(methodInfo(this, "testBean", "sampleWorker"));
    jobWorkerValue.setTenantIds(List.of("annotationTenantId"));
    // when
    customizer.customize(jobWorkerValue);
    // then
    assertThat(jobWorkerValue.getTenantIds())
        .containsExactlyInAnyOrder("annotationTenantId", "customTenantId");
  }

  @Test
  void shouldApplyClientDefaultTenantIdWhenNothingElseConfigured() {
    // given
    final CamundaClientProperties properties = properties();
    properties.setTenantId("testTenantId");
    final PropertyBasedZeebeWorkerValueCustomizer customizer =
        new PropertyBasedZeebeWorkerValueCustomizer(properties);
    final ZeebeWorkerValue jobWorkerValue = new ZeebeWorkerValue();
    jobWorkerValue.setMethodInfo(methodInfo(this, "testBean", "sampleWorker"));
    customizer.customize(jobWorkerValue);
    assertThat(jobWorkerValue.getTenantIds()).containsOnly("testTenantId");
  }

  @Test
  void shouldApplyWorkerDefaultTenantIdsOnlyWhenClientDefaultTenantIdIsSet() {
    // given
    final CamundaClientProperties properties = properties();
    properties.setTenantId("customTenantId");
    properties.getZeebe().getDefaults().setTenantIds(List.of("testTenantId1", "testTenantId2"));

    final PropertyBasedZeebeWorkerValueCustomizer customizer =
        new PropertyBasedZeebeWorkerValueCustomizer(properties);

    final ZeebeWorkerValue jobWorkerValue = new ZeebeWorkerValue();
    jobWorkerValue.setMethodInfo(methodInfo(this, "testBean", "sampleWorker"));
    // when
    customizer.customize(jobWorkerValue);
    // then
    assertThat(jobWorkerValue.getTenantIds())
        .containsExactlyInAnyOrder("testTenantId1", "testTenantId2");
  }

  @Test
  void shouldApplyDeprecatedDefaultTenantIdsWhenNothingElseConfigured() {
    // given
    final CamundaClientProperties properties = properties();
    properties.setTenantIds(List.of("testTenantId1", "testTenantId2"));

    final PropertyBasedZeebeWorkerValueCustomizer customizer =
        new PropertyBasedZeebeWorkerValueCustomizer(properties);

    final ZeebeWorkerValue jobWorkerValue = new ZeebeWorkerValue();
    jobWorkerValue.setMethodInfo(methodInfo(this, "testBean", "sampleWorker"));
    // when
    customizer.customize(jobWorkerValue);
    // then
    assertThat(jobWorkerValue.getTenantIds())
        .containsExactlyInAnyOrder("testTenantId1", "testTenantId2");
  }

  @Test
  void shouldApplyWorkerDefaultTenantIdsWhenDeprecatedDefaultTenantIdsAreSet() {
    // given
    final CamundaClientProperties properties = properties();
    properties.setTenantIds(List.of("testTenantId1", "testTenantId2"));
    properties.getZeebe().getDefaults().setTenantIds(List.of("testTenantId3", "testTenantId4"));

    final PropertyBasedZeebeWorkerValueCustomizer customizer =
        new PropertyBasedZeebeWorkerValueCustomizer(properties);

    final ZeebeWorkerValue jobWorkerValue = new ZeebeWorkerValue();
    jobWorkerValue.setMethodInfo(methodInfo(this, "testBean", "sampleWorker"));
    // when
    customizer.customize(jobWorkerValue);
    // then
    assertThat(jobWorkerValue.getTenantIds())
        .containsExactlyInAnyOrder("testTenantId3", "testTenantId4");
  }

  @Test
  void shouldMergeClientDefaultTenantIdsAndWorkerAnnotationTenantIdsWhenNothingElseConfigured() {
    // given
    final CamundaClientProperties properties = properties();
    properties.setTenantId("customTenantId");

    final PropertyBasedZeebeWorkerValueCustomizer customizer =
        new PropertyBasedZeebeWorkerValueCustomizer(properties);

    final ZeebeWorkerValue jobWorkerValue = new ZeebeWorkerValue();
    jobWorkerValue.setMethodInfo(methodInfo(this, "testBean", "sampleWorker"));
    jobWorkerValue.setTenantIds(List.of("annotationTenantId"));
    // when
    customizer.customize(jobWorkerValue);
    // then
    assertThat(jobWorkerValue.getTenantIds())
        .containsExactlyInAnyOrder("annotationTenantId", "customTenantId");
  }

  @Test
  void shouldApplyTenantIdWorkerOverridesRegardlessOfDefaultsSet() {
    // given
    final CamundaClientProperties properties = properties();
    properties.getZeebe().getDefaults().setTenantIds(List.of("workerDefaultsId"));
    properties.setTenantId("clientDefaultId");
    properties.setTenantIds(List.of("deprecatedClientDefaults"));
    final ZeebeWorkerValue overrideJobWorkerValue = new ZeebeWorkerValue();
    overrideJobWorkerValue.setMethodInfo(methodInfo(this, "testBean", "sampleWorker"));
    overrideJobWorkerValue.setTenantIds(List.of("overriddenTenantId"));
    properties.getZeebe().getOverride().put("sampleWorker", overrideJobWorkerValue);

    final PropertyBasedZeebeWorkerValueCustomizer customizer =
        new PropertyBasedZeebeWorkerValueCustomizer(properties);

    final ZeebeWorkerValue zeebeWorkerValue = new ZeebeWorkerValue();
    zeebeWorkerValue.setTenantIds(List.of("annotationWorkerDefaultsId"));
    zeebeWorkerValue.setMethodInfo(methodInfo(this, "testBean", "sampleWorker"));
    // when
    customizer.customize(zeebeWorkerValue);
    // then
    assertThat(zeebeWorkerValue.getTenantIds()).contains("overriddenTenantId");
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
