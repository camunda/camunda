/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.aggregation;

import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.AggregationPaginated;

public record JobErrorStatisticsAggregation(SearchQueryPage page)
    implements AggregationBase, AggregationPaginated {

  // Composite aggregation name
  public static final String AGGREGATION_BY_ERROR = "byError";

  // Composite aggregation source names
  public static final String AGGREGATION_SOURCE_NAME_ERROR_CODE = "errorCode";
  public static final String AGGREGATION_SOURCE_NAME_ERROR_MESSAGE = "errorMessage";

  // Sub-aggregation name: count distinct workers
  public static final String AGGREGATION_WORKERS = "workers";

  // Default size for composite aggregation
  public static final int AGGREGATION_COMPOSITE_SIZE = 10_000;

  // Field names
  public static final String FIELD_ERROR_CODE = "errorCode";
  public static final String FIELD_ERROR_MESSAGE = "errorMessage";
  public static final String FIELD_WORKER = "worker";
}
