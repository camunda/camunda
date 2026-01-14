/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.typehandler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

/**
 * TypeHandler for serializing and deserializing Map<String, byte[]> to/from BLOB columns using
 * JSON. This implementation uses Jackson for JSON serialization with Base64 encoding for byte
 * arrays, providing better robustness and portability compared to Java serialization.
 *
 * <p>Used for storing web session attributes.
 */
public final class MapByteArrayJsonTypeHandler extends BaseTypeHandler<Map<String, byte[]>> {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final TypeReference<Map<String, String>> MAP_TYPE_REF =
      new TypeReference<Map<String, String>>() {};

  @Override
  public void setNonNullParameter(
      final PreparedStatement ps,
      final int i,
      final Map<String, byte[]> parameter,
      final JdbcType jdbcType)
      throws SQLException {
    try {
      // Convert byte arrays to Base64 strings for JSON serialization
      final Map<String, String> base64Map = new HashMap<>();
      for (final Map.Entry<String, byte[]> entry : parameter.entrySet()) {
        base64Map.put(entry.getKey(), Base64.getEncoder().encodeToString(entry.getValue()));
      }

      // Serialize to JSON
      final String json = OBJECT_MAPPER.writeValueAsString(base64Map);
      ps.setBytes(i, json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    } catch (final JsonProcessingException e) {
      throw new SQLException("Error serializing map to JSON BLOB", e);
    }
  }

  @Override
  public Map<String, byte[]> getNullableResult(final ResultSet rs, final String columnName)
      throws SQLException {
    return deserialize(rs.getBytes(columnName));
  }

  @Override
  public Map<String, byte[]> getNullableResult(final ResultSet rs, final int columnIndex)
      throws SQLException {
    return deserialize(rs.getBytes(columnIndex));
  }

  @Override
  public Map<String, byte[]> getNullableResult(final CallableStatement cs, final int columnIndex)
      throws SQLException {
    return deserialize(cs.getBytes(columnIndex));
  }

  private Map<String, byte[]> deserialize(final byte[] bytes) throws SQLException {
    if (bytes == null || bytes.length == 0) {
      return new HashMap<>();
    }

    try {
      // Deserialize from JSON
      final String json = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
      final Map<String, String> base64Map = OBJECT_MAPPER.readValue(json, MAP_TYPE_REF);

      // Convert Base64 strings back to byte arrays
      final Map<String, byte[]> result = new HashMap<>();
      for (final Map.Entry<String, String> entry : base64Map.entrySet()) {
        result.put(entry.getKey(), Base64.getDecoder().decode(entry.getValue()));
      }

      return result;
    } catch (final JsonProcessingException e) {
      throw new SQLException("Error deserializing JSON BLOB to map", e);
    } catch (final IllegalArgumentException e) {
      throw new SQLException("Error decoding Base64 data", e);
    }
  }
}
