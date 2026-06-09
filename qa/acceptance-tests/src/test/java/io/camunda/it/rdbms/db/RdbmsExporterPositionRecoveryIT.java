/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db;

import static io.camunda.it.util.TestHelper.deployResource;
import static io.camunda.it.util.TestHelper.startProcessInstance;
import static io.camunda.it.util.TestHelper.waitForProcessesToBeDeployed;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.Iterables;
import io.camunda.client.CamundaClient;
import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import io.camunda.qa.util.cluster.TestCamundaApplication;
import io.camunda.zeebe.broker.exporter.stream.ExporterMetricsDoc;
import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.MeterRegistry;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.TestMethodOrder;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Integration test verifying that the RDBMS exporter recovers automatically when the
 * EXPORTER_POSITION table is tampered to simulate a diverged position.
 *
 * <p>Two scenarios are covered (run in order against a shared Camunda instance):
 *
 * <ol>
 *   <li><b>DB position ahead of broker</b> – simulates another exporter instance writing ahead (the
 *       original bug in issue #52460). The mismatch causes a reopen; the exporter re-syncs to the
 *       DB position and continues.
 *   <li><b>DB position behind broker</b> – simulates a DB rollback or manual admin tamper that left
 *       the position counter lower than expected. The mismatch triggers the same reopen path; on
 *       reopen the exporter requests a replay from the DB position. If log segments are available
 *       the replay succeeds; if not, the exporter fails hard. This test is disabled because the
 *       acceptance-test setup rejects the replay request.
 * </ol>
 */
@Tag("rdbms")
@TestInstance(Lifecycle.PER_CLASS)
@TestMethodOrder(OrderAnnotation.class)
class RdbmsExporterPositionRecoveryIT {

  private static final String POSTGRES_IMAGE = "postgres:16-alpine";
  private static final String DATABASE_NAME = "camunda";
  private static final String DATABASE_USER = "camunda";
  private static final String DATABASE_PASSWORD = "camunda";

  @AutoClose
  private final PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>(POSTGRES_IMAGE)
          .withDatabaseName(DATABASE_NAME)
          .withUsername(DATABASE_USER)
          .withPassword(DATABASE_PASSWORD)
          .withStartupTimeout(Duration.ofMinutes(5));

  @AutoClose private TestCamundaApplication testInstance;
  @AutoClose private CamundaClient camundaClient;

  private MeterRegistry meterRegistry;

  @BeforeAll
  void beforeAll() {
    postgres.start();

    testInstance =
        new TestCamundaApplication()
            .withSecondaryStorageType(SecondaryStorageType.rdbms)
            .withProperty("camunda.data.secondary-storage.rdbms.url", postgres.getJdbcUrl())
            .withProperty("camunda.data.secondary-storage.rdbms.username", postgres.getUsername())
            .withProperty("camunda.data.secondary-storage.rdbms.password", postgres.getPassword())
            .withExporter(
                "rdbms",
                cfg -> {
                  cfg.setClassName("io.camunda.db.rdbms.exporter.RdbmsExporter");
                  cfg.setArgs(Map.of("flushInterval", "PT0S"));
                })
            .withBasicAuth();

    testInstance.start();

    camundaClient = testInstance.newClientBuilder().build();
    meterRegistry = testInstance.bean(MeterRegistry.class);

    Objects.requireNonNull(meterRegistry);
    Objects.requireNonNull(camundaClient);

    deployResource(camundaClient, "process/service_tasks_v1.bpmn");
    waitForProcessesToBeDeployed(camundaClient, 1);

    // Let the exporter fully catch up before we tamper
    awaitExporterPositionStabilises();
  }

  @Test
  @Order(1)
  void shouldRecoverAfterPositionMismatchAndContinueExporting() throws Exception {
    // given — note the current DB position before tamper
    final long positionBeforeTamper = getCurrentDbExporterPosition();
    assertThat(positionBeforeTamper).isGreaterThan(0);

    // Advance the DB position ahead to simulate another exporter instance writing ahead.
    // A small offset (+50) is used so that the 30 new process instances (~300 records) push
    // the broker position past the tampered baseline after recovery.
    final long tamperedPosition = positionBeforeTamper + 50L;
    setDbExporterPosition(tamperedPosition);

    assertThat(getCurrentDbExporterPosition()).isEqualTo(tamperedPosition);

    // when — generate traffic so the exporter tries to flush and detects the mismatch
    for (int i = 0; i < 30; i++) {
      startProcessInstance(camundaClient, "service_tasks_v1", "{\"xyz\":\"foo\"}");
    }

    // then — the exporter reopens, re-syncs from DB position, and eventually the DB position
    // advances past the tampered value as new records (with positions > tamperedPosition) arrive
    Awaitility.await("exporter recovers and advances position past tampered value")
        .atMost(Duration.ofMinutes(3))
        .untilAsserted(
            () ->
                assertThat(getCurrentDbExporterPosition())
                    .as(
                        "DB exporter position must eventually exceed the tampered value, "
                            + "proving the exporter recovered and continued exporting")
                    .isGreaterThan(tamperedPosition));
  }

  /**
   * Scenario 2: RDBMS position is set to a value smaller than the broker's acknowledged position.
   *
   * <p>The in-transaction row-lock hook detects the divergence (DB &lt; {@code
   * lastFlushedPosition}) and raises a {@link
   * io.camunda.db.rdbms.write.queue.PositionMismatchException}. On reopen the exporter finds DB
   * &lt; broker and attempts a replay; but in this test environment the exporter is already in the
   * exporting phase and replay is rejected. The exporter then fails hard with an {@link
   * io.camunda.zeebe.exporter.api.ExporterException} rather than silently accepting a stale
   * baseline (which would risk data loss). This scenario requires log segments to be available in
   * production for automatic recovery.
   *
   * <p>Disabled: replay is not available while the exporter is in the exporting phase; the
   * hard-fail path cannot be fully verified in the acceptance-test setup. A manual integration test
   * with log-segment availability is required to cover the replay-succeeds path.
   */
  @Test
  @Order(2)
  @Disabled(
      "Replay is not available while the exporter is in the exporting phase; "
          + "this scenario requires log segments that are not present in this test setup")
  void shouldRecoverWhenRdbmsPositionIsLowerThanBrokerPosition() throws Exception {
    // given — wait until the position has stabilised after the previous test
    Awaitility.await("position stabilises after previous test")
        .atMost(Duration.ofMinutes(2))
        .until(
            () -> {
              final long acknowledged = getCurrentAcknowledgedExporterPosition();
              final long db = getCurrentDbExporterPosition();
              // both sides agree and are advancing (non-trivially ahead of 0)
              return acknowledged > 0 && Math.abs(acknowledged - db) <= 10;
            });

    final long positionBeforeTamper = getCurrentDbExporterPosition();
    assertThat(positionBeforeTamper).isGreaterThan(0);

    // Set the DB position below the broker's acknowledged position to simulate a DB rollback or
    // an admin mistake that left the position counter behind the actual data state.
    final long tamperedPosition = positionBeforeTamper - 5;
    setDbExporterPosition(tamperedPosition);

    assertThat(getCurrentDbExporterPosition()).isEqualTo(tamperedPosition);

    // when — generate traffic so the exporter flushes and detects the mismatch
    for (int i = 0; i < 20; i++) {
      startProcessInstance(camundaClient, "service_tasks_v1", "{\"xyz\":\"foo\"}");
    }

    // then — the exporter reopens, accepts the DB position as the new baseline, and eventually
    // advances the DB position past the pre-tamper value (proving it did not get stuck)
    Awaitility.await("exporter recovers from lower-than-expected DB position")
        .atMost(Duration.ofMinutes(3))
        .untilAsserted(
            () ->
                assertThat(getCurrentDbExporterPosition())
                    .as(
                        "DB exporter position must eventually exceed the pre-tamper value, "
                            + "proving the exporter recovered and continued exporting after the gap")
                    .isGreaterThan(positionBeforeTamper));
  }

  // -----------------------------------------------------------------------
  // helpers
  // -----------------------------------------------------------------------

  private void awaitExporterPositionStabilises() {
    Awaitility.await("initial export stabilises")
        .atMost(Duration.ofMinutes(1))
        .until(
            () -> {
              final long pos = getCurrentAcknowledgedExporterPosition();
              return pos > 0;
            });
  }

  private long getCurrentAcknowledgedExporterPosition() {
    return getMeterLong(ExporterMetricsDoc.LAST_UPDATED_EXPORTED_POSITION.getName());
  }

  private long getMeterLong(final String name) {
    try {
      final var meters =
          meterRegistry.get(name).tag("exporter", "rdbms").tag("partition", "1").meter().measure();
      final Measurement first = Iterables.getFirst(meters, null);
      return first == null ? 0L : (long) first.getValue();
    } catch (final Exception e) {
      return 0L;
    }
  }

  /**
   * Opens a direct, auto-commit JDBC connection to the Postgres container. Using DriverManager
   * bypasses the HikariCP pool (which is configured with {@code autoCommit=false}), so every
   * statement is immediately visible to the exporter without an explicit {@code commit()} call.
   */
  private Connection directPostgresConnection() throws SQLException {
    return DriverManager.getConnection(
        postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
  }

  private long getCurrentDbExporterPosition() throws Exception {
    try (final Connection conn = directPostgresConnection();
        final PreparedStatement ps =
            conn.prepareStatement(
                "SELECT LAST_EXPORTED_POSITION FROM EXPORTER_POSITION WHERE PARTITION_ID = 1")) {
      try (final ResultSet rs = ps.executeQuery()) {
        return rs.next() ? rs.getLong(1) : 0L;
      }
    }
  }

  private void setDbExporterPosition(final long position) throws Exception {
    try (final Connection conn = directPostgresConnection();
        final PreparedStatement ps =
            conn.prepareStatement(
                "UPDATE EXPORTER_POSITION SET LAST_EXPORTED_POSITION = ? WHERE PARTITION_ID = 1")) {
      ps.setLong(1, position);
      final int rows = ps.executeUpdate();
      if (rows == 0) {
        throw new IllegalStateException(
            "setDbExporterPosition updated 0 rows — EXPORTER_POSITION row not found for PARTITION_ID=1");
      }
    }
  }
}
