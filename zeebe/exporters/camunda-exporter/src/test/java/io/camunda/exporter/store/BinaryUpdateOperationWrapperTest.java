/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.store;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.json.stream.JsonGenerator;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch._types.Script;
import org.opensearch.client.opensearch.core.bulk.UpdateOperation;

class BinaryUpdateOperationWrapperTest {

  private JacksonJsonpMapper jsonpMapper;

  @BeforeEach
  void setUp() {
    jsonpMapper = new JacksonJsonpMapper();
  }

  @Test
  void shouldSerializeWrappedDocumentAsRawJsonInBulkPayload() {
    // given
    final Map<String, Object> doc = Map.of("name", "alice", "age", 42);
    final BinaryUpdateOperationWrapper wrapper =
        new BinaryUpdateOperationWrapper(jsonpMapper).document(doc);
    final UpdateOperation<Object> op = wrapper.build("idx", "id-1", null, 3);

    // when
    final String serialized = serialize(op);

    // then
    assertThat(serialized)
        .contains("\"_index\":\"idx\"")
        .contains("\"_id\":\"id-1\"")
        .contains("\"retry_on_conflict\":3")
        .contains("\"doc\":{")
        .contains("\"name\":\"alice\"")
        .contains("\"age\":42")
        .doesNotContain("\"doc\":\"");
  }

  @Test
  void shouldSerializeUpsertAndDocumentTogether() {
    // given
    final Map<String, Object> doc = Map.of("a", 1);
    final Map<String, Object> upsert = Map.of("b", 2);
    final BinaryUpdateOperationWrapper wrapper =
        new BinaryUpdateOperationWrapper(jsonpMapper).document(doc).upsert(upsert);
    final UpdateOperation<Object> op = wrapper.build("idx", "id-1", "route-1", 3);

    // when
    final String serialized = serialize(op);

    // then
    assertThat(serialized)
        .contains("\"routing\":\"route-1\"")
        .contains("\"doc\":{\"a\":1}")
        .contains("\"upsert\":{\"b\":2}");
  }

  @Test
  void shouldKeepScriptUnwrapped() {
    // given
    final Script script = Script.of(s -> s.inline(i -> i.source("ctx._source.x = 1")));
    final BinaryUpdateOperationWrapper wrapper =
        new BinaryUpdateOperationWrapper(jsonpMapper).script(script);
    final UpdateOperation<Object> op = wrapper.build("idx", "id-1", null, 3);

    // when
    final String serialized = serialize(op);

    // then
    assertThat(serialized).contains("\"script\":").contains("ctx._source.x = 1");
  }

  @Test
  void shouldReportPayloadBytesAsSumOfBinarySizes() {
    // given
    final Map<String, Object> doc = Map.of("a", 1);
    final Map<String, Object> upsert = Map.of("b", 2);
    final long expectedDocBytes = serializedBytes(doc);
    final long expectedUpsertBytes = serializedBytes(upsert);

    // when
    final BinaryUpdateOperationWrapper wrapper =
        new BinaryUpdateOperationWrapper(jsonpMapper).document(doc).upsert(upsert);

    // then
    assertThat(wrapper.payloadBytes()).isEqualTo(expectedDocBytes + expectedUpsertBytes);
  }

  @Test
  void shouldReportZeroPayloadBytesForScriptOnlyWrapper() {
    // given
    final Script script = Script.of(s -> s.inline(i -> i.source("ctx._source.x = 1")));

    // when
    final BinaryUpdateOperationWrapper wrapper =
        new BinaryUpdateOperationWrapper(jsonpMapper).script(script);

    // then
    assertThat(wrapper.payloadBytes()).isZero();
  }

  private String serialize(final UpdateOperation<Object> op) {
    final ByteArrayOutputStream baos = new ByteArrayOutputStream();
    final var serializables = op._serializables();
    while (serializables.hasNext()) {
      final Object item = serializables.next();
      try (JsonGenerator generator = jsonpMapper.jsonProvider().createGenerator(baos)) {
        jsonpMapper.serialize(item, generator);
      }
      baos.write('\n');
    }
    return baos.toString(StandardCharsets.UTF_8);
  }

  private long serializedBytes(final Object value) {
    final ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (JsonGenerator generator = jsonpMapper.jsonProvider().createGenerator(baos)) {
      jsonpMapper.serialize(value, generator);
    }
    return baos.size();
  }
}
