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
package io.camunda.spring.client.bean.factory;

import static io.camunda.spring.client.annotation.AnnotationUtil.getDeploymentValue;
import static org.junit.jupiter.api.Assertions.*;

import io.camunda.spring.client.annotation.Deployment;
import io.camunda.spring.client.annotation.value.DeploymentValue;
import io.camunda.spring.client.bean.ClassInfo;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ReadZeebeDeploymentValueTest {

  @Test
  public void shouldReadSingleClassPathResourceTest() {
    // given
    final ClassInfo classInfo = ClassInfo.builder().bean(new WithSingleClassPathResource()).build();

    final DeploymentValue expectedDeploymentValue =
        DeploymentValue.builder()
            .beanInfo(classInfo)
            .resources(Collections.singletonList("classpath*:/1.bpmn"))
            .build();

    // when
    final Optional<DeploymentValue> valueForClass = getDeploymentValue(classInfo);

    // then
    assertTrue(valueForClass.isPresent());
    assertEquals(expectedDeploymentValue, valueForClass.get());
  }

  @Test
  public void shouldReadMultipleClassPathResourcesTest() {
    // given
    final ClassInfo classInfo =
        ClassInfo.builder().bean(new WithMultipleClassPathResource()).build();

    final DeploymentValue expectedDeploymentValue =
        DeploymentValue.builder()
            .beanInfo(classInfo)
            .resources(Arrays.asList("classpath*:/1.bpmn", "classpath*:/2.bpmn"))
            .build();

    // when
    final Optional<DeploymentValue> valueForClass = getDeploymentValue(classInfo);

    // then
    assertTrue(valueForClass.isPresent());
    assertEquals(expectedDeploymentValue, valueForClass.get());
  }

  @Test
  public void shouldReadNoClassPathResourcesTest() {
    // given
    final ClassInfo classInfo = ClassInfo.builder().bean(new WithoutAnnotation()).build();

    // when
    final Optional<DeploymentValue> valueForClass = getDeploymentValue(classInfo);

    // then
    assertFalse(valueForClass.isPresent());
  }

  @Test
  public void shouldReadSingleClassPathResourceTestLegacy() {
    // given
    final ClassInfo classInfo =
        ClassInfo.builder().bean(new WithSingleClassPathResourceLegacy()).build();

    final DeploymentValue expectedDeploymentValue =
        DeploymentValue.builder()
            .beanInfo(classInfo)
            .resources(Collections.singletonList("classpath*:/1.bpmn"))
            .build();

    // when
    final Optional<DeploymentValue> valueForClass = getDeploymentValue(classInfo);

    // then
    assertTrue(valueForClass.isPresent());
    assertEquals(expectedDeploymentValue, valueForClass.get());
  }

  @Test
  public void shouldReadMultipleClassPathResourcesTestLegacy() {
    // given
    final ClassInfo classInfo =
        ClassInfo.builder().bean(new WithMultipleClassPathResourceLegacy()).build();

    final DeploymentValue expectedDeploymentValue =
        DeploymentValue.builder()
            .beanInfo(classInfo)
            .resources(Arrays.asList("classpath*:/1.bpmn", "classpath*:/2.bpmn"))
            .build();

    // when
    final Optional<DeploymentValue> valueForClass = getDeploymentValue(classInfo);

    // then
    assertTrue(valueForClass.isPresent());
    assertEquals(expectedDeploymentValue, valueForClass.get());
  }

  @Deployment(resources = "classpath*:/1.bpmn")
  private static final class WithSingleClassPathResource {}

  @Deployment(resources = {"classpath*:/1.bpmn", "classpath*:/2.bpmn"})
  private static final class WithMultipleClassPathResource {}

  @io.camunda.zeebe.spring.client.annotation.Deployment(resources = "classpath*:/1.bpmn")
  private static final class WithSingleClassPathResourceLegacy {}

  @io.camunda.zeebe.spring.client.annotation.Deployment(
      resources = {"classpath*:/1.bpmn", "classpath*:/2.bpmn"})
  private static final class WithMultipleClassPathResourceLegacy {}

  private static final class WithoutAnnotation {}
}
