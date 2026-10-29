/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker;

import io.camunda.application.commons.secrets.PhysicalTenantSecretStores;
import io.camunda.application.commons.secrets.PhysicalTenantSecretStores.Owned;
import io.camunda.authentication.pt.PhysicalTenantHttpSecurity;
import io.camunda.cluster.DefaultPhysicalTenantRegistry;
import io.camunda.cluster.PhysicalTenant;
import io.camunda.cluster.PhysicalTenantHealth;
import io.camunda.cluster.PhysicalTenantHealth.State;
import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.configuration.Camunda;
import io.camunda.configuration.physicaltenants.PhysicalTenantEnvironments;
import io.camunda.configuration.physicaltenants.PhysicalTenantResolver;
import io.camunda.secretstore.SecretStoreRegistry;
import io.camunda.security.api.model.scope.ScopeReloadResult;
import io.camunda.zeebe.broker.PhysicalTenantChangeException.Reason;
import io.camunda.zeebe.broker.system.PhysicalTenantContext;
import io.camunda.zeebe.broker.system.PhysicalTenantModules;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.gateway.GatewayPhysicalTenants;
import java.time.Duration;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReentrantLock;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Adds, replaces and removes physical tenants on this node while it runs, without a node restart.
 * The other tenants keep serving.
 *
 * <p>A change carries the whole configuration subtree of one tenant, with names relative to {@code
 * camunda.physical-tenants.<id>}. It passes the boot validations before anything changes: the
 * resolution of the whole tenant set, the exporters of the tenant and the per-tenant broker
 * validations. A change that fails them leaves the node as it was.
 *
 * <p>A change stops and starts the tenant module: the broker module through {@link
 * PhysicalTenantModules} and the gateway module through {@link GatewayPhysicalTenants}. The
 * tenant's HTTP security (its scoped chains in the Camunda Security Library) changes through {@link
 * PhysicalTenantRestSecurity}: an added tenant gets it once its module started, so its REST API
 * serves once it can; a replaced tenant gets it before its module stops, and a change the library
 * keeps back (an identity provider that cannot be reached) rejects the whole change, so REST and
 * gRPC never serve different configurations of the tenant; a removed tenant loses it first, so its
 * REST API answers 404 while its module stops.
 *
 * <p>Only one change is applied at a time; a concurrent change is rejected, not queued.
 */
@NullMarked
public final class PhysicalTenantLifecycleService {

  private static final Logger LOG = Loggers.SYSTEM_LOGGER;
  private static final SecretStoreRegistry NOT_BUILT_YET = new SecretStoreRegistry(Map.of());
  private static final String SECURITY_NOT_APPLIED =
      "keeps its previous HTTP security, the change is not applied: ";

  private final Broker broker;
  private final SystemContextLoader systemContextLoader;
  private final Camunda rootCamunda;
  private final DefaultPhysicalTenantRegistry registry;
  private final Duration timeout;
  private final List<PhysicalTenantResources> resources;

  private final PhysicalTenantSecretStores secretStores;
  private final PhysicalTenantRestSecurity restSecurity;
  // the job secret stores built for a runtime context, released when the context is closed; the
  // stores of a boot context belong to the application context
  private final Map<PhysicalTenantContext, Owned<SecretStoreRegistry>> ownedSecretStores =
      Collections.synchronizedMap(new IdentityHashMap<>());
  private final ReentrantLock changeLock = new ReentrantLock();
  private final Map<String, Camunda> configurations = new ConcurrentHashMap<>();
  private final Map<String, PhysicalTenantContext> contexts = new ConcurrentHashMap<>();
  private ConfigurableEnvironment environment;

  public PhysicalTenantLifecycleService(
      final Broker broker,
      final SystemContextLoader systemContextLoader,
      final ConfigurableEnvironment bootEnvironment,
      final Camunda rootCamunda,
      final PhysicalTenantResolver bootResolver,
      final DefaultPhysicalTenantRegistry registry,
      final Duration timeout,
      final List<PhysicalTenantResources> resources,
      final PhysicalTenantSecretStores secretStores,
      final PhysicalTenantRestSecurity restSecurity) {
    this.broker = broker;
    this.resources = List.copyOf(resources);
    this.secretStores = secretStores;
    this.restSecurity = restSecurity;
    this.systemContextLoader = systemContextLoader;
    environment = bootEnvironment;
    this.rootCamunda = rootCamunda;
    this.registry = registry;
    this.timeout = timeout;
    configurations.putAll(bootResolver.getAll());
    contexts.putAll(broker.getSystemContext().getPhysicalTenantContexts());
    broker
        .start()
        .thenAccept(
            started ->
                registry.tenants().forEach(tenant -> markHealthyWhenReady(tenant, modules())));
  }

