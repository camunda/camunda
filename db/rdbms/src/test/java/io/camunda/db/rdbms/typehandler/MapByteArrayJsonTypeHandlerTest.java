/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.typehandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MapByteArrayJsonTypeHandlerTest {

  private MapByteArrayJsonTypeHandler typeHandler;

  @BeforeEach
  void setUp() {
    typeHandler = new MapByteArrayJsonTypeHandler();
  }

  @Test
  void shouldSerializeAndDeserializeValidMap() throws Exception {
    // given
    final Map<String, byte[]> originalMap = new HashMap<>();
    originalMap.put("key1", "value1".getBytes());
    originalMap.put("key2", "value2".getBytes());

    // when - serialize
    final PreparedStatement ps = mock(PreparedStatement.class);
    typeHandler.setNonNullParameter(ps, 1, originalMap, null);

    // Capture the serialized bytes
    final var serializedBytes = new byte[1][];
    verify(ps)
        .setBytes(
            org.mockito.ArgumentMatchers.eq(1),
            org.mockito.ArgumentMatchers.argThat(
                bytes -> {
                  serializedBytes[0] = bytes;
                  return true;
                }));

    // when - deserialize
    final ResultSet rs = mock(ResultSet.class);
    when(rs.getBytes("attributes")).thenReturn(serializedBytes[0]);
    final Map<String, byte[]> deserializedMap = typeHandler.getNullableResult(rs, "attributes");

    // then
    assertThat(deserializedMap).isNotNull();
    assertThat(deserializedMap).hasSize(2);
    assertThat(deserializedMap.get("key1")).isEqualTo("value1".getBytes());
    assertThat(deserializedMap.get("key2")).isEqualTo("value2".getBytes());
  }

  @Test
  void shouldHandleEmptyMap() throws Exception {
    // given
    final Map<String, byte[]> emptyMap = new HashMap<>();

    // when - serialize
    final PreparedStatement ps = mock(PreparedStatement.class);
    typeHandler.setNonNullParameter(ps, 1, emptyMap, null);

    // Capture the serialized bytes
    final var serializedBytes = new byte[1][];
    verify(ps)
        .setBytes(
            org.mockito.ArgumentMatchers.eq(1),
            org.mockito.ArgumentMatchers.argThat(
                bytes -> {
                  serializedBytes[0] = bytes;
                  return true;
                }));

    // when - deserialize
    final ResultSet rs = mock(ResultSet.class);
    when(rs.getBytes("attributes")).thenReturn(serializedBytes[0]);
    final Map<String, byte[]> deserializedMap = typeHandler.getNullableResult(rs, "attributes");

    // then
    assertThat(deserializedMap).isNotNull();
    assertThat(deserializedMap).isEmpty();
  }

  @Test
  void shouldReturnEmptyMapForNullBytes() throws Exception {
    // given
    final ResultSet rs = mock(ResultSet.class);
    when(rs.getBytes("attributes")).thenReturn(null);

    // when
    final Map<String, byte[]> result = typeHandler.getNullableResult(rs, "attributes");

    // then
    assertThat(result).isNotNull();
    assertThat(result).isEmpty();
  }

  @Test
  void shouldReturnEmptyMapForEmptyByteArray() throws Exception {
    // given
    final ResultSet rs = mock(ResultSet.class);
    when(rs.getBytes("attributes")).thenReturn(new byte[0]);

    // when
    final Map<String, byte[]> result = typeHandler.getNullableResult(rs, "attributes");

    // then
    assertThat(result).isNotNull();
    assertThat(result).isEmpty();
  }

  @Test
  void shouldHandleBinaryData() throws Exception {
    // given - map with binary byte arrays
    final Map<String, byte[]> binaryMap = new HashMap<>();
    binaryMap.put("binaryKey1", new byte[] {0, 1, 2, 3, 127, (byte) 128, (byte) 255});
    binaryMap.put("binaryKey2", new byte[] {(byte) 0xFF, (byte) 0xFE, (byte) 0xFD});

    // when - serialize and deserialize
    final PreparedStatement ps = mock(PreparedStatement.class);
    typeHandler.setNonNullParameter(ps, 1, binaryMap, null);

    final var serializedBytes = new byte[1][];
    verify(ps)
        .setBytes(
            org.mockito.ArgumentMatchers.eq(1),
            org.mockito.ArgumentMatchers.argThat(
                bytes -> {
                  serializedBytes[0] = bytes;
                  return true;
                }));

    final ResultSet rs = mock(ResultSet.class);
    when(rs.getBytes("attributes")).thenReturn(serializedBytes[0]);
    final Map<String, byte[]> result = typeHandler.getNullableResult(rs, "attributes");

    // then
    assertThat(result).isNotNull();
    assertThat(result).hasSize(2);
    assertThat(result.get("binaryKey1"))
        .isEqualTo(new byte[] {0, 1, 2, 3, 127, (byte) 128, (byte) 255});
    assertThat(result.get("binaryKey2"))
        .isEqualTo(new byte[] {(byte) 0xFF, (byte) 0xFE, (byte) 0xFD});
  }

  @Test
  void shouldHandleLargeByteArrays() throws Exception {
    // given - map with large byte arrays
    final Map<String, byte[]> largeMap = new HashMap<>();
    final byte[] largeArray = new byte[10000];
    for (int i = 0; i < largeArray.length; i++) {
      largeArray[i] = (byte) (i % 256);
    }
    largeMap.put("largeKey", largeArray);

    // when - serialize and deserialize
    final PreparedStatement ps = mock(PreparedStatement.class);
    typeHandler.setNonNullParameter(ps, 1, largeMap, null);

    final var serializedBytes = new byte[1][];
    verify(ps)
        .setBytes(
            org.mockito.ArgumentMatchers.eq(1),
            org.mockito.ArgumentMatchers.argThat(
                bytes -> {
                  serializedBytes[0] = bytes;
                  return true;
                }));

    final ResultSet rs = mock(ResultSet.class);
    when(rs.getBytes("attributes")).thenReturn(serializedBytes[0]);
    final Map<String, byte[]> result = typeHandler.getNullableResult(rs, "attributes");

    // then
    assertThat(result).isNotNull();
    assertThat(result).hasSize(1);
    assertThat(result.get("largeKey")).isEqualTo(largeArray);
  }

  @Test
  void shouldRejectInvalidJsonData() throws Exception {
    // given - invalid JSON data
    final byte[] invalidJson = "not valid json".getBytes();

    // when/then
    final ResultSet rs = mock(ResultSet.class);
    when(rs.getBytes("attributes")).thenReturn(invalidJson);

    assertThatThrownBy(() -> typeHandler.getNullableResult(rs, "attributes"))
        .isInstanceOf(SQLException.class)
        .hasMessageContaining("Error deserializing JSON BLOB to map");
  }

  @Test
  void shouldRejectInvalidBase64Data() throws Exception {
    // given - JSON with invalid Base64 string
    final String invalidBase64Json = "{\"key\":\"not-valid-base64!!!\"}";
    final byte[] invalidData = invalidBase64Json.getBytes(java.nio.charset.StandardCharsets.UTF_8);

    // when/then
    final ResultSet rs = mock(ResultSet.class);
    when(rs.getBytes("attributes")).thenReturn(invalidData);

    assertThatThrownBy(() -> typeHandler.getNullableResult(rs, "attributes"))
        .isInstanceOf(SQLException.class)
        .hasMessageContaining("Error decoding Base64 data");
  }

  @Test
  void shouldHandleSpecialCharactersInKeys() throws Exception {
    // given - map with special characters in keys
    final Map<String, byte[]> specialMap = new HashMap<>();
    specialMap.put("key with spaces", "value1".getBytes());
    specialMap.put("key-with-dashes", "value2".getBytes());
    specialMap.put("key_with_underscores", "value3".getBytes());
    specialMap.put("keyWith中文", "value4".getBytes());

    // when - serialize and deserialize
    final PreparedStatement ps = mock(PreparedStatement.class);
    typeHandler.setNonNullParameter(ps, 1, specialMap, null);

    final var serializedBytes = new byte[1][];
    verify(ps)
        .setBytes(
            org.mockito.ArgumentMatchers.eq(1),
            org.mockito.ArgumentMatchers.argThat(
                bytes -> {
                  serializedBytes[0] = bytes;
                  return true;
                }));

    final ResultSet rs = mock(ResultSet.class);
    when(rs.getBytes("attributes")).thenReturn(serializedBytes[0]);
    final Map<String, byte[]> result = typeHandler.getNullableResult(rs, "attributes");

    // then
    assertThat(result).isNotNull();
    assertThat(result).hasSize(4);
    assertThat(result.get("key with spaces")).isEqualTo("value1".getBytes());
    assertThat(result.get("key-with-dashes")).isEqualTo("value2".getBytes());
    assertThat(result.get("key_with_underscores")).isEqualTo("value3".getBytes());
    assertThat(result.get("keyWith中文")).isEqualTo("value4".getBytes());
  }
}
