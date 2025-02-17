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
package io.camunda.spring.client.bean;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.spring.client.annotation.*;
import java.beans.Introspector;
import org.junit.jupiter.api.Test;

public class ClassInfoTest {

  @Test
  public void getBeanInfo() {
    final WithDeploymentAnnotation withDeploymentAnnotation = new WithDeploymentAnnotation();

    final ClassInfo beanInfo = beanInfo(withDeploymentAnnotation);

    assertThat(beanInfo.getBean()).isEqualTo(withDeploymentAnnotation);
    assertThat(beanInfo.getBeanName()).isEqualTo("withDeploymentAnnotation");
    assertThat(beanInfo.getTargetClass()).isEqualTo(WithDeploymentAnnotation.class);
  }

  @Test
  public void hasZeebeeDeploymentAnnotation() {
    assertThat(beanInfo(new WithDeploymentAnnotation()).hasClassAnnotation(Deployment.class))
        .isTrue();
  }

  @Test
  public void hasNoZeebeeDeploymentAnnotation() {
    assertThat(beanInfo(new WithoutDeploymentAnnotation()).hasClassAnnotation(Deployment.class))
        .isFalse();
  }

  @Test
  public void hasZeebeWorkerMethod() {
    assertThat(beanInfo(new WithZeebeWorker()).hasMethodAnnotation(JobWorker.class)).isTrue();
  }

  @Test
  public void hasNotZeebeWorkerMethod() {
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

  public static class NoPropertiesSet {
    @JobWorker
    public void handle() {}
  }

  public static class TenantBound {
    @JobWorker(tenantIds = "tenant-1")
    public void handle() {}
  }
}
