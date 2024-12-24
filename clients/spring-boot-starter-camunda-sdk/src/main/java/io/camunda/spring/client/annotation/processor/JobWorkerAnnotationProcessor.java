/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.spring.client.annotation.processor;

import static io.camunda.spring.client.annotation.AnnotationUtil.getJobWorkerValue;
import static io.camunda.spring.client.annotation.AnnotationUtil.isJobWorker;
import static org.springframework.util.ReflectionUtils.doWithMethods;

import io.camunda.client.CamundaClient;
import io.camunda.spring.client.annotation.customizer.JobWorkerValueCustomizer;
import io.camunda.spring.client.annotation.value.JobWorkerValue;
import io.camunda.spring.client.bean.ClassInfo;
import io.camunda.spring.client.configuration.AnnotationProcessorConfiguration;
import io.camunda.spring.client.jobhandling.JobWorkerManager;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.ReflectionUtils;

/**
 * Always created by {@link AnnotationProcessorConfiguration}
 *
 * <p>Triggered by {@link CamundaAnnotationProcessorRegistry#postProcessAfterInitialization(Object,
 * String)} to add Handler subscriptions for {@link io.camunda.spring.client.annotation.JobWorker}
 * method-annotations.
 */
public class JobWorkerAnnotationProcessor extends AbstractCamundaAnnotationProcessor {

  private static final Logger LOGGER = LoggerFactory.getLogger(JobWorkerAnnotationProcessor.class);

  private final JobWorkerManager jobWorkerManager;

  private final List<JobWorkerValue> jobWorkerValues = new ArrayList<>();
  private final List<JobWorkerValueCustomizer> jobWorkerValueCustomizers;

  public JobWorkerAnnotationProcessor(
      final JobWorkerManager jobWorkerFactory,
      final List<JobWorkerValueCustomizer> jobWorkerValueCustomizers) {
    jobWorkerManager = jobWorkerFactory;
    this.jobWorkerValueCustomizers = jobWorkerValueCustomizers;
  }

  @Override
  public boolean isApplicableFor(final ClassInfo beanInfo) {
    return isJobWorker(beanInfo);
  }

  @Override
  public void configureFor(final ClassInfo beanInfo) {
    final List<JobWorkerValue> newJobWorkerValues = new ArrayList<>();

    doWithMethods(
        beanInfo.getTargetClass(),
        method ->
            getJobWorkerValue(beanInfo.toMethodInfo(method)).ifPresent(newJobWorkerValues::add),
        ReflectionUtils.USER_DECLARED_METHODS);

    LOGGER.info(
        "Configuring {} Zeebe worker(s) of bean '{}': {}",
        newJobWorkerValues.size(),
        beanInfo.getBeanName(),
        newJobWorkerValues);
    jobWorkerValues.addAll(newJobWorkerValues);
  }

  @Override
  public void start(final CamundaClient client) {
    jobWorkerValues.stream()
        .peek(
            zeebeWorkerValue ->
                jobWorkerValueCustomizers.forEach(
                    customizer -> customizer.customize(zeebeWorkerValue)))
        .filter(JobWorkerValue::getEnabled)
        .forEach(
            zeebeWorkerValue -> {
              jobWorkerManager.openWorker(client, zeebeWorkerValue);
            });
  }

  @Override
  public void stop(final CamundaClient camundaClient) {
    jobWorkerManager.closeAllOpenWorkers();
  }
}
