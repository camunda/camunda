/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.search;

import io.camunda.application.commons.pt.PerTenantSchemaInitialization;
import io.camunda.exporter.adapters.ClientAdapter;
import io.camunda.search.schema.SchemaManager;
import io.camunda.search.schema.SchemaManagerContainer;
import io.camunda.search.schema.SearchEngineHealthCheckPermissionException;
import io.camunda.search.schema.config.SearchEngineConfiguration;
import io.camunda.search.schema.exceptions.IncompatibleVersionException;
import io.camunda.search.schema.metrics.SchemaManagerMetrics;
import io.camunda.webapps.schema.descriptors.IndexDescriptors;
import io.camunda.zeebe.util.VisibleForTesting;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

/**
 * Binds the Elasticsearch/OpenSearch schema managers to {@link PerTenantSchemaInitialization},
 * which owns the retry loop, the startup gate and the per-tenant state. This class only supplies
 * the storage-specific parts: what one attempt does, which failures retrying cannot repair, and
 * whether this node holds startup at the gate.
 */
public class SearchEngineSchemaInitializer
    implements InitializingBean, DisposableBean, SchemaManagerContainer {

  private static final Logger LOGGER = LoggerFactory.getLogger(SearchEngineSchemaInitializer.class);
  private final Map<String, SearchEngineConfiguration> configs;
  private final Map<String, IndexDescriptors> descriptors;
  private final MeterRegistry meterRegistry;
  private final boolean holdsStartup;
  private final PerTenantSchemaInitialization initialization;

  /**
   * One entry per tenant that currently holds a client: added by the tenant's own task on its first
   * attempt, removed when the schema is applied or when the node shuts down. A tenant that keeps
   * failing keeps its entry, and its client, for its next attempt.
   */
  private final Map<String, ClientAdapter> clientsByTenant = new ConcurrentHashMap<>();

  /**
   * @param holdsStartup whether this node keeps its listening socket closed until a physical tenant
   *     is serviceable. Only a node with an HTTP gateway does; every other node has no consumer
   *     that benefits from waiting. It also decides whether the node can abort: the abort belongs
   *     to the gate, so a node that does not wait at one stays up with every tenant degraded, as it
   *     does today.
   */
  public SearchEngineSchemaInitializer(
      final Map<String, SearchEngineConfiguration> configsByTenant,
      final Map<String, IndexDescriptors> descriptorsByTenant,
      final MeterRegistry meterRegistry,
      final boolean holdsStartup) {
    configs = configsByTenant;
    descriptors = descriptorsByTenant;
    this.meterRegistry = meterRegistry;
    this.holdsStartup = holdsStartup;
    initialization =
        new PerTenantSchemaInitialization(
            configs.keySet(),
            this::initializeTenant,
            SearchEngineSchemaInitializer::isTerminal,
            tenantId -> configs.get(tenantId).schemaManager().getRetry());
  }

  @Override
  public void afterPropertiesSet() {
    LOGGER.info(
        "Initializing search engine schema for {} physical tenant(s): {}",
        configs.size(),
        configs.keySet());

    if (!addShutdownHook()) {
      // skipping schema initialization as JVM is shutting down
      return;
    }

    initialization.start();

    if (!holdsStartup) {
      LOGGER.info(
          "No HTTP gateway is enabled on this node, so schema initialization continues in the"
              + " background and startup is not held.");
      return;
    }

    LOGGER.info(
        "Holding startup until a physical tenant is serviceable, so that no client reaches this"
            + " node before it can answer.");
    initialization.awaitGate();
  }

  @Override
  public void destroy() {
    // Stop the tasks before taking their clients away, so that a task still mid-attempt fails
    // against a closed client only after it has already been told to stop, where the failure is
    // logged as a shutdown and not as a degraded tenant.
    initialization.close();
    clientsByTenant.keySet().forEach(this::releaseClientOf);
  }

  @Override
  public boolean isInitialized(final String physicalTenantId) {
    return initialization.isInitialized(physicalTenantId);
  }

  /**
   * Returns true if the schema initialization completed successfully for <em>all</em> physical
   * tenants. This can be used by dependent components to check if they should proceed with their
   * initialization.
   *
   * @return true if schema initialization completed successfully for all tenants, false otherwise
   */
  @Override
  public boolean isInitialized() {
    return !configs.isEmpty() && configs.keySet().stream().allMatch(this::isInitialized);
  }

  /**
   * One attempt at applying a tenant's schema. Any failure propagates to the retry loop.
   *
   * <p>The client is built once per tenant and reused across that tenant's attempts, not rebuilt on
   * each one: building it opens a connection pool and its I/O threads, and a tenant whose storage
   * is down retries for as long as the node runs, so per-attempt rebuilding would churn both every
   * backoff interval indefinitely. It is released as soon as the schema is applied, which is the
   * only point at which this tenant has no further use for it.
   */
  @VisibleForTesting
  void initializeTenant(final String physicalTenantId) {
    final SearchEngineConfiguration configuration = configs.get(physicalTenantId);
    final IndexDescriptors indexDescriptors = descriptors.get(physicalTenantId);
    if (indexDescriptors == null) {
      // A wiring defect rather than a storage failure: no amount of retrying produces descriptors,
      // and left unclassified it would log a stack trace every backoff interval forever.
      throw new TerminalSchemaInitializationException(
          "No index descriptors are configured for physical tenant '" + physicalTenantId + "'");
    }

    final ClientAdapter clientAdapter =
        clientsByTenant.computeIfAbsent(physicalTenantId, id -> newClientAdapter(configuration));
    try (final SchemaManager schemaManager =
        new SchemaManager(
            clientAdapter.getSearchEngineClient(),
            indexDescriptors.indices(),
            indexDescriptors.templates(),
            configuration,
            clientAdapter.objectMapper(),
            new SchemaManagerMetrics(meterRegistry, physicalTenantId))) {
      schemaManager.startupOnce();
    }
    if (configuration.schemaManager().isCreateSchema()
        && configuration.schemaManager().isHealthCheckEnabled()
        && !clientAdapter.getSearchEngineClient().isHealthy()) {
      // Not terminal: a red/unreachable cluster right after schema creation may still turn
      // yellow/green shortly after, so this attempt is retried like any other storage failure.
      // A missing 'monitor' privilege is the one health-check failure classified terminal, in
      // isTerminal() below.
      throw new SchemaNotReadyException(
          "Cluster health check failed for physical tenant '"
              + physicalTenantId
              + "' after schema"
              + " initialization.");
    }
    releaseClientOf(physicalTenantId);
  }

  /**
   * Releases a tenant's client, if it still holds one. Removing before closing is what makes this
   * safe to call from the tenant's own task and from {@link #destroy()} concurrently: whichever
   * gets the adapter closes it, and the other gets nothing.
   */
  private void releaseClientOf(final String physicalTenantId) {
    final ClientAdapter clientAdapter = clientsByTenant.remove(physicalTenantId);
    if (clientAdapter != null) {
      closeQuietly(clientAdapter, physicalTenantId);
    }
  }

  /**
   * Building the client performs no I/O, so a failure here is a static misconfiguration — an
   * unsupported database type, or connect settings that cannot be turned into a client. Retrying it
   * would never succeed, hence the terminal marker.
   */
  private static ClientAdapter newClientAdapter(final SearchEngineConfiguration configuration) {
    try {
      return ClientAdapter.of(configuration.connect());
    } catch (final Exception e) {
      throw new TerminalSchemaInitializationException("Failed to build the search client", e);
    }
  }

  @VisibleForTesting
  static void closeQuietly(final ClientAdapter clientAdapter, final String physicalTenantId) {
    try {
      clientAdapter.close();
    } catch (final Exception e) {
      // A client that cannot be released does not invalidate a schema that was applied, and
      // failing here would retry an initialization that already succeeded.
      LOGGER.debug(
          "Failed to close the search client of physical tenant '{}'", physicalTenantId, e);
    }
  }

  /**
   * A schema that the running version cannot migrate stays incompatible however often it is
   * retried, so this is terminal too. A missing 'monitor' cluster privilege is also terminal: no
   * amount of retrying grants the permission. Everything else — an unreachable cluster, a rejected
   * request, a mapping the current attempt could not validate, a cluster that has not yet turned
   * yellow/green — is retried, because it may be repaired without restarting the node.
   */
  @VisibleForTesting
  static boolean isTerminal(final Throwable failure) {
    return failure instanceof IncompatibleVersionException
        || failure instanceof TerminalSchemaInitializationException
        || failure instanceof SearchEngineHealthCheckPermissionException;
  }

  /**
   * Adds a shutdown hook to the JVM that will be triggered when the application is shutting
   *
   * @return true if the shutdown hook was added successfully, false if the JVM is already shutting
   *     down
   */
  private boolean addShutdownHook() {
    try {
      Runtime.getRuntime()
          .addShutdownHook(
              new Thread(
                  () -> {
                    LOGGER.trace("Shutdown hook triggered");
                    initialization.close();
                  }));
      return true;
    } catch (final IllegalStateException e) {
      // This can happen if the shutdown hook is added after the JVM has started shutting down.
      // In this case, we just ignore the exception.
      LOGGER.debug("JVM is shutting down, cannot add the schema initializer shutdown hook", e);
      return false;
    }
  }

  /** Marks a failure that no amount of retrying can repair. */
  static final class TerminalSchemaInitializationException extends RuntimeException {

    TerminalSchemaInitializationException(final String message) {
      super(message);
    }

    TerminalSchemaInitializationException(final String message, final Throwable cause) {
      super(message, cause);
    }
  }

  /** A retryable failure: the cluster health check did not pass after schema initialization. */
  static final class SchemaNotReadyException extends RuntimeException {

    SchemaNotReadyException(final String message) {
      super(message);
    }
  }
}
