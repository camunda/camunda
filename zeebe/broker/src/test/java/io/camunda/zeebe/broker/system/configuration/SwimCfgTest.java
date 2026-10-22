/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.configuration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

final class SwimCfgTest {

  @Test
  void shouldNotValidateSwimWhenDetectorIsDisabled() {
    // given
    final var cfg = new FailureDetectorCfg();
    cfg.getSwim().setMinObservers(0);

    // when - then
    assertThatCode(() -> cfg.init(new BrokerCfg(), "")).doesNotThrowAnyException();
  }

  @Test
  void shouldAcceptTheDefaults() {
    // given
    final var cfg = enabled();

    // when - then
    assertThatCode(() -> cfg.init(new BrokerCfg(), "")).doesNotThrowAnyException();
  }

  @ParameterizedTest
  @ValueSource(strings = {"reportInterval", "reportTimeout", "detectionWindow", "recoveryWindow"})
  void shouldRejectWindowsThatAreNotWholeSeconds(final String window) {
    // given - SWIM probes in milliseconds, the detector's windows are in seconds
    final var cfg = enabled();
    final var swim = cfg.getSwim();
    switch (window) {
      case "reportInterval" -> swim.setReportInterval(Duration.ofMillis(1_500));
      case "reportTimeout" -> swim.setReportTimeout(Duration.ofMillis(5_500));
      case "detectionWindow" -> swim.setDetectionWindow(Duration.ofMillis(30_500));
      default -> swim.setRecoveryWindow(Duration.ofMillis(500));
    }

    // when - then
    assertThatThrownBy(() -> cfg.init(new BrokerCfg(), ""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(window)
        .hasMessageContaining("whole number of seconds");
  }

  @Test
  void shouldRejectReportIntervalBelowOneSecond() {
    // given
    final var cfg = enabled();
    cfg.getSwim().setReportInterval(Duration.ZERO);

    // when - then
    assertThatThrownBy(() -> cfg.init(new BrokerCfg(), ""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("reportInterval");
  }

  @Test
  void shouldRejectReportTimeoutBelowTwiceTheReportInterval() {
    // given
    final var cfg = enabled();
    cfg.getSwim().setReportInterval(Duration.ofSeconds(3));
    cfg.getSwim().setReportTimeout(Duration.ofSeconds(5));

    // when - then
    assertThatThrownBy(() -> cfg.init(new BrokerCfg(), ""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("reportTimeout");
  }

  @Test
  void shouldRejectDetectionWindowBelowReportTimeout() {
    // given
    final var cfg = enabled();
    cfg.getSwim().setReportTimeout(Duration.ofSeconds(10));
    cfg.getSwim().setDetectionWindow(Duration.ofSeconds(5));
    final var brokerCfg = new BrokerCfg();
    brokerCfg.getCluster().getMembership().setFailureTimeout(Duration.ofSeconds(1));

    // when - then
    assertThatThrownBy(() -> cfg.init(brokerCfg, ""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("detectionWindow")
        .hasMessageContaining("reportTimeout");
  }

  @Test
  void shouldRejectDetectionWindowBelowTheSwimFailureTimeout() {
    // given - declaring brokers gone before SWIM gave up on them would defeat SWIM's refutations
    final var cfg = enabled();
    cfg.getSwim().setDetectionWindow(Duration.ofSeconds(30));
    final var brokerCfg = new BrokerCfg();
    brokerCfg.getCluster().getMembership().setFailureTimeout(Duration.ofSeconds(60));

    // when - then
    assertThatThrownBy(() -> cfg.init(brokerCfg, ""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("SWIM failure timeout");
  }

  @Test
  void shouldAcceptDetectionWindowEqualToTheSwimFailureTimeout() {
    // given
    final var cfg = enabled();
    cfg.getSwim().setDetectionWindow(Duration.ofSeconds(60));
    final var brokerCfg = new BrokerCfg();
    brokerCfg.getCluster().getMembership().setFailureTimeout(Duration.ofSeconds(60));

    // when - then
    assertThatCode(() -> cfg.init(brokerCfg, "")).doesNotThrowAnyException();
  }

  @Test
  void shouldAcceptNoRecoveryWindow() {
    // given - the region is failed over as soon as it is detected as gone
    final var cfg = enabled();
    cfg.getSwim().setRecoveryWindow(Duration.ZERO);

    // when - then
    assertThatCode(() -> cfg.init(new BrokerCfg(), "")).doesNotThrowAnyException();
  }

  @Test
  void shouldRejectNegativeRecoveryWindow() {
    // given
    final var cfg = enabled();
    cfg.getSwim().setRecoveryWindow(Duration.ofSeconds(-1));

    // when - then
    assertThatThrownBy(() -> cfg.init(new BrokerCfg(), ""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("recoveryWindow");
  }

  @Test
  void shouldRejectMinObserversBelowOne() {
    // given
    final var cfg = enabled();
    cfg.getSwim().setMinObservers(0);

    // when - then
    assertThatThrownBy(() -> cfg.init(new BrokerCfg(), ""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("minObservers");
  }

  private static FailureDetectorCfg enabled() {
    final var cfg = new FailureDetectorCfg();
    cfg.setType(FailureDetectorType.SWIM);
    return cfg;
  }
}
