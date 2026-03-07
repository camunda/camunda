/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.persist.rdbms;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

/**
 * MyBatis TypeHandler for Map&lt;String, byte[]&gt; columns. Serializes the map to JSON where each
 * byte[] value is Base64-encoded.
 */
public class MapByteArrayJsonTypeHandler extends BaseTypeHandler<Map<String, byte[]>> {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final TypeReference<Map<String, byte[]>> TYPE_REF = new TypeReference<>() {};

  @Override
  public void setNonNullParameter(
      final PreparedStatement ps,
      final int i,
      final Map<String, byte[]> parameter,
      final JdbcType jdbcType)
      throws SQLException {
    try {
      ps.setString(i, MAPPER.writeValueAsString(parameter));
    } catch (final Exception e) {
      throw new SQLException("Failed to serialize Map<String, byte[]> to JSON", e);
    }
  }

  @Override
  public Map<String, byte[]> getNullableResult(final ResultSet rs, final String columnName)
      throws SQLException {
    return deserialize(rs.getString(columnName));
  }

  @Override
  public Map<String, byte[]> getNullableResult(final ResultSet rs, final int columnIndex)
      throws SQLException {
    return deserialize(rs.getString(columnIndex));
  }

  @Override
  public Map<String, byte[]> getNullableResult(final CallableStatement cs, final int columnIndex)
      throws SQLException {
    return deserialize(cs.getString(columnIndex));
  }

  private Map<String, byte[]> deserialize(final String json) throws SQLException {
    if (json == null || json.isEmpty()) {
      return new HashMap<>();
    }
    try {
      return MAPPER.readValue(json, TYPE_REF);
    } catch (final Exception e) {
      throw new SQLException("Failed to deserialize JSON to Map<String, byte[]>", e);
    }
  }
}
