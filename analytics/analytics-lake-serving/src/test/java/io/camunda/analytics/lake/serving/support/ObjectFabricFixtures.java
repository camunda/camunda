/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.serving.support;

import java.nio.file.Path;

/**
 * A planted object-fabric scenario for {@code /api/objects/*}: one instance ({@code
 * instance_key=9001}) with a top-level element A, a subprocess scope element B, and an element C
 * nested inside B (via {@code flow_scope_key}) -- so a {@code SCOPE}-attributed journey (sighted at
 * B) picks up B+C but not A, while a {@code ROOT}-attributed journey (root sighting) picks up all
 * three.
 *
 * <table>
 *   <caption>ground truth</caption>
 *   <tr><td>activities</td><td>A (element_key=100, flow_scope_key=NULL), B (element_key=200,
 *       flow_scope_key=NULL), C (element_key=201, flow_scope_key=200)</td></tr>
 *   <tr><td>objects</td><td>{@code invoice}/{@code INV-SCOPE} sighted at scope_key=200 (B);
 *       {@code invoice}/{@code INV-ROOT} sighted at scope_key=NULL (root)</td></tr>
 *   <tr><td>instance_links</td><td>one link from instance 9001 to child instance 9002</td></tr>
 *   <tr><td>object_relations</td><td>one edge {@code invoice/INV-ROOT -> lineItem/LI-1}</td></tr>
 * </table>
 */
public final class ObjectFabricFixtures {

  public static final long INSTANCE_KEY = 9001;
  public static final String PROCESS_ID = "orderProcess";
  public static final String OBJECT_TYPE = "invoice";
  public static final String SCOPE_OBJECT_ID = "INV-SCOPE";
  public static final String ROOT_OBJECT_ID = "INV-ROOT";
  public static final long CHILD_INSTANCE_KEY = 9002;

  private ObjectFabricFixtures() {}

  public static void build(final Path warehouseDir) {
    ParquetFixtures.writeTableFromQuery(
        warehouseDir,
        "activities",
        "SELECT "
            + INSTANCE_KEY
            + " AS instance_key, '"
            + PROCESS_ID
            + "' AS process_id, 1 AS version, 'default' AS tenant_id, "
            + "'A' AS element_id, 'SERVICE_TASK' AS element_type, 100 AS element_key, "
            + "'COMPLETED' AS state, "
            + "CAST(TIMESTAMP '2024-01-01 00:00:00' AS TIMESTAMPTZ) AS started_at, "
            + "CAST(TIMESTAMP '2024-01-01 00:01:00' AS TIMESTAMPTZ) AS ended_at, 60000 AS duration_ms, "
            + "CAST(TIMESTAMP '2024-01-01 00:00:00' AS TIMESTAMPTZ) AS instance_started_at, "
            + "CAST(NULL AS BIGINT) AS flow_scope_key "
            + "UNION ALL SELECT "
            + INSTANCE_KEY
            + ", '"
            + PROCESS_ID
            + "', 1, 'default', 'B', 'SUB_PROCESS', 200, 'COMPLETED', "
            + "CAST(TIMESTAMP '2024-01-01 00:01:00' AS TIMESTAMPTZ), "
            + "CAST(TIMESTAMP '2024-01-01 00:03:00' AS TIMESTAMPTZ), 120000, "
            + "CAST(TIMESTAMP '2024-01-01 00:00:00' AS TIMESTAMPTZ), CAST(NULL AS BIGINT) "
            + "UNION ALL SELECT "
            + INSTANCE_KEY
            + ", '"
            + PROCESS_ID
            + "', 1, 'default', 'C', 'SERVICE_TASK', 201, 'COMPLETED', "
            + "CAST(TIMESTAMP '2024-01-01 00:01:30' AS TIMESTAMPTZ), "
            + "CAST(TIMESTAMP '2024-01-01 00:02:30' AS TIMESTAMPTZ), 60000, "
            + "CAST(TIMESTAMP '2024-01-01 00:00:00' AS TIMESTAMPTZ), 200");

    ParquetFixtures.writeTableFromQuery(
        warehouseDir,
        "objects",
        "SELECT '"
            + OBJECT_TYPE
            + "' AS object_type, '"
            + SCOPE_OBJECT_ID
            + "' AS object_id, "
            + INSTANCE_KEY
            + " AS instance_key, '"
            + PROCESS_ID
            + "' AS process_id, 1 AS version, 200 AS scope_key, 'root' AS qualifier, "
            + "CAST(TIMESTAMP '2024-01-01 00:01:00' AS TIMESTAMPTZ) AS first_seen "
            + "UNION ALL SELECT '"
            + OBJECT_TYPE
            + "', '"
            + ROOT_OBJECT_ID
            + "', "
            + INSTANCE_KEY
            + ", '"
            + PROCESS_ID
            + "', 1, CAST(NULL AS BIGINT), 'root', "
            + "CAST(TIMESTAMP '2024-01-01 00:00:00' AS TIMESTAMPTZ)");

    ParquetFixtures.writeTableFromQuery(
        warehouseDir,
        "instance_links",
        "SELECT "
            + "CAST("
            + INSTANCE_KEY
            + " AS BIGINT) AS parent_instance_key, CAST("
            + CHILD_INSTANCE_KEY
            + " AS BIGINT) AS child_instance_key, 'CALL_ACTIVITY' AS link_type, "
            + "CAST(100 AS BIGINT) AS via_element_instance_key, "
            + "CAST(TIMESTAMP '2024-01-01 00:00:30' AS TIMESTAMPTZ) AS linked_at");

    ParquetFixtures.writeTableFromQuery(
        warehouseDir,
        "object_relations",
        "SELECT '"
            + OBJECT_TYPE
            + "' AS parent_type, '"
            + ROOT_OBJECT_ID
            + "' AS parent_id, 'lineItem' AS child_type, 'LI-1' AS child_id, "
            + "CAST(TIMESTAMP '2024-01-01 00:00:00' AS TIMESTAMPTZ) AS first_seen");
  }

