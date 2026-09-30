/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.rdbms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.application.commons.pt.EveryTenantTerminallyFailedException;
import io.camunda.application.commons.pt.PerTenantSchemaInitialization.Deferral;
import io.camunda.application.commons.pt.PerTenantSchemaInitialization.DeferralCheck;
import io.camunda.application.commons.rdbms.RdbmsSchemaInitializer.TerminalSchemaInitializationException;
import io.camunda.configuration.Rdbms;
import io.camunda.db.rdbms.NoopSchemaManager;
import io.camunda.db.rdbms.RdbmsSchemaManager;
import io.camunda.db.rdbms.exception.RdbmsSchemaMigrationFailedException;
import io.camunda.db.rdbms.exception.RdbmsSchemaVersionIncompatibleException;
import io.camunda.db.rdbms.exception.RdbmsSchemaVersionIndeterminateException;
import io.camunda.db.rdbms.exception.RdbmsSchemaVersionUnreadableException;
import io.camunda.zeebe.util.retry.RetryConfiguration;
import java.sql.SQLException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Covers the storage-specific half of RDBMS schema initialization — which failures are terminal,
 * what one attempt does, which of the two shapes a node takes for its tenant count, and how both
 * leave a recovering tenant alone. The gate rule itself and the retry loop belong to {@code
 * PerTenantSchemaInitializationTest}.
 *
 * <p>No database is involved: every schema manager here is a fake, because what is under test is
 * what the initializer does with an attempt's outcome, not what an attempt does.
 */
@Timeout(60)
final class RdbmsSchemaInitializerTest {

  private static final String TENANT_A = "tenanta";
  private static final String TENANT_B = "tenantb";

  private RdbmsSchemaInitializer initializer;

  @AfterEach
  void tearDown() {
    if (initializer != null) {
      // stops any background retry loop, which would otherwise keep failing for the rest of the
      // suite
      initializer.destroy();
      initializer = null;
    }
  }

  // ---- single-tenant node: the same per-tenant path, with the original failure surfaced ----

  @Test
  void shouldInitializeASingleTenantBeforeReleasingStartup() throws Exception {
    // given
    final var manager = new FakeSchemaManager();
    initializer = initializer(Map.of(TENANT_A, manager));

    // when
    initializer.afterPropertiesSet();

    // then
    assertThat(manager.attempts()).isEqualTo(1);
    assertThat(initializer.isInitialized(TENANT_A)).isTrue();
  }

  @Test
  void shouldAbortStartupWithTheOriginalFailureWhenTheSingleTenantFailsTerminally() {
    // given - a single-tenant node has no other tenant to aggregate, so what an operator reads is
    // the schema manager's own failure
    final var failure = new RdbmsSchemaVersionIncompatibleException("8.9.0", "8.11.0");
    final var manager = FakeSchemaManager.alwaysFailingWith(failure);
    initializer = initializer(Map.of(TENANT_A, manager));

    // when / then
    assertThatThrownBy(() -> initializer.afterPropertiesSet()).isSameAs(failure);
    assertThat(manager.attempts()).isOne();
    assertThat(initializer.isInitialized(TENANT_A)).isFalse();
  }

  @Test
  void shouldRetryASingleTenantUntilItCanInitialize() throws Exception {
    // given - a missing DDL grant, which an operator can add while the node waits
    final var manager = FakeSchemaManager.failingTimes(2, new SQLException("no DDL grant"));
    initializer = initializer(Map.of(TENANT_A, manager));

    // when
    initializer.afterPropertiesSet();

    // then
    assertThat(manager.attempts()).isEqualTo(3);
    assertThat(initializer.isInitialized(TENANT_A)).isTrue();
  }

  @Test
  void shouldReportAnUnknownTenantAsNotInitialized() {
    // given
    initializer = initializer(Map.of(TENANT_A, new FakeSchemaManager()));

    // when / then
    assertThat(initializer.isInitialized("nosuchtenant")).isFalse();
  }

  @Test
  void shouldInitializeSingleTenantImmediately() {
    // given
    final var manager = new FakeSchemaManager();
    initializer = initializer(Map.of(TENANT_A, manager));

    // when
    initializer.initializeNow(TENANT_A);

    // then
    assertThat(manager.attempts()).isOne();
  }

