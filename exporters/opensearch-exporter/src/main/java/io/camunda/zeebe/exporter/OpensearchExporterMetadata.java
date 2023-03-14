/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.exporter;

import io.camunda.zeebe.protocol.record.ValueType;
import java.util.EnumMap;
import java.util.Map;

public final class OpensearchExporterMetadata {

  private Map<ValueType, Long> recordCountersByValueType = new EnumMap<>(ValueType.class);

  public Map<ValueType, Long> getRecordCountersByValueType() {
    return recordCountersByValueType;
  }

  public void setRecordCountersByValueType(final Map<ValueType, Long> recordCountersByValueType) {
    this.recordCountersByValueType = recordCountersByValueType;
  }
}
