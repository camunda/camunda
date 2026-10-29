/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.secrets;

import io.camunda.cluster.PhysicalTenantHealth;
import io.camunda.cluster.PhysicalTenantHealth.State;
import io.camunda.cluster.PhysicalTenantRegistry;
import io.camunda.configuration.ConfigurationSecretReference;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Re-reads the configuration references of one physical tenant from its internal-config store and
 * pushes a changed value to each consumer that holds a client built from it. Each read goes to the
 * store, not its cache.
 *
 * <p>A consumer whose refresh fails keeps its previous client and is retried on the next run. The
 * tenant is reported {@link State#DEGRADED} in the {@link PhysicalTenantRegistry} until every
 * consumer refreshes again; the detail names each failed consumer and why, never a secret value.
 *
 * <p>The refresher follows the tenant's lifecycle: a replaced tenant re-points it to the store of
 * its new configuration and its consumers stay registered; a removed tenant closes it together with
 * every consumer.
 */
public final class InternalConfigRefresher implements AutoCloseable {

  private static final Logger LOG = LoggerFactory.getLogger(InternalConfigRefresher.class);

  /** Marks a degraded health as reported by this refresher, so that it only clears its own. */
  static final String DEGRADED_DETAIL = "secrets could not be refreshed: ";

  private final PhysicalTenantRegistry registry;
  private final List<Registration> registrations = new CopyOnWriteArrayList<>();
  private final Map<String, String> failures = new ConcurrentHashMap<>();
  private volatile InternalConfigSecretResolver resolver;
  private volatile boolean closed;

  public InternalConfigRefresher(
      final InternalConfigSecretResolver resolver, final PhysicalTenantRegistry registry) {
    this.resolver = resolver;
    this.registry = registry;
  }

  /** Resolves the references from another store from the next run on. */
  void repoint(final InternalConfigSecretResolver resolver) {
    this.resolver = resolver;
  }

  /** Stops refreshing and closes every registered consumer, for a tenant that was removed. */
  @Override
  public void close() {
    closed = true;
    registrations.forEach(
        registration -> {
          try {
            registration.consumer().close();
          } catch (final RuntimeException e) {
            LOG.warn(
                "Failed to close '{}' of physical tenant '{}'",
                registration.consumer().name(),
                resolver.physicalTenantId(),
                e);
          }
        });
    registrations.clear();
  }

  public boolean isClosed() {
    return closed;
  }

  /**
   * Registers a consumer whose client is built from {@code applied}. A consumer that references no
   * secret has nothing to refresh and is not registered.
   */
  public void register(final Consumer consumer, final Map<String, String> applied) {
    if (closed) {
      throw new IllegalStateException(
          "The refresher of physical tenant '%s' is closed".formatted(resolver.physicalTenantId()));
    }
    if (consumer.configured().values().stream()
        .noneMatch(ConfigurationSecretReference::isReference)) {
      return;
    }
    registrations.add(new Registration(consumer, applied));
  }

  /** Runs one refresh of every registered consumer. */
  public void refresh() {
    if (!closed) {
      registrations.forEach(this::refresh);
      reportHealth();
    }
  }

  private void refresh(final Registration registration) {
    final var tenantId = resolver.physicalTenantId();
    final var consumer = registration.consumer();
    try {
      final var resolved = resolver.resolve(consumer.configured());
      if (!resolved.equals(registration.applied())) {
        consumer.apply(resolved);
        registration.applied(resolved);
        LOG.info(
            "Applied refreshed secrets to '{}' of physical tenant '{}'", consumer.name(), tenantId);
      }
      failures.remove(consumer.name());
    } catch (final Exception e) {
      LOG.warn(
          "Failed to refresh '{}' of physical tenant '{}'; it keeps its previous client",
          consumer.name(),
          tenantId,
          e);
      failures.put(consumer.name(), e.getClass().getSimpleName() + ": " + e.getMessage());
    }
  }

  /**
   * Degrades a serving tenant while a consumer fails, and restores the health this refresher
   * degraded once all succeed. A tenant that is starting, restarting or failed is left to its
   * lifecycle; the next run reports again once it serves.
   */
  private void reportHealth() {
    final var tenantId = resolver.physicalTenantId();
    final var current = registry.health(tenantId).orElse(null);
    if (current == null || registry.find(tenantId).isEmpty()) {
      return;
    }
    final var ours =
        current.state() == State.DEGRADED
            && current.detail() != null
            && current.detail().startsWith(DEGRADED_DETAIL);
    try {
      if (!failures.isEmpty()) {
        if (current.state() == State.HEALTHY || ours) {
          registry.reportHealth(
              tenantId,
              new PhysicalTenantHealth(State.DEGRADED, DEGRADED_DETAIL + new TreeMap<>(failures)));
        }
      } else if (ours) {
        registry.reportHealth(tenantId, PhysicalTenantHealth.of(State.HEALTHY));
      }
    } catch (final IllegalArgumentException removed) {
      LOG.debug("Physical tenant '{}' was removed while its secrets refreshed", tenantId);
    }
  }

  /** A component that holds a client built from configuration values of one physical tenant. */
  public interface Consumer {

    String name();

    /** The configured value per property name, each a literal or a reference. */
    Map<String, String> configured();

    /**
     * Applies the resolved values, for example by replacing a client.
     *
     * @throws Exception if they cannot be applied; the consumer must then keep its previous client
     */
    void apply(Map<String, String> resolved) throws Exception;

    /** Releases the client, once the tenant it belongs to is removed. */
    default void close() {}
  }

  private static final class Registration {

    private final Consumer consumer;
    private volatile Map<String, String> applied;

    private Registration(final Consumer consumer, final Map<String, String> applied) {
      this.consumer = consumer;
      this.applied = applied;
    }

    private Consumer consumer() {
      return consumer;
    }

    private Map<String, String> applied() {
      return applied;
    }

    private void applied(final Map<String, String> applied) {
      this.applied = applied;
    }
  }
}
