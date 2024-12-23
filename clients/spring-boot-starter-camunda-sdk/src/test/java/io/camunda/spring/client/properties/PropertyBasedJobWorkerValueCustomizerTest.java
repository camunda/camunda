/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
import io.camunda.spring.client.properties.common.ZeebeClientProperties;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

  private static CamundaClientConfigurationProperties legacyProperties() {
    final CamundaClientConfigurationProperties properties =
        new CamundaClientConfigurationProperties(null);
    properties.applyOverrides();
    return properties;
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
    final CamundaClientConfigurationProperties properties = legacyProperties();
    final PropertyBasedJobWorkerValueCustomizer customizer =
        new PropertyBasedJobWorkerValueCustomizer(properties, properties());
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
        new PropertyBasedJobWorkerValueCustomizer(legacyProperties(), properties());
    final JobWorkerValue jobWorkerValue = new JobWorkerValue();
    jobWorkerValue.setFetchVariables(List.of("a", "var1", "b"));
    jobWorkerValue.setMethodInfo(methodInfo(this, "testBean", "activatedJobWorker"));
    // when
    customizer.customize(jobWorkerValue);
    // then
    assertThat(jobWorkerValue.getFetchVariables()).containsExactly("a", "var1", "b");
  }

  @Test
  void shouldSetDefaultNameLegacy() {
    // given
    final CamundaClientConfigurationProperties properties = legacyProperties();
    properties.getWorker().setDefaultName("defaultName");
    final PropertyBasedJobWorkerValueCustomizer customizer =
        new PropertyBasedJobWorkerValueCustomizer(properties, properties());
    final JobWorkerValue jobWorkerValue = new JobWorkerValue();
    jobWorkerValue.setMethodInfo(methodInfo(this, "testBean", "sampleWorker"));
    // when
    customizer.customize(jobWorkerValue);
    // then
    assertThat(jobWorkerValue.getName()).isEqualTo("defaultName");
  }

  @Test
  void shouldSetDefaultName() {
    // given
    final CamundaClientProperties properties = properties();
    final ZeebeClientProperties zeebeClientProperties = new ZeebeClientProperties();
    zeebeClientProperties.setDefaults(new JobWorkerValue());
    properties.setZeebe(zeebeClientProperties);
    properties.getZeebe().getDefaults().setName("defaultName");
    final PropertyBasedJobWorkerValueCustomizer customizer =
        new PropertyBasedJobWorkerValueCustomizer(legacyProperties(), properties);
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
        new PropertyBasedJobWorkerValueCustomizer(legacyProperties(), properties());
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
        new PropertyBasedJobWorkerValueCustomizer(legacyProperties(), properties());
    final JobWorkerValue jobWorkerValue = new JobWorkerValue();
    jobWorkerValue.setMethodInfo(methodInfo(this, "testBean", "sampleWorker"));
    // when
    customizer.customize(jobWorkerValue);
    // then
    assertThat(jobWorkerValue.getName()).isEqualTo("testBean#sampleWorker");
  }

  @Test
  void shouldSetDefaultTenantIdsLegacy() {
    // given
    final CamundaClientConfigurationProperties properties = legacyProperties();
    properties.setDefaultJobWorkerTenantIds(List.of("customTenantId"));

    final PropertyBasedJobWorkerValueCustomizer customizer =
        new PropertyBasedJobWorkerValueCustomizer(properties, properties());

    final JobWorkerValue jobWorkerValue = new JobWorkerValue();
    jobWorkerValue.setMethodInfo(methodInfo(this, "testBean", "sampleWorker"));

    // when
    customizer.customize(jobWorkerValue);
    // then
    assertThat(jobWorkerValue.getTenantIds()).contains("customTenantId");
  }

  @Test
  void shouldSetDefaultTenantIds() {
    // given
    final CamundaClientProperties properties = properties();
    properties.setTenantIds(List.of("customTenantId"));

    final PropertyBasedJobWorkerValueCustomizer customizer =
        new PropertyBasedJobWorkerValueCustomizer(legacyProperties(), properties);

    final JobWorkerValue jobWorkerValue = new JobWorkerValue();
    jobWorkerValue.setMethodInfo(methodInfo(this, "testBean", "sampleWorker"));
    // when
    customizer.customize(jobWorkerValue);
    // then
    assertThat(jobWorkerValue.getTenantIds()).contains("customTenantId");
  }

  @Test
  void shouldSetDefaultTypeLegacy() {
    // given
    final CamundaClientConfigurationProperties properties = legacyProperties();
    properties.getWorker().setDefaultType("defaultType");
    final PropertyBasedJobWorkerValueCustomizer customizer =
        new PropertyBasedJobWorkerValueCustomizer(properties, properties());
    final JobWorkerValue jobWorkerValue = new JobWorkerValue();
    jobWorkerValue.setMethodInfo(methodInfo(this, "testBean", "sampleWorker"));
    // when
    customizer.customize(jobWorkerValue);
    // then
    assertThat(jobWorkerValue.getType()).isEqualTo("defaultType");
  }

  @Test
  void shouldSetDefaultType() {
    // given
    final CamundaClientProperties properties = properties();
    final ZeebeClientProperties zeebeClientProperties = new ZeebeClientProperties();
    zeebeClientProperties.setDefaults(new JobWorkerValue());
    properties.setZeebe(zeebeClientProperties);
    properties.getZeebe().getDefaults().setType("defaultType");
    final PropertyBasedJobWorkerValueCustomizer customizer =
        new PropertyBasedJobWorkerValueCustomizer(legacyProperties(), properties);
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
        new PropertyBasedJobWorkerValueCustomizer(legacyProperties(), properties());
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
        new PropertyBasedJobWorkerValueCustomizer(legacyProperties(), properties());
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
        new PropertyBasedJobWorkerValueCustomizer(legacyProperties(), properties());
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
        new PropertyBasedJobWorkerValueCustomizer(legacyProperties(), properties());
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
        new PropertyBasedJobWorkerValueCustomizer(legacyProperties(), properties());
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
        new PropertyBasedJobWorkerValueCustomizer(legacyProperties(), properties());
    final JobWorkerValue jobWorkerValue = new JobWorkerValue();
    jobWorkerValue.setMethodInfo(methodInfo(this, "testBean", "sampleWorker"));
    // when
    customizer.customize(jobWorkerValue);
    // then
    assertThat(jobWorkerValue.getFetchVariables()).doesNotContain("var2");
  }

  @Test
  void shouldApplyOverridesLegacy() {
    // given
    final CamundaClientConfigurationProperties properties = legacyProperties();
    final JobWorkerValue override = new JobWorkerValue();
    override.setEnabled(false);
    properties.getWorker().getOverride().put("sampleWorker", override);
    final PropertyBasedJobWorkerValueCustomizer customizer =
        new PropertyBasedJobWorkerValueCustomizer(properties, properties());
    final JobWorkerValue jobWorkerValue = new JobWorkerValue();
    jobWorkerValue.setMethodInfo(methodInfo(this, "testBean", "sampleWorker"));
    assertThat(jobWorkerValue.getEnabled()).isNull();
    // when
    customizer.customize(jobWorkerValue);
    // then
    assertThat(jobWorkerValue.getEnabled()).isFalse();
  }

  @Test
  void shouldApplyOverrides() {
    // given
    final CamundaClientProperties properties = properties();
    final ZeebeClientProperties zeebeClientProperties = new ZeebeClientProperties();
    final JobWorkerValue override = new JobWorkerValue();
    override.setEnabled(false);
    final Map<String, JobWorkerValue> overrideMap = new HashMap<>();
    overrideMap.put("sampleWorker", override);
    zeebeClientProperties.setOverride(overrideMap);
    properties.setZeebe(zeebeClientProperties);
    final PropertyBasedJobWorkerValueCustomizer customizer =
        new PropertyBasedJobWorkerValueCustomizer(legacyProperties(), properties);
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
    final ZeebeClientProperties zeebeClientProperties = new ZeebeClientProperties();
    final JobWorkerValue override = new JobWorkerValue();
    override.setEnabled(false);
    zeebeClientProperties.setDefaults(override);
    properties.setZeebe(zeebeClientProperties);
    final PropertyBasedJobWorkerValueCustomizer customizer =
        new PropertyBasedJobWorkerValueCustomizer(legacyProperties(), properties);
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
    final ZeebeClientProperties zeebeClientProperties = new ZeebeClientProperties();
    final JobWorkerValue override = new JobWorkerValue();
    override.setEnabled(false);
    final Map<String, JobWorkerValue> overrideMap = new HashMap<>();
    overrideMap.put("sampleWorker", override);
    zeebeClientProperties.setOverride(overrideMap);
    final JobWorkerValue globalOverride = new JobWorkerValue();
    globalOverride.setEnabled(true);
    zeebeClientProperties.setDefaults(globalOverride);
    properties.setZeebe(zeebeClientProperties);
    final PropertyBasedJobWorkerValueCustomizer customizer =
        new PropertyBasedJobWorkerValueCustomizer(legacyProperties(), properties);
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
        new PropertyBasedJobWorkerValueCustomizer(legacyProperties(), properties());
    final JobWorkerValue jobWorkerValue = new JobWorkerValue();
    jobWorkerValue.setMethodInfo(methodInfo(this, "testBean", "sampleWorkerWithJsonProperty"));

    // when
    customizer.customize(jobWorkerValue);

    // then
    assertThat(jobWorkerValue.getFetchVariables()).containsExactly("some_name");
  }

  @Test
  void shouldNotApplyPropertyAnnotationOnEmptyValue() {
    // given
    final PropertyBasedJobWorkerValueCustomizer customizer =
        new PropertyBasedJobWorkerValueCustomizer(legacyProperties(), properties());
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