  // -----------------------------------------------------------------------------------------
  // Stats scenario: a self-contained "order" object-type warehouse for POST /api/objects/stats
  // and the /api/objects/list DURATION_DESC sort -- independent of build() above (own object
  // type, own instances/tables), so it doesn't disturb any of that scenario's planted numbers.
  // -----------------------------------------------------------------------------------------

  public static final String STATS_OBJECT_TYPE = "order";
  public static final String STATS_PROCESS_A = "checkoutProcess";
  public static final String STATS_PROCESS_B = "expressCheckoutProcess";
  public static final String ORD_1 = "ORD-1";
  public static final String ORD_2 = "ORD-2";
  public static final String ORD_3 = "ORD-3";
  public static final String ORD_4 = "ORD-4";

  /**
   * Plants four {@code order} objects across two processes, a fanout distribution over {@code
   * object_relations} (children: 1, 2, 3, 3), and {@code object_lifecycle} rows for three of the
   * four objects (the fourth, {@link #ORD_4}, stays open -- no lifecycle row -- to exercise {@code
   * DURATION_DESC}'s {@code NULLS LAST}).
   *
   * <table>
   *   <caption>ground truth</caption>
   *   <tr><td>by process</td><td>{@code checkoutProcess}: {@link #ORD_1} (sighted twice, same
   *       object), {@link #ORD_2}, {@link #ORD_3} -&gt; 3 distinct objects; {@code
   *       expressCheckoutProcess}: {@link #ORD_4} -&gt; 1</td></tr>
   *   <tr><td>fanout</td><td>{@link #ORD_2} -&gt; 1 child, {@link #ORD_3} -&gt; 2 children, {@link
   *       #ORD_1} and {@link #ORD_4} -&gt; 3 children each</td></tr>
   *   <tr><td>lifecycle</td><td>{@link #ORD_2} 900_000ms/COMPLETED, {@link #ORD_1} 500_000ms/
   *       COMPLETED, {@link #ORD_3} 200_000ms/CANCELLED, {@link #ORD_4} open (no row)</td></tr>
   * </table>
   */
  public static void buildStatsScenario(final Path warehouseDir) {
    ParquetFixtures.writeTableFromQuery(
        warehouseDir,
        "activities",
        "SELECT * FROM (VALUES "
            + "(2001, '"
            + STATS_PROCESS_A
            + "', 1, 'default', 'Task', 'SERVICE_TASK', 9001, 'COMPLETED', "
            + "CAST(TIMESTAMP '2024-02-01 00:00:00' AS TIMESTAMPTZ), "
            + "CAST(TIMESTAMP '2024-02-01 00:01:00' AS TIMESTAMPTZ), 60000, "
            + "CAST(TIMESTAMP '2024-02-01 00:00:00' AS TIMESTAMPTZ), CAST(NULL AS BIGINT)), "
            + "(2002, '"
            + STATS_PROCESS_A
            + "', 1, 'default', 'Task', 'SERVICE_TASK', 9002, 'COMPLETED', "
            + "CAST(TIMESTAMP '2024-02-01 00:00:00' AS TIMESTAMPTZ), "
            + "CAST(TIMESTAMP '2024-02-01 00:01:00' AS TIMESTAMPTZ), 60000, "
            + "CAST(TIMESTAMP '2024-02-01 00:00:00' AS TIMESTAMPTZ), CAST(NULL AS BIGINT)), "
            + "(2003, '"
            + STATS_PROCESS_A
            + "', 1, 'default', 'Task', 'SERVICE_TASK', 9003, 'COMPLETED', "
            + "CAST(TIMESTAMP '2024-02-01 00:00:00' AS TIMESTAMPTZ), "
            + "CAST(TIMESTAMP '2024-02-01 00:01:00' AS TIMESTAMPTZ), 60000, "
            + "CAST(TIMESTAMP '2024-02-01 00:00:00' AS TIMESTAMPTZ), CAST(NULL AS BIGINT)), "
            + "(2004, '"
            + STATS_PROCESS_A
            + "', 1, 'default', 'Task', 'SERVICE_TASK', 9004, 'COMPLETED', "
            + "CAST(TIMESTAMP '2024-02-01 00:00:00' AS TIMESTAMPTZ), "
            + "CAST(TIMESTAMP '2024-02-01 00:01:00' AS TIMESTAMPTZ), 60000, "
            + "CAST(TIMESTAMP '2024-02-01 00:00:00' AS TIMESTAMPTZ), CAST(NULL AS BIGINT)), "
            + "(2005, '"
            + STATS_PROCESS_B
            + "', 1, 'default', 'Task', 'SERVICE_TASK', 9005, 'COMPLETED', "
            + "CAST(TIMESTAMP '2024-02-01 00:00:00' AS TIMESTAMPTZ), "
            + "CAST(TIMESTAMP '2024-02-01 00:01:00' AS TIMESTAMPTZ), 60000, "
            + "CAST(TIMESTAMP '2024-02-01 00:00:00' AS TIMESTAMPTZ), CAST(NULL AS BIGINT))"
            + ") AS t(instance_key, process_id, version, tenant_id, element_id, element_type, "
            + "element_key, state, started_at, ended_at, duration_ms, instance_started_at, "
            + "flow_scope_key)");

    ParquetFixtures.writeTableFromQuery(
        warehouseDir,
        "objects",
        "SELECT * FROM (VALUES "
            + "('"
            + STATS_OBJECT_TYPE
            + "', '"
            + ORD_1
            + "', 2001, '"
            + STATS_PROCESS_A
            + "', 1, CAST(NULL AS BIGINT), 'root', "
            + "CAST(TIMESTAMP '2024-02-01 00:00:00' AS TIMESTAMPTZ)), "
            + "('"
            + STATS_OBJECT_TYPE
            + "', '"
            + ORD_1
            + "', 2002, '"
            + STATS_PROCESS_A
            + "', 1, CAST(NULL AS BIGINT), 'root', "
            + "CAST(TIMESTAMP '2024-02-01 00:00:01' AS TIMESTAMPTZ)), "
            + "('"
            + STATS_OBJECT_TYPE
            + "', '"
            + ORD_2
            + "', 2003, '"
            + STATS_PROCESS_A
            + "', 1, CAST(NULL AS BIGINT), 'root', "
            + "CAST(TIMESTAMP '2024-02-01 00:00:02' AS TIMESTAMPTZ)), "
            + "('"
            + STATS_OBJECT_TYPE
            + "', '"
            + ORD_3
            + "', 2004, '"
            + STATS_PROCESS_A
            + "', 1, CAST(NULL AS BIGINT), 'root', "
            + "CAST(TIMESTAMP '2024-02-01 00:00:03' AS TIMESTAMPTZ)), "
            + "('"
            + STATS_OBJECT_TYPE
            + "', '"
            + ORD_4
            + "', 2005, '"
            + STATS_PROCESS_B
            + "', 1, CAST(NULL AS BIGINT), 'root', "
            + "CAST(TIMESTAMP '2024-02-01 00:00:04' AS TIMESTAMPTZ))"
            + ") AS t(object_type, object_id, instance_key, process_id, version, scope_key, "
            + "qualifier, first_seen)");

    ParquetFixtures.writeTableFromQuery(
        warehouseDir,
        "object_relations",
        "SELECT * FROM (VALUES "
            + "('"
            + STATS_OBJECT_TYPE
            + "', '"
            + ORD_1
            + "', 'lineItem', 'LI-1', CAST(TIMESTAMP '2024-02-01 00:00:00' AS TIMESTAMPTZ)), "
            + "('"
            + STATS_OBJECT_TYPE
            + "', '"
            + ORD_1
            + "', 'lineItem', 'LI-2', CAST(TIMESTAMP '2024-02-01 00:00:00' AS TIMESTAMPTZ)), "
            + "('"
            + STATS_OBJECT_TYPE
            + "', '"
            + ORD_1
            + "', 'lineItem', 'LI-3', CAST(TIMESTAMP '2024-02-01 00:00:00' AS TIMESTAMPTZ)), "
            + "('"
            + STATS_OBJECT_TYPE
            + "', '"
            + ORD_2
            + "', 'lineItem', 'LI-4', CAST(TIMESTAMP '2024-02-01 00:00:00' AS TIMESTAMPTZ)), "
            + "('"
            + STATS_OBJECT_TYPE
            + "', '"
            + ORD_3
            + "', 'lineItem', 'LI-5', CAST(TIMESTAMP '2024-02-01 00:00:00' AS TIMESTAMPTZ)), "
            + "('"
            + STATS_OBJECT_TYPE
            + "', '"
            + ORD_3
            + "', 'lineItem', 'LI-6', CAST(TIMESTAMP '2024-02-01 00:00:00' AS TIMESTAMPTZ)), "
            + "('"
            + STATS_OBJECT_TYPE
            + "', '"
            + ORD_4
            + "', 'lineItem', 'LI-7', CAST(TIMESTAMP '2024-02-01 00:00:00' AS TIMESTAMPTZ)), "
            + "('"
            + STATS_OBJECT_TYPE
            + "', '"
            + ORD_4
            + "', 'lineItem', 'LI-8', CAST(TIMESTAMP '2024-02-01 00:00:00' AS TIMESTAMPTZ)), "
            + "('"
            + STATS_OBJECT_TYPE
            + "', '"
            + ORD_4
            + "', 'lineItem', 'LI-9', CAST(TIMESTAMP '2024-02-01 00:00:00' AS TIMESTAMPTZ))"
            + ") AS t(parent_type, parent_id, child_type, child_id, first_seen)");

    ParquetFixtures.writeTableFromQuery(
        warehouseDir,
        "object_lifecycle",
        "SELECT * FROM (VALUES "
            + "('"
            + STATS_OBJECT_TYPE
            + "', '"
            + ORD_1
            + "', 'root', CAST(TIMESTAMP '2024-02-01 00:00:00' AS TIMESTAMPTZ), "
            + "CAST(TIMESTAMP '2024-02-01 00:08:20' AS TIMESTAMPTZ), 500000, 'COMPLETED', 2), "
            + "('"
            + STATS_OBJECT_TYPE
            + "', '"
            + ORD_2
            + "', 'root', CAST(TIMESTAMP '2024-02-01 00:00:02' AS TIMESTAMPTZ), "
            + "CAST(TIMESTAMP '2024-02-01 00:15:00' AS TIMESTAMPTZ), 900000, 'COMPLETED', 1), "
            + "('"
            + STATS_OBJECT_TYPE
            + "', '"
            + ORD_3
            + "', 'root', CAST(TIMESTAMP '2024-02-01 00:00:03' AS TIMESTAMPTZ), "
            + "CAST(TIMESTAMP '2024-02-01 00:03:20' AS TIMESTAMPTZ), 200000, 'CANCELLED', 1)"
            + ") AS t(object_type, object_id, birth_qualifier, birth_ts, closed_at, duration_ms, "
            + "outcome, n_sightings)");
  }

