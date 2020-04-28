/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.gateway.impl.probes.health;

import io.zeebe.gateway.impl.SpringGatewayBridge;
import org.springframework.boot.actuate.autoconfigure.health.ConditionalOnEnabledHealthIndicator;
import org.springframework.boot.actuate.autoconfigure.health.HealthContributorAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** {@link EnableAutoConfiguration Auto-configuration} for {@link GatewayStartedHealthIndicator}. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnEnabledHealthIndicator("gatewayStarted")
@AutoConfigureBefore(HealthContributorAutoConfiguration.class)
public class GatewayStartedHealthIndicatorAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean(name = "gatewayStartedHealthIndicator")
  public GatewayStartedHealthIndicator gatewayStartedHealthIndicator(
      SpringGatewayBridge gatewayBridge) {
    /**
     * Here we effectively chain two suppliers to decouple their creation in time.
     *
     * <p>The first supplier created here and passed into the constructor of the health indicator is
     * created first. This happens very early in the application's life cycle. At this point in
     * time, the gateway status supplier inside gateway bridge is not yet registered.
     *
     * <p>Later, the actual gateway status supplier will be registered at gatewayBridge. The
     * chaining allows us to delegate over two hops.
     */
    return new GatewayStartedHealthIndicator(() -> gatewayBridge.getGatewayStatus());
  }
}
