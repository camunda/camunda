/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.it.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

import io.zeebe.broker.it.gateway.GatewayHealthIndicatorsIntegrationTest.Config;
import io.zeebe.gateway.Gateway.Status;
import io.zeebe.gateway.impl.SpringGatewayBridge;
import io.zeebe.gateway.impl.probes.health.GatewayStartedHealthIndicator;
import io.zeebe.gateway.impl.probes.health.MemoryHealthIndicator;
import java.util.function.Supplier;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = Config.class)
public class GatewayHealthIndicatorsIntegrationTest {

  @Autowired MemoryHealthIndicator memoryHealthIndicator;

  @Autowired GatewayStartedHealthIndicator gatewayStartedHealthIndicator;

  @Autowired SpringGatewayBridge springGatewayBridge;

  @Test
  public void shouldInitializeMemoryHealthIndicatorWithDefaults() {
    assertThat(memoryHealthIndicator.getThreshold()).isEqualTo(0.1, offset(0.001));
  }

  @Test
  public void shouldCreateGatewayStartedHealthIndicatorThatIsBackedBySpringGatewayBridge() {
    // precondition
    assertThat(gatewayStartedHealthIndicator).isNotNull();
    assertThat(springGatewayBridge).isNotNull();

    // given
    final Supplier<Status> statusSupplier = () -> Status.SHUTDOWN;

    // when
    final Health actualHealthBeforeRegisteringStatusSupplier =
        gatewayStartedHealthIndicator.health();
    springGatewayBridge.registerGatewayStatusSupplier(statusSupplier);
    final Health actualHealthAfterRegisteringStatusSupplier =
        gatewayStartedHealthIndicator.health();

    // then
    assertThat(actualHealthBeforeRegisteringStatusSupplier.getStatus())
        .isSameAs(org.springframework.boot.actuate.health.Status.UNKNOWN);
    assertThat(actualHealthAfterRegisteringStatusSupplier.getStatus())
        .isSameAs(org.springframework.boot.actuate.health.Status.OUT_OF_SERVICE);
  }

  @Configuration
  @ComponentScan({"io.zeebe.gateway.impl"})
  static class Config {}
}
