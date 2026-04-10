/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql.typehandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NullToEmptyStringTypeHandlerTest {

  private NullToEmptyStringTypeHandler typeHandler;

  @BeforeEach
  void setUp() {
    typeHandler = new NullToEmptyStringTypeHandler();
  }

  @Test
  void shouldReturnEmptyStringWhenResultSetReturnsNullByColumnName() throws Exception {
    // given
    final ResultSet rs = mock(ResultSet.class);
    when(rs.getString("column")).thenReturn(null);

    // when
    final String result = typeHandler.getNullableResult(rs, "column");

    // then
    assertThat(result).isEqualTo("");
  }

  @Test
  void shouldReturnValueWhenResultSetReturnsNonNullByColumnName() throws Exception {
    // given
    final ResultSet rs = mock(ResultSet.class);
    when(rs.getString("column")).thenReturn("hello");

    // when
    final String result = typeHandler.getNullableResult(rs, "column");

    // then
    assertThat(result).isEqualTo("hello");
  }

  @Test
  void shouldReturnEmptyStringWhenResultSetReturnsNullByColumnIndex() throws Exception {
    // given
    final ResultSet rs = mock(ResultSet.class);
    when(rs.getString(1)).thenReturn(null);

    // when
    final String result = typeHandler.getNullableResult(rs, 1);

    // then
    assertThat(result).isEqualTo("");
  }

  @Test
  void shouldReturnValueWhenResultSetReturnsNonNullByColumnIndex() throws Exception {
    // given
    final ResultSet rs = mock(ResultSet.class);
    when(rs.getString(1)).thenReturn("world");

    // when
    final String result = typeHandler.getNullableResult(rs, 1);

    // then
    assertThat(result).isEqualTo("world");
  }

  @Test
  void shouldReturnEmptyStringWhenCallableStatementReturnsNull() throws Exception {
    // given
    final CallableStatement cs = mock(CallableStatement.class);
    when(cs.getString(1)).thenReturn(null);

    // when
    final String result = typeHandler.getNullableResult(cs, 1);

    // then
    assertThat(result).isEqualTo("");
  }

  @Test
  void shouldReturnValueWhenCallableStatementReturnsNonNull() throws Exception {
    // given
    final CallableStatement cs = mock(CallableStatement.class);
    when(cs.getString(1)).thenReturn("value");

    // when
    final String result = typeHandler.getNullableResult(cs, 1);

    // then
    assertThat(result).isEqualTo("value");
  }

  @Test
  void shouldPassThroughEmptyStringUnchanged() throws Exception {
    // given
    final ResultSet rs = mock(ResultSet.class);
    when(rs.getString("column")).thenReturn("");

    // when
    final String result = typeHandler.getNullableResult(rs, "column");

    // then
    assertThat(result).isEqualTo("");
  }

  @Test
  void shouldSetNonNullParameterOnPreparedStatement() throws Exception {
    // given
    final PreparedStatement ps = mock(PreparedStatement.class);

    // when
    typeHandler.setNonNullParameter(ps, 1, "test", null);

    // then
    verify(ps).setString(1, "test");
  }
}
