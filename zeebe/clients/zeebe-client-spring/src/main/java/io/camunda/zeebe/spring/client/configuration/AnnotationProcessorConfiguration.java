/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.spring.client.configuration;

import io.camunda.zeebe.spring.client.annotation.customizer.ZeebeWorkerValueCustomizer;
import io.camunda.zeebe.spring.client.annotation.processor.AbstractZeebeAnnotationProcessor;
import io.camunda.zeebe.spring.client.annotation.processor.ZeebeAnnotationProcessorRegistry;
import io.camunda.zeebe.spring.client.annotation.processor.ZeebeDeploymentAnnotationProcessor;
import io.camunda.zeebe.spring.client.annotation.processor.ZeebeWorkerAnnotationProcessor;
import io.camunda.zeebe.spring.client.event.ZeebeClientEventListener;
import io.camunda.zeebe.spring.client.jobhandling.JobWorkerManager;
import java.util.List;
import org.springframework.context.annotation.Bean;

public class AnnotationProcessorConfiguration {

  @Bean
  public ZeebeAnnotationProcessorRegistry zeebeAnnotationProcessorRegistry(
      final List<AbstractZeebeAnnotationProcessor> processors) {
    return new ZeebeAnnotationProcessorRegistry(processors);
  }

  @Bean
  public ZeebeClientEventListener zeebeClientEventListener(
      final ZeebeAnnotationProcessorRegistry zeebeAnnotationProcessorRegistry) {
    return new ZeebeClientEventListener(zeebeAnnotationProcessorRegistry);
  }

  @Bean
  public ZeebeDeploymentAnnotationProcessor deploymentPostProcessor() {
    return new ZeebeDeploymentAnnotationProcessor();
  }

  @Bean
  public ZeebeWorkerAnnotationProcessor zeebeWorkerPostProcessor(
      final JobWorkerManager jobWorkerManager,
      final List<ZeebeWorkerValueCustomizer> zeebeWorkerValueCustomizers) {
    return new ZeebeWorkerAnnotationProcessor(jobWorkerManager, zeebeWorkerValueCustomizers);
  }
}
