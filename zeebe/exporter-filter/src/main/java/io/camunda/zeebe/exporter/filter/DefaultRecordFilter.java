/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.filter;

import static io.camunda.zeebe.exporter.filter.NameRule.Type.ENDS_WITH;
import static io.camunda.zeebe.exporter.filter.NameRule.Type.EXACT;
import static io.camunda.zeebe.exporter.filter.NameRule.Type.STARTS_WITH;
import static io.camunda.zeebe.exporter.filter.VariableNameFilterRecord.parseRules;

import io.camunda.zeebe.exporter.api.context.Context;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import java.util.ArrayList;
import java.util.List;

public final class DefaultRecordFilter implements Context.RecordFilter {

  private final ExportRecordFilterChain exportRecordFilterChain;
  private final FilterConfiguration configuration;

  public DefaultRecordFilter(final FilterConfiguration configuration) {
    this.configuration = configuration;

    exportRecordFilterChain = new ExportRecordFilterChain(createRecordFilters(configuration));
  }

  private static List<ExporterRecordFilter> createRecordFilters(
      final FilterConfiguration configuration) {

    final var index = configuration.filterIndexConfig();

    final var nameInclusionRules = getNameInclusionRules(index);
    final var nameExclusionRules = getNameExclusionRules(index);

    final List<ExporterRecordFilter> filters =
        new ArrayList<>(
            List.of(new VariableNameFilterRecord(nameInclusionRules, nameExclusionRules)));

    return List.copyOf(filters);
  }

  private static List<NameRule> getNameInclusionRules(final FilterConfiguration.IndexConfig index) {
    final List<NameRule> rules = new ArrayList<>();
    rules.addAll(parseRules(index.getVariableNameInclusionExact(), EXACT));
    rules.addAll(parseRules(index.getVariableNameInclusionStartWith(), STARTS_WITH));
    rules.addAll(parseRules(index.getVariableNameInclusionEndWith(), ENDS_WITH));
    return List.copyOf(rules);
  }

  private static List<NameRule> getNameExclusionRules(final FilterConfiguration.IndexConfig index) {
    final List<NameRule> rules = new ArrayList<>();
    rules.addAll(parseRules(index.getVariableNameExclusionExact(), EXACT));
    rules.addAll(parseRules(index.getVariableNameExclusionStartWith(), STARTS_WITH));
    rules.addAll(parseRules(index.getVariableNameExclusionEndWith(), ENDS_WITH));
    return List.copyOf(rules);
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
