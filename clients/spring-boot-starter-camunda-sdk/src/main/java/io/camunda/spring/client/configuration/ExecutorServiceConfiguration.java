/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.spring.client.configuration;

import static io.camunda.spring.client.configuration.CamundaClientConfigurationImpl.DEFAULT;
import static io.camunda.spring.client.configuration.PropertyUtil.getProperty;

import io.camunda.spring.client.jobhandling.CamundaClientExecutorService;
import io.camunda.spring.client.properties.CamundaClientConfigurationProperties;
import io.camunda.spring.client.properties.CamundaClientProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.instrument.binder.jvm.ExecutorServiceMetrics;
import java.util.Collections;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@ConditionalOnClass(MeterRegistry.class)
@ConditionalOnMissingBean(CamundaClientExecutorService.class)
public class ExecutorServiceConfiguration {

  private final CamundaClientConfigurationProperties configurationProperties;
  private final CamundaClientProperties camundaClientProperties;

  public ExecutorServiceConfiguration(
      final CamundaClientConfigurationProperties configurationProperties,
      final CamundaClientProperties camundaClientProperties) {
    this.configurationProperties = configurationProperties;
    this.camundaClientProperties = camundaClientProperties;
  }

  @Bean
  public CamundaClientExecutorService zeebeClientThreadPool(
      @Autowired(required = false) final MeterRegistry meterRegistry) {
    final ScheduledExecutorService threadPool =
        Executors.newScheduledThreadPool(
            getProperty(
                "NumJobWorkerExecutionThreads",
                null,
                DEFAULT.getNumJobWorkerExecutionThreads(),
                camundaClientProperties::getExecutionThreads,
                () -> camundaClientProperties.getZeebe().getExecutionThreads(),
                configurationProperties::getNumJobWorkerExecutionThreads));
    if (meterRegistry != null) {
      final MeterBinder threadPoolMetrics =
          new ExecutorServiceMetrics(
              threadPool, "zeebe_client_thread_pool", Collections.emptyList());
      threadPoolMetrics.bindTo(meterRegistry);
    }
    return new CamundaClientExecutorService(threadPool, true);
  }
}
