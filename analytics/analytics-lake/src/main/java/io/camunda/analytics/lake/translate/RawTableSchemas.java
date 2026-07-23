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
  public static final String OBJECTS_TABLE = "objects";
  public static final String INSTANCE_LINKS_TABLE = "instance_links";
  public static final String OBJECT_RELATIONS_TABLE = "object_relations";
  public static final String OBJECT_LIFECYCLE_TABLE = "object_lifecycle";

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
   *
   * <p>Schema v4 adds {@code flow_scope_key} (nullable {@code LONG}): the element's immediate BPMN
   * flow scope's element instance key ({@code ProcessInstanceRecordValue#getFlowScopeKey()}),
   * {@code null} when it equals the owning instance key (a top-level element's own flow scope) —
   * the subtree-attribution enabler journey reads need, e.g. "every element inside this
   * multi-instance body". See {@code
   * io.camunda.analytics.lake.write.IcebergLakeWriter#ACTIVITY_SCHEMA}'s own javadoc for the
   * field-id side of this.
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
            timestamptzColumn(icebergSchema, "instance_started_at", -1, true),
            // Schema v4 -- see this method's own javadoc paragraph above.
            column(icebergSchema, "flow_scope_key", ColumnType.LONG, -1, false, true)));
  }

  /**
   * The object-fabric sightings dictionary table: one row per distinct (object type, object id,
   * instance, scope) ever sighted (see {@code io.camunda.analytics.lake.translate.LakeTranslator}'s
   * "Object fabric capture" javadoc section). Not a partials table (no declaration fingerprint) —
   * same reasoning as {@link #variants(Schema)}: a row's content is fully determined by its key, so
   * there is nothing for a fingerprint to guard against. Sort key {@code (object_type, object_id)};
   * family day source is {@code first_seen}.
   */
  public static TableSchema objects(final Schema icebergSchema) {
    return new TableSchema(
        OBJECTS_TABLE,
        List.of(
            column(
                icebergSchema,
                "object_type",
                ColumnType.STRING_DICT,
                ObjectColumns.SORT_OBJECT_TYPE,
                false),
            column(
                icebergSchema,
                "object_id",
                ColumnType.STRING_DICT,
                ObjectColumns.SORT_OBJECT_ID,
                false),
            column(icebergSchema, "instance_key", ColumnType.LONG, -1, false),
            column(icebergSchema, "process_id", ColumnType.STRING_DICT, -1, false),
            column(icebergSchema, "version", ColumnType.INT, -1, false),
            // NULL == root scope -- see LakeTranslator's own scope-key convention.
            column(icebergSchema, "scope_key", ColumnType.LONG, -1, false, true),
            column(icebergSchema, "qualifier", ColumnType.STRING_DICT, -1, false),
            timestamptzColumn(icebergSchema, "first_seen", -1, true)));
  }

  /**
   * The call-activity instance-link dictionary table: one row per child instance ever created via a
   * call activity (see {@code LakeTranslator}'s "Object fabric capture" javadoc section). Not a
   * partials table, same reasoning as {@link #objects(Schema)}. Sort key {@code
   * (parent_instance_key, child_instance_key)}; family day source is {@code linked_at}.
   */
  public static TableSchema instanceLinks(final Schema icebergSchema) {
    return new TableSchema(
        INSTANCE_LINKS_TABLE,
        List.of(
            column(
                icebergSchema,
                "parent_instance_key",
                ColumnType.LONG,
                InstanceLinkColumns.SORT_PARENT_INSTANCE_KEY,
                false),
            column(
                icebergSchema,
                "child_instance_key",
                ColumnType.LONG,
                InstanceLinkColumns.SORT_CHILD_INSTANCE_KEY,
                false),
            column(icebergSchema, "link_type", ColumnType.STRING_DICT, -1, false),
            column(icebergSchema, "via_element_instance_key", ColumnType.LONG, -1, false, true),
            timestamptzColumn(icebergSchema, "linked_at", -1, true)));
  }

  /**
   * The object-relations dictionary table: one row per distinct (parent type, parent id, child
   * type, child id) edge ever derived at instance completion (see {@code LakeTranslator}'s "Object
   * fabric capture" javadoc section for the v1 root⊇non-root rule this backs). Not a partials
   * table, same reasoning as {@link #objects(Schema)}. Sort key {@code (parent_type, parent_id,
   * child_type, child_id)}; family day source is {@code first_seen}.
   */
  public static TableSchema objectRelations(final Schema icebergSchema) {
    return new TableSchema(
        OBJECT_RELATIONS_TABLE,
        List.of(
            column(
                icebergSchema,
                "parent_type",
                ColumnType.STRING_DICT,
                ObjectRelationColumns.SORT_PARENT_TYPE,
                false),
            column(
                icebergSchema,
                "parent_id",
                ColumnType.STRING_DICT,
                ObjectRelationColumns.SORT_PARENT_ID,
                false),
            column(
                icebergSchema,
                "child_type",
                ColumnType.STRING_DICT,
                ObjectRelationColumns.SORT_CHILD_TYPE,
                false),
            column(
                icebergSchema,
                "child_id",
                ColumnType.STRING_DICT,
                ObjectRelationColumns.SORT_CHILD_ID,
                false),
            timestamptzColumn(icebergSchema, "first_seen", -1, true)));
  }

  /**
   * The object-lifecycle fact table: one row per object closing (see {@code
   * io.camunda.analytics.lake.translate.LakeTranslator}'s "Object lifecycle capture" javadoc
   * section) — a FACT kind like {@code instances}, not a dictionary: created via the plain {@link
   * io.camunda.analytics.lake.write.IcebergLakeWriter#tableOrCreate}-equivalent path with no
   * declaration fingerprint (a closing is a one-time event, never re-derivable content the way a
   * dictionary row is — see that class's own {@code OBJECT_LIFECYCLE_SCHEMA} javadoc). Sort key
   * {@code (object_type, object_id)} — at most one row per object, ever (the tombstone rule
   * enforces this); family day source is {@code birth_ts}, not {@code closed_at}: grouping by
   * <em>birth</em> cohort (not close date) is what lets {@code object_cohorts}' survival read (born
   * − closed per cohort) line up against {@code objects_born}'s own birth-keyed counts.
   */
  public static TableSchema objectLifecycle(final Schema icebergSchema) {
    return new TableSchema(
        OBJECT_LIFECYCLE_TABLE,
        List.of(
            column(
                icebergSchema,
                "object_type",
                ColumnType.STRING_DICT,
                ObjectLifecycleColumns.SORT_OBJECT_TYPE,
                false),
            column(
                icebergSchema,
                "object_id",
                ColumnType.STRING_DICT,
                ObjectLifecycleColumns.SORT_OBJECT_ID,
                false),
            column(icebergSchema, "birth_qualifier", ColumnType.STRING_DICT, -1, false),
            timestamptzColumn(icebergSchema, "birth_ts", -1, true),
            timestamptzColumn(icebergSchema, "closed_at", -1, false),
            column(icebergSchema, "duration_ms", ColumnType.LONG, -1, false),
            column(icebergSchema, "outcome", ColumnType.STRING_DICT, -1, false),
            column(icebergSchema, "n_sightings", ColumnType.INT, -1, false)));
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

    /** Schema v4 -- see {@link #activities(Schema)}'s own javadoc; nullable. */
    public static final int FLOW_SCOPE_KEY = 12;

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

  /**
   * See {@link InstanceColumns}; same idea for the {@code objects} dictionary table — {@code
   * LakeTranslator} appends sighting rows by these positions.
   */
  public static final class ObjectColumns {
    public static final int OBJECT_TYPE = 0;
    public static final int OBJECT_ID = 1;
    public static final int INSTANCE_KEY = 2;
    public static final int PROCESS_ID = 3;
    public static final int VERSION = 4;
    public static final int SCOPE_KEY = 5;
    public static final int QUALIFIER = 6;
    public static final int FIRST_SEEN = 7;

    private static final int SORT_OBJECT_TYPE = 0;
    private static final int SORT_OBJECT_ID = 1;

    private ObjectColumns() {}
  }

  /**
   * See {@link InstanceColumns}; same idea for the {@code instance_links} dictionary table — {@code
   * LakeTranslator} appends link rows by these positions.
   */
  public static final class InstanceLinkColumns {
    public static final int PARENT_INSTANCE_KEY = 0;
    public static final int CHILD_INSTANCE_KEY = 1;
    public static final int LINK_TYPE = 2;
    public static final int VIA_ELEMENT_INSTANCE_KEY = 3;
    public static final int LINKED_AT = 4;

    private static final int SORT_PARENT_INSTANCE_KEY = 0;
    private static final int SORT_CHILD_INSTANCE_KEY = 1;

    private InstanceLinkColumns() {}
  }

  /**
   * See {@link InstanceColumns}; same idea for the {@code object_relations} dictionary table —
   * {@code LakeTranslator} appends relation rows by these positions.
   */
  public static final class ObjectRelationColumns {
    public static final int PARENT_TYPE = 0;
    public static final int PARENT_ID = 1;
    public static final int CHILD_TYPE = 2;
    public static final int CHILD_ID = 3;
    public static final int FIRST_SEEN = 4;

    private static final int SORT_PARENT_TYPE = 0;
    private static final int SORT_PARENT_ID = 1;
    private static final int SORT_CHILD_TYPE = 2;
    private static final int SORT_CHILD_ID = 3;

    private ObjectRelationColumns() {}
  }

  /**
   * See {@link InstanceColumns}; same idea for the {@code object_lifecycle} fact table — {@code
   * LakeTranslator} appends closing rows by these positions.
   */
  public static final class ObjectLifecycleColumns {
    public static final int OBJECT_TYPE = 0;
    public static final int OBJECT_ID = 1;
    public static final int BIRTH_QUALIFIER = 2;
    public static final int BIRTH_TS = 3;
    public static final int CLOSED_AT = 4;
    public static final int DURATION_MS = 5;
    public static final int OUTCOME = 6;
    public static final int N_SIGHTINGS = 7;

    private static final int SORT_OBJECT_TYPE = 0;
    private static final int SORT_OBJECT_ID = 1;

    private ObjectLifecycleColumns() {}
  }
}
