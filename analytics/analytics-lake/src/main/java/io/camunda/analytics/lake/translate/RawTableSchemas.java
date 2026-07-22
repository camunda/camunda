/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.translate;

import io.camunda.analytics.lake.sink.ColumnType;
import io.camunda.analytics.lake.sink.TableSchema;
import java.util.List;
import org.apache.iceberg.Schema;
import org.apache.iceberg.types.Types;

/**
 * The L0 sink's {@link TableSchema} descriptors for the two raw lake tables, matching {@code
 * io.camunda.analytics.lake.write.IcebergLakeWriter}'s own Iceberg schemas column for column.
 *
 * <p>Field ids are resolved from the live catalog {@link Schema} by column name — never hardcoded
 * here — per {@link TableSchema}'s own javadoc ("field ids are authoritative and must come from the
 * Iceberg table's schema in the catalog"): hardcoding a second copy of the ids would silently drift
 * from the catalog the moment either side changed without the other, corrupting column resolution
 * for every reader. Callers pass the actual {@code instancesTable().schema()}/{@code
 * activitiesTable().schema()} obtained from the catalog.
 */
public final class RawTableSchemas {

  public static final String INSTANCES_TABLE = "instances";
  public static final String ACTIVITIES_TABLE = "activities";

  private RawTableSchemas() {}

  /**
   * Sort key {@code (process_id, key, started_at)}; family day source is {@code started_at}
   * (instance activation) — see the class-level "Column mapping"/"Sort keys" contract this mirrors.
   * {@code started_at}/{@code ended_at} are {@code timestamptz} columns (epoch microseconds on the
   * batch vector, see {@link TableSchema.Column#timestamptz()}); {@code duration_ms} stays a plain
   * millisecond {@code LONG}.
   */
  public static TableSchema instances(final Schema icebergSchema) {
    return new TableSchema(
        INSTANCES_TABLE,
        List.of(
            column(icebergSchema, "key", ColumnType.LONG, InstanceColumns.SORT_KEY, false),
            column(icebergSchema, "process_definition_key", ColumnType.LONG, -1, false),
            column(
                icebergSchema,
                "process_id",
                ColumnType.STRING_DICT,
                InstanceColumns.SORT_PROCESS_ID,
                false),
            column(icebergSchema, "version", ColumnType.INT, -1, false),
            column(icebergSchema, "tenant_id", ColumnType.STRING_DICT, -1, false),
            column(icebergSchema, "state", ColumnType.STRING_DICT, -1, false),
            timestamptzColumn(icebergSchema, "started_at", InstanceColumns.SORT_START_MS, true),
            timestamptzColumn(icebergSchema, "ended_at", -1, false),
            column(icebergSchema, "duration_ms", ColumnType.LONG, -1, false),
            column(icebergSchema, "vars_json", ColumnType.BINARY, -1, false)));
  }

  /**
   * Sort key {@code (process_id, instance_key, started_at)}; family day source is {@code
   * instance_started_at} (the owning instance's activation, not the element's own start).
   */
  public static TableSchema activities(final Schema icebergSchema) {
    return new TableSchema(
        ACTIVITIES_TABLE,
        List.of(
            column(
                icebergSchema,
                "instance_key",
                ColumnType.LONG,
                ActivityColumns.SORT_INSTANCE_KEY,
                false),
            column(
                icebergSchema,
                "process_id",
                ColumnType.STRING_DICT,
                ActivityColumns.SORT_PROCESS_ID,
                false),
            column(icebergSchema, "version", ColumnType.INT, -1, false),
            column(icebergSchema, "tenant_id", ColumnType.STRING_DICT, -1, false),
            column(icebergSchema, "element_id", ColumnType.STRING_DICT, -1, false),
            column(icebergSchema, "element_type", ColumnType.STRING_DICT, -1, false),
            column(icebergSchema, "element_key", ColumnType.LONG, -1, false),
            column(icebergSchema, "state", ColumnType.STRING_DICT, -1, false),
            timestamptzColumn(icebergSchema, "started_at", ActivityColumns.SORT_START_MS, false),
            timestamptzColumn(icebergSchema, "ended_at", -1, false),
            column(icebergSchema, "duration_ms", ColumnType.LONG, -1, false),
            timestamptzColumn(icebergSchema, "instance_started_at", -1, true)));
  }

  private static TableSchema.Column column(
      final Schema icebergSchema,
      final String name,
      final ColumnType type,
      final int sortOrder,
      final boolean familyDaySource) {
    final Types.NestedField field = findField(icebergSchema, name);
    return new TableSchema.Column(name, type, field.fieldId(), false, sortOrder, familyDaySource);
  }

  /** Same as {@link #column}, but for a {@code timestamptz} column (see the class javadoc). */
  private static TableSchema.Column timestamptzColumn(
      final Schema icebergSchema,
      final String name,
      final int sortOrder,
      final boolean familyDaySource) {
    final Types.NestedField field = findField(icebergSchema, name);
    return new TableSchema.Column(
        name, ColumnType.LONG, field.fieldId(), false, sortOrder, familyDaySource, true);
  }

  private static Types.NestedField findField(final Schema icebergSchema, final String name) {
    final Types.NestedField field = icebergSchema.findField(name);
    if (field == null) {
      throw new IllegalArgumentException(
          "iceberg schema " + icebergSchema + " has no field named " + name);
    }
    return field;
  }

  /**
   * Column indexes into the {@code instances} {@link TableSchema}/{@link
   * io.camunda.analytics.lake.sink.RowAppender}, in the order {@link #instances(Schema)} declares
   * them — {@code LakeTranslator} appends rows by these positions.
   */
  public static final class InstanceColumns {
    public static final int KEY = 0;
    public static final int PROCESS_DEFINITION_KEY = 1;
    public static final int PROCESS_ID = 2;
    public static final int VERSION = 3;
    public static final int TENANT_ID = 4;
    public static final int STATE = 5;
    public static final int STARTED_AT = 6;
    public static final int ENDED_AT = 7;
    public static final int DURATION_MS = 8;
    public static final int VARS_JSON = 9;

    /** Sort-key position of {@code process_id} (primary, after the implicit family-day lead). */
    private static final int SORT_PROCESS_ID = 0;

    /** Sort-key position of {@code key} (secondary). */
    private static final int SORT_KEY = 1;

    /** Sort-key position of {@code started_at} (tertiary; also the family-day source). */
    private static final int SORT_START_MS = 2;

    private InstanceColumns() {}
  }

  /** See {@link InstanceColumns}; same idea for the {@code activities} table. */
  public static final class ActivityColumns {
    public static final int INSTANCE_KEY = 0;
    public static final int PROCESS_ID = 1;
    public static final int VERSION = 2;
    public static final int TENANT_ID = 3;
    public static final int ELEMENT_ID = 4;
    public static final int ELEMENT_TYPE = 5;
    public static final int ELEMENT_KEY = 6;
    public static final int STATE = 7;
    public static final int STARTED_AT = 8;
    public static final int ENDED_AT = 9;
    public static final int DURATION_MS = 10;
    public static final int INSTANCE_STARTED_AT = 11;

    private static final int SORT_PROCESS_ID = 0;
    private static final int SORT_INSTANCE_KEY = 1;
    private static final int SORT_START_MS = 2;

    private ActivityColumns() {}
  }
}
