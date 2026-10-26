/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.search.filter.Operation;
import java.util.List;
import org.junit.jupiter.api.Test;

class VariableDbQueryTest {

  @Test
  void shouldReplaceProtectionModeNamesWithStoredCodes() {
    // given
    final var query =
        VariableDbQuery.of(
            b ->
                b.filter(
                    f ->
                        f.protectionModeOperations(
                            Operation.eq("REDACT"), Operation.notIn("REDACT"))));

    // when
    final var operations = query.protectionModeCodeOperations();

    // then
    assertThat(operations).containsExactly(Operation.eq(0), Operation.notIn(0));
  }

  @Test
  void shouldKeepOperationsWithoutValues() {
    // given
    final var query =
        VariableDbQuery.of(b -> b.filter(f -> f.protectionModeOperations(Operation.exists(false))));

    // when
    final List<Operation<Integer>> operations = query.protectionModeCodeOperations();

    // then
    assertThat(operations).containsExactly(Operation.exists(false));
  }

  @Test
  void shouldRejectUnknownProtectionMode() {
    // given
    final var query =
        VariableDbQuery.of(b -> b.filter(f -> f.protectionModeOperations(Operation.eq("UNKNOWN"))));

    // when / then
    assertThatThrownBy(query::protectionModeCodeOperations)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Unknown protection mode 'UNKNOWN'");
  }
}
