/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter;

import static io.camunda.zeebe.exporter.common.filter.NameRule.Type.*;
import static io.camunda.zeebe.exporter.common.filter.VariableNameFilterRecord.parseRules;

import io.camunda.zeebe.exporter.ElasticsearchExporterConfiguration.IndexConfiguration;
import io.camunda.zeebe.exporter.api.context.Context;
import io.camunda.zeebe.exporter.common.filter.ExportRecordFilter;
import io.camunda.zeebe.exporter.common.filter.NameRule;
import io.camunda.zeebe.exporter.common.filter.RecordTypeFilter;
import io.camunda.zeebe.exporter.common.filter.RequiredValueTypeFilter;
import io.camunda.zeebe.exporter.common.filter.VariableNameFilterRecord;
import io.camunda.zeebe.exporter.common.filter.VariableTypeFilter;
import io.camunda.zeebe.exporter.common.filter.VariableTypeFilter.VariableValueType;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ElasticsearchRecordFilter implements Context.RecordFilter {

  private final ExportRecordFilter delegate;
  private final ElasticsearchExporterConfiguration configuration;

  ElasticsearchRecordFilter(final ElasticsearchExporterConfiguration configuration) {
    this.configuration = configuration;
    final var index = configuration.index;

    final Set<VariableValueType> valueTypeInclusion =
        VariableTypeFilter.parseTypes(index.variableValueTypeInclusion);
    final Set<VariableValueType> valueTypeExclusion =
        VariableTypeFilter.parseTypes(index.variableValueTypeExclusion);

    final List<NameRule> nameInclusionRules = getNameInclusionRules(index);
    final List<NameRule> nameExclusionRules = getNameExclusionRules(index);
    delegate =
        new ExportRecordFilter(
            List.of(
                new RecordTypeFilter(configuration::shouldIndexRecordType),
                new RequiredValueTypeFilter(
                    configuration::getIsIncludeEnabledRecords,
                    configuration::shouldIndexValueType,
                    configuration::shouldIndexRequiredValueType),
                new VariableTypeFilter(valueTypeInclusion, valueTypeExclusion),
                new VariableNameFilterRecord(nameInclusionRules, nameExclusionRules)));
  }

  private static List<NameRule> getNameExclusionRules(final IndexConfiguration index) {
    final List<NameRule> nameExclusionRules = new ArrayList<>();
    nameExclusionRules.addAll(parseRules(index.variableNameExclusionExact, EXACT));
    nameExclusionRules.addAll(parseRules(index.variableNameExclusionStartWith, STARTS_WITH));
    nameExclusionRules.addAll(parseRules(index.variableNameExclusionEndWith, ENDS_WITH));
    return nameExclusionRules;
  }

  private static List<NameRule> getNameInclusionRules(final IndexConfiguration index) {
    final List<NameRule> nameInclusionRules = new ArrayList<>();
    nameInclusionRules.addAll(parseRules(index.variableNameInclusionExact, EXACT));
    nameInclusionRules.addAll(parseRules(index.variableNameInclusionStartWith, STARTS_WITH));
    nameInclusionRules.addAll(parseRules(index.variableNameInclusionEndWith, ENDS_WITH));
    return nameInclusionRules;
  }

  // Still used by ExporterDirector to build ExporterEventFilter
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
