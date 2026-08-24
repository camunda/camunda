/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.rdbms;

import io.camunda.application.commons.pt.PerTenantSchemaInitialization;
import io.camunda.application.commons.pt.SchemaInitialization;
import io.camunda.application.commons.pt.SingleTenantSchemaInitialization;
import io.camunda.db.rdbms.RdbmsSchemaManager;
import io.camunda.db.rdbms.RdbmsSchemaManagerRegistry;
import io.camunda.db.rdbms.exception.RdbmsSchemaVersionIncompatibleException;
import io.camunda.db.rdbms.exception.RdbmsSchemaVersionIndeterminateException;
import io.camunda.zeebe.util.VisibleForTesting;
import io.camunda.zeebe.util.retry.RetryConfiguration;
import java.time.Duration;
import java.util.Map;
import java.util.function.Function;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

/**
 * Initializes every physical tenant's RDBMS schema and reports which tenants are ready, for the
 * exporter, the request-time rejection path and the per-tenant readiness gauge that all consult
 * {@link RdbmsSchemaManagerRegistry}.
 *
 * <p>Which of two shapes it takes is decided by how many tenants there are:
 *
 * <ul>
 *   <li><b>Two or more tenants</b> — {@link PerTenantSchemaInitialization}, the isolated shape. It
 *       owns the retry loop, the startup gate and the per-tenant state; this class supplies only
 *       the storage-specific parts, exactly as {@code SearchEngineSchemaInitializer} does for
 *       Elasticsearch/OpenSearch. One tenant's failure degrades that tenant alone.
 *   <li><b>One tenant, or none</b> — {@link SingleTenantSchemaInitialization}, the synchronous
 *       shape this application has always had: apply the schema during the context refresh and let
 *       a failure abort startup.
 * </ul>
 *
 * <p>The fork is not a simplification for the trivial case; it is required. {@code RestoreApp}
 * imports the RDBMS configuration and is single-tenant by design, so on the isolated path it would
 * hold at the gate and retry forever where today it exits non-zero in seconds — and forcing it not
 * to hold is worse still, because it would then write exporter positions against a schema that may
 * not exist yet. What a one-shot job needs is "block until all settled, then fail if any failed",
 * which neither shape offers. Forking on the tenant count keeps such a process on the synchronous
 * path by construction, and makes this class a no-op for every existing single-tenant deployment.
 *
 * <p>Unlike the Elasticsearch/OpenSearch adapter, the isolated path holds startup on every node,
 * gateway or not. Holding is not incidental to the abort, it <em>is</em> the abort: {@code
 * EveryTenantTerminallyFailedException} is raised only by {@link
 * PerTenantSchemaInitialization#awaitGate()}, so a broker whose every tenant is terminal would
 * otherwise come up successfully and silently export nothing, where today it exits non-zero. The
 * asymmetry costs nothing here because a non-gateway RDBMS node's context refresh already blocks on
 * schema initialization today; Elasticsearch/OpenSearch cannot say that.
 */
