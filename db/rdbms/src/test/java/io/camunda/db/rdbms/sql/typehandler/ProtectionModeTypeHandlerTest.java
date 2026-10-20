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

import io.camunda.db.rdbms.write.domain.VariableDbModel.ProtectionModeDbModel;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.apache.ibatis.type.JdbcType;
import org.junit.jupiter.api.Test;

class ProtectionModeTypeHandlerTest {

  private final ProtectionModeTypeHandler handler = new ProtectionModeTypeHandler();

  @Test
  void shouldWriteCodeOfMode() throws SQLException {
    // given
    final var ps = mock(PreparedStatement.class);

    // when
    handler.setParameter(ps, 1, ProtectionModeDbModel.REDACT, JdbcType.INTEGER);

    // then
    verify(ps).setInt(1, 0);
  }

  @Test
  void shouldReadModeFromCode() throws SQLException {
    // given
    final var rs = mock(ResultSet.class);
    when(rs.getInt("PROTECTION_MODE")).thenReturn(0);
    when(rs.wasNull()).thenReturn(false);

    // when
    final var mode = handler.getNullableResult(rs, "PROTECTION_MODE");

    // then
    assertThat(mode).isEqualTo(ProtectionModeDbModel.REDACT);
  }

  @Test
  void shouldReadNullForNullColumn() throws SQLException {
    // given
    final var rs = mock(ResultSet.class);
    when(rs.getInt("PROTECTION_MODE")).thenReturn(0);
    when(rs.wasNull()).thenReturn(true);

    // when
    final var mode = handler.getNullableResult(rs, "PROTECTION_MODE");

    // then
    assertThat(mode).isNull();
  }

  @Test
  void shouldReadNullForUnknownCode() throws SQLException {
    // given
    final var rs = mock(ResultSet.class);
    when(rs.getInt("PROTECTION_MODE")).thenReturn(99);

    // when
    final var mode = handler.getNullableResult(rs, "PROTECTION_MODE");

    // then
    assertThat(mode).isNull();
  }
}
