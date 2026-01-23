/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.probes.health;

import io.camunda.zeebe.gateway.impl.SpringGatewayBridge;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.health.autoconfigure.contributor.ConditionalOnEnabledHealthIndicator;
import org.springframework.boot.health.autoconfigure.contributor.HealthContributorAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** {@link EnableAutoConfiguration Auto-configuration} for {@link ClusterHealthIndicator}. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnEnabledHealthIndicator("gateway-cluster")
@AutoConfigureBefore(HealthContributorAutoConfiguration.class)
public class ClusterHealthIndicatorAutoConfiguration {

  @Bean(name = "clusterHealth")
  @ConditionalOnMissingBean(name = "gatewayClusterHealthIndicator")
  public ClusterHealthIndicator gatewayClusterHealthIndicator(
      final SpringGatewayBridge gatewayBridge) {
    return new ClusterHealthIndicator(gatewayBridge::getClusterState);
  }
}
