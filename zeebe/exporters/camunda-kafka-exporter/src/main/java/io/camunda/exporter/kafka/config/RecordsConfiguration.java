/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.kafka.config;

import io.camunda.zeebe.protocol.record.ValueType;
import java.util.Map;

public record RecordsConfiguration(
    RecordConfiguration defaults, Map<ValueType, RecordConfiguration> byType) {

  public RecordConfiguration forType(final ValueType valueType) {
    final RecordConfiguration config = byType.get(valueType);
    return config != null ? config : defaults;
  }
}
