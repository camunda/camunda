/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.common.filter;

import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class VariableNameFilter implements RecordValueFilter {

  private static final String LIST_SEPARATOR = ";";

  private final NameFilter nameFilter;

  public VariableNameFilter(
      final List<NameRule> inclusionRules, final List<NameRule> exclusionRules) {
    nameFilter = new NameFilter(inclusionRules, exclusionRules);
  }

  @Override
  public boolean accept(final RecordValue value) {
    if (!(value instanceof final VariableRecordValue variableRecordValue)) {
      return true;
    }

    return nameFilter.test(variableRecordValue.getName());
  }

  public static List<NameRule> parseRules(final String raw, final NameRule.Type type) {

    if (raw == null || raw.trim().isEmpty()) {
      return Collections.emptyList();
    }

    return Arrays.stream(raw.split(LIST_SEPARATOR))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .map(pattern -> new NameRule(type, pattern))
        .toList();
  }
}
