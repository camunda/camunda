/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.example.kafkastreams;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serializer;

/**
 * A tiny Jackson-backed {@link Serde} used for the domain types.
 *
 * <p>Kafka Streams requires an <b>explicit</b> serde for every key and value type that crosses a
 * store or a repartition topic — there is no reflective magic. This class exists purely to make that
 * requirement concrete and readable; a production topology would likely use a schema-aware serde
 * (Avro/Protobuf + registry) instead of JSON.
 *
 * <p>Note: the framework decides <i>when</i> to serialize (writing to the changelog, shuffling across
 * the repartition topic, spilling RocksDB); we only supply the <i>how</i>.
 */
public final class JsonSerde<T> implements Serde<T> {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final Class<T> type;

  public JsonSerde(final Class<T> type) {
    this.type = type;
  }

  public static <T> Serde<T> of(final Class<T> type) {
    return new JsonSerde<>(type);
  }

  @Override
  public Serializer<T> serializer() {
    return (topic, data) -> {
      if (data == null) {
        return null;
      }
      try {
        return MAPPER.writeValueAsBytes(data);
      } catch (final Exception e) {
        throw new SerializationException("Failed to serialize " + type.getSimpleName(), e);
      }
    };
  }

  @Override
  public Deserializer<T> deserializer() {
    return (topic, bytes) -> {
      if (bytes == null) {
        return null;
      }
      try {
        return MAPPER.readValue(bytes, type);
      } catch (final Exception e) {
        throw new SerializationException("Failed to deserialize " + type.getSimpleName(), e);
      }
    };
  }

  @Override
  public void configure(final Map<String, ?> configs, final boolean isKey) {
    // no-op
  }

  @Override
  public void close() {
    // no-op
  }
}
