/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.webapp;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Seeds the serving tables with representative demo data so the dashboard renders without a running
 * pipeline. Enabled only with {@code -Danalytics.seed=true}; it is a no-op if data already exists,
 * so it never touches a live dataset. Writes a few process definitions across several hourly
 * windows for one tenant, mirroring exactly the columns the pipeline's sinks write.
 */
@Component
@ConditionalOnProperty(name = "analytics.seed", havingValue = "true")
public class DemoSeeder implements CommandLineRunner {

  private static final Logger LOG = LoggerFactory.getLogger(DemoSeeder.class);
  private static final long HOUR = 3_600_000L;
  private static final long WINDOWS = 12; // last 12 hourly windows
  private static final long BASE = 1_750_000_000_000L; // fixed start (deterministic demo)
  private static final long SLA_MS = 300_000L;
  private static final String TENANT = "<default>";

  private static final List<Def> DEFS =
      List.of(
          new Def("order-process", 2251799813685249L, 1, 45_000L, 6),
          new Def("payment-process", 2251799813685252L, 2, 120_000L, 4),
          new Def("shipping-process", 2251799813685255L, 1, 600_000L, 3));

  private final JdbcTemplate jdbc;

  public DemoSeeder(final JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void run(final String... args) {
    final Long existing =
        jdbc.queryForObject("SELECT COUNT(*) FROM proc_inst_duration_pctl_window", Long.class);
    if (existing != null && existing > 0) {
      LOG.info(
          "Demo seed skipped: serving tables already have data ({} percentile rows)", existing);
      return;
    }
    LOG.info("Seeding demo analytics data ({} definitions x {} windows)", DEFS.size(), WINDOWS);
    for (final Def def : DEFS) {
      for (long w = 0; w < WINDOWS; w++) {
        seedWindow(def, BASE + w * HOUR, w);
      }
      seedElements(def);
    }
    seedTopProcesses(BASE + (WINDOWS - 1) * HOUR);
  }

  private void seedWindow(final Def def, final long windowStart, final long w) {
    // a gentle diurnal wobble so the trend charts have shape
    final double wobble = 1.0 + 0.25 * Math.sin(w / 2.0);
    final long count = 40 + (def.spread() * 5) + (w % 5) * 7;
    final long p50 = Math.round(def.baseP50() * wobble);
    final long p75 = Math.round(p50 * 1.4);
    final long p90 = Math.round(p50 * 1.9);
    final long p99 = Math.round(p50 * 3.1);
    final long min = Math.round(def.baseP50() * 0.3);
    final long max = Math.round(p99 * 1.2);

    jdbc.update(
        "INSERT INTO proc_inst_duration_pctl_window (bpmn_process_id, process_definition_key,"
            + " version, tenant_id, window_start, window_size_ms, observation_count, min_duration_ms,"
            + " max_duration_ms, p50_duration_ms, p75_duration_ms, p90_duration_ms, p99_duration_ms)"
            + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
        def.bpmnProcessId(),
        def.key(),
        def.version(),
        TENANT,
        windowStart,
        HOUR,
        count,
        min,
        max,
        p50,
        p75,
        p90,
        p99);

    // exec-time (avg/min/max) for the same cells — total chosen so avg ≈ p50 * 1.1
    final long total = Math.round(count * p50 * 1.1);
    jdbc.update(
        "INSERT INTO proc_inst_exec_time_window (dataset_id, region, process_definition_key,"
            + " bpmn_process_id, version, tenant_id, window_start, window_size_ms, completed_count,"
            + " total_duration_ms, min_duration_ms, max_duration_ms) VALUES (1,?,?,?,?,?,?,?,?,?,?,?)",
        "EU",
        def.key(),
        def.bpmnProcessId(),
        def.version(),
        TENANT,
        windowStart,
        HOUR,
        count,
        total,
        min,
        max);

    // SLA-met + no-incident percentages, matched/total (additive)
    final long slaMatched = Math.round(count * (p50 <= SLA_MS ? 0.9 : 0.55) - (w % 3));
    final long noIncidentMatched = count - (w % 4) - def.spread();
    insertRatio(def, windowStart, "sla_met", Math.max(0, slaMatched), count);
    insertRatio(def, windowStart, "no_incident", Math.max(0, noIncidentMatched), count);

    // distinct processes active per tenant (same for the whole tenant per window)
    if (def == DEFS.get(0)) {
      final long distinct = DEFS.size();
      jdbc.update(
          "INSERT INTO proc_distinct_window (tenant_id, window_start, window_size_ms,"
              + " distinct_estimate, distinct_lower, distinct_upper) VALUES (?,?,?,?,?,?)",
          TENANT,
          windowStart,
          HOUR,
          distinct,
          distinct,
          distinct);
    }
  }

  private void insertRatio(
      final Def def,
      final long windowStart,
      final String metric,
      final long matched,
      final long total) {
    final double ratio = total == 0 ? 0.0 : (double) matched / total;
    jdbc.update(
        "INSERT INTO proc_ratio_window (bpmn_process_id, process_definition_key, version, tenant_id,"
            + " window_start, window_size_ms, metric, matched_count, total_count, ratio)"
            + " VALUES (?,?,?,?,?,?,?,?,?,?)",
        def.bpmnProcessId(),
        def.key(),
        def.version(),
        TENANT,
        windowStart,
        HOUR,
        metric,
        matched,
        total,
        ratio);
  }

  private void seedElements(final Def def) {
    final String[][] elements = {
      {"StartEvent_1", "START_EVENT"},
      {"Task_Validate", "SERVICE_TASK"},
      {"Task_Approve", "USER_TASK"},
      {"Gateway_Check", "EXCLUSIVE_GATEWAY"},
      {"EndEvent_1", "END_EVENT"},
    };
    final long windowStart = BASE + (WINDOWS - 1) * HOUR;
    long i = 0;
    for (final String[] el : elements) {
      final long executed = 300 - i * 40 + def.spread() * 10;
      final long p50 = def.baseP50() / 5 + i * 3_000L;
      final long p90 = p50 * 2;
      final long max = p90 * 2;
      final long total = Math.round(executed * p50 * 1.2);
      jdbc.update(
          "INSERT INTO element_execution_window (bpmn_process_id, process_definition_key, version,"
              + " tenant_id, element_id, element_type, window_start, window_size_ms, executed_count,"
              + " total_duration_ms, min_duration_ms, max_duration_ms) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
          def.bpmnProcessId(),
          def.key(),
          def.version(),
          TENANT,
          el[0],
          el[1],
          windowStart,
          HOUR,
          executed,
          total,
          p50 / 2,
          max);
      jdbc.update(
          "INSERT INTO element_duration_pctl_window (bpmn_process_id, process_definition_key,"
              + " version, tenant_id, element_id, element_type, window_start, window_size_ms,"
              + " observation_count, min_duration_ms, max_duration_ms, p50_duration_ms,"
              + " p75_duration_ms, p90_duration_ms, p99_duration_ms) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
          def.bpmnProcessId(),
          def.key(),
          def.version(),
          TENANT,
          el[0],
          el[1],
          windowStart,
          HOUR,
          executed,
          p50 / 2,
          max,
          p50,
          Math.round(p50 * 1.4),
          p90,
          Math.round(p90 * 1.6));
      i++;
    }
  }

  private void seedTopProcesses(final long windowStart) {
    int rank = 1;
    // busiest first — order > payment > shipping
    final long[] volumes = {1800, 950, 320};
    for (final Def def : DEFS) {
      final long est = volumes[rank - 1];
      jdbc.update(
          "INSERT INTO top_processes_window (tenant_id, window_start, window_size_ms, rank,"
              + " bpmn_process_id, estimate, lower_bound, upper_bound) VALUES (?,?,?,?,?,?,?,?)",
          TENANT,
          windowStart,
          HOUR,
          rank,
          def.bpmnProcessId(),
          est,
          est,
          est);
      rank++;
    }
  }

  private record Def(String bpmnProcessId, long key, int version, long baseP50, long spread) {}
}
