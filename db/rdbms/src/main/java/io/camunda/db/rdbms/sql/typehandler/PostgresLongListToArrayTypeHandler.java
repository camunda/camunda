/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql.typehandler;

import java.sql.Array;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

@MappedTypes(List.class)
@MappedJdbcTypes(JdbcType.ARRAY)
public class PostgresLongListToArrayTypeHandler extends BaseTypeHandler<List<Long>> {

  @Override
  public void setNonNullParameter(
      final PreparedStatement ps, final int i, final List<Long> parameter, final JdbcType jdbcType)
      throws SQLException {
    final Array array = ps.getConnection().createArrayOf("bigint", parameter.toArray());
    ps.setArray(i, array);
  }

  @Override
  public List<Long> getNullableResult(final ResultSet rs, final String columnName)
      throws SQLException {
    return toList(rs.getArray(columnName));
  }

  @Override
  public List<Long> getNullableResult(final ResultSet rs, final int columnIndex)
      throws SQLException {
    return toList(rs.getArray(columnIndex));
  }

  @Override
  public List<Long> getNullableResult(final CallableStatement cs, final int columnIndex)
      throws SQLException {
    return toList(cs.getArray(columnIndex));
  }

  private List<Long> toList(final Array array) throws SQLException {
    if (array == null) {
      return null;
    }
    return List.of((Long[]) array.getArray());
  }
}
