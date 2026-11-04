/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.search.exception.CamundaSearchException;
import io.camunda.search.exception.CamundaSearchException.Reason;
import java.sql.SQLException;
import java.util.stream.Stream;
import org.apache.ibatis.exceptions.PersistenceException;
import org.apache.ibatis.session.SqlSession;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class RdbmsMapperBundleExceptionTranslationTest {

  @ParameterizedTest
  @MethodSource("databaseErrors")
  void shouldTranslateDatabaseErrorsFromBundleMappers(
      final SQLException sqlException, final Reason expectedReason) {
    // given
    final var failure = new PersistenceException(sqlException);
    final var bundle = bundleWhoseMappersThrow(failure);

    // when / then
    assertThatThrownBy(() -> bundle.sequenceFlowMapper().search(null))
        .isInstanceOfSatisfying(
            CamundaSearchException.class, e -> assertThat(e.getReason()).isEqualTo(expectedReason))
        .hasCause(failure);
  }

  static Stream<Arguments> databaseErrors() {
    return Stream.of(
        Arguments.of(new SQLException("unreachable", "08001"), Reason.CONNECTION_FAILED),
        Arguments.of(new SQLException("ORA-01795", "42000", 1795), Reason.INVALID_ARGUMENT));
  }

  private static RdbmsMapperBundle bundleWhoseMappersThrow(final RuntimeException failure) {
    final var sqlSession = mock(SqlSession.class);
    when(sqlSession.getMapper(any()))
        .thenAnswer(
            invocation ->
                mock(
                    invocation.getArgument(0, Class.class),
                    invocationOnMapper -> {
                      throw failure;
                    }));
    return RdbmsMapperBundle.from(null, sqlSession, null);
  }
}
