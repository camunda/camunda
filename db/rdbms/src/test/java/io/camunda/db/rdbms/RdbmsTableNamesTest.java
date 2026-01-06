/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RdbmsTableNamesTest {

  @Test
  void shouldContainAllExpectedTables() {
    // given / when
    final var tableNames = RdbmsTableNames.TABLE_NAMES;

    // then - verify that the list is not empty and contains expected tables
    assertThat(tableNames).isNotEmpty();
    assertThat(tableNames).contains("PROCESS_INSTANCE");
    assertThat(tableNames).contains("PROCESS_DEFINITION");
    assertThat(tableNames).contains("FLOW_NODE_INSTANCE");
    assertThat(tableNames).contains("VARIABLE");
    assertThat(tableNames).contains("INCIDENT");
    assertThat(tableNames).contains("USER_TASK");
    assertThat(tableNames).contains("JOB");
  }

  @Test
  void shouldBeImmutable() {
    // given / when / then - verify list cannot be modified
    assertThatThrownBy(() -> RdbmsTableNames.TABLE_NAMES.add("TEST"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void shouldHaveUniqueEntries() {
    // given / when
    final var tableNames = RdbmsTableNames.TABLE_NAMES;

    // then
    assertThat(tableNames).doesNotHaveDuplicates();
  }
}
