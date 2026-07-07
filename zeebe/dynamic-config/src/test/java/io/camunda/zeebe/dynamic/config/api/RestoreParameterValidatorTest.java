/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.api;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Instant;
import org.junit.jupiter.api.Test;

final class RestoreParameterValidatorTest {

  private static final Instant EARLIER = Instant.parse("2026-01-01T10:00:00Z");
  private static final Instant LATER = Instant.parse("2026-01-01T12:00:00Z");
  private static final Instant NULL_INSTANT = null;

  @Test
  void shouldAcceptBackupIdOnly() {
    // when / then
    assertThatCode(
            () -> RestoreParameterValidator.validate(true, NULL_INSTANT, NULL_INSTANT, false))
        .doesNotThrowAnyException();
  }

  @Test
  void shouldAcceptTimeRangeOnlyWhenRangesAllowed() {
    // when / then
    assertThatCode(() -> RestoreParameterValidator.validate(false, EARLIER, LATER, true))
        .doesNotThrowAnyException();
  }

  @Test
  void shouldRejectNoParameters() {
    // when / then
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(
            () -> RestoreParameterValidator.validate(false, NULL_INSTANT, NULL_INSTANT, false))
        .withMessage("Must specify either backupId or from/to.");
  }

  @Test
  void shouldRejectBackupIdAndTimeRangeTogether() {
    // when / then
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> RestoreParameterValidator.validate(true, EARLIER, NULL_INSTANT, true))
        .withMessage("Cannot specify both backupId and from/to parameters. Choose one approach.");
  }

  @Test
  void shouldRejectTimeRangeWithoutContinuousBackups() {
    // when / then
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> RestoreParameterValidator.validate(false, EARLIER, LATER, false))
        .withMessage("Time range restore (from/to) is only supported for continuous backups.");
  }

  @Test
  void shouldRejectSingleBoundWithoutContinuousBackups() {
    // when / then
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> RestoreParameterValidator.validate(false, EARLIER, NULL_INSTANT, false))
        .withMessage("Time range restore (from/to) is only supported for continuous backups.");
  }

  @Test
  void shouldAcceptBackupIdWithContinuousBackups() {
    // when / then
    assertThatCode(
            () -> RestoreParameterValidator.validate(true, NULL_INSTANT, NULL_INSTANT, false))
        .doesNotThrowAnyException();
  }

  @Test
  void shouldRejectFromAfterTo() {
    // when / then
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> RestoreParameterValidator.validate(false, LATER, EARLIER, true))
        .withMessage(
            "Invalid time range: from (%s) must be before to (%s)".formatted(LATER, EARLIER));
  }
}
