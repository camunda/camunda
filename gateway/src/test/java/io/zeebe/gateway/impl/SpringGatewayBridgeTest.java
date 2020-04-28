/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.gateway.impl;

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.gateway.Gateway.Status;
import java.util.function.Supplier;
import org.junit.Before;
import org.junit.Test;

public class SpringGatewayBridgeTest {

  private SpringGatewayBridge sutBrigde;

  @Before
  public void setUp() {
    sutBrigde = new SpringGatewayBridge();
  }

  @Test
  public void shouldReturnNoGatewayStatusByDefault() {
    // when
    final Status actual = sutBrigde.getGatewayStatus();

    // then
    assertThat(actual).describedAs("Gateway status when no supplier is set").isNull();
  }

  @Test
  public void shouldUseGatewayStatusSupplierWhenSet() {
    // given
    final Supplier<Status> testSupplier = () -> Status.RUNNING;
    sutBrigde.registerGatewayStatusSupplier(testSupplier);

    // when
    final Status actual = sutBrigde.getGatewayStatus();

    // then
    assertThat(actual).isSameAs(Status.RUNNING);
  }
}
