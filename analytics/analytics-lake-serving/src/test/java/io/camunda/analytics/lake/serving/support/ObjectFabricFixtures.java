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
}
