/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.serving.sql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class SqlTextTest {

  @Test
  void shouldParseIsoInstant() {
    // given / when / then
    assertThat(SqlText.parseInstant("2026-07-23T12:00:00Z"))
        .isEqualTo(Instant.parse("2026-07-23T12:00:00Z"));
    assertThat(SqlText.parseInstant("2026-07-23T14:00:00+02:00"))
        .isEqualTo(Instant.parse("2026-07-23T12:00:00Z"));
  }

  @Test
  void shouldParseEpochMillisDigitString() {
    // given: the webapp's Date.now() millis, bound by Jackson as a digit string

    // when / then
    assertThat(SqlText.parseInstant("1784723383499"))
        .isEqualTo(Instant.ofEpochMilli(1784723383499L));
  }

  @Test
  void shouldRejectShortDigitStringsAsDates() {
    // given: "2026" must never silently read as epoch millis (a 1970 instant)

    // when / then
    assertThatIllegalArgumentException()
        .isThrownBy(() -> SqlText.parseInstant("2026"))
        .withMessageContaining("2026");
  }

  @Test
  void shouldRejectGarbage() {
    // given / when / then
    assertThatIllegalArgumentException()
        .isThrownBy(() -> SqlText.parseInstant("not-a-date"))
        .withMessageContaining("not-a-date");
    assertThatIllegalArgumentException().isThrownBy(() -> SqlText.parseInstant("  "));
    assertThatIllegalArgumentException().isThrownBy(() -> SqlText.parseInstant(null));
  }

  @Test
  void shouldRenderTimestamptzLiteralFromEitherForm() {
    // given / when / then
    assertThat(SqlText.timestamptzLiteral("1784723383499"))
        .isEqualTo("TIMESTAMPTZ '" + Instant.ofEpochMilli(1784723383499L) + "'");
    assertThat(SqlText.timestamptzLiteral("2026-07-23T12:00:00Z"))
        .isEqualTo("TIMESTAMPTZ '2026-07-23T12:00:00Z'");
  }
}
