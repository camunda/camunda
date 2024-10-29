/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.report.service;

import static io.camunda.optimize.service.util.ExceptionUtil.isInstanceIndexNotFoundException;

import io.camunda.optimize.service.db.report.MinMaxStatDto;
import java.util.Arrays;
import org.slf4j.Logger;

public abstract class AbstractMinMaxStatsService {

  protected static final String NESTED_AGGREGATION_FIRST_FIELD = "nestedAggField1";
  protected static final String NESTED_AGGREGATION_SECOND_FIELD = "nestedAggField2";

  protected static final String FILTER_AGGREGATION_FIRST_FIELD = "filterAggField1";
  protected static final String FILTER_AGGREGATION_SECOND_FIELD = "filterAggField2";

  protected static final String STATS_AGGREGATION_FIRST_FIELD = "statsAggField1";
  protected static final String STATS_AGGREGATION_SECOND_FIELD = "statsAggField2";
  private static final Logger LOG =
      org.slf4j.LoggerFactory.getLogger(AbstractMinMaxStatsService.class);

  protected MinMaxStatDto returnEmptyResultIfInstanceIndexNotFound(
      final RuntimeException e, final String[] indexNames) {
    if (isInstanceIndexNotFoundException(e)) {
      LOG.info(
          "Could not calculate minMaxStats because at least one required instance indices from {} does not exist. "
              + "Returning min and max 0 instead.",
          Arrays.toString(indexNames));
      return new MinMaxStatDto(0, 0);
    }
    throw e;
  }
}
