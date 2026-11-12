/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.rdbms;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.db.ZeebeDbException;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.SQLRecoverableException;
import java.sql.SQLTimeoutException;
import java.util.List;
import org.apache.ibatis.exceptions.PersistenceException;
import org.junit.jupiter.api.Test;

final class SqlErrorsTest {

  @Test
  void shouldTreatPostgresErrorsThatMayPassAsRecoverable() {
    // serialization failure, deadlock, lock not available, statement cancelled, connection failure
    final var states = List.of("40001", "40P01", "55P03", "57014", "08006");

    assertThat(states)
        .allSatisfy(
            state ->
                assertThat(SqlErrors.translate(new SQLException("error", state)))
                    .isInstanceOf(ZeebeDbException.class));
  }

  @Test
  void shouldTreatTransientAndRecoverableErrorsAsRecoverable() {
    assertThat(SqlErrors.translate(new SQLTimeoutException("timeout")))
        .isInstanceOf(ZeebeDbException.class);
    assertThat(SqlErrors.translate(new SQLRecoverableException("lost")))
        .isInstanceOf(ZeebeDbException.class);
  }

  @Test
  void shouldFindTheDatabaseErrorBehindMyBatisErrors() {
    // given
    final var error = new PersistenceException(new SQLException("deadlock", "40P01"));

    // when / then
    assertThat(SqlErrors.translate(error)).isInstanceOf(ZeebeDbException.class);
  }

  @Test
  void shouldFindTheDatabaseErrorThatABatchChained() {
    // given
    final var error = new SQLException("batch failed", "XX000");
    error.setNextException(new SQLException("deadlock", "40P01"));

    // when / then
    assertThat(SqlErrors.translate(error)).isInstanceOf(ZeebeDbException.class);
  }

  @Test
  void shouldTreatOtherErrorsAsUnexpected() {
    // given
    final var errors =
        List.<Exception>of(
            new SQLException("table missing", "42P01"),
            new SQLIntegrityConstraintViolationException("duplicate", "23505"),
            new SQLException("no state"),
            new PersistenceException("no cause"));

    // when / then
    assertThat(errors)
        .allSatisfy(
            error ->
                assertThat(SqlErrors.translate(error))
                    .isNotInstanceOf(ZeebeDbException.class)
                    .hasCause(error));
  }
}
