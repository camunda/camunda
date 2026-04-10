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
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class VariableNameFilter implements ExporterRecordFilter, RecordVersionFilter {

  private static final Logger LOG = LoggerFactory.getLogger(VariableNameFilter.class);

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

    final boolean accepted = nameFilter.accept(variableRecordValue.getName());
    if (!accepted && LOG.isDebugEnabled()) {
      LOG.debug(
          "VariableNameFilter rejected record {}: variable name '{}' did not match name rules",
          record.getKey(),
          variableRecordValue.getName());
    }
    return accepted;
  }

  @Override
  public SemanticVersion minRecordBrokerVersion() {
    return MIN_SEMANTIC_VERSION;
  }
}
