/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.config.VendorDatabasePropertiesLoader;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import liquibase.change.custom.CustomTaskChange;
import liquibase.database.Database;
import liquibase.exception.ValidationErrors;
import liquibase.integration.spring.SpringLiquibase;
import liquibase.resource.ResourceAccessor;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Verifies that physical tenants migrating at the same time each end up with their changelog lock
 * released, running real Liquibase and the production changelog against real H2 databases.
 *
 * <p>Liquibase ends every update by discarding its {@code LockServiceFactory} singleton JVM-wide,
 * so a run that finishes while another is still holding its lock used to leave that second lock set
 * — silently, since the release is skipped rather than attempted and failed. The tenant then lost
 * its next start to Liquibase's full changelog-lock wait, over and over, until the lock aged past
 * {@code ddl-lock-wait-timeout} and the stale-lock probe force-released it (#61009).
 *
 * <p>The interleaving that does this is decided here rather than raced for. The tenant under test
 * runs a changelog that ends by handing control back to the test from inside the update, while
 * Liquibase still holds its changelog lock, and it stays there while its peer runs a whole
 * migration of its own — the interleaving that leaks. Waiting for that peer is bounded, and the
 * bound decides nothing: if the peer finished, the assertions below catch the lock it left behind;
 * if it never started, there was no reset to skip a release, which is what the serialization in
 * {@link LiquibaseSchemaManager} buys. The bound is sized from a measured uncontended run of the
 * very migration being waited for, so that "the peer did not finish" cannot quietly come to mean
 * "the peer was slow".
 */
class LiquibaseSchemaManagerConcurrentMigrationH2Test {

  private static final Duration DDL_LOCK_WAIT_TIMEOUT = Duration.ofMinutes(15);
  private static final String PARKING_CHANGELOG =
      "db/changelog/rdbms-concurrent-migration/parking-changelog.xml";

  /**
   * How much longer than an uncontended run of the same migration the peer is given before this
   * test concludes that it never started at all, plus a grace so that a run measured in
   * single-digit milliseconds still leaves a workable margin. Capped, so that a slow measurement
   * cannot stretch the passing case out.
   */
  private static final int UNCONTENDED_RUN_MARGIN = 10;

  private static final Duration MEASUREMENT_GRACE = Duration.ofSeconds(1);
  private static final Duration MAX_PEER_CHANCE = Duration.ofSeconds(15);

  private final List<Throwable> migrationFailures = new CopyOnWriteArrayList<>();

  @Test
  @Timeout(value = 60, unit = TimeUnit.SECONDS)
  void shouldReleaseEveryTenantsLockWhenTheirMigrationsOverlap() throws Exception {
    // given — two tenants on their own databases; the peer is migrated up front, so the run it
    // makes below is the same no-op re-run every restart makes, and one uncontended such run is
    // timed to size the wait for it
    final var parkedTenant = dataSourceFor("overlap-parked");
    final var peerTenant = dataSourceFor("overlap-peer");
    schemaManagerFor(peerTenant).initialize();
    final var peersChance = peersChanceToFinish(timeUncontendedRunOf(peerTenant));

    final var parkedIsInsideItsUpdate = new CountDownLatch(1);
    final var peerHasFinished = new CountDownLatch(1);
    ParkInsideUpdate.onExecute(
        () -> {
          parkedIsInsideItsUpdate.countDown();
          awaitPeerToFinishIfItCan(peerHasFinished, peersChance);
        });
    final var parked =
        migrationThread("parked", () -> parkingSchemaManagerFor(parkedTenant).initialize());
    final var peer =
        migrationThread(
            "peer",
            () -> {
              parkedIsInsideItsUpdate.await();
              try {
                schemaManagerFor(peerTenant).initialize();
              } finally {
                peerHasFinished.countDown();
              }
            });

    // when — both tenants migrate at the same time, as PerTenantSchemaInitialization runs them,
    // with the peer's whole run falling inside the window in which the other holds its lock
    parked.start();
    peer.start();
    parked.join();
    peer.join();

    // then — both migrations went through, and neither tenant left its changelog lock behind for
    // its own next start to wait out
    assertThat(migrationFailures).as("both tenants migrated successfully").isEmpty();
    assertThat(isLockHeld(parkedTenant)).as("parked tenant's changelog lock").isFalse();
    assertThat(isLockHeld(peerTenant)).as("peer tenant's changelog lock").isFalse();
  }

  /**
   * Returns either once the peer has finished a migration of its own — the interleaving that used
   * to leave this tenant's lock behind — or once it has had long enough that it cannot have started
   * one.
   */
  private void awaitPeerToFinishIfItCan(
      final CountDownLatch peerHasFinished, final Duration peersChance) {
    try {
      peerHasFinished.await(peersChance.toMillis(), TimeUnit.MILLISECONDS);
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while giving the peer tenant its turn", e);
    }
  }

  /** Times the no-op re-run that the peer will make again, with nothing else running. */
  private Duration timeUncontendedRunOf(final DataSource tenant) throws Exception {
    final var startedAt = System.nanoTime();
    schemaManagerFor(tenant).initialize();
    return Duration.ofNanos(System.nanoTime() - startedAt);
  }

  private Duration peersChanceToFinish(final Duration uncontendedRun) {
    final var scaled = uncontendedRun.multipliedBy(UNCONTENDED_RUN_MARGIN).plus(MEASUREMENT_GRACE);
    return scaled.compareTo(MAX_PEER_CHANCE) > 0 ? MAX_PEER_CHANCE : scaled;
  }

  private Thread migrationThread(final String tenantId, final TenantMigration migration) {
    return Thread.ofVirtual()
        .name("schema-init-" + tenantId)
        .unstarted(
            () -> {
              try {
                migration.run();
              } catch (final Throwable t) {
                migrationFailures.add(
                    new IllegalStateException(
                        "Schema initialization failed for tenant '" + tenantId + "'", t));
              }
            });
  }

  private LiquibaseSchemaManager schemaManagerFor(final DataSource dataSource) throws Exception {
    return new LiquibaseSchemaManager(schemaConfigFor(dataSource), "8.10.0");
  }

  /** A schema manager whose changelog ends by handing control back to this test. */
  private LiquibaseSchemaManager parkingSchemaManagerFor(final DataSource dataSource)
      throws Exception {
    return new LiquibaseSchemaManager(schemaConfigFor(dataSource), "8.10.0") {
      @Override
      protected SpringLiquibase buildRunner() {
        final var runner = super.buildRunner();
        runner.setChangeLog(PARKING_CHANGELOG);
        return runner;
      }
    };
  }

  private PerTenantSchemaConfig schemaConfigFor(final DataSource dataSource) throws Exception {
    return new PerTenantSchemaConfig(
        dataSource, VendorDatabasePropertiesLoader.load("h2"), "", true, DDL_LOCK_WAIT_TIMEOUT);
  }

  private DataSource dataSourceFor(final String name) {
    final var dataSource = new JdbcDataSource();
    dataSource.setURL(
        "jdbc:h2:mem:" + name + "-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL");
    dataSource.setUser("sa");
    dataSource.setPassword("");
    return dataSource;
  }

  private boolean isLockHeld(final DataSource dataSource) throws Exception {
    try (final var connection = dataSource.getConnection();
        final var statement = connection.createStatement();
        final var result =
            statement.executeQuery("SELECT LOCKED FROM DATABASECHANGELOGLOCK WHERE ID = 1")) {
      return result.next() && result.getBoolean("LOCKED");
    }
  }

  @FunctionalInterface
  private interface TenantMigration {
    void run() throws Exception;
  }

  public static final class ParkInsideUpdate implements CustomTaskChange {

    private static final AtomicReference<Runnable> BEHAVIOUR = new AtomicReference<>(() -> {});

    static void onExecute(final Runnable behaviour) {
      BEHAVIOUR.set(behaviour);
    }

    @Override
    public void execute(final Database database) {
      BEHAVIOUR.get().run();
    }

    @Override
    public String getConfirmationMessage() {
      return "Handed control back to the test while holding this tenant's changelog lock";
    }

    @Override
    public void setUp() {}

    @Override
    public void setFileOpener(final ResourceAccessor resourceAccessor) {}

    @Override
    public ValidationErrors validate(final Database database) {
      return new ValidationErrors();
    }
  }
}
