/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.interpreter.util;

import io.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramInterval;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class AggregateByDateUnitMapperES {
  private static final String UNSUPPORTED_UNIT_STRING = "Unsupported unit: ";

  public static DateHistogramInterval mapToDateHistogramInterval(final AggregateByDateUnit unit) {
    switch (unit) {
      case YEAR:
        return DateHistogramInterval.YEAR;
      case MONTH:
        return DateHistogramInterval.MONTH;
      case WEEK:
        return DateHistogramInterval.WEEK;
      case DAY:
        return DateHistogramInterval.DAY;
      case HOUR:
        return DateHistogramInterval.HOUR;
      case MINUTE:
        return DateHistogramInterval.MINUTE;
      default:
        throw new IllegalArgumentException(UNSUPPORTED_UNIT_STRING + unit);
    }
  }
}
