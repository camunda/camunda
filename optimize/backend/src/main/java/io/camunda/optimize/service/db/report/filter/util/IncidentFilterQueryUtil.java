/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

package io.camunda.optimize.service.db.report.filter.util;

import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.INCIDENTS;

import io.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import java.util.List;
import java.util.stream.Stream;

public abstract class IncidentFilterQueryUtil {
  protected static String nestedFieldReference(final String fieldName) {
    return INCIDENTS + "." + fieldName;
  }

  protected static boolean containsViewLevelFilterOfType(
      final List<ProcessFilterDto<?>> filters,
      final Class<? extends ProcessFilterDto<?>> filterClass) {
    return filters.stream()
        .filter(filter -> FilterApplicationLevel.VIEW.equals(filter.getFilterLevel()))
        .anyMatch(filterClass::isInstance);
  }

  protected static <T extends ProcessFilterDto<?>> Stream<T> findAllViewLevelFiltersOfType(
      final List<ProcessFilterDto<?>> filters, final Class<T> filterClass) {
    return filters.stream()
        .filter(filter -> FilterApplicationLevel.VIEW.equals(filter.getFilterLevel()))
        .filter(filterClass::isInstance)
        .map(filterClass::cast);
  }
}