  /**
   * Adds a tenant and starts its module.
   *
   * @return the new incarnation; it is {@link State#HEALTHY} once its partitions are installed
   * @throws PhysicalTenantChangeException if the change is rejected, or the module did not start
   */
  public PhysicalTenant add(
      final String physicalTenantId, final Map<String, Object> configuration) {
    return add(physicalTenantId, configuration, UUID.randomUUID());
  }

  /**
   * Adds a tenant as the given incarnation, for an incarnation that the cluster agreed on.
   *
   * @see #add(String, Map)
   */
  public PhysicalTenant add(
      final String physicalTenantId,
      final Map<String, Object> configuration,
      final UUID incarnationId) {
    return underChangeLock(
        () -> {
          if (registry.find(physicalTenantId).isPresent()) {
            throw rejected(Reason.CONFLICT, physicalTenantId, "it already exists", null);
          }
          if (registry.isRemoved(physicalTenantId)) {
            throw rejected(
                Reason.CONFLICT,
                physicalTenantId,
                "the id was removed and stays reserved until its data is purged",
                null);
          }
          final var candidate = resolve(physicalTenantId, configuration);
          final var tenant = new PhysicalTenant(physicalTenantId, candidate.name(), incarnationId);
          registry.add(tenant);
          commit(physicalTenantId, candidate);
          startModule(tenant, candidate.context());
          addSecurity(physicalTenantId, candidate);
          return tenant;
        });
  }

  /**
   * Replaces the configuration of a tenant and restarts only its module. The tenant keeps its
   * incarnation.
   *
   * @throws PhysicalTenantChangeException if the change is rejected, or the module did not start
   */
  public PhysicalTenant replace(
      final String physicalTenantId, final Map<String, Object> configuration) {
    return underChangeLock(
        () -> {
          final var current = requireChangeable(physicalTenantId);
          final var candidate = resolve(physicalTenantId, configuration);
          replaceSecurity(physicalTenantId, candidate);
          registry.reportHealth(
              physicalTenantId,
              new PhysicalTenantHealth(State.RESTARTING, "applying a configuration change"));
          gateway()
              .ifPresent(
                  gateway ->
                      gateway.markUnavailable(
                          physicalTenantId, "its configuration change is being applied"));
          try {
            stopModule(physicalTenantId);
          } catch (final RuntimeException e) {
            close(physicalTenantId, candidate.context());
            registry.reportHealth(
                physicalTenantId,
                new PhysicalTenantHealth(State.FAILED, "module failed to stop: " + e));
            throw rejected(Reason.FAILED, physicalTenantId, "its module failed to stop", e);
          }
          final var replaced = current.withName(candidate.name());
          registry.replace(replaced);
          commit(physicalTenantId, candidate);
          startModule(replaced, candidate.context());
          return replaced;
        });
  }

  /**
   * Stops the module of a tenant and removes it from this node. Its data stays on disk and its id
   * stays reserved.
   *
   * @throws PhysicalTenantChangeException if the change is rejected
   */
  public void remove(final String physicalTenantId) {
    underChangeLock(
        () -> {
          requireChangeable(physicalTenantId);
          restSecurity.remove(physicalTenantId);
          gateway().ifPresent(gateway -> gateway.remove(physicalTenantId));
          stopModule(physicalTenantId);
          releaseResources(physicalTenantId);
          registry.remove(physicalTenantId);
          configurations.remove(physicalTenantId);
          environment =
              PhysicalTenantEnvironments.withTenants(
                  environment, Map.of(), Set.of(physicalTenantId));
          return physicalTenantId;
        });
  }

