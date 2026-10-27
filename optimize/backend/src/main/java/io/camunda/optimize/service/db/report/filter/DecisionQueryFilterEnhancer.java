/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.report.filter;

import static io.camunda.optimize.util.SuppressionConstants.UNCHECKED_CAST;

import io.camunda.optimize.dto.optimize.query.report.single.decision.filter.DecisionFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterDataDto;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class DecisionQueryFilterEnhancer {

  public DecisionQueryFilterEnhancer() {}

  @SuppressWarnings(UNCHECKED_CAST)
  public <T extends FilterDataDto> List<T> extractFilters(
      final List<DecisionFilterDto<?>> filter,
      final Class<? extends DecisionFilterDto<?>> filterClass) {
    return filter.stream()
        .filter(filterClass::isInstance)
        .map(dateFilter -> (T) dateFilter.getData())
        .collect(Collectors.toList());
  }
}
