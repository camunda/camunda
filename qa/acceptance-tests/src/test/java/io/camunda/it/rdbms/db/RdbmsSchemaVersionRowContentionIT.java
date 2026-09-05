/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db;

import static io.camunda.cluster.PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.camunda.application.commons.rdbms.RdbmsDataSources;
import io.camunda.application.commons.rdbms.RdbmsSchemaInitializer;
import io.camunda.db.rdbms.PerTenantSchemaConfig;
import io.camunda.db.rdbms.RdbmsSchemaManager;
import io.camunda.db.rdbms.RdbmsSchemaManagers;
import io.camunda.db.rdbms.exception.RdbmsSchemaVersionUnreadableException;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.zeebe.util.VersionUtil;
import java.sql.Connection;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Verifies on every supported vendor that a node initializing its schema against a {@code
 * RDBMS_SCHEMA_VERSION} row another session holds uncommitted reaches an outcome, rather than
 * hanging forever with nothing logged (#61405). The running test application plays the first node,
 * a connection borrowed from its pool plays the peer, and a second {@code RdbmsSchemaInitializer}
 * plays the node that starts into the contention.
 *
 * <p>One case per half of the fix. A <b>restart</b> finds the version it would record already
 * recorded, so no write is issued and there is nothing to contend for. A <b>version change</b> has
 * to write, so the statement timeout is what has to end the wait.
 *
 * <p>This belongs on the matrix rather than on one database because the mechanism varies in every
 * respect: how long an unbounded wait lasts, whether a statement timeout can end it, and whether a
 * read has to wait behind an uncommitted write at all. An earlier version ran against PostgreSQL
 * alone and passed; running it here is what turned up Oracle. So each case is written against the
 * outcome, with the vendors that reach it differently named where they diverge.
 *
 * <p>Both cases use a single {@code default} tenant, which is every deployment that has not
 * configured physical tenants, and is also the shape with the least between the database call and
 * the operator: {@code RdbmsSchemaInitializer} forks on the tenant count, and with one tenant it
 * applies the schema on the calling thread, so a failure travels out into Spring's own context
 * refresh. What the gate does when a node has a tenant to spare is {@code
 * RdbmsSchemaVersionRowContentionMultiTenantIT}.
 */
@Tag("rdbms")
@ExtendWith(CamundaRdbmsInvocationContextProviderExtension.class)
final class RdbmsSchemaVersionRowContentionIT {

  /**
   * Vendors whose row-lock wait no application-level bound can reach: measured on Oracle 23 (free),
   * neither a statement timeout nor {@link java.sql.Statement#cancel()} interrupts it. H2 ignores
   * both as well but is absent from this set deliberately — its own {@code LOCK_TIMEOUT} gives up
   * after ~2s, so the wait ends there regardless. Emptying this set is the goal; doing it needs
   * vendor-specific SQL ({@code SELECT ... FOR UPDATE WAIT n}, or a {@code MERGE}) in a class that
   * has none.
   */
  private static final Set<String> UNBOUNDED_WRITE_WAIT = Set.of("oracle");

  /** What the node that starts into the contention runs, and so has to record. */
  private static final String APPLICATION_VERSION = "8.10.0";

  /**
   * Seeded as already recorded to force the write: one minor behind, so the upgrade path is legal
   * and the version genuinely has to change. Fixed rather than derived from the running version, so
   * that what this test forces does not move with the release.
   */
  private static final String PREVIOUS_VERSION = "8.9.0";

  /**
   * An upper bound on "bounded", deliberately well above the store's own statement timeout rather
   * than equal to it: what is asserted is that startup settles at all, and pinning the exact value
   * would make tuning it a test change.
   */
  private static final Duration BOUNDED = Duration.ofMinutes(2);

  /**
   * How long a vendor in {@link #UNBOUNDED_WRITE_WAIT} is watched before the wait is called
   * unbounded. Comfortably longer than the store's statement timeout, so that "still waiting" means
   * the timeout had its chance and could not take it.
   */
  private static final Duration PAST_THE_STATEMENT_TIMEOUT = Duration.ofSeconds(45);

  @TestTemplate
  void shouldSettleARestartWithoutContendingAtAll(final CamundaRdbmsTestApplication testApplication)
      throws Exception {
    // given - the schema is migrated and its version recorded, which is the state every restart
    // after the first finds
    final var dataSources = testApplication.bean(RdbmsDataSources.class);
    final var dataSource = dataSources.dataSourceFor(DEFAULT_PHYSICAL_TENANT_ID);
    final var initializer = initializerFor(dataSources, VersionUtil.getVersion());

    // and - a peer session has written that row and not committed. Borrowed from the application's
    // own pool so that this needs no vendor-specific connection details; the row is only ever
    // rolled back, so the version the application recorded is what every later test still reads.
    try (final var peer = dataSource.getConnection()) {
      peer.setAutoCommit(false);
      holdRow(peer, "0.0.1");

      try {
        // when - the node restarts against the same schema
        final var startedAt = System.nanoTime();
        final var outcome = catchThrowable(initializer::afterPropertiesSet);
        final var settledIn = Duration.ofNanos(System.nanoTime() - startedAt);

        // then - it reached an outcome at all, which is the whole regression: this call used to
        // stay inside the JDBC driver for as long as the peer held the row, throwing nothing and
        // so logging nothing
        assertThat(settledIn)
            .as("startup settled rather than waiting for as long as the peer held the row")
            .isLessThan(BOUNDED);

        // and - which outcome is the vendor's to decide, because it decides whether a read has to
        // wait behind an uncommitted write
        if (outcome == null) {
          // Snapshot reads (PostgreSQL, Oracle, MySQL, MariaDB, H2): the recorded version is read
          // straight past the peer's uncommitted row, matches what this node would write, and so
          // no contending write is issued at all. This is the case that matters most, being every
          // restart of an already-initialized deployment - and it is the only thing that saves
          // Oracle, whose lock wait no application-level timeout can bound.
          assertThat(initializer.isInitialized(DEFAULT_PHYSICAL_TENANT_ID)).isTrue();
        } else {
          // MSSQL's locking READ COMMITTED makes even that read wait, so there the statement
          // timeout is what ends it: a typed, retryable failure, carried out of the synchronous
          // single-tenant path, which on a plain deployment is Spring's own context refresh.
          assertThat(outcome)
              .isInstanceOf(RdbmsSchemaVersionUnreadableException.class)
              .hasMessageContaining("[RDBMS Schema]")
              .hasStackTraceContaining("RdbmsSchemaVersionStore")
              .hasStackTraceContaining("SingleTenantSchemaInitialization.start")
              .hasStackTraceContaining("RdbmsSchemaInitializer.afterPropertiesSet");
          assertThat(initializer.isInitialized(DEFAULT_PHYSICAL_TENANT_ID)).isFalse();
        }
      } finally {
        initializer.destroy();
        peer.rollback();
      }
    }
  }

  @TestTemplate
  void shouldBoundAContendedWriteWhereTheVendorAllowsItToBeBounded(
      final CamundaRdbmsTestApplication testApplication) throws Exception {
    final var dataSources = testApplication.bean(RdbmsDataSources.class);
    final var dataSource = dataSources.dataSourceFor(DEFAULT_PHYSICAL_TENANT_ID);
    final var databaseId = dataSources.vendorPropertiesFor(DEFAULT_PHYSICAL_TENANT_ID).databaseId();

    // given - an older version recorded, so this node has to replace it and the skip does not
    // apply. Restored afterwards, since the application this borrows is shared with later tests.
    final var recordedByTheApplication = readRecordedVersion(dataSource);
    writeRecordedVersion(dataSource, PREVIOUS_VERSION);
    try {
      // and - a peer session holding that row uncommitted, so the write has to wait for it
      try (final var peer = dataSource.getConnection()) {
        peer.setAutoCommit(false);
        holdRow(peer, PREVIOUS_VERSION);

        // when - the node starts, on its own thread because on some vendors it will not return
        final var initializer = initializerFor(dataSources, APPLICATION_VERSION);
        final var startupSettled = new CountDownLatch(1);
        final var startupThread =
            Thread.ofPlatform()
                .name("context-refresh")
                .start(
                    () -> {
                      try {
                        initializer.afterPropertiesSet();
                      } catch (final Throwable expectedOnSomeVendors) {
                        // whether it settled at all is the assertion, not how
                      } finally {
                        startupSettled.countDown();
                      }
                    });
        try {
          if (UNBOUNDED_WRITE_WAIT.contains(databaseId)) {
            // then - the known gap, asserted rather than hidden: this vendor's lock wait ignores
            // the statement timeout, so startup is still inside the JDBC call after the timeout
            // has had every chance to fire. A restart never reaches this path, because the write
            // is skipped; a first start against a fresh database, or a real version change, does.
            // When this vendor gains a bound, this assertion fails and the vendor comes off
            // UNBOUNDED_WRITE_WAIT.
            assertThat(
                    startupSettled.await(PAST_THE_STATEMENT_TIMEOUT.toSeconds(), TimeUnit.SECONDS))
                .as("'%s' has no bound for a contended write yet", databaseId)
                .isFalse();
          } else {
            // then - the statement timeout ends the wait, whether the driver cancels the statement
            // (PostgreSQL, MySQL, MariaDB, MSSQL) or the vendor's own lock timeout gets there
            // first (H2)
            assertThat(startupSettled.await(BOUNDED.toSeconds(), TimeUnit.SECONDS))
                .as("'%s' bounds a contended write", databaseId)
                .isTrue();
            assertThat(initializer.isInitialized(DEFAULT_PHYSICAL_TENANT_ID)).isFalse();
          }
        } finally {
          // releasing the row lets an unbounded wait finish too, so no thread is left parked
          peer.rollback();
        }

        assertThat(startupSettled.await(BOUNDED.toSeconds(), TimeUnit.SECONDS))
            .as("startup settles once the peer releases the row")
            .isTrue();
        startupThread.join(BOUNDED.toMillis());
        initializer.destroy();
      }
    } finally {
      writeRecordedVersion(dataSource, recordedByTheApplication);
    }
  }

  // ---- helpers ----

  /** Writes the row without committing, leaving it locked by an open transaction. */
  private static void holdRow(final Connection peer, final String version) throws Exception {
    try (final var statement = peer.createStatement()) {
      statement.executeUpdate(
          "UPDATE RDBMS_SCHEMA_VERSION SET VERSION = '" + version + "' WHERE ID = 1");
    }
  }

  private static @Nullable String readRecordedVersion(final DataSource dataSource)
      throws Exception {
    try (final var connection = dataSource.getConnection();
        final var statement = connection.createStatement();
        final var result = statement.executeQuery("SELECT VERSION FROM RDBMS_SCHEMA_VERSION")) {
      return result.next() ? result.getString(1) : null;
    }
  }

  private static void writeRecordedVersion(
      final DataSource dataSource, final @Nullable String version) throws Exception {
    if (version == null) {
      return;
    }
    try (final var connection = dataSource.getConnection()) {
      try (final var statement = connection.createStatement()) {
        statement.executeUpdate(
            "UPDATE RDBMS_SCHEMA_VERSION SET VERSION = '" + version + "' WHERE ID = 1");
      }
      // the application's pool runs with autoCommit disabled, so returning the connection without
      // this rolls the write back
      connection.commit();
    }
  }

  /**
   * Another node's initializer against the same schema as the running application: same data
   * source, same (empty) prefix, and single-tenant, so it takes the synchronous shape.
   */
  private static RdbmsSchemaInitializer initializerFor(
      final RdbmsDataSources dataSources, final String applicationVersion) {
    final Map<String, RdbmsSchemaManager> schemaManagers =
        RdbmsSchemaManagers.fromConfigs(
            Map.of(
                DEFAULT_PHYSICAL_TENANT_ID,
                new PerTenantSchemaConfig(
                    dataSources.dataSourceFor(DEFAULT_PHYSICAL_TENANT_ID),
                    dataSources.vendorPropertiesFor(DEFAULT_PHYSICAL_TENANT_ID),
                    "",
                    /* autoDdl= */ true,
                    Duration.ofMinutes(15))),
            applicationVersion);
    return new RdbmsSchemaInitializer(schemaManagers);
  }
}
