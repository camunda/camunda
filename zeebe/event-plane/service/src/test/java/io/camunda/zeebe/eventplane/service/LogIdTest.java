/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

final class LogIdTest {

  @Test
  void shouldReadTheClusterTheTenantAndThePartition() {
    // when
    final var log = LogId.parse("my cluster/default/3");

    // then
    assertThat(log).isEqualTo(new LogId("my cluster", "default", 3));
    assertThat(log).hasToString("my cluster/default/3");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "",
        "c",
        "c/default",
        "c/default/",
        "c/default/0",
        "c/default/-1",
        "c/default/p",
        "/default/1",
        "c//1",
        "c/default/1/2",
        "c/default/99999999999"
      })
  void shouldRejectWhatIsNoLog(final String text) {
    // when, then
    assertThatThrownBy(() -> LogId.parse(text))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(
            "Expected a log as {cluster}/{tenant}/{partition}, with a partition of 1 or more, but got '%s'"
                .formatted(text));
  }
}
