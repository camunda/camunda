/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.spring.client.bean.value.factory;

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
import org.mockito.MockitoAnnotations;

public class ReadZeebeDeploymentValueTest {

  private ZeebeDeploymentAnnotationProcessor annotationProcessor;

  @BeforeEach
  public void init() {
    MockitoAnnotations.initMocks(this);
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
  private static class WithSingleClassPathResource {}

  @Deployment(resources = {"classpath*:/1.bpmn", "classpath*:/2.bpmn"})
  private static class WithMultipleClassPathResource {}

  private static class WithoutAnnotation {}
}
