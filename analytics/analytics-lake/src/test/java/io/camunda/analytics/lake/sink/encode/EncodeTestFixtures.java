/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.sink.encode;

import io.camunda.analytics.lake.sink.ColumnType;
import io.camunda.analytics.lake.sink.TableSchema;
import java.util.ArrayList;
import java.util.List;
import org.apache.iceberg.Schema;
import org.apache.iceberg.types.Type;
import org.apache.iceberg.types.Types;

/** Shared schema fixtures for the L0 sink encoder tests. */
final class EncodeTestFixtures {

  private EncodeTestFixtures() {}

  /**
   * A small "instances"-shaped table exercising every {@link ColumnType}: a required LONG sort key,
   * the required LONG family-day source, a nullable STRING_DICT, a nullable LONG (bloom filter
   * target alongside the sort key), and a nullable BINARY.
   */
  static TableSchema instancesSchema() {
    return new TableSchema(
        "instances",
        List.of(
            new TableSchema.Column("instance_key", ColumnType.LONG, 1, false, 0, false),
            new TableSchema.Column("start_ms", ColumnType.LONG, 2, false, -1, true),
            new TableSchema.Column("process_id", ColumnType.STRING_DICT, 3, true, -1, false),
            new TableSchema.Column("element_key", ColumnType.LONG, 4, true, -1, false),
            new TableSchema.Column("vars_json", ColumnType.BINARY, 5, true, -1, false)));
  }

  /** Builds the Iceberg {@link Schema} matching {@code tableSchema}, field id for field id. */
  static Schema icebergSchema(final TableSchema tableSchema) {
    final List<Types.NestedField> fields = new ArrayList<>();
    for (final TableSchema.Column column : tableSchema.columns()) {
      final Type type =
          switch (column.type()) {
            case LONG -> Types.LongType.get();
            case INT -> Types.IntegerType.get();
            case STRING_DICT -> Types.StringType.get();
            case BINARY -> Types.BinaryType.get();
          };
      fields.add(
          column.nullable()
              ? Types.NestedField.optional(column.icebergFieldId(), column.name(), type)
              : Types.NestedField.required(column.icebergFieldId(), column.name(), type));
    }
    return new Schema(fields);
  }
}
