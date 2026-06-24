/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql.typehandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class PostgresLongListToArrayTypeHandlerTest {

  private final PostgresLongListToArrayTypeHandler handler =
      new PostgresLongListToArrayTypeHandler();

  @Test
  void shouldConvertListToArray() throws Exception {
    final PreparedStatement ps = mock(PreparedStatement.class);
    final Connection conn = mock(Connection.class);
    final Array array = mock(Array.class);

    when(ps.getConnection()).thenReturn(conn);
    when(conn.createArrayOf(eq("bigint"), any())).thenReturn(array);

    final List<Long> input = List.of(1L, 2L, 3L);
    handler.setNonNullParameter(ps, 1, input, null);

    verify(conn).createArrayOf(eq("bigint"), eq(new Long[] {1L, 2L, 3L}));
    verify(ps).setArray(1, array);
    verify(array).free();
  }

  @Test
  void shouldConvertEmptyListToArray() throws Exception {
    final PreparedStatement ps = mock(PreparedStatement.class);
    final Connection conn = mock(Connection.class);
    final Array array = mock(Array.class);

    when(ps.getConnection()).thenReturn(conn);
    when(conn.createArrayOf(eq("bigint"), any())).thenReturn(array);

    final List<Long> input = List.of();
    handler.setNonNullParameter(ps, 1, input, null);

    verify(conn).createArrayOf(eq("bigint"), eq(new Long[] {}));
    verify(ps).setArray(1, array);
    verify(array).free();
  }

  @Test
  void shouldConvertArrayToList() throws Exception {
    final ResultSet rs = mock(ResultSet.class);
    final Array array = mock(Array.class);

    when(rs.getArray("col")).thenReturn(array);
    when(array.getArray()).thenReturn(new Long[] {10L, 20L, 30L});

    final List<Long> result = handler.getNullableResult(rs, "col");

    assertThat(result).containsExactly(10L, 20L, 30L);
    verify(array).free();
  }

  @Test
  void shouldReturnNullForNullArray() throws Exception {
    final ResultSet rs = mock(ResultSet.class);
    when(rs.getArray(anyInt())).thenReturn(null);

    final List<Long> result = handler.getNullableResult(rs, 1);

    assertThat(result).isNull();
  }
}
