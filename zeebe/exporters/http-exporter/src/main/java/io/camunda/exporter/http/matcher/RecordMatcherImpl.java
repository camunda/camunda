/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.http.matcher;

import io.camunda.zeebe.protocol.record.Record;

public class RecordMatcherImpl implements RecordMatcher {
  private final FilterRecordMatcher filterRecordMatcher;

  public RecordMatcherImpl(final FilterRecordMatcher filterRecordMatcher) {
    this.filterRecordMatcher = filterRecordMatcher;
  }

  @Override
  public boolean matches(final Record<?> record) {
    if (filterRecordMatcher != null) {
      return filterRecordMatcher.matches(record);
    }
    return true; // No matchers defined, so everything matches
  }
}
