/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.typehandler;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

/**
 * Oracle treats empty strings as NULL. This type handler converts null values back to empty strings
 * when reading from the database, ensuring that fields which are required (non-nullable) in the API
 * specification are never returned as null even if the underlying database stored them as NULL.
 *
 * <p>Use this handler on MyBatis result map columns for String fields that are required and
 * non-nullable in the API contract but may legitimately be empty (e.g., protobuf default values).
 */
public final class NullToEmptyStringTypeHandler extends BaseTypeHandler<String> {

  @Override
  public void setNonNullParameter(
      final PreparedStatement ps, final int i, final String parameter, final JdbcType jdbcType)
      throws SQLException {
    ps.setString(i, parameter);
  }

  @Override
  public String getNullableResult(final ResultSet rs, final String columnName) throws SQLException {
    return nullToEmpty(rs.getString(columnName));
  }

  @Override
  public String getNullableResult(final ResultSet rs, final int columnIndex) throws SQLException {
    return nullToEmpty(rs.getString(columnIndex));
  }

  @Override
  public String getNullableResult(final CallableStatement cs, final int columnIndex)
      throws SQLException {
    return nullToEmpty(cs.getString(columnIndex));
  }

  private static String nullToEmpty(final String value) {
    return value == null ? "" : value;
  }
}
