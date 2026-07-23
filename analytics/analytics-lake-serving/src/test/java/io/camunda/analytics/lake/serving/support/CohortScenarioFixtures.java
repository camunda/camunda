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
 * A planted {@code instances} raw-table scenario for {@code cohort-compare}/{@code
 * exemplars}/{@code conditions}: 80 instances of process {@code orderProcess} over {@code
 * variant_hash} {@code V1}/{@code V2}, {@code duration_ms} correlated with variant so a {@code
 * THRESHOLD} split at 2000ms produces a known, hand-computed lift for each bucket.
 *
 * <table>
 *   <caption>ground truth (threshold duration_ms &gt; 2000 = "slow")</caption>
 *   <tr><td>V1 (40 instances)</td><td>35 fast (500ms), 5 slow (5000ms)</td></tr>
 *   <tr><td>V2 (40 instances)</td><td>5 fast (500ms), 35 slow (5000ms)</td></tr>
 * </table>
 *
 * <p>totalSlow = 40, totalFast = 40 (across both buckets) -&gt; V1: slowShare 0.125, fastShare
 * 0.875, lift 1/7 (&lt;1, "protective"); V2: slowShare 0.875, fastShare 0.125, lift 7 (&gt;1,
 * "risk"). With the default {@code supportFloor} (20), only V2 (slowN=35) survives; passing {@code
 * supportFloor=5} keeps both (V1's slowN=5).
 */
public final class CohortScenarioFixtures {

  public static final String PROCESS_ID = "orderProcess";
  public static final String FROM = "2024-01-01T00:00:00Z";
  public static final String TO = "2024-01-01T01:00:00Z";
  public static final double THRESHOLD = 2000.0;
  public static final long FAST_DURATION = 500;
  public static final long SLOW_DURATION = 5000;

  private CohortScenarioFixtures() {}

  public static void build(final Path warehouseDir) {
    ParquetFixtures.writeTableFromQuery(
        warehouseDir,
        "instances",
        "SELECT "
            + "1000 + i AS key, 500 AS process_definition_key, '"
            + PROCESS_ID
            + "' AS process_id, 1 AS version, 'default' AS tenant_id, 'COMPLETED' AS state, "
            + "CAST(TIMESTAMP '2024-01-01 00:10:00' AS TIMESTAMPTZ) AS started_at, "
            + "CAST(TIMESTAMP '2024-01-01 00:20:00' AS TIMESTAMPTZ) AS ended_at, "
            + "duration AS duration_ms, "
            + "CAST('{\"amount\": ' || (duration / 10) || '}' AS BLOB) AS vars_json, "
            + "variant AS variant_hash "
            + "FROM ("
            + "  SELECT i, 'V1' AS variant, (CASE WHEN i < 35 THEN "
            + FAST_DURATION
            + " ELSE "
            + SLOW_DURATION
            + " END) AS duration FROM range(40) t(i)"
            + "  UNION ALL"
            + "  SELECT i, 'V2' AS variant, (CASE WHEN i < 5 THEN "
            + FAST_DURATION
            + " ELSE "
            + SLOW_DURATION
            + " END) AS duration FROM range(40) t(i)"
            + ")");

    ParquetFixtures.writeTableFromQuery(
        warehouseDir,
        "variants",
        "SELECT '"
            + PROCESS_ID
            + "' AS process_id, 1 AS version, 'V1' AS variant_hash, "
            + "CAST('task1\ntask2' AS BLOB) AS elements, CAST('flow1' AS BLOB) AS flows, "
            + "CAST(TIMESTAMP '2024-01-01 00:20:00' AS TIMESTAMPTZ) AS first_seen "
            + "UNION ALL SELECT '"
            + PROCESS_ID
            + "', 1, 'V2', CAST('task1\ntask3' AS BLOB), CAST('flow2' AS BLOB), "
            + "CAST(TIMESTAMP '2024-01-01 00:20:00' AS TIMESTAMPTZ)");
  }
}
