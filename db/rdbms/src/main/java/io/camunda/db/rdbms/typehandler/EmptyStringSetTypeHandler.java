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
import java.util.Collections;
import java.util.Set;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

/**
 * Maps a textual column to a Set<String> while defaulting to an empty set when the column is null
 * or blank. Writing will serialize the set as a comma-separated String.
 */
public final class EmptyStringSetTypeHandler extends BaseTypeHandler<Set<String>> {

  @Override
  public void setNonNullParameter(
      final PreparedStatement ps, final int i, final Set<String> parameter, final JdbcType jdbcType)
      throws SQLException {
    ps.setString(i, String.join(",", parameter));
  }

  @Override
  public Set<String> getNullableResult(final ResultSet rs, final String columnName)
      throws SQLException {
    return parse(rs.getString(columnName));
  }

  @Override
  public Set<String> getNullableResult(final ResultSet rs, final int columnIndex)
      throws SQLException {
    return parse(rs.getString(columnIndex));
  }

  @Override
  public Set<String> getNullableResult(final CallableStatement cs, final int columnIndex)
      throws SQLException {
    return parse(cs.getString(columnIndex));
  }

  private Set<String> parse(final String value) {
    if (value == null || value.isBlank()) {
      return Collections.emptySet();
    }
    final String[] parts = value.split("\\s*,\\s*");
    return Set.of(parts);
  }
}
