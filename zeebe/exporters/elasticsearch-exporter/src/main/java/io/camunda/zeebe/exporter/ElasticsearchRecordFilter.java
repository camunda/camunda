/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter;

import io.camunda.zeebe.exporter.api.context.Context;
import io.camunda.zeebe.exporter.filter.ExportRecordFilterChain;
import io.camunda.zeebe.exporter.filter.RecordTypeFilter;
import io.camunda.zeebe.exporter.filter.RequiredValueTypeFilter;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import java.util.List;

public class ElasticsearchRecordFilter implements Context.RecordFilter {

  private final ExportRecordFilterChain delegate;
  private final ElasticsearchExporterConfiguration configuration;

  ElasticsearchRecordFilter(final ElasticsearchExporterConfiguration configuration) {
    this.configuration = configuration;

    final var recordFilters =
        List.of(
            new RecordTypeFilter(configuration::shouldIndexRecordType),
            new RequiredValueTypeFilter(
                configuration::getIsIncludeEnabledRecords,
                configuration::shouldIndexValueType,
                configuration::shouldIndexRequiredValueType));

    delegate = new ExportRecordFilterChain(recordFilters);
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
  public boolean acceptRecord(final Record<?> value) {
    return delegate.acceptRecord(value);
  }
}
