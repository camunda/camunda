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
 * A shared planted-step scenario reused by the series/changepoint/decompose/screen/investigate
 * tests: 40 one-minute windows over {@code [2024-01-01T00:00Z, 2024-01-01T00:40Z)}, with a clean
 * step change at window index 20 ({@code 2024-01-01T00:20:00Z}).
 *
 * <table>
 *   <caption>ground truth</caption>
 *   <tr><td>{@code instances_metrics}/{@code _hist}, dim {@code process_id = 'orderProcess'}</td>
 *       <td>{@code duration_ms} avg 1000 (windows 0-19) steps to 3000 (windows 20-39)</td></tr>
 *   <tr><td>{@code instances_metrics}, dim {@code process_id = 'refundProcess'}</td>
 *       <td>{@code duration_ms} avg flat at 1500 the whole time (the non-driver dim value)</td></tr>
 *   <tr><td>{@code activities_metrics}, dim {@code process_id = 'orderProcess'}</td>
 *       <td>{@code duration_ms} avg 500 steps to 1500 at the same window 20 (a correlated
 *       candidate for {@code screen}), across two {@code element_id} values (task1/task2) that
 *       {@code screen}'s process_id-only filter must aggregate over correctly</td></tr>
 * </table>
 *
 * <p>{@code instances_hist} carries one bin per window covering the same avg exactly (midpoint
 * 1000/3000), so a {@code quantile} query resolves to precisely the same numbers as the plain
 * average -- deterministic either way.
 */
public final class StepScenarioFixtures {

  public static final String FROM = "2024-01-01T00:00:00Z";
  public static final String TO = "2024-01-01T00:40:00Z";
  public static final String STEP_AT = "2024-01-01T00:20:00Z";
  public static final String ORDER_PROCESS = "orderProcess";
  public static final String REFUND_PROCESS = "refundProcess";
  public static final double BEFORE_AVG = 1000.0;
  public static final double AFTER_AVG = 3000.0;

  private StepScenarioFixtures() {}

  public static void build(final Path warehouseDir) {
    ParquetFixtures.writeTableFromQuery(
        warehouseDir,
        "instances_metrics",
        "SELECT "
            + "CAST(TIMESTAMP '2024-01-01 00:00:00' + (INTERVAL '1 minute' * i) AS TIMESTAMPTZ) AS window_start, "
            + "'"
            + ORDER_PROCESS
            + "' AS process_id, "
            + "10 AS duration_ms_cnt, "
            + "CAST((CASE WHEN i < 20 THEN 1000 ELSE 3000 END) * 10 AS BIGINT) AS duration_ms_sum, "
            + "CAST((CASE WHEN i < 20 THEN 1000 ELSE 3000 END) AS BIGINT) AS duration_ms_min, "
            + "CAST((CASE WHEN i < 20 THEN 1000 ELSE 3000 END) AS BIGINT) AS duration_ms_max "
            + "FROM range(40) t(i) "
            + "UNION ALL SELECT "
            + "CAST(TIMESTAMP '2024-01-01 00:00:00' + (INTERVAL '1 minute' * i) AS TIMESTAMPTZ), "
            + "'"
            + REFUND_PROCESS
            + "', 5, 7500, 1500, 1500 FROM range(40) t(i)");

    ParquetFixtures.writeTableFromQuery(
        warehouseDir,
        "instances_hist",
        "SELECT "
            + "CAST(TIMESTAMP '2024-01-01 00:00:00' + (INTERVAL '1 minute' * i) AS TIMESTAMPTZ) AS window_start, "
            + "'"
            + ORDER_PROCESS
            + "' AS process_id, 'duration_ms' AS measure, 'exp2ll-3' AS scheme, "
            + "CAST((CASE WHEN i < 20 THEN 900 ELSE 2900 END) AS BIGINT) AS bin_lo, "
            + "CAST((CASE WHEN i < 20 THEN 1100 ELSE 3100 END) AS BIGINT) AS bin_hi, "
            + "10 AS cnt "
            + "FROM range(40) t(i)");

    ParquetFixtures.writeTableFromQuery(
        warehouseDir,
        "activities_metrics",
        "SELECT "
            + "CAST(TIMESTAMP '2024-01-01 00:00:00' + (INTERVAL '1 minute' * i) AS TIMESTAMPTZ) AS window_start, "
            + "'"
            + ORDER_PROCESS
            + "' AS process_id, element AS element_id, "
            + "4 AS duration_ms_cnt, "
            + "CAST((CASE WHEN i < 20 THEN 500 ELSE 1500 END) * 4 AS BIGINT) AS duration_ms_sum, "
            + "CAST((CASE WHEN i < 20 THEN 500 ELSE 1500 END) AS BIGINT) AS duration_ms_min, "
            + "CAST((CASE WHEN i < 20 THEN 500 ELSE 1500 END) AS BIGINT) AS duration_ms_max "
            + "FROM range(40) t(i), (SELECT unnest(['task1', 'task2']) AS element) e");

    // A count()-only entity (matching the real `instance_starts` shape: no measures, just a bare
    // `cnt`), for exercising the series/screen "measure == null" default path.
    ParquetFixtures.writeTableFromQuery(
        warehouseDir,
        "instance_starts_metrics",
        "SELECT "
            + "CAST(TIMESTAMP '2024-01-01 00:00:00' + (INTERVAL '1 minute' * i) AS TIMESTAMPTZ) AS window_start, "
            + "'"
            + ORDER_PROCESS
            + "' AS process_id, "
            + "10 AS cnt "
            + "FROM range(40) t(i)");
  }
}
