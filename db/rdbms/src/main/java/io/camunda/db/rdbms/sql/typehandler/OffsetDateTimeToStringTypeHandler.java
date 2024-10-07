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

/**
 * @since 3.4.5
 *
 * @author Tomas Rohovsky
 */
public class OffsetDateTimeToStringTypeHandler extends BaseTypeHandler<String> {

  @Override
  public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType)
      throws SQLException {
    ps.setObject(i, parameter);
  }

  @Override
  public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
    return mapToISOString(rs.getObject(columnName, OffsetDateTime.class));
  }

  @Override
  public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
    return mapToISOString(rs.getObject(columnIndex, OffsetDateTime.class));
  }

  @Override
  public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
    return mapToISOString(cs.getObject(columnIndex, OffsetDateTime.class));
  }

  private String mapToISOString(OffsetDateTime offsetDateTime) {
    if (offsetDateTime == null) {
      return null;
    }

    return offsetDateTime.toString();
  }

}