  // -----------------------------------------------------------------------------------------
  // Journey lane-cap scenario: a single convergence object sighted (at the root) by many more
  // instances than the JOURNEY_INSTANCE_CAP, for ObjectsService#journey's lane-capping tests.
  // Self-contained warehouse (own build*, not appended to build()'s or buildStatsScenario's
  // tables) -- see buildJourneyCapScenario's own Javadoc for why.
  // -----------------------------------------------------------------------------------------

  public static final String MANY_OBJECT_ID = "INV-MANY";
  public static final int MANY_INSTANCE_COUNT = 25;
  public static final long MANY_FIRST_INSTANCE_KEY = 70_001;

  /**
   * Plants {@link #MANY_INSTANCE_COUNT} instances (keys {@link #MANY_FIRST_INSTANCE_KEY} through
   * {@code + MANY_INSTANCE_COUNT - 1}), each sighting {@code invoice}/{@link #MANY_OBJECT_ID} once
   * at the root, with {@code first_seen} strictly increasing with the instance key -- so "most
   * recently active" and "highest instance_key" agree, letting a test assert exactly which
   * instances the {@code JOURNEY_INSTANCE_CAP} keeps (the {@link #MANY_INSTANCE_COUNT} - 20 highest
   * keys) versus drops (the 5 lowest). Each instance has exactly one activity, so activity counts
   * translate directly into instance counts.
   *
   * <p>Builds its own throwaway warehouse (own {@code objects}/{@code activities} tables, plus
   * empty-but-present {@code instance_links}/{@code object_relations} tables so {@link
   * io.camunda.analytics.lake.serving.objects.ObjectsService#journey} -- which unconditionally
   * queries all four -- doesn't fail on a missing view): calling this into the same warehouse
   * directory as {@link #build(Path)} would silently overwrite that scenario's planted {@code
   * objects}/{@code activities} data (both write to the same fixed {@code part-0} file per table),
   * which is exactly what "never change existing planted numbers" rules out.
   */
  public static void buildJourneyCapScenario(final Path warehouseDir) {
    ParquetFixtures.writeTableFromQuery(
        warehouseDir,
        "activities",
        "SELECT "
            + MANY_FIRST_INSTANCE_KEY
            + " + i AS instance_key, '"
            + PROCESS_ID
            + "' AS process_id, 1 AS version, 'default' AS tenant_id, 'Task' AS element_id, "
            + "'SERVICE_TASK' AS element_type, 900 AS element_key, 'COMPLETED' AS state, "
            + "CAST(TIMESTAMP '2024-04-01 00:00:00' AS TIMESTAMPTZ) + (i * INTERVAL 1 MINUTE) AS"
            + " started_at, CAST(TIMESTAMP '2024-04-01 00:00:00' AS TIMESTAMPTZ) + (i * INTERVAL 1"
            + " MINUTE) + INTERVAL 1 MINUTE AS ended_at, 60000 AS duration_ms, "
            + "CAST(TIMESTAMP '2024-04-01 00:00:00' AS TIMESTAMPTZ) + (i * INTERVAL 1 MINUTE) AS"
            + " instance_started_at, CAST(NULL AS BIGINT) AS flow_scope_key "
            + "FROM range(0, "
            + MANY_INSTANCE_COUNT
            + ") AS t(i)");

    ParquetFixtures.writeTableFromQuery(
        warehouseDir,
        "objects",
        "SELECT '"
            + OBJECT_TYPE
            + "' AS object_type, '"
            + MANY_OBJECT_ID
            + "' AS object_id, "
            + MANY_FIRST_INSTANCE_KEY
            + " + i AS instance_key, '"
            + PROCESS_ID
            + "' AS process_id, 1 AS version, CAST(NULL AS BIGINT) AS scope_key, 'root' AS"
            + " qualifier, CAST(TIMESTAMP '2024-04-01 00:00:00' AS TIMESTAMPTZ) + (i * INTERVAL 1"
            + " MINUTE) AS first_seen FROM range(0, "
            + MANY_INSTANCE_COUNT
            + ") AS t(i)");

    writeEmptyLinksAndRelations(warehouseDir);
  }

