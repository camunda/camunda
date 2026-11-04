/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.configuration.engine;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

final class MessagesCfgTest {

  @Test
  void shouldAcceptDefaultValues() {
    // given
    final var cfg = new MessagesCfg();

    // when - then
    assertThatCode(() -> cfg.init(new BrokerCfg(), "")).doesNotThrowAnyException();
  }

  @ParameterizedTest
  @ValueSource(ints = {0, -1})
  void shouldRejectPendingSubscriptionRecoveryBatchLimitBelowOne(final int batchLimit) {
    // given
    final var cfg = new MessagesCfg();
    cfg.setPendingSubscriptionRecoveryBatchLimit(batchLimit);

    // when - then
    assertThatThrownBy(() -> cfg.init(new BrokerCfg(), ""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "Messages pendingSubscriptionRecoveryBatchLimit must be at least 1 but was "
                + batchLimit);
  }
}
