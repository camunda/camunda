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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.application.commons.rdbms.RdbmsDataSources;
import io.camunda.db.rdbms.RdbmsSchemaVersionStore;
import io.camunda.db.rdbms.exception.RdbmsSchemaVersionIncompatibleException;
import io.camunda.db.rdbms.exception.RdbmsSchemaVersionUnreadableException;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.zeebe.util.VersionUtil;
import java.sql.Connection;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Verifies {@link RdbmsSchemaVersionStore#tableExists} finds {@code RDBMS_SCHEMA_VERSION} on every
 * real database vendor, not just H2 -- unquoted identifiers fold differently per vendor (e.g.
 * PostgreSQL/MySQL/MariaDB lower case vs. H2/Oracle/MSSQL upper case), which the check previously
 * missed.
 *
 * <p>Also covers the vendor-behavior half of #61405's fix, per vendor: whether a driver's {@code
 * Statement.setQueryTimeout} actually interrupts a write contended for a row lock. That fact
 * doesn't depend on the exact timeout value, so it is checked here directly against the store, with
 * {@link RdbmsSchemaVersionStore#statementTimeoutSeconds()} shortened for the occasion -- rather
 * than against a real {@code RdbmsSchemaInitializer}, which would need to wait out the store's own
 * production timeout on every vendor in the matrix.
 */
@Tag("rdbms")
@ExtendWith(CamundaRdbmsInvocationContextProviderExtension.class)
final class RdbmsSchemaVersionStoreIT {

  /**
   * Vendors whose row-lock wait no application-level bound can reach: measured on Oracle 23 (free),
   * neither a statement timeout nor {@link java.sql.Statement#cancel()} interrupts it. H2 ignores
   * both as well but is absent from this set deliberately -- its own {@code LOCK_TIMEOUT} gives up
   * after ~2s, so the wait ends there regardless. Emptying this set is the goal; doing it needs
   * vendor-specific SQL ({@code SELECT ... FOR UPDATE WAIT n}, or a {@code MERGE}) in a class that
   * has none.
   */
  private static final Set<String> UNBOUNDED_WRITE_WAIT = Set.of("oracle");

  /** What the store recording into the contended row runs, and so has to record. */
  private static final String APPLICATION_VERSION = "8.10.0";

  /**
   * Seeded as already recorded to force the write: one minor behind, so the upgrade path is legal
   * and the version genuinely has to change. Fixed rather than derived from the running version, so
   * that what this test forces does not move with the release.
   */
  private static final String PREVIOUS_VERSION = "8.9.0";

  /**
   * What {@link RdbmsSchemaVersionStore#statementTimeoutSeconds()} is shortened to for this test,
   * so that a vendor which does bound the wait settles in seconds rather than the production
   * timeout's 30. Whether a driver honors a query timeout under row-lock contention at all does not
   * depend on which value is configured, only on one being enforced.
   */
  private static final Duration SHORT_TIMEOUT = Duration.ofSeconds(3);

  /**
   * How long a vendor in {@link #UNBOUNDED_WRITE_WAIT} is watched before the wait is called
   * unbounded. Comfortably longer than {@link #SHORT_TIMEOUT}, so that "still waiting" means the
   * timeout had its chance and could not take it.
   */
  private static final Duration PAST_THE_SHORT_TIMEOUT = Duration.ofSeconds(10);

  /**
   * An upper bound on "bounded", deliberately well above {@link #SHORT_TIMEOUT} rather than equal
   * to it: what is asserted is that the write settles at all, and pinning the exact value would
   * make tuning it a test change.
   */
  private static final Duration BOUNDED = Duration.ofSeconds(30);

  @TestTemplate
  void shouldBoundAContendedWriteWhereTheVendorAllowsItToBeBounded(
      final CamundaRdbmsTestApplication testApplication) throws Exception {
    final DataSource dataSource = testApplication.bean(DataSource.class);
    final var databaseId =
        testApplication
            .bean(RdbmsDataSources.class)
            .vendorPropertiesFor(DEFAULT_PHYSICAL_TENANT_ID)
            .databaseId();

    // given - an older version recorded, so this store has to replace it and the skip does not
    // apply. Restored afterwards, since this test application is shared with the other tests in
    // this class.
    final var recordedByTheApplication = readRecordedVersion(dataSource);
    writeRecordedVersion(dataSource, PREVIOUS_VERSION);
    try {
      // and - a peer session holding that row uncommitted, so the write has to wait for it
      try (final var peer = dataSource.getConnection()) {
        peer.setAutoCommit(false);
        holdRow(peer, PREVIOUS_VERSION);

        // when - a store with a shortened statement timeout records a real version change, on its
        // own thread because on some vendors it will not return
        final var versionStore = new ShortTimeoutSchemaVersionStore(dataSource);
        final var settled = new CountDownLatch(1);
        final var outcome = new AtomicReference<Throwable>();
        final var writer =
            Thread.ofPlatform()
                .name("contended-schema-write")
                .start(
                    () -> {
                      try {
                        versionStore.recordCurrentVersion();
                      } catch (final Throwable expectedOnSomeVendors) {
                        outcome.set(expectedOnSomeVendors);
                      } finally {
                        settled.countDown();
                      }
                    });
        try {
          if (UNBOUNDED_WRITE_WAIT.contains(databaseId)) {
            // then - the known gap, asserted rather than hidden: this vendor's lock wait ignores
            // the statement timeout, so the write is still in flight after the timeout has had
            // every chance to fire. When this vendor gains a bound, this assertion fails and the
            // vendor comes off UNBOUNDED_WRITE_WAIT.
            assertThat(settled.await(PAST_THE_SHORT_TIMEOUT.toSeconds(), TimeUnit.SECONDS))
                .as("'%s' has no bound for a contended write yet", databaseId)
                .isFalse();
          } else {
            // then - the statement timeout ends the wait, surfaced as the typed, retryable
            // failure that RdbmsSchemaInitializer#isTerminal already classifies that way
            assertThat(settled.await(BOUNDED.toSeconds(), TimeUnit.SECONDS))
                .as("'%s' bounds a contended write", databaseId)
                .isTrue();
            assertThat(outcome.get())
                .as("the timeout is surfaced as a retryable, typed failure")
                .isInstanceOf(RdbmsSchemaVersionUnreadableException.class);
            assertThat(readRecordedVersion(dataSource)).isEqualTo(PREVIOUS_VERSION);
          }
        } finally {
          // releasing the row lets an unbounded wait finish too, so no thread is left parked
          peer.rollback();
        }

        assertThat(settled.await(BOUNDED.toSeconds(), TimeUnit.SECONDS))
            .as("the write settles once the peer releases the row")
            .isTrue();
        writer.join(BOUNDED.toMillis());

        // and - nothing contends for the row any more, so the real path now recovers it
        versionStore.recordCurrentVersion();
        assertThat(readRecordedVersion(dataSource)).isEqualTo(APPLICATION_VERSION);
      }
    } finally {
      writeRecordedVersion(dataSource, recordedByTheApplication);
    }
  }

  @TestTemplate
  void shouldFindExistingSchemaVersionRegardlessOfVendorIdentifierCasing(
      final CamundaRdbmsTestApplication testApplication) {
    final DataSource dataSource = testApplication.bean(DataSource.class);
    final var versionStore = new RdbmsSchemaVersionStore(dataSource, "", VersionUtil.getVersion());

    // The test application's own startup migration already recorded the running version, so a
    // second, independently constructed store checking against that same version is the
    // happy-path case: the version matches, so this must pass without error.
    assertThatCode(versionStore::checkCompatibility).doesNotThrowAnyException();
  }

  @TestTemplate
  void shouldDetectIncompatibleUpgradePathRegardlessOfVendorIdentifierCasing(
      final CamundaRdbmsTestApplication testApplication) throws Exception {
    final DataSource dataSource = testApplication.bean(DataSource.class);
    final var versionStore = new RdbmsSchemaVersionStore(dataSource, "", VersionUtil.getVersion());

    // Rewind the version the test application's own startup migration already recorded, far
    // enough behind the running version to be an illegal, minor-version-skipping upgrade path.
    // Plain DML resolves the unquoted table name via the vendor's own identifier folding, the
    // same way the production upsert in RdbmsSchemaVersionStore#recordCurrentVersion does, so this
    // does not itself depend on the tableExists behavior under test.
    try (final var connection = dataSource.getConnection()) {
      final var autoCommit = connection.getAutoCommit();
      connection.setAutoCommit(false);
      try (final var statement = connection.createStatement()) {
        statement.executeUpdate("UPDATE RDBMS_SCHEMA_VERSION SET VERSION = '8.0.0' WHERE ID = 1");
      }
      connection.commit();
      connection.setAutoCommit(autoCommit);
    }

    try {
      assertThatThrownBy(versionStore::checkCompatibility)
          .isInstanceOf(RdbmsSchemaVersionIncompatibleException.class);
    } finally {
      // Restore the version a real boot would have recorded, so any later test reusing this
      // vendor's shared, cached test application observes a consistent, correctly-migrated state.
      versionStore.recordCurrentVersion();
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
   * A store whose {@link RdbmsSchemaVersionStore#statementTimeoutSeconds()} is shortened to {@link
   * #SHORT_TIMEOUT}, so that whether a vendor's driver honors it under row-lock contention can be
   * checked in seconds instead of waiting out the production value.
   */
  private static final class ShortTimeoutSchemaVersionStore extends RdbmsSchemaVersionStore {
    private ShortTimeoutSchemaVersionStore(final DataSource dataSource) {
      super(dataSource, "", APPLICATION_VERSION);
    }

    @Override
    protected int statementTimeoutSeconds() {
      return (int) SHORT_TIMEOUT.toSeconds();
    }
  }
}