  // -----------------------------------------------------------------------------------------
  // Journey attribution edge cases: (a) one object sighted at the root in one instance and at a
  // scope in a different instance, exercising both branches of ObjectsService's single
  // attribution query together; (b) the same object sighted at the root AND at a scope of the
  // SAME instance, in both possible first-seen orders, exercising the first-seen-wins tie-break.
  // Self-contained warehouse, same reasoning as buildJourneyCapScenario above.
  // -----------------------------------------------------------------------------------------

  public static final long CROSS_ROOT_INSTANCE_KEY = 61_001;
  public static final long CROSS_SCOPE_INSTANCE_KEY = 61_002;
  public static final String CROSS_OBJECT_ID = "INV-CROSS";

  public static final long TIE_ROOT_FIRST_INSTANCE_KEY = 62_001;
  public static final String TIE_ROOT_FIRST_OBJECT_ID = "INV-TIE-ROOTFIRST";

  public static final long TIE_SCOPE_FIRST_INSTANCE_KEY = 62_002;
  public static final String TIE_SCOPE_FIRST_OBJECT_ID = "INV-TIE-SCOPEFIRST";

  /**
   * Plants three independent {@code invoice} objects, all resolved by {@link
   * io.camunda.analytics.lake.serving.objects.ObjectsService#journey} in a single attribution query
   * even though they exercise different branches of it:
   *
   * <table>
   *   <caption>ground truth</caption>
   *   <tr><td>{@link #CROSS_OBJECT_ID}</td><td>sighted at the root in {@link
   *       #CROSS_ROOT_INSTANCE_KEY} (one activity {@code R}) and at scope {@code P} (key 510) in
   *       {@link #CROSS_SCOPE_INSTANCE_KEY} (activities {@code P}, {@code Q} under it, and a
   *       sibling {@code Z} outside it) -- expect {@code R} ROOT, {@code P}+{@code Q} SCOPE,
   *       {@code Z} unattributed (not sighted, not in any subtree).</td></tr>
   *   <tr><td>{@link #TIE_ROOT_FIRST_OBJECT_ID}</td><td>one instance ({@link
   *       #TIE_ROOT_FIRST_INSTANCE_KEY}, activities {@code A}/{@code B}/{@code C} same shape as
   *       {@link #build}'s scenario) sighted at the root first, then at scope {@code B} a minute
   *       later -- expect the whole instance ROOT (the later scope sighting can't claim anything
   *       back).</td></tr>
   *   <tr><td>{@link #TIE_SCOPE_FIRST_OBJECT_ID}</td><td>the same {@code A}/{@code B}/{@code C}
   *       shape in {@link #TIE_SCOPE_FIRST_INSTANCE_KEY}, sighted at scope {@code B} first, then at
   *       the root a minute later -- expect {@code B}+{@code C} SCOPE (the earlier scope sighting)
   *       and {@code A} ROOT (only what the scope sighting didn't already claim).</td></tr>
   * </table>
   */
  public static void buildJourneyAttributionEdgeCasesScenario(final Path warehouseDir) {
    ParquetFixtures.writeTableFromQuery(
        warehouseDir,
        "activities",
        "SELECT * FROM (VALUES "
            + activityRow(CROSS_ROOT_INSTANCE_KEY, "R", "SERVICE_TASK", 500, null)
            + ", "
            + activityRow(CROSS_SCOPE_INSTANCE_KEY, "P", "SUB_PROCESS", 510, null)
            + ", "
            + activityRow(CROSS_SCOPE_INSTANCE_KEY, "Q", "SERVICE_TASK", 511, 510L)
            + ", "
            + activityRow(CROSS_SCOPE_INSTANCE_KEY, "Z", "SERVICE_TASK", 512, null)
            + ", "
            + activityRow(TIE_ROOT_FIRST_INSTANCE_KEY, "A", "SERVICE_TASK", 520, null)
            + ", "
            + activityRow(TIE_ROOT_FIRST_INSTANCE_KEY, "B", "SUB_PROCESS", 521, null)
            + ", "
            + activityRow(TIE_ROOT_FIRST_INSTANCE_KEY, "C", "SERVICE_TASK", 522, 521L)
            + ", "
            + activityRow(TIE_SCOPE_FIRST_INSTANCE_KEY, "A", "SERVICE_TASK", 530, null)
            + ", "
            + activityRow(TIE_SCOPE_FIRST_INSTANCE_KEY, "B", "SUB_PROCESS", 531, null)
            + ", "
            + activityRow(TIE_SCOPE_FIRST_INSTANCE_KEY, "C", "SERVICE_TASK", 532, 531L)
            + ") AS t(instance_key, process_id, version, tenant_id, element_id, element_type, "
            + "element_key, state, started_at, ended_at, duration_ms, instance_started_at, "
            + "flow_scope_key)");

    ParquetFixtures.writeTableFromQuery(
        warehouseDir,
        "objects",
        "SELECT * FROM (VALUES "
            + objectRow(CROSS_OBJECT_ID, CROSS_ROOT_INSTANCE_KEY, null, "2024-05-01 00:00:00")
            + ", "
            + objectRow(CROSS_OBJECT_ID, CROSS_SCOPE_INSTANCE_KEY, 510L, "2024-05-01 00:00:00")
            + ", "
            + objectRow(
                TIE_ROOT_FIRST_OBJECT_ID, TIE_ROOT_FIRST_INSTANCE_KEY, null, "2024-05-01 00:00:00")
            + ", "
            + objectRow(
                TIE_ROOT_FIRST_OBJECT_ID, TIE_ROOT_FIRST_INSTANCE_KEY, 521L, "2024-05-01 00:01:00")
            + ", "
            + objectRow(
                TIE_SCOPE_FIRST_OBJECT_ID,
                TIE_SCOPE_FIRST_INSTANCE_KEY,
                531L,
                "2024-05-01 00:00:00")
            + ", "
            + objectRow(
                TIE_SCOPE_FIRST_OBJECT_ID,
                TIE_SCOPE_FIRST_INSTANCE_KEY,
                null,
                "2024-05-01 00:01:00")
            + ") AS t(object_type, object_id, instance_key, process_id, version, scope_key, "
            + "qualifier, first_seen)");

    writeEmptyLinksAndRelations(warehouseDir);
  }

