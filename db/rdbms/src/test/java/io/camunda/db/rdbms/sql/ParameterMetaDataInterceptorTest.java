/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Arrays;
import java.util.List;
import org.apache.ibatis.executor.parameter.ParameterHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class ParameterMetaDataInterceptorTest {

  private final PreparedStatement driverStatement = mock(PreparedStatement.class);

  @Test
  void shouldFailParameterMetaDataLookupWithSqlExceptionWithoutAskingTheDriver()
      throws SQLException {
    // given a driver whose metadata lookup blows up with an unchecked exception, like Oracle's
    when(driverStatement.getParameterMetaData()).thenThrow(new ArrayIndexOutOfBoundsException());
    final var handler = intercepted(ps -> ps.getParameterMetaData());

    // when / then MyBatis only tolerates a SQLException here, and the driver is left alone
    assertThatThrownBy(() -> handler.setParameters(driverStatement))
        .isInstanceOf(SQLFeatureNotSupportedException.class);
    verify(driverStatement, never()).getParameterMetaData();
  }

  @Test
  void shouldDelegateAllOtherCallsToTheDriverStatement() throws SQLException {
    // given
    final var handler = intercepted(ps -> ps.setLong(1, 7L));

    // when
    handler.setParameters(driverStatement);

    // then
    verify(driverStatement).setLong(1, 7L);
  }

  @Test
  void shouldPropagateExceptionsOfTheDriverStatementUnwrapped() throws SQLException {
    // given
    final var failure = new SQLException("boom");
    doThrow(failure).when(driverStatement).setLong(1, 7L);
    final var handler = intercepted(ps -> ps.setLong(1, 7L));

    // when / then
    assertThat(
            org.assertj.core.api.Assertions.catchThrowable(
                () -> handler.setParameters(driverStatement)))
        .isSameAs(failure);
  }

  @Test
  void shouldBindParametersWithMyBatisDefaultHandlerWhenDriverMetaDataLookupIsBroken()
      throws SQLException {
    // given the real MyBatis parameter handler and a driver that fails like Oracle's does
    when(driverStatement.getParameterMetaData()).thenThrow(new ArrayIndexOutOfBoundsException());
    final var configuration = new Configuration();
    configuration.addInterceptor(new ParameterMetaDataInterceptor());
    final var mapping = new ParameterMapping.Builder(configuration, "id", Long.class).build();
    final var boundSql = new BoundSql(configuration, "select ?", List.of(mapping), 7L);
    final var statement =
        new MappedStatement.Builder(
                configuration, "test.select", ps -> boundSql, SqlCommandType.SELECT)
            .build();

    // when
    configuration.newParameterHandler(statement, 7L, boundSql).setParameters(driverStatement);

    // then the parameter is bound and the driver's metadata was never consulted
    verify(driverStatement).setLong(1, 7L);
    verify(driverStatement, never()).getParameterMetaData();
  }

  @Test
  void shouldBeRemovedOnceMyBatisSupportsIgnoreParameterMetaData() {
    // given MyBatis 3.6.0, which has no setting to skip the driver's parameter metadata lookup
    // when the setting appears (mybatis-3#3844, since 3.6.1) this workaround is obsolete
    final var hasSetting =
        Arrays.stream(Configuration.class.getMethods())
            .map(Method::getName)
            .anyMatch("setIgnoreParameterMetaData"::equals);

    // then
    assertThat(hasSetting)
        .as(
            "MyBatis now supports ignoreParameterMetaData (mybatis-3#3844). Remove"
                + " ParameterMetaDataInterceptor and call"
                + " configuration.setIgnoreParameterMetaData(true) in MyBatisConfiguration."
                + " See camunda/camunda#69167.")
        .isFalse();
  }

  private static ParameterHandler intercepted(final StatementBinder binder) {
    final ParameterHandler target =
        new ParameterHandler() {
          @Override
          public Object getParameterObject() {
            return null;
          }

          @Override
          public void setParameters(final PreparedStatement ps) throws SQLException {
            binder.bind(ps);
          }
        };
    return (ParameterHandler) Plugin.wrap(target, new ParameterMetaDataInterceptor());
  }

  @FunctionalInterface
  private interface StatementBinder {
    void bind(PreparedStatement ps) throws SQLException;
  }
}