  /**
   * Runs the boot validations on a configuration of a tenant without changing anything.
   *
   * @return the broker configuration the tenant would run with
   * @throws PhysicalTenantChangeException with {@link Reason#INVALID} if the validations fail
   */
  public BrokerCfg validate(
      final String physicalTenantId, final Map<String, Object> configuration) {
    final var candidate = resolve(physicalTenantId, configuration);
    close(physicalTenantId, candidate.context());
    return candidate.context().config();
  }

  /**
   * Resolves the configuration a tenant would run with, the root merged with the given subtree,
   * without the boot validations of {@link #validate}.
   *
   * @throws PhysicalTenantChangeException with {@link Reason#INVALID} if it does not resolve
   */
  public Camunda effectiveConfiguration(
      final String physicalTenantId, final Map<String, Object> configuration) {
    try {
      return PhysicalTenantResolver.of(
              PhysicalTenantEnvironments.withTenants(
                  environment, Map.of(physicalTenantId, configuration), Set.of()),
              rootCamunda)
          .forPhysicalTenant(physicalTenantId);
    } catch (final RuntimeException e) {
      throw rejected(Reason.INVALID, physicalTenantId, e.getMessage(), e);
    }
  }

  /**
   * @return completes once every local partition of the tenant is installed after its last start
   */
  public CompletableFuture<Void> awaitReady(final String physicalTenantId) {
    return modules().awaitReady(physicalTenantId);
  }

  /** The health of a tenant on this node and the last change of its HTTP security. */
  public Optional<LocalStatus> localStatus(final String physicalTenantId) {
    return registry
        .health(physicalTenantId)
        .map(
            health ->
                new LocalStatus(
                    health.state(),
                    health.detail(),
                    restSecurity.status(physicalTenantId).orElse(null)));
  }

  /** The resolved configuration a tenant currently runs with. */
  public Optional<Camunda> configuration(final String physicalTenantId) {
    return Optional.ofNullable(configurations.get(physicalTenantId));
  }

  private Candidate resolve(
      final String physicalTenantId, final Map<String, Object> configuration) {
    final var candidateEnvironment =
        PhysicalTenantEnvironments.withTenants(
            environment, Map.of(physicalTenantId, configuration), Set.of());
    final PhysicalTenantResolver resolver;
    final Camunda camunda;
    try {
      resolver = PhysicalTenantResolver.of(candidateEnvironment, rootCamunda);
      camunda = resolver.forPhysicalTenant(physicalTenantId);
      camunda.getSecrets().getRefreshInterval();
    } catch (final RuntimeException e) {
      throw rejected(Reason.INVALID, physicalTenantId, e.getMessage(), e);
    }

    final PhysicalTenantContext context;
    try {
      secretStores.validate(physicalTenantId, camunda.getSecrets());
      // the stores are built when the module starts, see withOwnJobStores
      context =
          systemContextLoader.buildPhysicalTenantContext(physicalTenantId, camunda, NOT_BUILT_YET);
    } catch (final RuntimeException e) {
      throw rejected(Reason.INVALID, physicalTenantId, e.getMessage(), e);
    }
    try {
      broker
          .getSystemContext()
          .getPhysicalTenantConfigurationValidator()
          .validate(physicalTenantId, context);
    } catch (final RuntimeException e) {
      close(physicalTenantId, context);
      throw rejected(Reason.INVALID, physicalTenantId, e.getMessage(), e);
    }
    return new Candidate(
        candidateEnvironment, camunda, context, resolver.nameOf(physicalTenantId).orElse(null));
  }

  /**
   * Gives an added tenant its HTTP security. A tenant whose security cannot be built keeps running
   * and fails closed on REST (404) until a later change of its configuration applies; it reports
   * {@link State#DEGRADED} meanwhile.
   */
  private void addSecurity(final String physicalTenantId, final Candidate candidate) {
    final String why;
    try {
      final var result =
          restSecurity.apply(physicalTenantId, candidate.environment(), candidate.configuration());
      if (result.isEmpty() || PhysicalTenantHttpSecurity.takesEffect(result.get())) {
        return;
      }
      why = SECURITY_NOT_APPLIED + result.get().outcome() + ": " + failureOf(physicalTenantId);
    } catch (final RuntimeException e) {
      LOG.warn("Physical tenant '{}' has no HTTP security", physicalTenantId, e);
      registry.reportHealth(
          physicalTenantId,
          new PhysicalTenantHealth(
              State.DEGRADED,
              "has no HTTP security, its REST API answers 404: "
                  + PhysicalTenantRestSecurity.describe(e)));
      return;
    }
    registry.reportHealth(physicalTenantId, new PhysicalTenantHealth(State.DEGRADED, why));
  }

