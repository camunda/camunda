/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.report.aggregations;

import io.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationDto;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;

public abstract class AggregationStrategy {
  public abstract AggregationDto getAggregationType();

  protected String createAggregationName(final String... segments) {
    return Arrays.stream(segments).filter(Objects::nonNull).collect(Collectors.joining("_"));
  }
}
