/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.http.matcher;

import io.camunda.zeebe.protocol.record.Record;
import java.util.function.Supplier;

public class CombinedMatcher implements RecordMatcher {
  private final FilterRecordMatcher filterRecordMatcher;
  private final RuleRecordMatcher ruleRecordMatcher;

  public CombinedMatcher(
      final FilterRecordMatcher filterRecordMatcher, final RuleRecordMatcher ruleRecordMatcher) {
    this.filterRecordMatcher = filterRecordMatcher;
    this.ruleRecordMatcher = ruleRecordMatcher;
  }

  @Override
  public boolean matches(final Record<?> record, final Supplier<String> jsonSupplier) {
    if (filterRecordMatcher != null) {
      if (filterRecordMatcher.matches(record)) {
        if (ruleRecordMatcher != null) {
          return ruleRecordMatcher.matches(jsonSupplier.get());
        } else {
          return true;
        }
      } else {
        return false;
      }
    } else if (ruleRecordMatcher != null) {
      return ruleRecordMatcher.matches(jsonSupplier.get());
    } else {
      return true; // No matchers defined, so everything matches
    }
  }
}
