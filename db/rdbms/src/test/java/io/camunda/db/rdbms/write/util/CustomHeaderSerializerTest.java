/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

public class CustomHeaderSerializerTest {

  @Test
  public void testSerializeAndDeserialize() {
    // given
    final Map<String, String> headers = Map.of("key1", "value1", "key2", "value2");

    // when
    final String serialized = CustomHeaderSerializer.serialize(headers);
    final Map<String, String> deserializedHeaders = CustomHeaderSerializer.deserialize(serialized);

    // then
    assertThat(headers).containsAllEntriesOf(deserializedHeaders);
  }

  @Test
  public void testSerializeEmptyMap() {
    // given
    final Map<String, String> headers = Map.of();
    final String expectedJson = "{}";

    // when
    final String serialized = CustomHeaderSerializer.serialize(headers);

    // then
    assertThat(serialized).isEqualTo(expectedJson);
  }

  @Test
  public void testSerializeNull() {
    // given
    final Map<String, String> headers = null;

    // when
    final String serialized = CustomHeaderSerializer.serialize(headers);

    // then
    assertThat(serialized).isNull();
  }

  @Test
  public void testDeserializeEmptyJson() {
    // given
    final String json = "{}";
    final Map<String, String> expectedHeaders = Map.of();

    // when
    final Map<String, String> deserialized = CustomHeaderSerializer.deserialize(json);

    // then
    assertThat(deserialized).isEqualTo(expectedHeaders);
  }

  @Test
  public void testDeserializeNull() {
    // given
    final String json = null;

    // when
    final Map<String, String> deserialized = CustomHeaderSerializer.deserialize(json);

    // then
    assertThat(deserialized).isNull();
  }
}
