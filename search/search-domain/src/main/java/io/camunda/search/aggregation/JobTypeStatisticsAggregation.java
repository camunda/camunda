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

public record JobTypeStatisticsAggregation(SearchQueryPage page)
    implements AggregationBase, AggregationPaginated {

  // Composite aggregation name
  public static final String AGGREGATION_BY_TYPE = "byType";

  // Composite aggregation source name
  public static final String AGGREGATION_SOURCE_NAME_JOB_TYPE = "jobType";

  // Default size for composite aggregation
  public static final int AGGREGATION_COMPOSITE_SIZE = 10000;

  // Filter bucket names (within each job type bucket)
  public static final String AGGREGATION_CREATED = "created";
  public static final String AGGREGATION_COMPLETED = "completed";
  public static final String AGGREGATION_FAILED = "failed";

  // Sub-aggregation names (used within each filter bucket)
  public static final String AGGREGATION_COUNT = "count";
  public static final String AGGREGATION_LAST_UPDATED_AT = "lastUpdatedAt";

  // Workers aggregation
  public static final String AGGREGATION_WORKERS = "workers";

  // Field names for aggregations
  public static final String FIELD_JOB_TYPE = "jobType";
  public static final String FIELD_CREATED_COUNT = "createdCount";
  public static final String FIELD_COMPLETED_COUNT = "completedCount";
  public static final String FIELD_FAILED_COUNT = "failedCount";
  public static final String FIELD_LAST_CREATED_AT = "lastCreatedAt";
  public static final String FIELD_LAST_COMPLETED_AT = "lastCompletedAt";
  public static final String FIELD_LAST_FAILED_AT = "lastFailedAt";
  public static final String FIELD_WORKER = "worker";
}
