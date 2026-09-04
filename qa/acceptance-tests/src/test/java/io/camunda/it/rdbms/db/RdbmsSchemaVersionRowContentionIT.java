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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Verifies on every supported vendor that a node <b>restarting</b> against a {@code
 * RDBMS_SCHEMA_VERSION} row another session holds uncommitted reaches an outcome, rather than
 * hanging forever with nothing logged (#61405). The running test application plays the first node,
 * a connection borrowed from its pool plays the peer, and a second {@code RdbmsSchemaInitializer}
 * plays the node that starts into the contention.
 *
 * <p>A restart finds the version it would record already recorded, so no write is issued and there
 * is nothing to contend for — which is why this case needs no statement-timeout bound of its own,
 * and so needs neither a shortened timeout nor a wait calibrated to one. The other half of the fix,
 * a <b>version change</b> that genuinely has to write and so has to wait for the statement timeout,
 * is {@code RdbmsSchemaVersionStoreIT#shouldBoundAContendedWriteWhereTheVendorAllowsItToBeBounded}:
 * it drives the store directly with a shortened timeout, rather than a real {@code
 * RdbmsSchemaInitializer} waiting out the production one on every vendor in the matrix.
 *
 * <p>This belongs on the matrix rather than on one database because the mechanism varies in every
 * respect: how long an unbounded wait lasts, whether a statement timeout can end it, and whether a
 * read has to wait behind an uncommitted write at all. An earlier version ran against PostgreSQL
 * alone and passed; running it here is what turned up Oracle. So the case is written against the
 * outcome, with the vendors that reach it differently named where they diverge.
 *
 * <p>Uses a single {@code default} tenant, which is every deployment that has not configured
 * physical tenants, and is also the shape with the least between the database call and the
 * operator: {@code RdbmsSchemaInitializer} forks on the tenant count, and with one tenant it
 * applies the schema on the calling thread, so a failure travels out into Spring's own context
 * refresh. What the gate does when a node has a tenant to spare is {@code
 * RdbmsSchemaVersionRowContentionMultiTenantIT}.
 */
@Tag("rdbms")
@ExtendWith(CamundaRdbmsInvocationContextProviderExtension.class)
final class RdbmsSchemaVersionRowContentionIT {

  /**
   * An upper bound on "bounded", deliberately well above the store's own statement timeout rather
   * than equal to it: what is asserted is that startup settles at all, and pinning the exact value
   * would make tuning it a test change.
   */
  private static final Duration BOUNDED = Duration.ofMinutes(2);

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

  // ---- helpers ----

  /** Writes the row without committing, leaving it locked by an open transaction. */
  private static void holdRow(final Connection peer, final String version) throws Exception {
    try (final var statement = peer.createStatement()) {
      statement.executeUpdate(
          "UPDATE RDBMS_SCHEMA_VERSION SET VERSION = '" + version + "' WHERE ID = 1");
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
