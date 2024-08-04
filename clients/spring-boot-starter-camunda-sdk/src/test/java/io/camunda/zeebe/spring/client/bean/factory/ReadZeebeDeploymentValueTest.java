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
package io.camunda.zeebe.spring.client.bean.factory;

import static org.junit.jupiter.api.Assertions.*;

import io.camunda.zeebe.spring.client.annotation.Deployment;
import io.camunda.zeebe.spring.client.annotation.processor.ZeebeDeploymentAnnotationProcessor;
import io.camunda.zeebe.spring.client.annotation.value.ZeebeDeploymentValue;
import io.camunda.zeebe.spring.client.bean.ClassInfo;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ReadZeebeDeploymentValueTest {

  @InjectMocks private ZeebeDeploymentAnnotationProcessor annotationProcessor;

  @BeforeEach
  public void init() {
    annotationProcessor = new ZeebeDeploymentAnnotationProcessor();
  }

  @Test
  public void shouldReadSingleClassPathResourceTest() {
    // given
    final ClassInfo classInfo = ClassInfo.builder().bean(new WithSingleClassPathResource()).build();

    final ZeebeDeploymentValue expectedDeploymentValue =
        ZeebeDeploymentValue.builder()
            .beanInfo(classInfo)
            .resources(Collections.singletonList("classpath*:/1.bpmn"))
            .build();

    // when
    final Optional<ZeebeDeploymentValue> valueForClass =
        annotationProcessor.readAnnotation(classInfo);

    // then
    assertTrue(valueForClass.isPresent());
    assertEquals(expectedDeploymentValue, valueForClass.get());
  }

  @Test
  public void shouldReadMultipleClassPathResourcesTest() {
    // given
    final ClassInfo classInfo =
        ClassInfo.builder().bean(new WithMultipleClassPathResource()).build();

    final ZeebeDeploymentValue expectedDeploymentValue =
        ZeebeDeploymentValue.builder()
            .beanInfo(classInfo)
            .resources(Arrays.asList("classpath*:/1.bpmn", "classpath*:/2.bpmn"))
            .build();

    // when
    final Optional<ZeebeDeploymentValue> valueForClass =
        annotationProcessor.readAnnotation(classInfo);

    // then
    assertTrue(valueForClass.isPresent());
    assertEquals(expectedDeploymentValue, valueForClass.get());
  }

  @Test
  public void shouldReadNoClassPathResourcesTest() {
    // given
    final ClassInfo classInfo = ClassInfo.builder().bean(new WithoutAnnotation()).build();

    // when
    final Optional<ZeebeDeploymentValue> valueForClass =
        annotationProcessor.readAnnotation(classInfo);

    // then
    assertFalse(valueForClass.isPresent());
  }

  @Deployment(resources = "classpath*:/1.bpmn")
  private static final class WithSingleClassPathResource {}

  @Deployment(resources = {"classpath*:/1.bpmn", "classpath*:/2.bpmn"})
  private static final class WithMultipleClassPathResource {}

  private static final class WithoutAnnotation {}
}
