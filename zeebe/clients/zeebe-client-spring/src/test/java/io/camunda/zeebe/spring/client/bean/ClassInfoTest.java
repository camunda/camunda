/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.spring.client.bean;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.spring.client.annotation.*;
import java.beans.Introspector;
import org.junit.jupiter.api.Test;

public class ClassInfoTest {

  @Test
  public void getBeanInfo() throws Exception {
    final WithDeploymentAnnotation withDeploymentAnnotation = new WithDeploymentAnnotation();

    final ClassInfo beanInfo = beanInfo(withDeploymentAnnotation);

    assertThat(beanInfo.getBean()).isEqualTo(withDeploymentAnnotation);
    assertThat(beanInfo.getBeanName()).isEqualTo("withDeploymentAnnotation");
    assertThat(beanInfo.getTargetClass()).isEqualTo(WithDeploymentAnnotation.class);
  }

  @Test
  public void hasZeebeeDeploymentAnnotation() throws Exception {
    assertThat(beanInfo(new WithDeploymentAnnotation()).hasClassAnnotation(Deployment.class))
        .isTrue();
  }

  @Test
  public void hasNoZeebeeDeploymentAnnotation() throws Exception {
    assertThat(beanInfo(new WithoutDeploymentAnnotation()).hasClassAnnotation(Deployment.class))
        .isFalse();
  }

  @Test
  public void hasZeebeWorkerMethod() throws Exception {
    assertThat(beanInfo(new WithZeebeWorker()).hasMethodAnnotation(JobWorker.class)).isTrue();
  }

  @Test
  public void hasNotZeebeWorkerMethod() throws Exception {
    assertThat(beanInfo("normal String").hasMethodAnnotation(JobWorker.class)).isFalse();
  }

  private ClassInfo beanInfo(final Object bean) {
    return ClassInfo.builder()
        .bean(bean)
        .beanName(Introspector.decapitalize(bean.getClass().getSimpleName()))
        .build();
  }

  @Deployment(resources = "classpath*:/1.bpmn")
  public static class WithDeploymentAnnotation {}

  public static class WithoutDeploymentAnnotation {}

  public static class WithZeebeWorker {

    @JobWorker(type = "bar", timeout = 100L, name = "kermit", autoComplete = false)
    public void handle() {}
  }

  public static class WithZeebeWorkerAllValues {

    @JobWorker(
        type = "bar",
        timeout = 100L,
        name = "kermit",
        requestTimeout = 500L,
        pollInterval = 1_000L,
        maxJobsActive = 3,
        fetchVariables = {"foo"},
        autoComplete = true,
        enabled = true)
    public void handle() {}
  }

  public static class WithZeebeWorkerVariables {
    @JobWorker(type = "bar", timeout = 100L, fetchVariables = "var3", autoComplete = false)
    public void handle(@Variable final String var1, @Variable final int var2) {}
  }

  public static class WithDisabledZeebeWorker {
    @JobWorker(type = "bar", enabled = false, autoComplete = false)
    public void handle(@Variable final String var1, @Variable final int var2) {}
  }

  public static class WithZeebeWorkerVariablesComplexType {
    @JobWorker(type = "bar", timeout = 100L, fetchVariables = "var2", autoComplete = false)
    public void handle(@Variable final String var1, @Variable final ComplexTypeDTO var2) {}

    public static class ComplexTypeDTO {
      private String var1;
      private String var2;
    }
  }

  public static class NoPropertiesSet {
    @JobWorker
    public void handle() {}
  }

  public static class TenantBound {
    @JobWorker(tenantIds = "tenant-1")
    public void handle() {}
  }
}
