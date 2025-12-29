/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.common.filter;

import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

public final class SearchRecordFilter {

  private final Predicate<RecordType> typePredicate;
  private final Predicate<ValueType> valueTypePredicate;
  private final List<RecordValueFilter> recordValueFilters;

  public SearchRecordFilter(
      final Predicate<RecordType> typePredicate,
      final Predicate<ValueType> valueTypePredicate,
      final List<RecordValueFilter> recordValueFilters) {

    Objects.requireNonNull(typePredicate, "typePredicate must not be null");
    Objects.requireNonNull(valueTypePredicate, "valueTypePredicate must not be null");
    Objects.requireNonNull(recordValueFilters, "recordValueFilters must not be null");

    this.typePredicate = typePredicate;
    this.valueTypePredicate = valueTypePredicate;
    this.recordValueFilters = recordValueFilters;
  }

  public boolean acceptType(final RecordType recordType) {
    return typePredicate.test(recordType);
  }

  public boolean acceptValue(final ValueType valueType) {
    return valueTypePredicate.test(valueType);
  }

  public boolean acceptValue(final RecordValue value) {
    for (final RecordValueFilter filter : recordValueFilters) {
      if (!filter.accept(value)) {
        return false;
      }
    }
    return true;
  }
}