@NullMarked
public class RdbmsSchemaInitializer
    implements InitializingBean, DisposableBean, RdbmsSchemaManagerRegistry {

  static final Duration MIN_RETRY_DELAY = Duration.ofMillis(500);
  static final Duration MAX_RETRY_DELAY = Duration.ofSeconds(10);
  static final int MAX_RETRIES = Integer.MAX_VALUE;

  /**
   * The backoff a degraded tenant retries on, deliberately not configurable: what a degraded node
   * needs is to keep retrying, no deployment has asked to tune that, and a property surface is
   * easier to add later than to withdraw. The values are Elasticsearch/OpenSearch's, so that a
   * tenant degrades and recovers the same way whichever secondary storage it uses.
   *
   * <p>Unbounded is the load-bearing part. A finite budget would leave every tenant that was
   * migrating during a transient database outage permanently degraded until an operator restarts
   * the node, where a node with no serviceable tenant should stay held and retrying instead. It is
   * also why {@code LiquibaseSchemaManager}'s own three attempts are not a give-up policy but a
   * transient-deadlock retry inside a single attempt: nothing about this outer budget duplicates
   * them.
   */
  @VisibleForTesting static final RetryConfiguration DEFAULT_RETRY = unboundedRetry();

  private static final Logger LOG = LoggerFactory.getLogger(RdbmsSchemaInitializer.class);

  private final Map<String, RdbmsSchemaManager> schemaManagers;
  private final SchemaInitialization initialization;

  public RdbmsSchemaInitializer(final Map<String, RdbmsSchemaManager> schemaManagersByTenant) {
    this(schemaManagersByTenant, physicalTenantId -> DEFAULT_RETRY);
  }

  @VisibleForTesting
  RdbmsSchemaInitializer(
      final Map<String, RdbmsSchemaManager> schemaManagersByTenant,
      final Function<String, RetryConfiguration> retryConfig) {
    schemaManagers = schemaManagersByTenant;
    initialization =
        isIsolated()
            ? new PerTenantSchemaInitialization(
                schemaManagers.keySet(),
                this::initializeTenant,
                RdbmsSchemaInitializer::isTerminal,
                retryConfig)
            : new SingleTenantSchemaInitialization(this::initializeSynchronously);
  }

  @Override
  public void afterPropertiesSet() throws Exception {
    if (!isIsolated()) {
      initialization.start();
      return;
    }

    LOG.info(
        "Initializing the RDBMS schema of {} physical tenants independently: {}",
        schemaManagers.size(),
        schemaManagers.keySet());

    if (!addShutdownHook()) {
      // the JVM is already shutting down, so there is nothing left for these tasks to serve
      return;
    }

    initialization.start();

    LOG.info("Holding startup until a physical tenant's RDBMS schema is initialized.");
    initialization.awaitGate();
  }

  @Override
  public void destroy() {
    initialization.close();
  }

  /**
   * The tenant screen is here rather than in either shape: this is the side that knows which
   * tenants the node has, and a tenant it does not have has no schema to have applied.
   */
  @Override
  public boolean isInitialized(final String physicalTenantId) {
    return schemaManagers.containsKey(physicalTenantId)
        && initialization.isInitialized(physicalTenantId);
  }

  /** Whether one tenant's failure has anyone else's startup to spare. */
  private boolean isIsolated() {
    return schemaManagers.size() > 1;
  }

  /**
   * The single-tenant pass, which is {@code DefaultRdbmsSchemaManagerRegistry}'s loop unchanged:
   * the failure propagates out of the context refresh, so the node exits non-zero rather than
   * coming up degraded, and it propagates unwrapped so that what an operator reads is unchanged
   * from before this class existed.
   */
  private void initializeSynchronously() throws Exception {
    for (final var tenant : schemaManagers.entrySet()) {
      LOG.info("[RDBMS Schema] Initializing schema for physical tenant '{}'.", tenant.getKey());
      tenant.getValue().initialize();
      LOG.debug("[RDBMS Schema] Schema initialized for physical tenant '{}'.", tenant.getKey());
    }
  }

  /**
   * One attempt at applying a tenant's schema on the isolated path. Any failure propagates to the
   * retry loop, which walks the cause chain to classify it — so wrapping a checked failure, which
   * the loop's {@code Consumer} cannot declare, cannot hide a terminal cause inside a retryable
   * wrapper.
   */
  @VisibleForTesting
  void initializeTenant(final String physicalTenantId) {
    try {
      schemaManagerOf(physicalTenantId).initialize();
    } catch (final RuntimeException unchecked) {
      throw unchecked;
    } catch (final Exception checked) {
      throw new SchemaInitializationFailedException(physicalTenantId, checked);
    }
  }

  private RdbmsSchemaManager schemaManagerOf(final String physicalTenantId) {
    final RdbmsSchemaManager schemaManager = schemaManagers.get(physicalTenantId);
    if (schemaManager == null) {
      // A wiring defect rather than a storage failure: no amount of retrying produces a schema
      // manager, and left unclassified it would log a stack trace every backoff interval forever.
      throw new TerminalSchemaInitializationException(
          "No schema manager is configured for physical tenant '" + physicalTenantId + "'");
    }
    return schemaManager;
  }

  /**
   * A schema whose recorded version the running code cannot migrate from stays that way however
   * often it is retried, and so does a version that cannot be determined at all — an absent data
   * source, or a stored value that is not a semantic version. Everything else is retried, including
   * a missing DDL grant: a grant can be added while the node runs, so retrying genuinely repairs
   * it.
   */
  @VisibleForTesting
  static boolean isTerminal(final Throwable failure) {
    return failure instanceof RdbmsSchemaVersionIncompatibleException
        || failure instanceof RdbmsSchemaVersionIndeterminateException
        || failure instanceof TerminalSchemaInitializationException;
  }

  /**
   * Releases the gate when the JVM is asked to stop while the context refresh is still parked at
   * it. Spring's own shutdown hook cannot do that: it closes the context, which waits for the
   * refresh this class is holding, so the two would wait on each other until a tenant happened to
   * become serviceable. It is not about the tasks themselves — those run on virtual threads and
   * never keep the JVM alive.
   *
   * @return false if the JVM is already shutting down, in which case no task should be started
   */
  private boolean addShutdownHook() {
    try {
      Runtime.getRuntime()
          .addShutdownHook(
              new Thread(
                  () -> {
                    LOG.trace("Shutdown hook triggered");
                    initialization.close();
                  }));
      return true;
    } catch (final IllegalStateException e) {
      LOG.debug("JVM is shutting down, cannot add the schema initializer shutdown hook", e);
      return false;
    }
  }

  private static RetryConfiguration unboundedRetry() {
    final var retry = new RetryConfiguration();
    retry.setMaxRetries(MAX_RETRIES);
    retry.setMinRetryDelay(MIN_RETRY_DELAY);
    retry.setMaxRetryDelay(MAX_RETRY_DELAY);
    return retry;
  }

  /** Marks a failure that no amount of retrying can repair. */
  static final class TerminalSchemaInitializationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    TerminalSchemaInitializationException(final String message) {
      super(message);
    }
  }

  /** Carries a schema manager's checked failure into the retry loop, which cannot declare one. */
  static final class SchemaInitializationFailedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    SchemaInitializationFailedException(final String physicalTenantId, final Throwable cause) {
      super(
          "Failed to initialize the RDBMS schema of physical tenant '" + physicalTenantId + "'",
          cause);
    }
  }
}
