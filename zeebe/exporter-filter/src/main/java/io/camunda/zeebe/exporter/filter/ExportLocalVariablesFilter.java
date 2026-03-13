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

/**
 * Filters variable records based on whether they are local (scoped to a sub-element) or root
 * (scoped to the process instance itself).
 *
 * <p>A variable is considered local when its {@code scopeKey} differs from its {@code
 * processInstanceKey}. When local variable export is disabled, such records are rejected; root
 * variables and all non-variable records are always accepted.
 */
public final class ExportLocalVariablesFilter implements ExporterRecordFilter, RecordVersionFilter {

  private static final SemanticVersion MIN_BROKER_VERSION =
      new SemanticVersion(8, 10, 0, null, null);

  private final boolean exportLocalVariablesEnabled;

  public ExportLocalVariablesFilter(final boolean exportLocalVariablesEnabled) {
    this.exportLocalVariablesEnabled = exportLocalVariablesEnabled;
  }

  @Override
  public boolean accept(final Record<?> record) {
    if (!(record.getValue() instanceof final VariableRecordValue variableRecordValue)) {
      return true;
    }
    if (exportLocalVariablesEnabled) {
      return true;
    }
    final boolean isLocal =
        variableRecordValue.getScopeKey() != variableRecordValue.getProcessInstanceKey();
    return !isLocal;
  }

  @Override
  public SemanticVersion minRecordBrokerVersion() {
    return MIN_BROKER_VERSION;
  }
}