  // ---- recovery deferral ----

  @Test
  void shouldNotTouchASingleTenantsSchemaWhileItIsRecovering() throws Exception {
    // given - the node restarted while its tenant was in recovery mode
    final var manager = new FakeSchemaManager();
    initializer =
        initializer(Map.of(TENANT_A, manager), DeferralCheck.of(ignored -> Deferral.DEFERRED));

    // when
    initializer.afterPropertiesSet();

    // then - startup proceeds, and the schema is left for the restore to apply
    assertThat(manager.attempts()).isZero();
    assertThat(initializer.isInitialized(TENANT_A)).isFalse();
  }

  @Test
  void shouldApplyADeferredSingleTenantsSchemaOnceWhenRestored() throws Exception {
    // given
    final var manager = new FakeSchemaManager();
    initializer =
        initializer(Map.of(TENANT_A, manager), DeferralCheck.of(ignored -> Deferral.DEFERRED));
    initializer.afterPropertiesSet();

    // when - the restore's explicit invocation
    initializer.initializeNow(TENANT_A);

    // then
    assertThat(manager.attempts()).isOne();
    assertThat(initializer.isInitialized(TENANT_A)).isTrue();
  }

  @Test
  void shouldReapplyASingleTenantsSchemaWhenRestoredAfterStartup() throws Exception {
    // given - the schema was applied at startup, then the storage was replaced by a restore
    final var manager = new FakeSchemaManager();
    initializer = initializer(Map.of(TENANT_A, manager));
    initializer.afterPropertiesSet();

    // when - the restore's explicit invocation
    initializer.initializeNow(TENANT_A);

    // then - the schema pass runs against the restored storage rather than trusting the startup one
    assertThat(manager.attempts()).isEqualTo(2);
    assertThat(initializer.isInitialized(TENANT_A)).isTrue();
  }

