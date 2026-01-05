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
import java.util.regex.Pattern;

public final class VariableNameRecordValueFilter implements RecordValueFilter {

  public static final String REGEX_LIST_SEPARATOR = ";";

  private final Pattern[] variableNameInclusionList;

  public VariableNameRecordValueFilter(final String variableNameInclusion) {
    if (variableNameInclusion == null || variableNameInclusion.trim().isEmpty()) {
      variableNameInclusionList = new Pattern[0];
    } else {
      variableNameInclusionList =
          Arrays.stream(variableNameInclusion.trim().split(REGEX_LIST_SEPARATOR))
              .map(it -> Pattern.compile(it.trim()))
              .toArray(Pattern[]::new);
    }
  }

  @Override
  public boolean accept(final RecordValue value) {
    if (!(value instanceof final VariableRecordValue variableRecordValue)) {
      // only responsible for variable records; let others pass
      return true;
    }

    if (variableNameInclusionList.length == 0) {
      return true;
    }

    final String variableName = variableRecordValue.getName();
    for (final Pattern variablePattern : variableNameInclusionList) {
      if (variablePattern.matcher(variableName).matches()) {
        return true;
      }
    }
    return false;
  }
}
