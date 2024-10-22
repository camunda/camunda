/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql.typehandler;

import java.sql.*;
import java.util.Arrays;
import java.util.List;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

public class StringListTypeHandler extends BaseTypeHandler<List<String>> {

  @Override
  public void setNonNullParameter(
      PreparedStatement ps, int i, List<String> parameter, JdbcType jdbcType) throws SQLException {
    ps.setString(i, String.join(",", parameter));
  }

  @Override
  public List<String> getNullableResult(ResultSet rs, String columnName) throws SQLException {
    String columnValue = rs.getString(columnName);
    return columnValue == null ? null : Arrays.asList(columnValue.split(","));
  }

  @Override
  public List<String> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
    String columnValue = rs.getString(columnIndex);
    return columnValue == null ? null : Arrays.asList(columnValue.split(","));
  }

  @Override
  public List<String> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
    String columnValue = cs.getString(columnIndex);
    return columnValue == null ? null : Arrays.asList(columnValue.split(","));
  }
}
