/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.filter;

import io.camunda.zeebe.exporter.api.context.Context;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import java.util.Collections;

public final class DefaultRecordFilter implements Context.RecordFilter {

  private final ExportRecordFilterChain exportRecordFilterChain;
  private final FilterConfiguration configuration;

  public DefaultRecordFilter(final FilterConfiguration configuration) {
    this.configuration = configuration;

    // The next PR will implement the filters to be added in the chain
    exportRecordFilterChain = new ExportRecordFilterChain(Collections.emptyList());
  }

  @Override
  public boolean acceptType(final RecordType recordType) {
    return configuration.shouldIndexRecordType(recordType);
  }

  @Override
  public boolean acceptValue(final ValueType valueType) {
    return configuration.shouldIndexValueType(valueType);
  }

  @Override
  public boolean acceptRecord(final Record<?> record) {
    return exportRecordFilterChain.acceptRecord(record);
  }
}
