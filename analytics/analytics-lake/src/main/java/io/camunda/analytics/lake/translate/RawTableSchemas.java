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
  public static final String VARIANTS_TABLE = "variants";

  private RawTableSchemas() {}

  /**
   * Sort key {@code (process_id, key)} — identity-only, no timestamp component; family day source
   * is {@code started_at} (instance activation), which stays the implicit leading sort component
   * (see {@link io.camunda.analytics.lake.sink.SortedRun#dayRanges()}'s javadoc) but is no longer
   * also an explicit tertiary key. {@code key} (the process instance key) is a Zeebe key: unique
   * and monotonically increasing per partition, so once {@code process_id} groups rows of the same
   * process together, {@code key} alone gives a strict total order within that group with no ties
   * possible — a {@code started_at} tiebreaker cannot change the order, only cost a comparison
   * getting there. Because ties are unreachable by construction, {@link
   * io.camunda.analytics.lake.sink.batch.SegmentSorter}'s equal-keys worst case (the quicksort
   * degrading toward its insertion-sort fallback across a long run of ties) is unreachable too.
   * {@code started_at}/{@code ended_at} are {@code timestamptz} columns (epoch microseconds on the
   * batch vector, see {@link TableSchema.Column#logicalType()}); {@code duration_ms} stays a plain
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
            timestamptzColumn(icebergSchema, "started_at", -1, true),
            timestamptzColumn(icebergSchema, "ended_at", -1, false),
            column(icebergSchema, "duration_ms", ColumnType.LONG, -1, false),
            column(icebergSchema, "vars_json", ColumnType.BINARY, -1, false),
            // Schema v3 -- see IcebergLakeWriter#INSTANCE_SCHEMA's javadoc: nullable (an instance
            // completing with no accumulator -- e.g. state loss -- writes null, never fails).
            column(icebergSchema, "variant_hash", ColumnType.STRING_DICT, -1, false, true)));
  }

  /**
   * The variant-k1 dictionary table's {@link TableSchema}: one row per distinct (process id,
   * version, variant hash) triple ever seen, holding its sorted element/flow id lists. Not a
   * partials table (no declaration fingerprint) — see {@code
   * io.camunda.analytics.lake.write.IcebergLakeWriter#variantsTable()}'s javadoc. Sort key {@code
   * (process_id, variant_hash)}; family day source is {@code first_seen} (the completion time of
   * whichever instance first produced this row).
   */
  public static TableSchema variants(final Schema icebergSchema) {
    return new TableSchema(
        VARIANTS_TABLE,
        List.of(
            column(
                icebergSchema,
                "process_id",
                ColumnType.STRING_DICT,
                VariantColumns.SORT_PROCESS_ID,
                false),
            column(icebergSchema, "version", ColumnType.INT, -1, false),
            column(
                icebergSchema,
                "variant_hash",
                ColumnType.STRING_DICT,
                VariantColumns.SORT_VARIANT_HASH,
                false),
            column(icebergSchema, "elements", ColumnType.BINARY, -1, false),
            column(icebergSchema, "flows", ColumnType.BINARY, -1, false),
            timestamptzColumn(icebergSchema, "first_seen", -1, true)));
  }

  /**
   * Sort key {@code (process_id, instance_key, element_key)} — {@code element_key} replaces the old
   * {@code started_at} tiebreaker; family day source is {@code instance_started_at} (the owning
   * instance's activation, not the element's own start), still the implicit leading sort component,
   * no longer also an explicit key. All of an instance's elements live on the same Zeebe partition
   * as the instance itself, and element keys on one partition are unique and monotonically
   * increasing — so within one {@code (process_id, instance_key)} group, {@code element_key} order
   * <em>is</em> activation order, and, being unique, leaves no tie for {@code started_at} to break.
   * See {@link #instances(Schema)}'s javadoc for why that makes the sorter's equal-keys worst case
   * unreachable here too.
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
            column(
                icebergSchema,
                "element_key",
                ColumnType.LONG,
                ActivityColumns.SORT_ELEMENT_KEY,
                false),
            column(icebergSchema, "state", ColumnType.STRING_DICT, -1, false),
            timestamptzColumn(icebergSchema, "started_at", -1, false),
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
    return column(icebergSchema, name, type, sortOrder, familyDaySource, false);
  }

  /** Same as the 5-arg overload, additionally accepting {@code nullable} explicitly. */
  private static TableSchema.Column column(
      final Schema icebergSchema,
      final String name,
      final ColumnType type,
      final int sortOrder,
      final boolean familyDaySource,
      final boolean nullable) {
    final Types.NestedField field = findField(icebergSchema, name);
    return new TableSchema.Column(
        name, type, field.fieldId(), nullable, sortOrder, familyDaySource);
  }

  /** Same as {@link #column}, but for a {@code timestamptz} column (see the class javadoc). */
  private static TableSchema.Column timestamptzColumn(
      final Schema icebergSchema,
      final String name,
      final int sortOrder,
      final boolean familyDaySource) {
    final Types.NestedField field = findField(icebergSchema, name);
    return new TableSchema.Column(
        name,
        ColumnType.LONG,
        field.fieldId(),
        false,
        sortOrder,
        familyDaySource,
        TableSchema.LogicalType.TIMESTAMPTZ);
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

    /** Schema v3 -- see {@code IcebergLakeWriter#INSTANCE_SCHEMA}'s javadoc; nullable. */
    public static final int VARIANT_HASH = 10;

    /** Sort-key position of {@code process_id} (primary, after the implicit family-day lead). */
    private static final int SORT_PROCESS_ID = 0;

    /**
     * Sort-key position of {@code key} (secondary, and the last explicit key — see {@link
     * #instances(Schema)}'s javadoc for why a Zeebe key alone already yields a strict total order,
     * with no timestamp tiebreaker needed).
     */
    private static final int SORT_KEY = 1;

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

    /**
     * Sort-key position of {@code element_key} (tertiary, and the last explicit key — replaces the
     * old {@code started_at} tiebreaker; see {@link #activities(Schema)}'s javadoc for why an
     * element key alone already yields a strict total order within one instance).
     */
    private static final int SORT_ELEMENT_KEY = 2;

    private ActivityColumns() {}
  }

  /**
   * See {@link InstanceColumns}; same idea for the {@code variants} dictionary table — {@code
   * LakeTranslator} appends dictionary rows by these positions.
   */
  public static final class VariantColumns {
    public static final int PROCESS_ID = 0;
    public static final int VERSION = 1;
    public static final int VARIANT_HASH = 2;
    public static final int ELEMENTS = 3;
    public static final int FLOWS = 4;
    public static final int FIRST_SEEN = 5;

    private static final int SORT_PROCESS_ID = 0;
    private static final int SORT_VARIANT_HASH = 1;

    private VariantColumns() {}
  }
}