  /**
   * Changes the HTTP security of a replaced tenant before its module stops. If the security library
   * keeps the previous security, the whole change is rejected and the tenant keeps running as it
   * was, reporting {@link State#DEGRADED}; the caller retries.
   */
  private void replaceSecurity(final String physicalTenantId, final Candidate candidate) {
    final ScopeReloadResult result;
    try {
      result =
          restSecurity
              .apply(physicalTenantId, candidate.environment(), candidate.configuration())
              .orElse(null);
    } catch (final RuntimeException e) {
      restSecurity.revert(physicalTenantId);
      close(physicalTenantId, candidate.context());
      final var why =
          "its HTTP security could not change: " + PhysicalTenantRestSecurity.describe(e);
      registry.reportHealth(physicalTenantId, new PhysicalTenantHealth(State.DEGRADED, why));
      throw rejected(Reason.SECURITY, physicalTenantId, why, e);
    }
    if (result == null || PhysicalTenantHttpSecurity.takesEffect(result)) {
      return;
    }
    restSecurity.revert(physicalTenantId);
    close(physicalTenantId, candidate.context());
    final var why = SECURITY_NOT_APPLIED + result.outcome() + ": " + failureOf(physicalTenantId);
    registry.reportHealth(physicalTenantId, new PhysicalTenantHealth(State.DEGRADED, why));
    throw rejected(Reason.SECURITY, physicalTenantId, why, result.failure());
  }

  private String failureOf(final String physicalTenantId) {
    return restSecurity
        .status(physicalTenantId)
        .map(PhysicalTenantRestSecurity.Status::failure)
        .orElse("no cause reported");
  }

  private void commit(final String physicalTenantId, final Candidate candidate) {
    environment = candidate.environment();
    configurations.put(physicalTenantId, candidate.configuration());
    contexts.put(physicalTenantId, candidate.context());
  }

  private void startModule(final PhysicalTenant tenant, final PhysicalTenantContext candidate) {
    final var modules = modules();
    var context = candidate;
    try {
      final var configuration = Objects.requireNonNull(configurations.get(tenant.id()));
      context = withOwnJobStores(tenant.id(), configuration, candidate);
      resources.forEach(resource -> resource.apply(tenant.id(), configuration));
      await(modules.start(tenant.id(), context));
    } catch (final RuntimeException e) {
      registry.reportHealth(
          tenant.id(), new PhysicalTenantHealth(State.FAILED, "module failed to start: " + e));
      gateway()
          .ifPresent(
              gateway -> {
                gateway.put(tenant.id(), candidate.securityConfig());
                gateway.markUnavailable(tenant.id(), "its module failed to start");
              });
      throw rejected(Reason.FAILED, tenant.id(), "its module failed to start", e);
    }
    gateway().ifPresent(gateway -> gateway.put(tenant.id(), candidate.securityConfig()));
    markHealthyWhenReady(tenant, modules);
  }

  /**
   * Builds the secret stores that the tenant's jobs resolve from, for this start alone. They are
   * built only once the previous module of the tenant stopped and released its own: the stores of
   * both would publish the same meters, and releasing the previous ones would remove them.
   */
  private PhysicalTenantContext withOwnJobStores(
      final String physicalTenantId,
      final Camunda configuration,
      final PhysicalTenantContext candidate) {
    final var jobStores = secretStores.jobStores(physicalTenantId, configuration.getSecrets());
    final var context =
        new PhysicalTenantContext(
            candidate.securityConfig(),
            candidate.authorizationConverter(),
            candidate.featureFlags(),
            candidate.config(),
            candidate.exporterRepository(),
            jobStores.value());
    ownedSecretStores.put(context, jobStores);
    contexts.put(physicalTenantId, context);
    return context;
  }

