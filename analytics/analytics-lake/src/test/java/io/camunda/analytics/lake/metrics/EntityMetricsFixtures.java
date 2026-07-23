/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.metrics;

import io.camunda.analytics.lake.sink.ColumnType;
import io.camunda.analytics.lake.sink.TableSchema;
import java.util.List;

/** A small raw {@code user_tasks} schema shared by every test in this package. */
final class EntityMetricsFixtures {

  private EntityMetricsFixtures() {}

  static TableSchema userTasksRawSchema() {
    return new TableSchema(
        "user_tasks",
        List.of(
            new TableSchema.Column("process_id", ColumnType.STRING_DICT, 1, false, 0, false),
            new TableSchema.Column("element_id", ColumnType.STRING_DICT, 2, false, 1, false),
            new TableSchema.Column(
                "started_at",
                ColumnType.LONG,
                3,
                false,
                -1,
                true,
                TableSchema.LogicalType.TIMESTAMPTZ),
            new TableSchema.Column("work_time_ms", ColumnType.LONG, 4, false, -1, false),
            new TableSchema.Column("retry_count", ColumnType.INT, 5, true, -1, false),
            new TableSchema.Column("value", ColumnType.DOUBLE, 6, true, -1, false)));
  }
}