  @Test
  void shouldApplyASingleTenantsSchemaOnceItLeavesRecovery() throws Exception {
    // given - the node started while its tenant was recovering
    final var recovering = new AtomicBoolean(true);
    final var manager = new FakeSchemaManager();
    initializer =
        initializer(
            Map.of(TENANT_A, manager),
            DeferralCheck.of(ignored -> recovering.get() ? Deferral.DEFERRED : Deferral.NONE));
    initializer.afterPropertiesSet();

    // when - recovery ends without a restore
    recovering.set(false);

    // then - the schema is applied in the background, exactly once
    Awaitility.await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> assertThat(initializer.isInitialized(TENANT_A)).isTrue());
    Awaitility.await()
        .during(Duration.ofMillis(700))
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(() -> assertThat(manager.attempts()).isOne());
  }

  @Test
  void shouldRetryASingleTenantsSchemaThatFailsAfterItLeavesRecovery() throws Exception {
    // given - the node started while its tenant was recovering, and the database is not reachable
    // yet once recovery ends
    final var recovering = new AtomicBoolean(true);
    final var manager = FakeSchemaManager.failingTimes(2, new SQLException("connection refused"));
    initializer =
        initializer(
            Map.of(TENANT_A, manager),
            DeferralCheck.of(ignored -> recovering.get() ? Deferral.DEFERRED : Deferral.NONE));
    initializer.afterPropertiesSet();

    // when
    recovering.set(false);

    // then - the failures are retried rather than leaving the tenant uninitialized until a restart
    Awaitility.await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> assertThat(initializer.isInitialized(TENANT_A)).isTrue());
    assertThat(manager.attempts()).isEqualTo(3);
  }

  @Test
  void shouldNotReapplyASingleTenantsSchemaRestoredBeforeItLeavesRecovery() throws Exception {
    // given
    final var recovering = new AtomicBoolean(true);
    final var manager = new FakeSchemaManager();
    initializer =
        initializer(
            Map.of(TENANT_A, manager),
            DeferralCheck.of(ignored -> recovering.get() ? Deferral.DEFERRED : Deferral.NONE));
    initializer.afterPropertiesSet();

    // when - the restore applies it, and then the tenant leaves recovery
    initializer.initializeNow(TENANT_A);
    recovering.set(false);

    // then
    Awaitility.await()
        .during(Duration.ofMillis(700))
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(() -> assertThat(manager.attempts()).isOne());
    assertThat(initializer.isInitialized(TENANT_A)).isTrue();
  }

  @Test
  void shouldWaitForAPendingRecoveryDecisionBeforeInitializingASingleTenant() throws Exception {
    // given - discovery has not yet told whether the tenant is recovering
    final var checks = new AtomicInteger();
    final var manager = new FakeSchemaManager();
    initializer =
        initializer(
            Map.of(TENANT_A, manager),
            DeferralCheck.of(
                ignored -> checks.incrementAndGet() < 3 ? Deferral.PENDING : Deferral.NONE));

    // when
    initializer.afterPropertiesSet();

    // then - applied only once the decision resolved
    assertThat(checks).hasValue(3);
    assertThat(manager.attempts()).isOne();
    assertThat(initializer.isInitialized(TENANT_A)).isTrue();
  }

  @Test
  void shouldNotTouchARecoveringTenantsSchemaOnAMultiTenantNode() throws Exception {
    // given - tenant B is recovering, tenant A is not
    final var healthy = new FakeSchemaManager();
    final var recovering = new FakeSchemaManager();
    initializer =
        initializer(
            tenants(healthy, recovering),
            DeferralCheck.of(
                tenantId -> TENANT_B.equals(tenantId) ? Deferral.DEFERRED : Deferral.NONE));

    // when
    initializer.afterPropertiesSet();

    // then
    assertThat(initializer.isInitialized(TENANT_A)).isTrue();
    assertThat(recovering.attempts()).isZero();
    assertThat(initializer.isInitialized(TENANT_B)).isFalse();
  }

  // ---- the multi-tenant shape: isolated, retried in the background, gated ----

  @Test
  void shouldNotWithholdAServiceableTenantWhenAnotherFailsTerminally() throws Exception {
    // given - the defect this class exists to remove: one tenant's schema failure used to abort
    // the context and take every healthy tenant down with it
    final var healthy = new FakeSchemaManager();
    final var terminal =
        FakeSchemaManager.alwaysFailingWith(
            new RdbmsSchemaVersionIncompatibleException("8.9.0", "8.11.0"));
    initializer = initializer(tenants(healthy, terminal));

    // when - the gate opens on the serviceable tenant
    initializer.afterPropertiesSet();

    // then
    assertThat(initializer.isInitialized(TENANT_A)).isTrue();
    assertThat(initializer.isInitialized(TENANT_B)).isFalse();
  }

  @Test
  void shouldRecoverATenantWhoseFailureIsRepaired() throws Exception {
    // given - a missing DDL grant, which an operator can add without restarting the node
    final var healthy = new FakeSchemaManager();
    final var recovering = FakeSchemaManager.failingTimes(2, new SQLException("no DDL grant"));
    initializer = initializer(tenants(healthy, recovering));

    // when
    initializer.afterPropertiesSet();

    // then - the tenant comes back on its own, with no restart
    Awaitility.await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> assertThat(initializer.isInitialized(TENANT_B)).isTrue());
  }

  @Test
  void shouldInitializeOnlyTheRequestedTenantImmediately() {
    // given
    final var managerA = new FakeSchemaManager();
    final var managerB = new FakeSchemaManager();
    initializer = initializer(tenants(managerA, managerB));

    // when
    initializer.initializeNow(TENANT_B);

    // then
    assertThat(managerA.attempts()).isZero();
    assertThat(managerB.attempts()).isOne();
  }

  @Test
  void shouldHoldStartupUntilATenantIsServiceable() throws Exception {
    // given - neither tenant's database is reachable yet
    final var failing = FakeSchemaManager.alwaysFailingWith(new SQLException("connection refused"));
    final var alsoFailing =
        FakeSchemaManager.alwaysFailingWith(new SQLException("connection refused"));
    initializer = initializer(tenants(failing, alsoFailing));
    final var returned = new CountDownLatch(1);
    Thread.ofPlatform()
        .name("test-startup")
        .start(
            () -> {
              try {
                initializer.afterPropertiesSet();
              } catch (final Exception e) {
                throw new RuntimeException(e);
              }
              returned.countDown();
            });

    // when / then - the port stays shut rather than opening on the first connect failure and
    // serving the webapp 503 from every endpoint that needs secondary storage
    assertThat(returned.await(500, TimeUnit.MILLISECONDS)).isFalse();

    // and - shutdown still releases it
    initializer.destroy();
    assertThat(returned.await(10, TimeUnit.SECONDS)).isTrue();
  }

  @Test
  void shouldAbortStartupWhenEveryTenantFailsTerminally() {
    // given - both tenants misconfigured in a way retrying cannot repair
    initializer = initializer(bothTerminal());

    // when / then - the node takes itself down rather than coming up able to serve nobody, never
    // becoming ready, and exporting into a schema the classification just refused. The abort is
    // raised out of the gate, which is why an RDBMS node holds at one whether or not it serves
    // HTTP: a broker that skipped the gate would come up successfully and export nothing.
    assertThatThrownBy(() -> initializer.afterPropertiesSet())
        .isInstanceOf(EveryTenantTerminallyFailedException.class)
        .hasMessageContaining(TENANT_A)
        .hasMessageContaining(TENANT_B);
  }

  @Test
  void shouldReportReadyImmediatelyWhenAutoDdlIsDisabled() throws Exception {
    // given - with auto-ddl=false the operator owns the schema, so the attempt succeeds without
    // touching the database and that tenant is serviceable at once
    final var external = new NoopSchemaManager();
    final var failing = FakeSchemaManager.alwaysFailingWith(new SQLException("connection refused"));
    final var managers = new LinkedHashMap<String, RdbmsSchemaManager>();
    managers.put(TENANT_A, external);
    managers.put(TENANT_B, failing);
    initializer = initializer(managers);

    // when
    initializer.afterPropertiesSet();

    // then
    assertThat(initializer.isInitialized(TENANT_A)).isTrue();
    assertThat(initializer.isInitialized(TENANT_B)).isFalse();
  }

  // ---- classification ----

  @Test
  void shouldClassifyFailuresThatRetryingCannotRepair() {
    // given / when / then - a schema the running version cannot migrate from, a version that
    // cannot be determined at all, and a wiring defect all need an operator
    assertThat(
            RdbmsSchemaInitializer.isTerminal(
                new RdbmsSchemaVersionIncompatibleException("8.9.0", "8.11.0")))
        .isTrue();
    assertThat(
            RdbmsSchemaInitializer.isTerminal(
                new RdbmsSchemaVersionIndeterminateException("dataSource is not configured")))
        .isTrue();
    assertThat(
            RdbmsSchemaInitializer.isTerminal(
                new TerminalSchemaInitializationException("no schema manager")))
        .isTrue();
    assertThat(
            RdbmsSchemaInitializer.isTerminal(
                new RdbmsSchemaMigrationFailedException(
                    "changelog cannot be applied", new IllegalStateException("checksum mismatch"))))
        .as("an edited changeset stays mismatched however often the changelog is re-run")
        .isTrue();
  }

  @Test
  void shouldClassifyStorageFailuresAsRetryable() {
    // given / when / then - a refused connection, a missing DDL grant and a version that could not
    // be read are all repaired while the node runs, so the tenant keeps trying
    assertThat(RdbmsSchemaInitializer.isTerminal(new SQLException("connection refused"))).isFalse();
    assertThat(RdbmsSchemaInitializer.isTerminal(new SQLException("permission denied for schema")))
        .isFalse();
    assertThat(
            RdbmsSchemaInitializer.isTerminal(
                new RdbmsSchemaVersionUnreadableException("read failed", new SQLException("gone"))))
        .isFalse();
  }

  @Test
  void shouldRetryADegradedTenantWithoutABudgetThatRunsOut() {
    // given / when
    final var retry = new Rdbms().getRetry();

    // then - a finite budget would leave every tenant that was migrating during a transient
    // database outage permanently degraded until an operator restarts the node
    assertThat(retry.getMaxRetries()).isEqualTo(Integer.MAX_VALUE);

    // and - the same backoff Elasticsearch/OpenSearch degrade and recover on
    assertThat(retry.getMinRetryDelay()).isEqualTo(Duration.ofMillis(500));
    assertThat(retry.getMaxRetryDelay()).isEqualTo(Duration.ofSeconds(10));
  }

  @Test
  void shouldTreatAMissingSchemaManagerAsTerminal() {
    // given - a wiring defect: no amount of retrying produces a schema manager
    initializer = initializer(tenants(new FakeSchemaManager(), new FakeSchemaManager()));

    // when / then
    assertThatThrownBy(() -> initializer.initializeTenant("nosuchtenant"))
        .isInstanceOf(TerminalSchemaInitializationException.class)
        .hasMessageContaining("nosuchtenant");
  }

  @Test
  void shouldCarryACheckedFailureIntoTheRetryLoopWithoutHidingItsCause() {
    // given - the retry loop's Consumer cannot declare a checked exception, and the classification
    // walks the cause chain, so wrapping must not turn a terminal cause into a retryable one
    final var cause = new SQLException("wrapped");
    initializer =
        initializer(tenants(new FakeSchemaManager(), FakeSchemaManager.alwaysFailingWith(cause)));

    // when / then
    assertThatThrownBy(() -> initializer.initializeTenant(TENANT_B))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining(TENANT_B)
        .cause()
        .isSameAs(cause);
  }

  // ---- helpers ----

  /** Always holds startup at the gate, as an RDBMS node does. */
  private RdbmsSchemaInitializer initializer(final Map<String, RdbmsSchemaManager> managers) {
    return initializer(managers, DeferralCheck.of(ignored -> Deferral.NONE));
  }

  private RdbmsSchemaInitializer initializer(
      final Map<String, RdbmsSchemaManager> managers, final DeferralCheck deferralCheck) {
    return new RdbmsSchemaInitializer(managers, fastRetry(), deferralCheck);
  }

  private static Map<String, RdbmsSchemaManager> tenants(
      final RdbmsSchemaManager tenantA, final RdbmsSchemaManager tenantB) {
    final var managers = new LinkedHashMap<String, RdbmsSchemaManager>();
    managers.put(TENANT_A, tenantA);
    managers.put(TENANT_B, tenantB);
    return managers;
  }

  private static Map<String, RdbmsSchemaManager> bothTerminal() {
    return tenants(
        FakeSchemaManager.alwaysFailingWith(
            new RdbmsSchemaVersionIncompatibleException("8.9.0", "8.11.0")),
        FakeSchemaManager.alwaysFailingWith(
            new RdbmsSchemaVersionIndeterminateException("dataSource is not configured")));
  }

  /**
   * Unbounded, as the production configuration makes it: the plain {@link RetryConfiguration}
   * default is three attempts, which would quietly turn a tenant that keeps failing into one that
   * gives up and change what these tests are asserting.
   */
  private static Function<String, RetryConfiguration> fastRetry() {
    final var retry = new RetryConfiguration();
    retry.setMaxRetries(Integer.MAX_VALUE);
    retry.setMinRetryDelay(Duration.ofMillis(1));
    retry.setMaxRetryDelay(Duration.ofMillis(5));
    return physicalTenantId -> retry;
  }

  /** A schema manager whose attempts are counted and whose outcome the test decides. */
  private static final class FakeSchemaManager implements RdbmsSchemaManager {

    private final AtomicInteger attempts = new AtomicInteger();
    private final int failuresBeforeSuccess;
    private final Exception failure;

    private FakeSchemaManager() {
      this(0, null);
    }

    private FakeSchemaManager(final int failuresBeforeSuccess, final Exception failure) {
      this.failuresBeforeSuccess = failuresBeforeSuccess;
      this.failure = failure;
    }

    static FakeSchemaManager alwaysFailingWith(final Exception failure) {
      return new FakeSchemaManager(Integer.MAX_VALUE, failure);
    }

    static FakeSchemaManager failingTimes(final int times, final Exception failure) {
      return new FakeSchemaManager(times, failure);
    }

    int attempts() {
      return attempts.get();
    }

    @Override
    public void initialize() throws Exception {
      if (attempts.incrementAndGet() <= failuresBeforeSuccess) {
        throw failure;
      }
    }
  }
}
