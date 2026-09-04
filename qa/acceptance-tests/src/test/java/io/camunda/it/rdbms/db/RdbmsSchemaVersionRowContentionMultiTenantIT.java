/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.camunda.application.commons.rdbms.RdbmsSchemaInitializer;
import io.camunda.db.rdbms.PerTenantSchemaConfig;
import io.camunda.db.rdbms.RdbmsSchemaManager;
import io.camunda.db.rdbms.RdbmsSchemaManagers;
import io.camunda.db.rdbms.config.VendorDatabasePropertiesLoader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Covers what only a node with a tenant to spare can show: {@code PerTenantSchemaInitialization}'s
 * gate cannot open until every tenant has settled, so a tenant that never produced an outcome used
 * to hold the whole node's startup shut while its siblings sat migrated and ready — the signature
 * #61405 reports. The node must now come up, degrade the contended tenant alone, and recover it in
 * the background once the peer releases the row.
 *
 * <p>One tenant records an older version, so its start genuinely has to write and contends with a
 * peer holding that row; the other is left alone, and is what the gate opens on.
 *
 * <p>One database rather than the vendor matrix, because what this asserts is the gate — Java that
 * behaves identically on every vendor. Whether a contended write can be bounded at all <em>is</em>
 * vendor-dependent, and is asserted per vendor in {@code RdbmsSchemaVersionRowContentionIT}. Two
 * tenants also means migrating two independently prefixed schemas from scratch, which a throwaway
 * PostgreSQL absorbs and the shared per-vendor test applications would be left holding — so it
 * brings its own container, the way {@code RdbmsExporterPositionRecoveryIT} does.
 */
@Tag("rdbms")
@Timeout(180)
class RdbmsSchemaVersionRowContentionMultiTenantIT {

  /** What the restarting node runs, and so what it has to record. */
  private static final String APPLICATION_VERSION = "8.10.0";

  /**
   * What the first node recorded: one minor behind, so the restart is a legal upgrade that must
   * actually write the row. Recording the same version would make {@code recordCurrentVersion()}
   * skip the write, which is the point of the sibling cross-vendor test rather than this one.
   */
  private static final String PREVIOUS_VERSION = "8.9.0";

  private static final String HEALTHY_TENANT = "healthy";
  private static final String STUCK_TENANT = "stuck";
  private static final String HEALTHY_PREFIX = "H_";
  private static final String STUCK_PREFIX = "S_";
  private static final String DATABASE_NAME = "camunda";
  private static final String DATABASE_USER = "camunda";
  private static final String DATABASE_PASSWORD = "camunda";

  /**
   * An upper bound on "bounded", deliberately far above the store's own statement timeout rather
   * than equal to it: what this test is about is that the wait ends at all, and pinning the exact
   * value here would make tuning it a test change.
   */
  private static final Duration BOUNDED = Duration.ofMinutes(2);

  @SuppressWarnings("resource")
  private static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName(DATABASE_NAME)
          .withUsername(DATABASE_USER)
          .withPassword(DATABASE_PASSWORD)
          .withStartupTimeout(Duration.ofMinutes(5));

  @BeforeAll
  static void startPostgres() {
    POSTGRES.start();
  }

  @AfterAll
  static void stopPostgres() {
    POSTGRES.stop();
  }

