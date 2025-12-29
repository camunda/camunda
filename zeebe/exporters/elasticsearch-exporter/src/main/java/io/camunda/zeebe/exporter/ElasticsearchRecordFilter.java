/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter;

import io.camunda.zeebe.exporter.api.context.Context;
import io.camunda.zeebe.exporter.common.filter.SearchRecordFilter;
import io.camunda.zeebe.exporter.common.filter.VariableNameRecordValueFilter;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import java.util.List;

public class ElasticsearchRecordFilter implements Context.RecordFilter {

  private final SearchRecordFilter delegate;

  ElasticsearchRecordFilter(final ElasticsearchExporterConfiguration configuration) {
    delegate =
        new SearchRecordFilter(
            configuration::shouldIndexRecordType,
            configuration::shouldIndexValueType,
            List.of(new VariableNameRecordValueFilter(configuration.index.variableNameInclusion)));
  }

  @Override
  public boolean acceptType(final RecordType recordType) {
    return delegate.acceptType(recordType);
  }

  @Override
  public boolean acceptValue(final ValueType valueType) {
    return delegate.acceptValue(valueType);
  }

  @Override
  public boolean acceptValue(final RecordValue value) {
    return delegate.acceptValue(value);
  }
}
