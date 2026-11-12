/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway;

import io.atomix.cluster.MemberId;
import io.camunda.cluster.DefaultPhysicalTenantRegistry;
import io.camunda.cluster.PhysicalTenant;
import io.camunda.cluster.PhysicalTenantHealth;
import io.camunda.cluster.PhysicalTenantHealth.State;
import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.configuration.Camunda;
import io.camunda.configuration.physicaltenants.PhysicalTenantEnvironments;
import io.camunda.configuration.physicaltenants.PhysicalTenantResolver;
import io.camunda.security.configuration.EngineSecurityConfig;
import io.camunda.zeebe.broker.PhysicalTenantConfigCodec;
import io.camunda.zeebe.dynamic.config.ClusterConfigurationUpdateNotifier.ClusterConfigurationUpdateListener;
import io.camunda.zeebe.dynamic.config.impl.PhysicalTenantConfigFetcher;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.PhysicalTenantConfigReference;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Makes a standalone gateway serve the physical tenants that the brokers run, as the cluster
 * configuration it receives by gossip describes them: a tenant whose partition group carries a
 * configuration reference is served with the part of that configuration a gateway reads (see {@link
 * #gatewaySubtree}), fetched from the brokers that hold it; a newer version of it replaces the
 * tenant's gateway module; a disabled or removed group is no longer served.
 *
 * <p>The cluster configuration wins for every tenant, {@code default} and the tenants the gateway's
 * own configuration declares included: the gateway serves its own configuration of a tenant only
 * until it sees the tenant's reference, and keeps its module then only if the cluster's
 * configuration has the same gateway part.
 */
@NullMarked
public final class GatewayPhysicalTenantFollower
    implements ClusterConfigurationUpdateListener, AutoCloseable {

  private static final Logger LOG = Loggers.GATEWAY_LOGGER;
  private static final Duration RETRY_DELAY = Duration.ofSeconds(1);
  private static final Duration FETCH_TIMEOUT = Duration.ofSeconds(30);
  private static final String NAME = "name";
  private static final String SECURITY = "security.";

  private final GatewayPhysicalTenants gatewayTenants;
  private final DefaultPhysicalTenantRegistry registry;
  private final PhysicalTenantConfigFetcher fetcher;
  private final ConfigurableEnvironment bootEnvironment;
  private final Set<String> staticTenants;
  private final MemberId localMemberId;
  private final ScheduledExecutorService executor =
      Executors.newSingleThreadScheduledExecutor(
          Thread.ofPlatform().name("gateway-physical-tenants").daemon().factory());
  private final AtomicReference<CurrentClusterConfiguration> latest = new AtomicReference<>();
  private final AtomicBoolean scheduled = new AtomicBoolean();
  private final Map<String, Followed> followed = new ConcurrentHashMap<>();

  public GatewayPhysicalTenantFollower(
      final GatewayPhysicalTenants gatewayTenants,
      final DefaultPhysicalTenantRegistry registry,
      final PhysicalTenantConfigFetcher fetcher,
      final ConfigurableEnvironment bootEnvironment,
      final Set<String> staticTenants,
      final MemberId localMemberId) {
    this.gatewayTenants = gatewayTenants;
    this.registry = registry;
    this.fetcher = fetcher;
    this.bootEnvironment = bootEnvironment;
    this.staticTenants = Set.copyOf(staticTenants);
    this.localMemberId = localMemberId;
  }

  /** The gRPC-relevant part of a tenant's resolved configuration. */
  public static EngineSecurityConfig engineSecurityConfigOf(final Camunda camunda) {
    final var security = camunda.getSecurity();
    return new EngineSecurityConfig(
        security.getAuthentication(),
        security.getAuthorizations().isEnabled(),
        security.getMultiTenancy().isChecksEnabled(),
        security.getInitialization(),
        security.getCompiledIdValidationPattern(),
        security.getCompiledGroupIdValidationPattern());
  }

  @Override
  public void onClusterConfigurationUpdated(final CurrentClusterConfiguration configuration) {
    if (configuration.isUninitialized()) {
      return;
    }
    latest.set(configuration);
    schedule(Duration.ZERO);
  }

  /** The version of a tenant's configuration this gateway serves, if it follows the tenant. */
  public Optional<Long> followedVersion(final String physicalTenantId) {
    return Optional.ofNullable(followed.get(physicalTenantId)).map(Followed::version);
  }

  private void schedule(final Duration delay) {
    if (scheduled.compareAndSet(false, true)) {
      executor.schedule(this::reconcile, delay.toMillis(), TimeUnit.MILLISECONDS);
    }
  }

  private void reconcile() {
    scheduled.set(false);
    final var configuration = latest.get();
    if (configuration == null) {
      return;
    }
    var retry = false;
    for (final var entry : configuration.partitionGroups().entrySet()) {
      final var physicalTenantId = entry.getKey();
      final var group = entry.getValue();
      if (group.isDisabled()
          && !PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID.equals(physicalTenantId)) {
        stopServing(physicalTenantId);
        continue;
      }
      final var reference = group.tenantConfig().orElse(null);
      if (reference == null) {
        continue;
      }
      final var current = followed.get(physicalTenantId);
      if (current != null && current.version() >= reference.version()) {
        continue;
      }
      try {
        serve(physicalTenantId, reference, configuration);
      } catch (final Exception e) {
        LOG.warn(
            "Failed to serve version {} of physical tenant '{}' on this gateway; retrying",
            reference.version(),
            physicalTenantId,
            e);
        retry = true;
      }
    }
    if (retry) {
      schedule(RETRY_DELAY);
    }
  }

  private void serve(
      final String physicalTenantId,
      final PhysicalTenantConfigReference reference,
      final CurrentClusterConfiguration configuration)
      throws Exception {
    final var peers =
        PhysicalTenantConfigFetcher.peersToAsk(
            configuration.globalConfiguration(), reference, localMemberId);
    final var bytes =
        fetcher
            .fetch(physicalTenantId, reference, peers)
            .get(FETCH_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
    final var subtree = gatewaySubtree(PhysicalTenantConfigCodec.decode(bytes));
    final var servedFromOwnConfiguration =
        !followed.containsKey(physicalTenantId)
            && staticTenants.contains(physicalTenantId)
            && subtree.equals(
                gatewaySubtree(
                    PhysicalTenantEnvironments.subtreeOf(bootEnvironment, physicalTenantId)));
    if (!servedFromOwnConfiguration) {
      final var camunda =
          PhysicalTenantResolver.bindOne(
              PhysicalTenantEnvironments.withTenants(
                  bootEnvironment,
                  Map.of(physicalTenantId, PhysicalTenantConfigCodec.asProperties(subtree)),
                  Set.of()),
              physicalTenantId);
      gatewayTenants.put(physicalTenantId, engineSecurityConfigOf(camunda));
    }
    final var current = registry.find(physicalTenantId);
    final var tenant =
        new PhysicalTenant(
            physicalTenantId,
            subtree.get(NAME),
            current
                .map(PhysicalTenant::incarnationId)
                .orElseGet(() -> UUID.fromString(reference.incarnationId())));
    if (current.isPresent()) {
      registry.replace(tenant);
    } else {
      registry.release(physicalTenantId);
      registry.add(tenant);
    }
    registry.reportHealth(physicalTenantId, PhysicalTenantHealth.of(State.HEALTHY));
    followed.put(physicalTenantId, new Followed(reference.version(), reference.sha256()));
    LOG.info(
        "Serving physical tenant '{}' with version {} of the cluster's configuration{}",
        physicalTenantId,
        reference.version(),
        servedFromOwnConfiguration ? ", the same as this gateway's own" : "");
  }

  /**
   * The part of a tenant's configuration a gateway reads. The rest may name what only the brokers
   * declare, such as their exporters. The brokers validated the whole configuration; the gateway
   * binds this part over its own root without the checks across tenants, which compare against a
   * root that may differ from the brokers' (e.g. its secondary storage).
   */
  static Map<String, String> gatewaySubtree(final Map<String, String> subtree) {
    final var gateway = new TreeMap<String, String>();
    subtree.forEach(
        (name, value) -> {
          if (name.equals(NAME) || name.startsWith(SECURITY)) {
            gateway.put(name, value);
          }
        });
    return gateway;
  }

  private void stopServing(final String physicalTenantId) {
    final var served = gatewayTenants.known().contains(physicalTenantId);
    if (served) {
      gatewayTenants.remove(physicalTenantId);
    }
    if (registry.find(physicalTenantId).isPresent()) {
      registry.remove(physicalTenantId);
    }
    if (followed.remove(physicalTenantId) != null || served) {
      LOG.info("No longer serving physical tenant '{}', its group is disabled", physicalTenantId);
    }
  }

  @Override
  public void close() {
    executor.shutdownNow();
  }

  private record Followed(long version, String sha256) {}
}
