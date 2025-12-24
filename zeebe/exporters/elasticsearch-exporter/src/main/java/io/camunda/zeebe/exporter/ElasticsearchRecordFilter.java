/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter;

import io.camunda.zeebe.exporter.api.context.Context;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import org.apache.commons.lang3.StringUtils;

public class ElasticsearchRecordFilter implements Context.RecordFilter {

  private final ElasticsearchExporterConfiguration configuration;
  private final String[] variableNameInclusionList;

  ElasticsearchRecordFilter(final ElasticsearchExporterConfiguration configuration) {
    this.configuration = configuration;
    if (StringUtils.isBlank(configuration.index.variableNameInclusion)) {
      variableNameInclusionList = new String[0];
    } else {
      variableNameInclusionList = configuration.index.variableNameInclusion.trim().split(",");
    }
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
  public boolean acceptValue(final RecordValue value) {
    if (value instanceof final VariableRecordValue variableRecordValue) {
      return acceptVariableName(variableRecordValue);
    }

    return true;
  }

  private boolean acceptVariableName(final VariableRecordValue variableRecordValue) {
    if (variableNameInclusionList.length == 0) {
      return true;
    }

    final String variableName = variableRecordValue.getName();
    for (final String variableNameExclusion : variableNameInclusionList) {
      if (variableName.trim().startsWith(variableNameExclusion.trim())) {
        return true;
      }
    }
    return false;
  }
}
