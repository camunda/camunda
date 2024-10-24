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
import java.time.OffsetDateTime;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

public class OffsetDateTimeToStringTypeHandler extends BaseTypeHandler<String> {

  @Override
  public void setNonNullParameter(
      final PreparedStatement ps, final int i, final String parameter, final JdbcType jdbcType)
      throws SQLException {
    ps.setObject(i, parameter);
  }

  @Override
  public String getNullableResult(final ResultSet rs, final String columnName) throws SQLException {
    return mapToISOString(rs.getObject(columnName, OffsetDateTime.class));
  }

  @Override
  public String getNullableResult(final ResultSet rs, final int columnIndex) throws SQLException {
    return mapToISOString(rs.getObject(columnIndex, OffsetDateTime.class));
  }

  @Override
  public String getNullableResult(final CallableStatement cs, final int columnIndex)
      throws SQLException {
    return mapToISOString(cs.getObject(columnIndex, OffsetDateTime.class));
  }

  private String mapToISOString(final OffsetDateTime offsetDateTime) {
    if (offsetDateTime == null) {
      return null;
    }

    return offsetDateTime.toString();
  }
}
