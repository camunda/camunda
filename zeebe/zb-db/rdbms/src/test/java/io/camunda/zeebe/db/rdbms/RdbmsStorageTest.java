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

class RdbmsStorageTest {

  private final TestDatabase database = createDatabase();

  @AfterEach
  void tearDown() throws SQLException {
    database.close();
  }

  @Test
  void shouldUseTheStatementsOfTheDatabase() {
    // when
    final var storage = RdbmsStorage.open(database.dataSource(), "A_");

    // then
    assertThat(storage.databaseId()).isEqualTo(database.databaseId());
  }

  /** Overridden to run the same tests on another database. */
  TestDatabase createDatabase() {
    return new TestH2Database();
  }
}
