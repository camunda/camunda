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
import static io.camunda.it.util.TestHelper.waitForProcessInstance;
import static io.camunda.it.util.TestHelper.waitForProcessesToBeDeployed;
import static io.camunda.it.util.TestHelper.waitForUser;
import static io.camunda.zeebe.test.StableValuePredicate.hasStableValue;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.Iterables;
import io.camunda.client.CamundaClient;
import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import io.camunda.qa.util.cluster.TestCamundaApplication;
import io.camunda.zeebe.broker.exporter.metrics.MetricsExporter;
import io.camunda.zeebe.broker.exporter.stream.ExporterMetricsDoc;
import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.MeterRegistry;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeAll;
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
 *   <li><b>DB position behind broker</b> – simulates a DB rollback, or an automated failover to a
 *       lagging async replica, that left the position counter lower than expected. The mismatch
 *       triggers the same reopen path; on reopen the exporter requests a replay from the DB
 *       position, which rewinds the shared log reader and redelivers the missing records so the
 *       exporter re-syncs and continues.
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
            .withProperty("camunda.monitoring.metrics.enable-exporter-execution-metrics", true)
            .withProperty("camunda.data.secondary-storage.rdbms.url", postgres.getJdbcUrl())
            .withProperty("camunda.data.secondary-storage.rdbms.username", postgres.getUsername())
            .withProperty("camunda.data.secondary-storage.rdbms.password", postgres.getPassword())
            .withProperty("camunda.data.secondary-storage.rdbms.flush-interval", "PT0S")
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
    // given — note current positions before tamper
    final long positionBeforeTamper = getCurrentDbExporterPosition();
    assertThat(positionBeforeTamper).isGreaterThan(0);

    // Capture the MetricsExporter baseline so we can prove it keeps advancing independently.
    // It is auto-registered by BrokerCfg once execution metrics exporting is enabled in the test
    // setup.
    final long metricsPositionBeforeTamper = getCurrentMetricsExporterPosition();

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

    // then — the RDBMS exporter reopens, re-syncs from DB position, and eventually the DB
    // position advances past the tampered value as new records arrive
    Awaitility.await("exporter recovers and advances position past tampered value")
        .atMost(Duration.ofMinutes(3))
        .untilAsserted(
            () ->
                assertThat(getCurrentDbExporterPosition())
                    .as(
                        "DB exporter position must eventually exceed the tampered value, "
                            + "proving the exporter recovered and continued exporting")
                    .isGreaterThan(tamperedPosition));

    // and — the MetricsExporter must keep advancing despite the RDBMS reopen; it shares the same
    // actor loop but has its own ExporterContainer and is never closed or reopened as a side-effect
    Awaitility.await("MetricsExporter continues to export after RDBMS reopen")
        .atMost(Duration.ofMinutes(1))
        .untilAsserted(
            () ->
                assertThat(getCurrentMetricsExporterPosition())
                    .as(
                        "MetricsExporter last-updated position must advance past its pre-tamper "
                            + "baseline, proving it was not disrupted by the RDBMS exporter reopen")
                    .isGreaterThan(metricsPositionBeforeTamper));
  }

  /**
   * Scenario 2: RDBMS position is set to a value smaller than the broker's acknowledged position.
   *
   * <p>The in-transaction row-lock hook detects the divergence (DB &lt; {@code
   * lastFlushedPosition}) and raises a {@link
   * io.camunda.db.rdbms.exception.ExporterPositionMismatchException}. On reopen the exporter finds
   * DB &lt; broker and requests a replay from the DB position; the exporter director rewinds the
   * shared log reader to that position and redelivers the missing records, allowing the exporter to
   * re-sync and continue without any manual intervention or data loss.
   *
   * <p>A few users are created and then deleted directly from the database before rewinding the
   * position, so replaying their creation records re-inserts them rather than hitting a primary
   * key/unique constraint violation (which would occur if the rows were still present when the
   * exporter replays their INSERTs).
   */
  @Test
  @Order(2)
  void shouldRecoverWhenRdbmsPositionIsLowerThanBrokerPosition() throws Exception {
    // given — wait until the position has stabilised after the previous test
    awaitExporterPositionStabilises();

    final long positionBeforeUsers = getCurrentDbExporterPosition();
    assertThat(positionBeforeUsers).isGreaterThan(0);

    // create a few users; their creation records are exactly what the replay below must
    // redeliver once the exporter position is rewound to before they existed
    final var usernames =
        List.of(
            "recovery-user-1-" + UUID.randomUUID(),
            "recovery-user-2-" + UUID.randomUUID(),
            "recovery-user-3-" + UUID.randomUUID());
    for (final var username : usernames) {
      camundaClient
          .newCreateUserCommand()
          .username(username)
          .name(username)
          .password("password")
          .email(username + "@example.com")
          .send()
          .join();
    }
    for (final var username : usernames) {
      waitForUser(camundaClient, username);
    }

    // Delete the users directly from the database, simulating that their INSERTs were never
    // durably persisted downstream (consistent with rewinding the position past their records
    // below). Without this, replaying their creation would fail with a PK/unique constraint
    // violation instead of exercising the recovery path.
    for (final var username : usernames) {
      deleteUserFromDb(username);
    }

    // Rewind the DB position to before the users were created, simulating a DB rollback or an
    // automated failover to a lagging async replica that left the position counter behind the
    // actual data state.
    setDbExporterPosition(positionBeforeUsers);
    assertThat(getCurrentDbExporterPosition()).isEqualTo(positionBeforeUsers);

    // when — generate traffic so the exporter flushes and detects the mismatch
    final var processInstance =
        startProcessInstance(camundaClient, "service_tasks_v1", "{\"xyz\":\"foo\"}");

    // then — the exporter reopens, replays from the rewound position (re-inserting the users it
    // missed), and continues on to export the new process instance; all of it must become visible
    // again through the client, proving no records were lost or permanently skipped
    for (final var username : usernames) {
      waitForUser(camundaClient, username);
    }
    waitForProcessInstance(
        camundaClient,
        f -> f.processInstanceKey(processInstance.getProcessInstanceKey()),
        instances -> assertThat(instances).isNotEmpty());
  }

  // -----------------------------------------------------------------------
  // helpers
  // -----------------------------------------------------------------------

  private void awaitExporterPositionStabilises() {
    Awaitility.await("RDBMS exporter position stabilises")
        .atMost(Duration.ofMinutes(1))
        .during(Duration.ofSeconds(5))
        .until(this::getCurrentAcknowledgedExporterPosition, hasStableValue());
  }

  private long getCurrentAcknowledgedExporterPosition() {
    return getMeterLong(ExporterMetricsDoc.LAST_UPDATED_EXPORTED_POSITION.getName(), "rdbms");
  }

  private long getCurrentMetricsExporterPosition() {
    return getMeterLong(
        ExporterMetricsDoc.LAST_UPDATED_EXPORTED_POSITION.getName(),
        MetricsExporter.defaultExporterId());
  }

  private long getMeterLong(final String name, final String exporterId) {
    try {
      final var meters =
          meterRegistry
              .get(name)
              .tag("exporter", exporterId)
              .tag("partition", "1")
              .meter()
              .measure();
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

  private void deleteUserFromDb(final String username) throws Exception {
    try (final Connection conn = directPostgresConnection();
        final PreparedStatement ps =
            conn.prepareStatement("DELETE FROM USER_ WHERE USERNAME = ?")) {
      ps.setString(1, username);
      ps.executeUpdate();
    }
  }
}
