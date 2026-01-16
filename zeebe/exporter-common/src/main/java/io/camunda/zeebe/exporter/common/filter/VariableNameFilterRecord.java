/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.common.filter;

import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import java.util.Collections;
import java.util.List;

public final class VariableNameFilterRecord implements ExporterRecordFilter, RecordVersionFilter {

  private final NameFilter nameFilter;

  public VariableNameFilterRecord(
      final List<NameRule> inclusionRules, final List<NameRule> exclusionRules) {
    nameFilter = new NameFilter(inclusionRules, exclusionRules);
  }

  @Override
  public boolean accept(final Record<?> record) {
    if (!(record.getValue() instanceof final VariableRecordValue variableRecordValue)) {
      return true;
    }

    return nameFilter.test(variableRecordValue.getName());
  }

  public static List<NameRule> parseRules(final List<String> rawList, final NameRule.Type type) {

    if (rawList == null) {
      return Collections.emptyList();
    }

    return rawList.stream()
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .map(pattern -> new NameRule(type, pattern))
        .toList();
  }

  @Override
  public String minRecordVersion() {
    return "8.9.0";
  }
}
