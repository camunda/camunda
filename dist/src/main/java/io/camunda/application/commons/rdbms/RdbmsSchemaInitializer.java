/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.rdbms;

import io.camunda.application.commons.pt.PerTenantSchemaInitialization;
import io.camunda.application.commons.pt.PerTenantSchemaInitialization.DeferralCheck;
import io.camunda.application.commons.pt.SchemaInitializer;
import io.camunda.db.rdbms.RdbmsSchemaManager;
import io.camunda.db.rdbms.RdbmsSchemaManagerRegistry;
import io.camunda.db.rdbms.exception.RdbmsSchemaMigrationFailedException;
import io.camunda.db.rdbms.exception.RdbmsSchemaVersionIncompatibleException;
import io.camunda.db.rdbms.exception.RdbmsSchemaVersionIndeterminateException;
import io.camunda.zeebe.util.VisibleForTesting;
import io.camunda.zeebe.util.retry.RetryConfiguration;
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
 * <p>Every broker node uses {@link PerTenantSchemaInitialization}, including single-tenant nodes,
 * so recovery-aware deferral and retry behavior are consistent with Elasticsearch/OpenSearch.
 * RestoreApp does not create this bean; it only uses the RDBMS datasource and mapper wiring needed
 * to resolve exported positions.
 *
 * <p>Unlike the Elasticsearch/OpenSearch adapter, initialization holds startup on every node,
 * gateway or not. Holding is not incidental to the abort, it <em>is</em> the abort: {@code
 * EveryTenantTerminallyFailedException} is raised only by {@link
 * PerTenantSchemaInitialization#awaitGate()}, so a broker whose every tenant is terminal would
 * otherwise come up successfully and silently export nothing, where today it exits non-zero. The
 * asymmetry costs nothing here because a non-gateway RDBMS node's context refresh already blocks on
 * schema initialization today; Elasticsearch/OpenSearch cannot say that.
 */
@NullMarked
public class RdbmsSchemaInitializer
    implements InitializingBean, DisposableBean, RdbmsSchemaManagerRegistry, SchemaInitializer {

  private static final Logger LOG = LoggerFactory.getLogger(RdbmsSchemaInitializer.class);

  private final Map<String, RdbmsSchemaManager> schemaManagers;
  private final PerTenantSchemaInitialization initialization;

  /**
   * @param retryConfig the backoff a degraded tenant retries on, per physical tenant. Unbounded is
   *     the load-bearing default: a finite budget leaves every tenant that was migrating during a
   *     transient database outage permanently degraded until an operator restarts the node, where a
   *     node with no serviceable tenant should stay held and retrying instead. It is also why
   *     {@code LiquibaseSchemaManager}'s own three attempts are not a give-up policy but a
   *     transient-deadlock retry inside a single attempt: nothing about this outer budget
   *     duplicates them.
   */
  public RdbmsSchemaInitializer(
      final Map<String, RdbmsSchemaManager> schemaManagersByTenant,
      final Function<String, RetryConfiguration> retryConfig,
      final DeferralCheck deferralCheck) {
    schemaManagers = schemaManagersByTenant;
    initialization =
        new PerTenantSchemaInitialization(
            schemaManagers.keySet(),
            this::initializeTenant,
            RdbmsSchemaInitializer::isTerminal,
            retryConfig,
            deferralCheck);
  }

  @Override
  public void afterPropertiesSet() {
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
   * This class screens tenants because it knows which tenants the node has, and a tenant it does
   * not have has no schema to have applied.
   */
  @Override
  public boolean isInitialized(final String physicalTenantId) {
    return schemaManagers.containsKey(physicalTenantId)
        && initialization.isInitialized(physicalTenantId);
  }

  /** Applies one tenant's schema immediately for an in-process restore. */
  @Override
  public void initializeNow(final String physicalTenantId) {
    schemaManagerOf(physicalTenantId);
    initialization.initializeNow(physicalTenantId);
  }

  /**
   * One attempt at applying a tenant's schema. Any failure propagates to the retry loop, which
   * walks the cause chain to classify it — so wrapping a checked failure, which the loop's {@code
   * Consumer} cannot declare, cannot hide a terminal cause inside a retryable wrapper.
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
   * source, or a stored value that is not a semantic version — and so does a changelog that cannot
   * be applied to the schema as recorded. Everything else is retried, including a missing DDL
   * grant: a grant can be added while the node runs, so retrying genuinely repairs it.
   */
  @VisibleForTesting
  static boolean isTerminal(final Throwable failure) {
    return failure instanceof RdbmsSchemaVersionIncompatibleException
        || failure instanceof RdbmsSchemaVersionIndeterminateException
        || failure instanceof RdbmsSchemaMigrationFailedException
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