  private void markHealthyWhenReady(
      final PhysicalTenant tenant, final PhysicalTenantModules modules) {
    final UUID incarnation = tenant.incarnationId();
    modules
        .awaitReady(tenant.id())
        .whenComplete(
            (ready, error) -> {
              if (error != null
                  || registry
                      .find(tenant.id())
                      .filter(current -> current.incarnationId().equals(incarnation))
                      .isEmpty()) {
                return;
              }
              final var health = registry.health(tenant.id()).map(PhysicalTenantHealth::state);
              if (health
                  .filter(state -> state == State.STARTING || state == State.RESTARTING)
                  .isPresent()) {
                registry.reportHealth(tenant.id(), PhysicalTenantHealth.of(State.HEALTHY));
              }
            });
  }

  private void stopModule(final String physicalTenantId) {
    final var modules = modules();
    if (modules.running().contains(physicalTenantId)) {
      await(modules.stop(physicalTenantId));
    }
    final var context = contexts.remove(physicalTenantId);
    if (context != null) {
      close(physicalTenantId, context);
    }
  }

  private void releaseResources(final String physicalTenantId) {
    resources.forEach(
        resource -> {
          try {
            resource.release(physicalTenantId);
          } catch (final RuntimeException e) {
            LOG.warn(
                "Failed to release a resource of removed physical tenant '{}'",
                physicalTenantId,
                e);
          }
        });
  }

  private PhysicalTenant requireChangeable(final String physicalTenantId) {
    if (PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID.equals(physicalTenantId)) {
      throw rejected(
          Reason.CONFLICT,
          physicalTenantId,
          "the default tenant is the root configuration and changes only with a node restart",
          null);
    }
    return registry
        .find(physicalTenantId)
        .orElseThrow(() -> rejected(Reason.CONFLICT, physicalTenantId, "it does not exist", null));
  }

  private PhysicalTenantModules modules() {
    final var context = broker.getBrokerContext();
    if (context == null) {
      throw new IllegalStateException("The broker has not started yet");
    }
    return context.getPhysicalTenantModules();
  }

  private Optional<GatewayPhysicalTenants> gateway() {
    return Optional.ofNullable(broker.getBrokerContext())
        .map(context -> context.getEmbeddedGatewayService())
        .map(gatewayService -> gatewayService.get().physicalTenants());
  }

  private void await(final CompletableFuture<Void> future) {
    try {
      future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    } catch (final ExecutionException e) {
      throw e.getCause() instanceof final RuntimeException runtime
          ? runtime
          : new IllegalStateException(e.getCause());
    } catch (final TimeoutException e) {
      throw new IllegalStateException("Timed out after " + timeout, e);
    }
  }

  private void close(final String physicalTenantId, final PhysicalTenantContext context) {
    try {
      context.exporterRepository().close();
    } catch (final Exception e) {
      LOG.warn("Failed to close the exporters of physical tenant '{}'", physicalTenantId, e);
    }
    final var jobStores = ownedSecretStores.remove(context);
    if (jobStores != null) {
      try {
        jobStores.close();
      } catch (final RuntimeException e) {
        LOG.warn("Failed to close the secret stores of physical tenant '{}'", physicalTenantId, e);
      }
    }
  }

  private <T> T underChangeLock(final Change<T> change) {
    if (!changeLock.tryLock()) {
      throw new PhysicalTenantChangeException(
          Reason.IN_PROGRESS, "Another physical tenant change is in progress", null);
    }
    try {
      return change.apply();
    } finally {
      changeLock.unlock();
    }
  }

  private static PhysicalTenantChangeException rejected(
      final Reason reason,
      final String physicalTenantId,
      final @Nullable String why,
      final @Nullable Throwable cause) {
    return new PhysicalTenantChangeException(
        reason, "Cannot change physical tenant '%s': %s".formatted(physicalTenantId, why), cause);
  }

  @FunctionalInterface
  private interface Change<T> {
    T apply();
  }

  /**
   * The health of a tenant on this node, and the last change of its HTTP security.
   *
   * @param httpSecurity empty until the tenant's HTTP security changed on this node
   */
  public record LocalStatus(
      State health,
      @Nullable String healthDetail,
      PhysicalTenantRestSecurity.@Nullable Status httpSecurity) {}

  private record Candidate(
      ConfigurableEnvironment environment,
      Camunda configuration,
      PhysicalTenantContext context,
      @Nullable String name) {}
}
