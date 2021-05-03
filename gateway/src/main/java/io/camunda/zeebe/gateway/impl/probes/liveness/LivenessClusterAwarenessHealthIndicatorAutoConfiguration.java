/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.gateway.impl.probes.liveness;

import io.zeebe.gateway.impl.probes.health.ClusterAwarenessHealthIndicator;
import io.zeebe.util.health.DelayedHealthIndicator;
import io.zeebe.util.health.MemoryHealthIndicator;
import org.springframework.boot.actuate.autoconfigure.health.ConditionalOnEnabledHealthIndicator;
import org.springframework.boot.actuate.autoconfigure.health.HealthContributorAutoConfiguration;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
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
      ClusterAwarenessHealthIndicator healthIndicator,
      LivenessClusterAwarenessHealthIndicatorProperties properties) {
    return new DelayedHealthIndicator(healthIndicator, properties.getMaxDowntime());
  }
}
