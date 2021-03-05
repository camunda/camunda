/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.gateway.impl.probes.health;

import io.zeebe.gateway.impl.SpringGatewayBridge;
import org.springframework.boot.actuate.autoconfigure.health.ConditionalOnEnabledHealthIndicator;
import org.springframework.boot.actuate.autoconfigure.health.HealthContributorAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** {@link EnableAutoConfiguration Auto-configuration} for {@link StartedHealthIndicator}. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnEnabledHealthIndicator("gateway-started")
@AutoConfigureBefore(HealthContributorAutoConfiguration.class)
public class StartedHealthIndicatorAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean(name = "gatewayStartedHealthIndicator")
  public StartedHealthIndicator gatewayStartedHealthIndicator(SpringGatewayBridge gatewayBridge) {
    // Here we effectively chain two suppliers to decouple their creation in time.
    return new StartedHealthIndicator(gatewayBridge::getGatewayStatus);
  }
}
