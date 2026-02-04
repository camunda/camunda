/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.filter;

import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import io.camunda.zeebe.util.SemanticVersion;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class VariableNameFilter implements ExporterRecordFilter, RecordVersionFilter {

  private static final SemanticVersion MIN_SEMANTIC_VERSION =
      new SemanticVersion(8, 9, 0, null, null);

  private final NameFilter nameFilter;

  public VariableNameFilter(
      final List<NameFilterRule> inclusionRules, final List<NameFilterRule> exclusionRules) {
    nameFilter = new NameFilter(inclusionRules, exclusionRules);
  }

  @Override
  public boolean accept(final Record<?> record) {
    if (!(record.getValue() instanceof final VariableRecordValue variableRecordValue)) {
      return true;
    }

    return nameFilter.accept(variableRecordValue.getName());
  }

  public static List<NameFilterRule> parseRules(
      final List<String> rawList, final NameFilterRule.Type type) {

    if (rawList == null || rawList.isEmpty()) {
      return Collections.emptyList();
    }

    return rawList.stream()
        .filter(Objects::nonNull)
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .map(pattern -> new NameFilterRule(type, pattern))
        .toList();
  }

  @Override
  public SemanticVersion minRecordBrokerVersion() {
    return MIN_SEMANTIC_VERSION;
  }
}
