/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql.typehandler;

import io.camunda.db.rdbms.write.domain.VariableDbModel.ProtectionModeDbModel;
import io.camunda.search.entities.VariableEntity.ProtectionMode;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

/**
 * Converts between the stored protection mode code and the {@link ProtectionMode} of the variable
 * entity, which is what the search layer exposes. Writing the stored value goes through {@link
 * ProtectionModeTypeHandler}; this handler binds a searched mode as a filter parameter.
 */
public class EntityProtectionModeTypeHandler extends BaseTypeHandler<ProtectionMode> {

  @Override
  public void setNonNullParameter(
      final PreparedStatement ps,
      final int i,
      final ProtectionMode parameter,
      final JdbcType jdbcType)
      throws SQLException {
    ps.setInt(i, toCode(parameter));
  }

  @Override
  public ProtectionMode getNullableResult(final ResultSet rs, final String columnName)
      throws SQLException {
    return toProtocol(rs.getInt(columnName), rs.wasNull());
  }

  @Override
  public ProtectionMode getNullableResult(final ResultSet rs, final int columnIndex)
      throws SQLException {
    return toProtocol(rs.getInt(columnIndex), rs.wasNull());
  }

  @Override
  public ProtectionMode getNullableResult(final CallableStatement cs, final int columnIndex)
      throws SQLException {
    return toProtocol(cs.getInt(columnIndex), cs.wasNull());
  }

  private static int toCode(final ProtectionMode mode) {
    return switch (mode) {
      case REDACT -> ProtectionModeDbModel.REDACT.getCode();
    };
  }

  /**
   * A missing code is the empty side of the LEFT JOIN of a variable without protection. An unknown
   * code fails loudly, because dropping it would report a protected variable as unprotected.
   */
  private static ProtectionMode toProtocol(final int code, final boolean wasNull) {
    if (wasNull) {
      return null;
    }
    final var mode = ProtectionModeDbModel.fromCode(code);
    if (mode == null) {
      throw new IllegalStateException("Unknown protection mode code " + code);
    }
    return switch (mode) {
      case REDACT -> ProtectionMode.REDACT;
    };
  }
}
