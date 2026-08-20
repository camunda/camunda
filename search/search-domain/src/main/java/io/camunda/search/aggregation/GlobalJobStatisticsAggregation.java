/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.aggregation;

public class GlobalJobStatisticsAggregation implements AggregationBase {

  // Filter bucket names
  public static final String AGGREGATION_CREATED = "created";
  public static final String AGGREGATION_COMPLETED = "completed";
  public static final String AGGREGATION_FAILED = "failed";

  // Sub-aggregation names (used within each filter bucket)
  public static final String AGGREGATION_COUNT = "count";
  public static final String AGGREGATION_LAST_UPDATED_AT = "lastUpdatedAt";

  // For tracking incomplete batches
  public static final String AGGREGATION_INCOMPLETE = "incomplete";

  // Field names for aggregations
  public static final String FIELD_CREATED_COUNT = "createdCount";
  public static final String FIELD_COMPLETED_COUNT = "completedCount";
  public static final String FIELD_FAILED_COUNT = "failedCount";
  public static final String FIELD_LAST_CREATED_AT = "lastCreatedAt";
  public static final String FIELD_LAST_COMPLETED_AT = "lastCompletedAt";
  public static final String FIELD_LAST_FAILED_AT = "lastFailedAt";
  public static final String FIELD_INCOMPLETE_BATCH = "incompleteBatch";
}
