/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.spring.client.properties;

import static org.assertj.core.api.Assertions.*;

import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.spring.client.annotation.JobWorker;
import io.camunda.zeebe.spring.client.annotation.Variable;
import io.camunda.zeebe.spring.client.annotation.VariablesAsType;
import io.camunda.zeebe.spring.client.annotation.value.ZeebeWorkerValue;
import io.camunda.zeebe.spring.client.bean.ClassInfo;
import io.camunda.zeebe.spring.client.bean.MethodInfo;
import java.util.Arrays;
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

  private static ZeebeClientConfigurationProperties properties() {
    final ZeebeClientConfigurationProperties properties =
        new ZeebeClientConfigurationProperties(null);
    properties.applyOverrides();
    return properties;
  }

  @JobWorker
  void sampleWorker(
      @Variable final String var1, @VariablesAsType final ComplexProcessVariable var2) {}

  @JobWorker
  void activatedJobWorker(@Variable final String var1, final ActivatedJob activatedJob) {}

  @Test
  void shouldNotAdjustVariableFilterVariablesAsActivatedJobIsInjected() {
    // given
    final ZeebeClientConfigurationProperties properties = properties();
    final PropertyBasedZeebeWorkerValueCustomizer customizer =
        new PropertyBasedZeebeWorkerValueCustomizer(properties);
    final ZeebeWorkerValue zeebeWorkerValue = new ZeebeWorkerValue();
    zeebeWorkerValue.setFetchVariables(new String[] {"a", "var1", "b"});
    zeebeWorkerValue.setMethodInfo(methodInfo(this, "testBean", "activatedJobWorker"));
    // when
    customizer.customize(zeebeWorkerValue);
    // then
    assertThat(zeebeWorkerValue.getFetchVariables()).containsExactly("a", "var1", "b");
  }

  @Test
  void shouldSetDefaultName() throws NoSuchMethodException {
    // given
    final ZeebeClientConfigurationProperties properties = properties();
    properties.getWorker().setDefaultName("defaultName");
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
  void shouldSetGeneratedName() {
    // given
    final ZeebeClientConfigurationProperties properties = properties();
    final PropertyBasedZeebeWorkerValueCustomizer customizer =
        new PropertyBasedZeebeWorkerValueCustomizer(properties);
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
    final ZeebeClientConfigurationProperties properties = properties();
    properties.getWorker().setDefaultType("defaultType");
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
  void shouldSetGeneratedType() {
    // given
    final ZeebeClientConfigurationProperties properties = properties();
    final PropertyBasedZeebeWorkerValueCustomizer customizer =
        new PropertyBasedZeebeWorkerValueCustomizer(properties);
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
    final ZeebeClientConfigurationProperties properties = properties();
    final PropertyBasedZeebeWorkerValueCustomizer customizer =
        new PropertyBasedZeebeWorkerValueCustomizer(properties);
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
    final ZeebeClientConfigurationProperties properties = properties();
    final PropertyBasedZeebeWorkerValueCustomizer customizer =
        new PropertyBasedZeebeWorkerValueCustomizer(properties);
    final ZeebeWorkerValue zeebeWorkerValue = new ZeebeWorkerValue();
    zeebeWorkerValue.setMethodInfo(methodInfo(this, "testBean", "sampleWorker"));
    // when
    customizer.customize(zeebeWorkerValue);
    // then
    assertThat(zeebeWorkerValue.getFetchVariables()).contains("var3", "var4");
  }

  @Test
  void shouldNotSetNameOfVariablesAsTypeAnnotatedField() {
    // given
    final ZeebeClientConfigurationProperties properties = properties();
    final PropertyBasedZeebeWorkerValueCustomizer customizer =
        new PropertyBasedZeebeWorkerValueCustomizer(properties);
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
    final ZeebeClientConfigurationProperties properties = properties();
    final ZeebeWorkerValue override = new ZeebeWorkerValue();
    override.setEnabled(false);
    properties.getWorker().getOverride().put("sampleWorker", override);
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

  private static class ComplexProcessVariable {
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
}
