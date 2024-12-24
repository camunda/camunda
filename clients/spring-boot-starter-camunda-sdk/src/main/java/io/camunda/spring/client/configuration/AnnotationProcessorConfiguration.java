/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.spring.client.configuration;

import io.camunda.spring.client.annotation.customizer.JobWorkerValueCustomizer;
import io.camunda.spring.client.annotation.processor.AbstractCamundaAnnotationProcessor;
import io.camunda.spring.client.annotation.processor.CamundaAnnotationProcessorRegistry;
import io.camunda.spring.client.annotation.processor.DeploymentAnnotationProcessor;
import io.camunda.spring.client.annotation.processor.JobWorkerAnnotationProcessor;
import io.camunda.spring.client.event.CamundaClientEventListener;
import io.camunda.spring.client.jobhandling.JobWorkerManager;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

public class AnnotationProcessorConfiguration {

  @Bean
  public CamundaAnnotationProcessorRegistry zeebeAnnotationProcessorRegistry(
      final List<AbstractCamundaAnnotationProcessor> processors) {
    return new CamundaAnnotationProcessorRegistry(processors);
  }

  @Bean
  public CamundaClientEventListener zeebeClientEventListener(
      final CamundaAnnotationProcessorRegistry zeebeAnnotationProcessorRegistry) {
    return new CamundaClientEventListener(zeebeAnnotationProcessorRegistry);
  }

  @Bean
  @ConditionalOnProperty(value = "camunda.client.deployment.enabled", matchIfMissing = true)
  public DeploymentAnnotationProcessor deploymentPostProcessor() {
    return new DeploymentAnnotationProcessor();
  }

  @Bean
  public JobWorkerAnnotationProcessor zeebeWorkerPostProcessor(
      final JobWorkerManager jobWorkerManager,
      final List<JobWorkerValueCustomizer> jobWorkerValueCustomizers) {
    return new JobWorkerAnnotationProcessor(jobWorkerManager, jobWorkerValueCustomizers);
  }
}
