/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.filter.config;

import io.camunda.zeebe.exporter.filter.DefaultRecordFilter;
import io.camunda.zeebe.exporter.filter.FilterConfiguration;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import java.util.EnumMap;
import java.util.Map;

/**
 * Minimal test configuration implementing {@link FilterConfiguration} so tests can exercise {@link
 * DefaultRecordFilter} in isolation.
 *
 * <p>By default, nothing is indexed. Tests must explicitly enable record/value types via the fluent
 * helpers.
 */
public final class TestConfiguration implements FilterConfiguration {

  private final Map<ValueType, Boolean> normalFlags = new EnumMap<>(ValueType.class);
  private final Map<ValueType, Boolean> requiredFlags = new EnumMap<>(ValueType.class);
  private final Map<RecordType, Boolean> recordTypeFlags = new EnumMap<>(RecordType.class);

  private final TestIndexConfig indexConfig = new TestIndexConfig();

  /** Mark a {@link ValueType} as normally indexed. */
  public TestConfiguration withIndexedValueType(final ValueType valueType) {
    normalFlags.put(valueType, true);
    return this;
  }

  /** Mark a {@link ValueType} as required-indexed. */
  public TestConfiguration withRequiredValueType(final ValueType valueType) {
    requiredFlags.put(valueType, true);
    return this;
  }

  /** Mark a {@link RecordType} as indexed. */
  public TestConfiguration withIndexedRecordType(final RecordType recordType) {
    recordTypeFlags.put(recordType, true);
    return this;
  }

  @Override
  public boolean shouldIndexValueType(final ValueType valueType) {
    return normalFlags.getOrDefault(valueType, false);
  }

  @Override
  public boolean shouldIndexRequiredValueType(final ValueType valueType) {
    return requiredFlags.getOrDefault(valueType, false);
  }

  @Override
  public boolean shouldIndexRecordType(final RecordType recordType) {
    return recordTypeFlags.getOrDefault(recordType, false);
  }

  @Override
  public TestIndexConfig filterIndexConfig() {
    return indexConfig;
  }
}
