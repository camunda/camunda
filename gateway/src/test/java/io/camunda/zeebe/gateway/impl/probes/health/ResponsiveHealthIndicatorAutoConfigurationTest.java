/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.gateway.impl.probes.health;

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.gateway.impl.configuration.GatewayCfg;
import java.time.Duration;
import org.junit.Test;

public class ResponsiveHealthIndicatorAutoConfigurationTest {

  @Test
  public void shouldConfigureHealthIndicator() {
    // given
    final var gatewayCfg = new GatewayCfg();
    final var timeout = Duration.ofSeconds(3);
    final var properties = new ResponsiveHealthIndicatorProperties();
    properties.setRequestTimeout(timeout);

    final var sutAutoConfig = new ResponsiveHealthIndicatorAutoConfiguration();

    // when
    final var actualHealthIndicator =
        sutAutoConfig.gatewayResponsiveHealthIndicator(gatewayCfg, properties);

    // then
    assertThat(actualHealthIndicator).isNotNull();
    assertThat(actualHealthIndicator.getGatewayCfg()).isSameAs(gatewayCfg);
    assertThat(actualHealthIndicator.getDefaultTimeout()).isSameAs(timeout);
  }
}
