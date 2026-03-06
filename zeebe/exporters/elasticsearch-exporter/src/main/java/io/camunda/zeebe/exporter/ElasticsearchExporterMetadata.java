/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.camunda.zeebe.protocol.record.ValueType;
import java.util.EnumMap;
import java.util.Map;

public final class ElasticsearchExporterMetadata {

  /**
   * Single global record counter, incremented for every exported record regardless of value type.
   * This supersedes the legacy {@link #recordCountersByValueType} field.
   */
  private long recordCounter;

  /**
   * Legacy field kept solely for backward-compatible deserialization of metadata written by older
   * versions of the exporter. Excluded from serialization on write; only used during migration in
   * {@link ElasticsearchExporter#open}.
   */
  @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
  private Map<ValueType, Long> recordCountersByValueType = new EnumMap<>(ValueType.class);

  public long getRecordCounter() {
    return recordCounter;
  }

  public void setRecordCounter(final long recordCounter) {
    this.recordCounter = recordCounter;
  }

  public Map<ValueType, Long> getRecordCountersByValueType() {
    return recordCountersByValueType;
  }

  public void setRecordCountersByValueType(final Map<ValueType, Long> recordCountersByValueType) {
    this.recordCountersByValueType = recordCountersByValueType;
  }
}
