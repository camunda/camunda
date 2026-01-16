/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.common.filter;

import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import java.util.Objects;
import java.util.function.Predicate;

/** Simple record-level filter that delegates to a {@link Predicate} over {@link RecordType}. */
public final class RecordTypeFilter implements ExporterRecordFilter {

  private final Predicate<RecordType> predicate;

  public RecordTypeFilter(final Predicate<RecordType> predicate) {
    this.predicate = Objects.requireNonNull(predicate, "predicate must not be null");
  }

  @Override
  public boolean accept(final Record<?> record) {
    return predicate.test(record.getRecordType());
  }
}