  @Test
  void shouldComeUpAndDegradeOnlyTheContendedTenant() throws Exception {
    // given - both tenants have migrated once, sharing one PostgreSQL the way physical tenants
    // really do, distinguished only by table prefix: the state every restart after the first
    // finds, where RDBMS_SCHEMA_VERSION already has its row
    final var tenants = Map.of(HEALTHY_TENANT, HEALTHY_PREFIX, STUCK_TENANT, STUCK_PREFIX);
    runToCompletionOnce(tenants, PREVIOUS_VERSION);

    // and - a peer session has updated the stuck tenant's schema-version row and not committed:
    // from this node's side, indistinguishable from a session that never will
    try (final var peer = peerHolding(STUCK_PREFIX)) {
      // when - the node starts, as it does on every restart
      final var initializer = initializerFor(tenants, APPLICATION_VERSION);
      try {
        final var startedAt = System.nanoTime();
        initializer.afterPropertiesSet();
        final var heldFor = Duration.ofNanos(System.nanoTime() - startedAt);

        // then - startup got past the gate, which it could only do because the contended tenant
        // produced an outcome instead of none; the node serves the tenant it can and degrades the
        // one it cannot, rather than serving nobody
        assertThat(heldFor)
            .as("startup was held only until the contended statement timed out, not indefinitely")
            .isLessThan(BOUNDED);
        assertThat(initializer.isInitialized(HEALTHY_TENANT)).isTrue();
        assertThat(initializer.isInitialized(STUCK_TENANT)).isFalse();

        // when - the peer finally does what every other session is assumed to do promptly
        peer.rollback();

        // then - the retry that the bounded failure made possible carries the tenant the rest of
        // the way: no restart, no operator
        await("the contended tenant recovers on a later attempt")
            .atMost(Duration.ofSeconds(60))
            .untilAsserted(() -> assertThat(initializer.isInitialized(STUCK_TENANT)).isTrue());
      } finally {
        initializer.destroy();
      }
    }
  }

  // ---- helpers ----

  /**
   * A session standing in for any other node's own {@code recordCurrentVersion()} that has written
   * the row and not yet committed.
   */
  private static Connection peerHolding(final String prefix) throws Exception {
    final var peer =
        DriverManager.getConnection(POSTGRES.getJdbcUrl(), DATABASE_USER, DATABASE_PASSWORD);
    peer.setAutoCommit(false);
    try (final var statement = peer.createStatement()) {
      // rewrites the row with what it already holds, so that the only thing this peer changes is
      // that the row is now locked by an open transaction
      statement.executeUpdate(
          "UPDATE "
              + prefix
              + "RDBMS_SCHEMA_VERSION SET VERSION = '"
              + PREVIOUS_VERSION
              + "' WHERE ID = 1");
    }
    return peer;
  }

  private static void runToCompletionOnce(
      final Map<String, String> prefixesByTenant, final String applicationVersion)
      throws Exception {
    final var initializer = initializerFor(prefixesByTenant, applicationVersion);
    try {
      initializer.afterPropertiesSet();
      assertThat(prefixesByTenant.keySet()).allMatch(initializer::isInitialized);
    } finally {
      initializer.destroy();
    }
  }

  private static RdbmsSchemaInitializer initializerFor(
      final Map<String, String> prefixesByTenant, final String applicationVersion)
      throws Exception {
    final var configs = new LinkedHashMap<String, PerTenantSchemaConfig>();
    for (final var tenant : prefixesByTenant.entrySet()) {
      configs.put(tenant.getKey(), schemaConfig(dataSource(), tenant.getValue()));
    }
    final Map<String, RdbmsSchemaManager> schemaManagers =
        RdbmsSchemaManagers.fromConfigs(configs, applicationVersion);
    return new RdbmsSchemaInitializer(schemaManagers);
  }

  private static PerTenantSchemaConfig schemaConfig(
      final PGSimpleDataSource dataSource, final String prefix) throws Exception {
    return new PerTenantSchemaConfig(
        dataSource,
        VendorDatabasePropertiesLoader.load("postgresql"),
        prefix,
        /* autoDdl= */ true,
        Duration.ofMinutes(15));
  }

  private static PGSimpleDataSource dataSource() {
    final var dataSource = new PGSimpleDataSource();
    dataSource.setUrl(POSTGRES.getJdbcUrl());
    dataSource.setUser(DATABASE_USER);
    dataSource.setPassword(DATABASE_PASSWORD);
    return dataSource;
  }
}