  private static String activityRow(
      final long instanceKey,
      final String elementId,
      final String elementType,
      final long elementKey,
      final Long flowScopeKey) {
    return "("
        + instanceKey
        + ", '"
        + PROCESS_ID
        + "', 1, 'default', '"
        + elementId
        + "', '"
        + elementType
        + "', "
        + elementKey
        + ", 'COMPLETED', "
        + "CAST(TIMESTAMP '2024-05-01 00:00:00' AS TIMESTAMPTZ), "
        + "CAST(TIMESTAMP '2024-05-01 00:01:00' AS TIMESTAMPTZ), 60000, "
        + "CAST(TIMESTAMP '2024-05-01 00:00:00' AS TIMESTAMPTZ), "
        + (flowScopeKey == null ? "CAST(NULL AS BIGINT)" : flowScopeKey)
        + ")";
  }

  private static String objectRow(
      final String objectId, final long instanceKey, final Long scopeKey, final String firstSeen) {
    return "('"
        + OBJECT_TYPE
        + "', '"
        + objectId
        + "', "
        + instanceKey
        + ", '"
        + PROCESS_ID
        + "', 1, "
        + (scopeKey == null ? "CAST(NULL AS BIGINT)" : scopeKey)
        + ", 'root', CAST(TIMESTAMP '"
        + firstSeen
        + "' AS TIMESTAMPTZ))";
  }

