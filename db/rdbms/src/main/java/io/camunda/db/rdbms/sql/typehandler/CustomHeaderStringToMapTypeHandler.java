/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql.typehandler;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

public class CustomHeaderStringToMapTypeHandler extends BaseTypeHandler<Map<String, String>> {

  @Override
  public void setNonNullParameter(
      final PreparedStatement ps,
      final int i,
      final Map<String, String> parameter,
      final JdbcType jdbcType)
      throws SQLException {
    ps.setObject(i, parameter);
  }

  @Override
  public Map<String, String> getNullableResult(final ResultSet rs, final String columnName)
      throws SQLException {
    return Map.of(); // TODO
  }

  @Override
  public Map<String, String> getNullableResult(final ResultSet rs, final int columnIndex)
      throws SQLException {
    return Map.of(); // TODO
  }

  @Override
  public Map<String, String> getNullableResult(final CallableStatement cs, final int columnIndex)
      throws SQLException {
    return Map.of(); // TODO
  }
}
