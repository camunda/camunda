/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.probes.liveness;

import io.camunda.zeebe.gateway.impl.probes.health.ClusterAwarenessHealthIndicator;
import io.camunda.zeebe.util.health.DelayedHealthIndicator;
import io.camunda.zeebe.util.health.MemoryHealthIndicator;
import org.springframework.boot.actuate.autoconfigure.health.ConditionalOnEnabledHealthIndicator;
import org.springframework.boot.actuate.autoconfigure.health.HealthContributorAutoConfiguration;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** {@link EnableAutoConfiguration Auto-configuration} for {@link MemoryHealthIndicator}. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnEnabledHealthIndicator("gateway-clusterawareness")
@AutoConfigureBefore(HealthContributorAutoConfiguration.class)
@EnableConfigurationProperties(LivenessClusterAwarenessHealthIndicatorProperties.class)
@EnableScheduling
public class LivenessClusterAwarenessHealthIndicatorAutoConfiguration {

  @Bean(name = "livenessGatewayClusterAwarenessHealthIndicator")
  @ConditionalOnMissingBean(name = "livenessGatewayClusterAwarenessHealthIndicator")
  public HealthIndicator livenessGatewayClusterAwarenessHealthIndicator(
      final ClusterAwarenessHealthIndicator healthIndicator,
      final LivenessClusterAwarenessHealthIndicatorProperties properties) {
    return new DelayedHealthIndicator(healthIndicator, properties.getMaxDowntime());
  }
}
