/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class MapSerializerTest {

  @Test
  public void testSerializeAndDeserialize() {
    // given
    final Map<String, String> headers = Map.of("key1", "value1", "key2", "value2");

    // when
    final String serialized = MapSerializer.serialize(headers);
    final Map<String, String> deserializedHeaders = MapSerializer.deserialize(serialized);

    // then
    assertThat(headers).containsAllEntriesOf(deserializedHeaders);
  }

  @Test
  public void testSerializeEmptyMap() {
    // given
    final Map<String, String> headers = Map.of();
    final String expectedJson = "{}";

    // when
    final String serialized = MapSerializer.serialize(headers);

    // then
    assertThat(serialized).isEqualTo(expectedJson);
  }

  @Test
  public void testSerializeNull() {
    // given
    final Map<String, String> headers = null;

    // when
    final String serialized = MapSerializer.serialize(headers);

    // then
    assertThat(serialized).isNull();
  }

  @Test
  public void testDeserializeEmptyJson() {
    // given
    final String json = "{}";
    final Map<String, String> expectedHeaders = Map.of();

    // when
    final Map<String, String> deserialized = MapSerializer.deserialize(json);

    // then
    assertThat(deserialized).isEqualTo(expectedHeaders);
  }

  @Test
  public void testDeserializeNull() {
    // given
    final String json = null;

    // when
    final Map<String, String> deserialized = MapSerializer.deserialize(json);

    // then
    assertThat(deserialized).isNull();
  }

  @Test
  public void shouldSerializeNonEmptyMapToCompactJson() {
    // given
    final Map<String, String> headers = new LinkedHashMap<>();
    headers.put("b", "2");
    headers.put("a", "x \"q\" \u00e4");

    // when
    final String serialized = MapSerializer.serialize(headers);

    // then
    assertThat(serialized).isEqualTo("{\"b\":\"2\",\"a\":\"x \\\"q\\\" \u00e4\"}");
  }

  @Test
  public void shouldDeserializeNonStringValuesWithoutError() {
    // given
    final String json = "{\"n\":1,\"b\":true,\"s\":\"v\"}";

    // when
    final Map<String, ?> deserialized = MapSerializer.deserialize(json);

    // then
    assertThat(deserialized).containsOnlyKeys("n", "b", "s");
    assertThat(deserialized.get("s")).isEqualTo("v");
    assertThat(String.valueOf(deserialized.get("n"))).isEqualTo("1");
    assertThat(String.valueOf(deserialized.get("b"))).isEqualTo("true");
  }

  @Test
  public void shouldReturnNullForInvalidJson() {
    // given
    final String json = "{not json";

    // when
    final Map<String, String> deserialized = MapSerializer.deserialize(json);

    // then
    assertThat(deserialized).isNull();
  }

  @Test
  public void shouldReturnNullForEmptyString() {
    // when
    final Map<String, String> deserialized = MapSerializer.deserialize("");

    // then
    assertThat(deserialized).isNull();
  }
}
