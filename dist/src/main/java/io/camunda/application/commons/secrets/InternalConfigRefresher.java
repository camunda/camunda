/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.secrets;

import io.camunda.configuration.ConfigurationSecretReference;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Re-reads the configuration references of one physical tenant from its internal-config store and
 * pushes a changed value to each consumer that holds a client built from it. Each read goes to the
 * store, not its cache.
 *
 * <p>A consumer whose refresh fails keeps its previous client and is retried on the next run; the
 * tenant is reported as degraded until it succeeds.
 */
public final class InternalConfigRefresher {

  private static final Logger LOG = LoggerFactory.getLogger(InternalConfigRefresher.class);

  private final InternalConfigSecretResolver resolver;
  private final SecretRefreshHealth health;
  private final List<Registration> registrations = new CopyOnWriteArrayList<>();

  public InternalConfigRefresher(
      final InternalConfigSecretResolver resolver, final SecretRefreshHealth health) {
    this.resolver = resolver;
    this.health = health;
  }

  /**
   * Registers a consumer whose client is built from {@code applied}. A consumer that references no
   * secret has nothing to refresh and is not registered.
   */
  public void register(final Consumer consumer, final Map<String, String> applied) {
    if (consumer.configured().values().stream()
        .noneMatch(ConfigurationSecretReference::isReference)) {
      return;
    }
    registrations.add(new Registration(consumer, applied));
    health.refreshed(resolver.physicalTenantId(), consumer.name());
  }

  /** Runs one refresh of every registered consumer. */
  public void refresh() {
    registrations.forEach(this::refresh);
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
      health.refreshed(tenantId, consumer.name());
    } catch (final Exception e) {
      LOG.warn(
          "Failed to refresh '{}' of physical tenant '{}'; it keeps its previous client",
          consumer.name(),
          tenantId,
          e);
      health.failed(
          tenantId, consumer.name(), e.getClass().getSimpleName() + ": " + e.getMessage());
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
