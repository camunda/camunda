/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.broker.request;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.gateway.impl.broker.HashBasedDispatchStrategy;
import org.junit.jupiter.api.Test;

final class BrokerCreateProcessInstanceRequestTest {

  @Test
  void shouldReturnEmptyDispatchStrategyWhenNoBusinessId() {
    // given
    final var request = new BrokerCreateProcessInstanceRequest();

    // when/then
    assertThat(request.requestDispatchStrategy()).isEmpty();
  }

  @Test
  void shouldReturnEmptyDispatchStrategyWhenBusinessIdIsNull() {
    // given
    final var request = new BrokerCreateProcessInstanceRequest();
    request.setBusinessId(null);

    // when/then
    assertThat(request.requestDispatchStrategy()).isEmpty();
  }

  @Test
  void shouldReturnEmptyDispatchStrategyWhenBusinessIdIsEmpty() {
    // given
    final var request = new BrokerCreateProcessInstanceRequest();
    request.setBusinessId("");

    // when/then
    assertThat(request.requestDispatchStrategy()).isEmpty();
  }

  @Test
  void shouldReturnHashBasedDispatchStrategyWhenBusinessIdIsSet() {
    // given
    final var request = new BrokerCreateProcessInstanceRequest();
    request.setBusinessId("order-12345");

    // when/then
    assertThat(request.requestDispatchStrategy())
        .isPresent()
        .get()
        .isInstanceOf(HashBasedDispatchStrategy.class);
  }
}
