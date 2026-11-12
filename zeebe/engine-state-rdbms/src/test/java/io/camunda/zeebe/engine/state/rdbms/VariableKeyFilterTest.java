/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.rdbms;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.state.rdbms.VariableMapper.VariableKey;
import org.junit.jupiter.api.Test;

final class VariableKeyFilterTest {

  private static final long HIGHEST_AT_START = 1_000;

  private final VariableKeyFilter filter = new VariableKeyFilter(HIGHEST_AT_START);

  @Test
  void shouldNeverReportWrittenVariableAsAbsent() {
    // given
    final var keys = new VariableKey[1_000];
    for (int i = 0; i < keys.length; i++) {
      keys[i] = new VariableKey(HIGHEST_AT_START + 1 + i / 4, "variable-" + i);
    }

    // when
    for (final var key : keys) {
      filter.written(key);
    }

    // then
    for (final var key : keys) {
      assertThat(filter.neverWritten(key)).isFalse();
    }
  }

  @Test
  void shouldReportUnwrittenVariableOfNewScopeAsAbsent() {
    // given
    filter.written(new VariableKey(HIGHEST_AT_START + 1, "written"));

    // then
    assertThat(filter.neverWritten(new VariableKey(HIGHEST_AT_START + 2, "other"))).isTrue();
  }

  @Test
  void shouldAskTheDatabaseAboutScopesFromBeforeTheStart() {
    // then
    assertThat(filter.neverWritten(new VariableKey(HIGHEST_AT_START, "old"))).isFalse();
  }
}