  /**
   * {@code instance_links}/{@code object_relations} present but empty -- {@link
   * io.camunda.analytics.lake.serving.objects.ObjectsService#journey} queries both unconditionally,
   * so a warehouse that never writes them at all would fail the view-registry lookup rather than
   * simply returning no links/relations.
   */
  private static void writeEmptyLinksAndRelations(final Path warehouseDir) {
    ParquetFixtures.writeTableFromQuery(
        warehouseDir,
        "instance_links",
        "SELECT CAST(NULL AS BIGINT) AS parent_instance_key, "
            + "CAST(NULL AS BIGINT) AS child_instance_key, "
            + "CAST(NULL AS VARCHAR) AS link_type, "
            + "CAST(NULL AS BIGINT) AS via_element_instance_key, "
            + "CAST(NULL AS TIMESTAMPTZ) AS linked_at WHERE FALSE");

    ParquetFixtures.writeTableFromQuery(
        warehouseDir,
        "object_relations",
        "SELECT CAST(NULL AS VARCHAR) AS parent_type, "
            + "CAST(NULL AS VARCHAR) AS parent_id, "
            + "CAST(NULL AS VARCHAR) AS child_type, "
            + "CAST(NULL AS VARCHAR) AS child_id, "
            + "CAST(NULL AS TIMESTAMPTZ) AS first_seen WHERE FALSE");
  }
}
