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
import java.time.Duration;
import org.junit.jupiter.api.Test;

final class SecretResolutionCfgTest {

  @Test
  void shouldAcceptDefaultValues() {
    // given
    final var cfg = new SecretResolutionCfg();

    // when - then
    assertThatCode(() -> cfg.init(new BrokerCfg(), "")).doesNotThrowAnyException();
  }

  @Test
  void shouldRejectNonPositiveInterval() {
    // given
    final var cfg = new SecretResolutionCfg();
    cfg.setInterval(Duration.ZERO);

    // when - then
    assertThatThrownBy(() -> cfg.init(new BrokerCfg(), ""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Secret resolution interval must be positive but was PT0S");
  }

  @Test
  void shouldRejectRetryMaxAttemptsBelowOne() {
    // given
    final var cfg = new SecretResolutionCfg();
    cfg.setRetryMaxAttempts(0);

    // when - then
    assertThatThrownBy(() -> cfg.init(new BrokerCfg(), ""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Secret resolution retryMaxAttempts must be at least 1 but was 0");
  }

  @Test
  void shouldRejectNonPositiveRetryInitialDelay() {
    // given
    final var cfg = new SecretResolutionCfg();
    cfg.setRetryInitialDelay(Duration.ofSeconds(-1));

    // when - then
    assertThatThrownBy(() -> cfg.init(new BrokerCfg(), ""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Secret resolution retryInitialDelay must be positive but was PT-1S");
  }

  @Test
  void shouldRejectRetryMaxDelaySmallerThanRetryInitialDelay() {
    // given
    final var cfg = new SecretResolutionCfg();
    cfg.setRetryInitialDelay(Duration.ofSeconds(5));
    cfg.setRetryMaxDelay(Duration.ofSeconds(1));

    // when - then
    assertThatThrownBy(() -> cfg.init(new BrokerCfg(), ""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "Secret resolution retryMaxDelay must be positive and not smaller than"
                + " retryInitialDelay but was PT1S");
  }

  @Test
  void shouldRejectRetryBackoffFactorBelowOne() {
    // given
    final var cfg = new SecretResolutionCfg();
    cfg.setRetryBackoffFactor(0);

    // when - then
    assertThatThrownBy(() -> cfg.init(new BrokerCfg(), ""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Secret resolution retryBackoffFactor must be at least 1 but was 0");
  }
}
