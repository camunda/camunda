/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.rdbms;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

final class RdbmsStorageTest {

  private final TestH2Database database = new TestH2Database();

  @AfterEach
  void tearDown() throws SQLException {
    database.close();
  }

  @Test
  void shouldCreateSchemaWhenOpened() throws SQLException {
    // when
    RdbmsStorage.open(database.dataSource(), "A_");

    // then
    assertThat(database.tableNames()).contains("A_ZEEBE_STATE_PARTITION", "A_ZEEBE_STATE");
  }

  @Test
  void shouldUseH2Statements() {
    // when
    final var storage = RdbmsStorage.open(database.dataSource(), "A_");

    // then
    assertThat(storage.databaseId()).isEqualTo("h2");
  }
}
