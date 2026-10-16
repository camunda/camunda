/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql.typehandler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.jspecify.annotations.Nullable;

/**
 * Reads a JSON object stored as text (CLOB) into a {@code Map<String, Object>}. A NULL or blank
 * column yields {@code null}.
 */
public final class JsonObjectMapTypeHandler extends BaseTypeHandler<Map<String, Object>> {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

  /** Parses a JSON object string; returns {@code null} for null or blank input. */
  public static @Nullable Map<String, Object> parse(final @Nullable String json) {
    if (json == null || json.isBlank()) {
      return null;
    }
    try {
      return OBJECT_MAPPER.readValue(json, MAP_TYPE);
    } catch (final JsonProcessingException e) {
      throw new IllegalStateException("Failed to deserialize JSON object from database", e);
    }
  }

  @Override
  public void setNonNullParameter(
      final PreparedStatement ps,
      final int i,
      final Map<String, Object> parameter,
      final JdbcType jdbcType)
      throws SQLException {
    try {
      ps.setString(i, OBJECT_MAPPER.writeValueAsString(parameter));
    } catch (final JsonProcessingException e) {
      throw new SQLException("Failed to serialize map to JSON", e);
    }
  }

  @Override
  public @Nullable Map<String, Object> getNullableResult(
      final ResultSet rs, final String columnName) throws SQLException {
    return parse(rs.getString(columnName));
  }

  @Override
  public @Nullable Map<String, Object> getNullableResult(final ResultSet rs, final int columnIndex)
      throws SQLException {
    return parse(rs.getString(columnIndex));
  }

  @Override
  public @Nullable Map<String, Object> getNullableResult(
      final CallableStatement cs, final int columnIndex) throws SQLException {
    return parse(cs.getString(columnIndex));
  }
}
