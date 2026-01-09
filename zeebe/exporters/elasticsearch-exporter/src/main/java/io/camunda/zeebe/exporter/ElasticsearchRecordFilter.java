/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter;

import static io.camunda.zeebe.exporter.common.filter.NameRule.Type.*;

import io.camunda.zeebe.exporter.ElasticsearchExporterConfiguration.IndexConfiguration;
import io.camunda.zeebe.exporter.api.context.Context;
import io.camunda.zeebe.exporter.common.filter.ExportRecordFilter;
import io.camunda.zeebe.exporter.common.filter.NameRule;
import io.camunda.zeebe.exporter.common.filter.VariableNameFilter;
import io.camunda.zeebe.exporter.common.filter.VariableValueTypeFilter;
import io.camunda.zeebe.exporter.common.filter.VariableValueTypeFilter.VariableValueType;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ElasticsearchRecordFilter implements Context.RecordFilter {

  private final ExportRecordFilter delegate;

  ElasticsearchRecordFilter(final ElasticsearchExporterConfiguration configuration) {
    final var index = configuration.index;

    final List<NameRule> nameInclusionRules = getNameInclusionRules(index);
    final List<NameRule> nameExclusionRules = getNameExclusionRules(index);

    final Set<VariableValueType> valueTypeInclusion =
        VariableValueTypeFilter.parseTypes(index.variableValueTypeInclusion);
    final Set<VariableValueType> valueTypeExclusion =
        VariableValueTypeFilter.parseTypes(index.variableValueTypeExclusion);

    delegate =
        new ExportRecordFilter(
            configuration::shouldIndexRecordType,
            configuration::shouldIndexValueType,
            List.of(
                new VariableNameFilter(nameInclusionRules, nameExclusionRules),
                new VariableValueTypeFilter(valueTypeInclusion, valueTypeExclusion)));
  }

  private static List<NameRule> getNameExclusionRules(final IndexConfiguration index) {
    final List<NameRule> nameExclusionRules = new ArrayList<>();
    nameExclusionRules.addAll(VariableNameFilter.addRules(index.variableNameExclusionExact, EXACT));
    nameExclusionRules.addAll(
        VariableNameFilter.addRules(index.variableNameExclusionStartWith, STARTS_WITH));
    nameExclusionRules.addAll(
        VariableNameFilter.addRules(index.variableNameExclusionEndWith, ENDS_WITH));
    nameExclusionRules.addAll(VariableNameFilter.addRules(index.variableNameExclusionRegex, REGEX));
    return nameExclusionRules;
  }

  private static List<NameRule> getNameInclusionRules(final IndexConfiguration index) {
    final List<NameRule> nameInclusionRules = new ArrayList<>();
    nameInclusionRules.addAll(VariableNameFilter.addRules(index.variableNameInclusionExact, EXACT));
    nameInclusionRules.addAll(
        VariableNameFilter.addRules(index.variableNameInclusionStartWith, STARTS_WITH));
    nameInclusionRules.addAll(
        VariableNameFilter.addRules(index.variableNameInclusionEndWith, ENDS_WITH));
    nameInclusionRules.addAll(VariableNameFilter.addRules(index.variableNameInclusionRegex, REGEX));
    return nameInclusionRules;
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
