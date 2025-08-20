/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.http.transport;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.util.RawValue;
import io.camunda.exporter.http.subscription.BatchEntry;
import io.camunda.exporter.http.subscription.BatchMapper;
import io.camunda.zeebe.protocol.record.Record;
import java.util.List;

public class JsonBatchMapper implements BatchMapper<String, String> {
  private final ObjectMapper objectMapper;

  public JsonBatchMapper(final ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public BatchEntry<String> map(final Record<?> record) {
    return new BatchEntry<>(toJson(record), record.getPosition());
  }

  @Override
  public String map(final List<BatchEntry<String>> batchEntries) {
    return toJson(toRawValues(batchEntries));
  }

  private List<RawValue> toRawValues(final List<BatchEntry<String>> batchEntries) {
    return batchEntries.stream().map(batchEntry -> new RawValue(batchEntry.record())).toList();
  }

  private String toJson(final Object object) {
    try {
      return objectMapper.writeValueAsString(object);
    } catch (final JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }
}
