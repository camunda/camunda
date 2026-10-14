/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql.typehandler;

import io.camunda.db.rdbms.write.domain.VariableDbModel.ProtectionModeDbModel;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

public class ProtectionModeTypeHandler extends BaseTypeHandler<ProtectionModeDbModel> {

  @Override
  public void setNonNullParameter(
      final PreparedStatement ps,
      final int i,
      final ProtectionModeDbModel parameter,
      final JdbcType jdbcType)
      throws SQLException {
    ps.setInt(i, parameter.getCode());
  }

  @Override
  public ProtectionModeDbModel getNullableResult(final ResultSet rs, final String columnName)
      throws SQLException {
    final int result = rs.getInt(columnName);
    return result == 0 && rs.wasNull() ? null : ProtectionModeDbModel.fromCode(result);
  }

  @Override
  public ProtectionModeDbModel getNullableResult(final ResultSet rs, final int columnIndex)
      throws SQLException {
    final int result = rs.getInt(columnIndex);
    return result == 0 && rs.wasNull() ? null : ProtectionModeDbModel.fromCode(result);
  }

  @Override
  public ProtectionModeDbModel getNullableResult(final CallableStatement cs, final int columnIndex)
      throws SQLException {
    final int result = cs.getInt(columnIndex);
    return result == 0 && cs.wasNull() ? null : ProtectionModeDbModel.fromCode(result);
  }
}
