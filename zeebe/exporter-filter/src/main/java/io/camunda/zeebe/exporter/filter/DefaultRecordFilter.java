/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.filter;

import static io.camunda.zeebe.exporter.filter.NameFilterRule.Type.ENDS_WITH;
import static io.camunda.zeebe.exporter.filter.NameFilterRule.Type.EXACT;
import static io.camunda.zeebe.exporter.filter.NameFilterRule.Type.STARTS_WITH;
import static io.camunda.zeebe.exporter.filter.VariableNameFilter.parseRules;

import io.camunda.zeebe.exporter.api.context.Context;
import io.camunda.zeebe.exporter.filter.VariableTypeFilter.VariableValueType;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DefaultRecordFilter implements Context.RecordFilter {

  private static final Logger LOG = LoggerFactory.getLogger(DefaultRecordFilter.class);

  private final ExportRecordFilterChain exportRecordFilterChain;
  private final FilterConfiguration configuration;

  public DefaultRecordFilter(final FilterConfiguration configuration) {
    this.configuration = configuration;

    exportRecordFilterChain = new ExportRecordFilterChain(createRecordFilters(configuration));
  }

  private static List<ExporterRecordFilter> createRecordFilters(
      final FilterConfiguration configuration) {

    final var index = configuration.filterIndexConfig();

    final var variableNameInclusionRules = getVariableNameInclusionRules(index);
    final var variableNameExclusionRules = getVariableNameExclusionRules(index);

    final Set<VariableValueType> valueTypeInclusion =
        VariableTypeFilter.parseTypes(index.getVariableValueTypeInclusion());
    final Set<VariableTypeFilter.VariableValueType> valueTypeExclusion =
        VariableTypeFilter.parseTypes(index.getVariableValueTypeExclusion());

    final List<ExporterRecordFilter> filters = new ArrayList<>();

    // Just add filters if configured

    if (index.isOptimizeModeEnabled()) {
      LOG.info(
          "Optimize mode enabled. It might filter more restrictively than the filters defined in "
              + "acceptType, acceptValue, and acceptIntent. If you want to customize the filtering, "
              + "please disable optimize mode and use the other filter configuration options.");
      filters.add(new OptimizeModeFilter());
    }

    if (!variableNameInclusionRules.isEmpty() || !variableNameExclusionRules.isEmpty()) {
      LOG.info(
          "Variable name filters configured. Inclusion rules: {}, Exclusion rules: {}.",
          variableNameInclusionRules,
          variableNameExclusionRules);
      filters.add(new VariableNameFilter(variableNameInclusionRules, variableNameExclusionRules));
    }

    if (!valueTypeInclusion.isEmpty() || !valueTypeExclusion.isEmpty()) {
      LOG.info(
          "Variable type filters configured. Inclusion types: {}, Exclusion types: {}.",
          valueTypeInclusion,
          valueTypeExclusion);
      filters.add(new VariableTypeFilter(valueTypeInclusion, valueTypeExclusion));
    }

    return List.copyOf(filters);
  }

  private static List<NameFilterRule> getVariableNameInclusionRules(
      final FilterConfiguration.IndexConfig index) {
    final List<NameFilterRule> rules = new ArrayList<>();
    rules.addAll(parseRules(index.getVariableNameInclusionExact(), EXACT));
    rules.addAll(parseRules(index.getVariableNameInclusionStartWith(), STARTS_WITH));
    rules.addAll(parseRules(index.getVariableNameInclusionEndWith(), ENDS_WITH));
    return List.copyOf(rules);
  }

  private static List<NameFilterRule> getVariableNameExclusionRules(
      final FilterConfiguration.IndexConfig index) {
    final List<NameFilterRule> rules = new ArrayList<>();
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
