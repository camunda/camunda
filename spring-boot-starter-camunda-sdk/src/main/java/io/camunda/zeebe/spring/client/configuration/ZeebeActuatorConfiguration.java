/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.spring.client.configuration;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.spring.client.actuator.MicrometerMetricsRecorder;
import io.camunda.zeebe.spring.client.actuator.ZeebeClientHealthIndicator;
import io.camunda.zeebe.spring.client.metrics.DefaultNoopMetricsRecorder;
import io.camunda.zeebe.spring.client.metrics.MetricsRecorder;
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
public class ZeebeActuatorConfiguration {
  @Bean
  // ConditionalOnBean for MeterRegistry does not work (always missing, seems to be created too
  // late)
  // so using @Autowired(required=false) with null check
  public MetricsRecorder micrometerMetricsRecorder(
      final @Autowired(required = false) @Lazy MeterRegistry meterRegistry) {
    if (meterRegistry == null) {
      // We might have Actuator on the classpath without starting a MetricsRecorder in some cases
      return new DefaultNoopMetricsRecorder();
    } else {
      return new MicrometerMetricsRecorder(meterRegistry);
    }
  }

  /**
   * Workaround to fix premature initialization of MeterRegistry that seems to happen here, see
   * https://github.com/camunda-community-hub/spring-zeebe/issues/296
   */
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
      prefix = "management.health.zeebe",
      name = "enabled",
      matchIfMissing = true)
  @ConditionalOnClass(HealthIndicator.class)
  @ConditionalOnMissingBean(name = "zeebeClientHealthIndicator")
  public ZeebeClientHealthIndicator zeebeClientHealthIndicator(final ZeebeClient client) {
    return new ZeebeClientHealthIndicator(client);
  }
}
