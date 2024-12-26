/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.spring.client.configuration;

import io.camunda.client.CamundaClient;
import io.camunda.spring.client.actuator.CamundaClientHealthIndicator;
import io.camunda.spring.client.actuator.MicrometerMetricsRecorder;
import io.camunda.spring.client.metrics.MetricsRecorder;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.actuate.autoconfigure.endpoint.EndpointAutoConfiguration;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;

@AutoConfigureBefore(MetricsDefaultConfiguration.class)
@ConditionalOnClass({
  EndpointAutoConfiguration.class,
  MeterRegistry.class
}) // only if actuator is on classpath
public class CamundaActuatorConfiguration {
  @Bean
  @ConditionalOnMissingBean
  public MetricsRecorder micrometerMetricsRecorder(
      final @Autowired @Lazy MeterRegistry meterRegistry) {
    return new MicrometerMetricsRecorder(meterRegistry);
  }

  @Bean
  InitializingBean forceMeterRegistryPostProcessor(
      final @Autowired(required = false) @Qualifier("meterRegistryPostProcessor") BeanPostProcessor
              meterRegistryPostProcessor,
      final @Autowired(required = false) MeterRegistry registry) {
    if (registry == null || meterRegistryPostProcessor == null) {
      return () -> {};
    } else {
      return () -> meterRegistryPostProcessor.postProcessAfterInitialization(registry, "");
    }
  }

  @Bean
  @ConditionalOnProperty(
      prefix = "management.health.camunda",
      name = "enabled",
      matchIfMissing = true)
  @ConditionalOnClass(HealthIndicator.class)
  @ConditionalOnMissingBean(name = "camundaClientHealthIndicator")
  public CamundaClientHealthIndicator camundaClientHealthIndicator(final CamundaClient client) {
    return new CamundaClientHealthIndicator(client);
  }
}
