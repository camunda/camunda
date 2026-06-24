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

import io.camunda.zeebe.exporter.api.context.Context;
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

    final var variableNameInclusionRules =
        buildNameRules(
            index.getVariableNameInclusionExact(),
            index.getVariableNameInclusionStartWith(),
            index.getVariableNameInclusionEndWith());
    final var variableNameExclusionRules =
        buildNameRules(
            index.getVariableNameExclusionExact(),
            index.getVariableNameExclusionStartWith(),
            index.getVariableNameExclusionEndWith());

    final Set<VariableValueType> valueTypeInclusion =
        VariableTypeFilter.parseTypes(index.getVariableValueTypeInclusion());
    final Set<VariableValueType> valueTypeExclusion =
        VariableTypeFilter.parseTypes(index.getVariableValueTypeExclusion());

    final var localVariableNameInclusionRules =
        buildNameRules(
            index.getLocalVariableNameInclusionExact(),
            index.getLocalVariableNameInclusionStartWith(),
            index.getLocalVariableNameInclusionEndWith());
    final var localVariableNameExclusionRules =
        buildNameRules(
            index.getLocalVariableNameExclusionExact(),
            index.getLocalVariableNameExclusionStartWith(),
            index.getLocalVariableNameExclusionEndWith());
    final var rootVariableNameInclusionRules =
        buildNameRules(
            index.getRootVariableNameInclusionExact(),
            index.getRootVariableNameInclusionStartWith(),
            index.getRootVariableNameInclusionEndWith());
    final var rootVariableNameExclusionRules =
        buildNameRules(
            index.getRootVariableNameExclusionExact(),
            index.getRootVariableNameExclusionStartWith(),
            index.getRootVariableNameExclusionEndWith());

    final Set<VariableValueType> localValueTypeInclusion =
        VariableTypeFilter.parseTypes(index.getLocalVariableValueTypeInclusion());
    final Set<VariableValueType> localValueTypeExclusion =
        VariableTypeFilter.parseTypes(index.getLocalVariableValueTypeExclusion());
    final Set<VariableValueType> rootValueTypeInclusion =
        VariableTypeFilter.parseTypes(index.getRootVariableValueTypeInclusion());
    final Set<VariableValueType> rootValueTypeExclusion =
        VariableTypeFilter.parseTypes(index.getRootVariableValueTypeExclusion());

    // Filters are added in order of cheapness/selectivity: the chain short-circuits on the first
    // rejection, so higher-selectivity and lower-cost filters are placed first to avoid unnecessary
    // work for slower filters later in the chain.
    final List<ExporterRecordFilter> filters = new ArrayList<>();

    if (index.isOptimizeModeEnabled()) {
      LOG.info(
          "Optimize mode enabled. It might filter more restrictively than the filters defined in "
              + "acceptType, acceptValue, and acceptIntent. If you want to customize the filtering, "
              + "please disable optimize mode and use the other filter configuration options.");
      filters.add(new OptimizeModeFilter());
    }

    // Placed before VariableNameFilter: a single long comparison is cheaper than string matching,
    // so local variables are rejected before any name or type work runs.
    if (!index.isExportLocalVariablesEnabled()) {
      LOG.info(
          "Export local variables disabled. Local (sub-element scoped) variables will be filtered out.");
      filters.add(new ExportLocalVariablesFilter(false));
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

    if (!localVariableNameInclusionRules.isEmpty()
        || !localVariableNameExclusionRules.isEmpty()
        || !rootVariableNameInclusionRules.isEmpty()
        || !rootVariableNameExclusionRules.isEmpty()) {
      LOG.info(
          "Variable name scope filters configured. "
              + "Local inclusion rules: {}, Local exclusion rules: {}, "
              + "Root inclusion rules: {}, Root exclusion rules: {}.",
          localVariableNameInclusionRules,
          localVariableNameExclusionRules,
          rootVariableNameInclusionRules,
          rootVariableNameExclusionRules);
      filters.add(
          new VariableNameScopeFilter(
              localVariableNameInclusionRules,
              localVariableNameExclusionRules,
              rootVariableNameInclusionRules,
              rootVariableNameExclusionRules));
    }

    if (!localValueTypeInclusion.isEmpty()
        || !localValueTypeExclusion.isEmpty()
        || !rootValueTypeInclusion.isEmpty()
        || !rootValueTypeExclusion.isEmpty()) {
      LOG.info(
          "Variable type scope filters configured. "
              + "Local inclusion types: {}, Local exclusion types: {}, "
              + "Root inclusion types: {}, Root exclusion types: {}.",
          localValueTypeInclusion,
          localValueTypeExclusion,
          rootValueTypeInclusion,
          rootValueTypeExclusion);
      filters.add(
          new VariableTypeScopeFilter(
              localValueTypeInclusion,
              localValueTypeExclusion,
              rootValueTypeInclusion,
              rootValueTypeExclusion));
    }

    if (!index.getBpmnProcessIdInclusion().isEmpty()
        || !index.getBpmnProcessIdExclusion().isEmpty()) {
      LOG.info(
          "Bpmn process id filters configured. Inclusion ids: {}, Exclusion ids: {}.",
          index.getBpmnProcessIdInclusion(),
          index.getBpmnProcessIdExclusion());
      filters.add(
          new BpmnProcessFilter(
              index.getBpmnProcessIdInclusion(), index.getBpmnProcessIdExclusion()));
    }

    return List.copyOf(filters);
  }

  /**
   * Builds a flat list of {@link NameFilterRule}s from three raw string lists — one per match type.
   * Returns a mutable list; callers own the result.
   */
  private static List<NameFilterRule> buildNameRules(
      final List<String> exact, final List<String> startWith, final List<String> endWith) {
    final List<NameFilterRule> rules = new ArrayList<>();
    rules.addAll(NameFilterRule.parseRules(exact, EXACT));
    rules.addAll(NameFilterRule.parseRules(startWith, STARTS_WITH));
    rules.addAll(NameFilterRule.parseRules(endWith, ENDS_WITH));
    return rules;
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
