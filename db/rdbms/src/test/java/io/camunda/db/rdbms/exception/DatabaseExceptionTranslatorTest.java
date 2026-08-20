/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.exception;

import static io.camunda.db.rdbms.exception.DatabaseExceptionTranslator.ORA_01795_IN_LIST_TOO_LARGE;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.exception.CamundaSearchException;
import io.camunda.search.exception.CamundaSearchException.Reason;
import java.sql.SQLException;
import org.apache.ibatis.exceptions.PersistenceException;
import org.junit.jupiter.api.Test;

class DatabaseExceptionTranslatorTest {

  @Test
  void shouldTranslateDirectOra01795SqlException() {
    // given
    final var sqlException =
        new SQLException(
            "ORA-01795: maximum number of expressions in a list is 1000",
            "72000",
            ORA_01795_IN_LIST_TOO_LARGE);
    final var exception = new RuntimeException("Wrapper", sqlException);

    // when
    final var result = DatabaseExceptionTranslator.translateIfNeeded(exception);

    // then
    assertThat(result).isInstanceOf(CamundaSearchException.class);
    final var cse = (CamundaSearchException) result;
    assertThat(cse.getReason()).isEqualTo(Reason.INVALID_ARGUMENT);
    assertThat(cse.getMessage()).contains("IN clause").contains("1000");
    assertThat(cse.getCause()).isSameAs(exception);
  }

  @Test
  void shouldTranslateOra01795WrappedMultipleLayersDeep() {
    // given – simulate Spring + MyBatis wrapping: outer wraps PersistenceException wraps
    // SQLException
    final var sqlException =
        new SQLException(
            "ORA-01795: maximum number of expressions in a list is 1000",
            "72000",
            ORA_01795_IN_LIST_TOO_LARGE);
    final var persistence = new PersistenceException(sqlException);
    final var springWrapper = new RuntimeException("UncategorizedSQLException", persistence);

    // when
    final var result = DatabaseExceptionTranslator.translateIfNeeded(springWrapper);

    // then
    assertThat(result).isInstanceOf(CamundaSearchException.class);
    assertThat(((CamundaSearchException) result).getReason()).isEqualTo(Reason.INVALID_ARGUMENT);
  }

  @Test
  void shouldReturnOriginalExceptionForNonDatabaseErrors() {
    // given
    final var original = new RuntimeException("Some other error");

    // when
    final var result = DatabaseExceptionTranslator.translateIfNeeded(original);

    // then
    assertThat(result).isSameAs(original);
  }

  @Test
  void shouldReturnOriginalExceptionForOtherSqlErrorCodes() {
    // given – ORA-00904 (invalid identifier) should NOT be translated
    final var cause = new SQLException("ORA-00904: invalid identifier", "42000", 904);
    final var exception = new RuntimeException("Wrapper", cause);

    // when
    final var result = DatabaseExceptionTranslator.translateIfNeeded(exception);

    // then
    assertThat(result).isSameAs(exception);
  }

  @Test
  void shouldReturnOriginalExceptionWhenNoCause() {
    // given
    final var exception = new RuntimeException("No cause");

    // when
    final var result = DatabaseExceptionTranslator.translateIfNeeded(exception);

    // then
    assertThat(result).isSameAs(exception);
  }
}
