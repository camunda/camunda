/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter;

import static io.camunda.zeebe.exporter.common.filter.NameRule.Type.*;
import static io.camunda.zeebe.exporter.common.filter.VariableNameFilter.parseRules;

import io.camunda.zeebe.exporter.ElasticsearchExporterConfiguration.IndexConfiguration;
import io.camunda.zeebe.exporter.api.context.Context;
import io.camunda.zeebe.exporter.common.filter.BpmnProcessFilter;
import io.camunda.zeebe.exporter.common.filter.ExportRecordFilter;
import io.camunda.zeebe.exporter.common.filter.FlowNodeFilter;
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

    final Set<VariableValueType> valueTypeInclusion =
        VariableValueTypeFilter.parseTypes(index.variableValueTypeInclusion);
    final Set<VariableValueType> valueTypeExclusion =
        VariableValueTypeFilter.parseTypes(index.variableValueTypeExclusion);

    final List<NameRule> nameInclusionRules = getNameInclusionRules(index);
    final List<NameRule> nameExclusionRules = getNameExclusionRules(index);

    delegate =
        new ExportRecordFilter(
            configuration::shouldIndexRecordType,
            configuration::shouldIndexRequiredValueType,
            List.of(
                new BpmnProcessFilter(index.bpmnProcessIdInclusion, index.bpmnProcessIdExclusion),
                new FlowNodeFilter(index.flowTypeInclusion, index.flowTypeExclusion),
                new VariableValueTypeFilter(valueTypeInclusion, valueTypeExclusion),
                new VariableNameFilter(nameInclusionRules, nameExclusionRules)));
  }

  private static List<NameRule> getNameExclusionRules(final IndexConfiguration index) {
    final List<NameRule> nameExclusionRules = new ArrayList<>();
    nameExclusionRules.addAll(parseRules(index.variableNameExclusionExact, EXACT));
    nameExclusionRules.addAll(parseRules(index.variableNameExclusionStartWith, STARTS_WITH));
    nameExclusionRules.addAll(parseRules(index.variableNameExclusionEndWith, ENDS_WITH));
    nameExclusionRules.addAll(parseRules(index.variableNameExclusionRegex, REGEX));
    return nameExclusionRules;
  }

  private static List<NameRule> getNameInclusionRules(final IndexConfiguration index) {
    final List<NameRule> nameInclusionRules = new ArrayList<>();
    nameInclusionRules.addAll(parseRules(index.variableNameInclusionExact, EXACT));
    nameInclusionRules.addAll(parseRules(index.variableNameInclusionStartWith, STARTS_WITH));
    nameInclusionRules.addAll(parseRules(index.variableNameInclusionEndWith, ENDS_WITH));
    nameInclusionRules.addAll(parseRules(index.variableNameInclusionRegex, REGEX));
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
