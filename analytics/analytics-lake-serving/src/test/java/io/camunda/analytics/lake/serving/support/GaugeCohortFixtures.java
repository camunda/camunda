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
 * Planted scenarios for {@link io.camunda.analytics.lake.serving.tools.GaugeSeriesService} and
 * {@link io.camunda.analytics.lake.serving.tools.CohortShareService}: a raw {@code
 * open_instances_gauge} sample table (no metrics-registry entity behind it) and a hist-only {@code
 * instance_cohorts_hist} table (no {@code instance_cohorts_metrics} sibling -- exercising {@link
 * io.camunda.analytics.lake.serving.catalog.MetricRegistry}'s hist-only entity-derivation path).
 *
 * <table>
 *   <caption>gauge ground truth ({@code [FROM, TO)}, one 60-minute bucket)</caption>
 *   <tr><td>{@code orderProcess} samples (every 10 min)</td><td>10, 20, 10, 20, 10, 20 -> avg 15.0</td></tr>
 *   <tr><td>{@code refundProcess} samples (every 10 min)</td><td>5, 5, 5, 5, 5, 5 -> constant 5</td></tr>
 *   <tr><td>unfiltered (every process summed per instant, then bucket-averaged)</td>
 *       <td>15, 25, 15, 25, 15, 25 -> avg 20.0</td></tr>
 * </table>
 *
 * <table>
 *   <caption>cohort-share ground truth (one bin scheme, one window bucket, {@code duration_ms}
 *   in milliseconds)</caption>
 *   <tr><td>bin [0, 1_000_000)</td><td>cnt 40 -- within both 1h (3_600_000) and 1d (86_400_000)</td></tr>
 *   <tr><td>bin [1_000_000, 5_000_000)</td><td>cnt 30 -- within 1d only</td></tr>
 *   <tr><td>bin [5_000_000, 100_000_000)</td><td>cnt 30 -- within neither</td></tr>
 * </table>
 *
 * <p>total = 100 -&gt; share within 1h = 40/100 = 0.4; share within 1d = (40+30)/100 = 0.7.
 */
public final class GaugeCohortFixtures {

  public static final String ORDER_PROCESS = "orderProcess";
  public static final String REFUND_PROCESS = "refundProcess";
  public static final String FROM = "2024-01-01T00:00:00Z";
  public static final String TO = "2024-01-01T01:00:00Z";
  public static final double ORDER_PROCESS_AVG = 15.0;
  public static final double REFUND_PROCESS_AVG = 5.0;
  public static final double UNFILTERED_AVG = 20.0;
  public static final long WITHIN_1H_MS = 3_600_000L;
  public static final long WITHIN_1D_MS = 86_400_000L;
  public static final double SHARE_WITHIN_1H = 0.4;
  public static final double SHARE_WITHIN_1D = 0.7;

  private GaugeCohortFixtures() {}

  public static void buildGauge(final Path warehouseDir) {
    ParquetFixtures.writeTableFromQuery(
        warehouseDir,
        "open_instances_gauge",
        "SELECT "
            + "CAST(TIMESTAMP '2024-01-01 00:00:00' + (INTERVAL '10 minutes' * i) AS TIMESTAMPTZ) AS sampled_at, "
            + "'"
            + ORDER_PROCESS
            + "' AS process_id, "
            + "(CASE WHEN i % 2 = 0 THEN 10 ELSE 20 END) AS open_instances "
            + "FROM range(6) t(i) "
            + "UNION ALL SELECT "
            + "CAST(TIMESTAMP '2024-01-01 00:00:00' + (INTERVAL '10 minutes' * i) AS TIMESTAMPTZ), "
            + "'"
            + REFUND_PROCESS
            + "', 5 FROM range(6) t(i)");
  }

  public static void buildCohortHist(final Path warehouseDir) {
    ParquetFixtures.writeTableFromQuery(
        warehouseDir,
        "instance_cohorts_hist",
        "SELECT "
            + "CAST(TIMESTAMP '2024-01-01 00:00:00' AS TIMESTAMPTZ) AS window_start, "
            + "'"
            + ORDER_PROCESS
            + "' AS process_id, 'duration_ms' AS measure, 'exp2ll-3' AS scheme, "
            + "CAST(bin_lo AS BIGINT) AS bin_lo, CAST(bin_hi AS BIGINT) AS bin_hi, CAST(cnt AS BIGINT) AS cnt "
            + "FROM (VALUES (0, 1000000, 40), (1000000, 5000000, 30), (5000000, 100000000, 30)) "
            + "AS b(bin_lo, bin_hi, cnt)");
  }
}
