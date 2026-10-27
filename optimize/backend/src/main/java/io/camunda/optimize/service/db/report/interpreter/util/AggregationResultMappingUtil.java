/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.report.interpreter.util;

import java.util.Optional;

public final class AggregationResultMappingUtil {

  private AggregationResultMappingUtil() {}

  public static Double mapToDoubleOrNull(final Double value) {
    return Double.isInfinite(value) || Double.isNaN(value) ? null : value;
  }

  public static Optional<String> firstField(final String... fields) {
    return Optional.ofNullable(fields).filter(f -> f.length != 0).map(f -> f[0]);
  }
}
