/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import io.atomix.cluster.MemberId;
import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.configuration.physicaltenants.PhysicalTenantConfigChange;
import io.camunda.configuration.physicaltenants.PhysicalTenantConfigValidator;
import io.camunda.configuration.physicaltenants.PhysicalTenantEnvironments;
import io.camunda.zeebe.broker.partitioning.topology.ClusterConfigurationService;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationCoordinatorSupplier;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.PhysicalTenantConfigRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequestSender;
import io.camunda.zeebe.dynamic.config.api.ErrorResponse;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.ApplyPhysicalTenantConfigOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.StopPhysicalTenantOperation;
import io.camunda.zeebe.dynamic.config.state.PhysicalTenantConfigReference;
import io.camunda.zeebe.dynamic.config.state.PhysicalTenantConfigReference.Owner;
import io.camunda.zeebe.dynamic.config.state.TenantAvailability;
import io.camunda.zeebe.util.Either;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * The front door to changes of physical tenants' configurations. Both inputs use it: the management
 * API, which owns the tenants it creates, and the static configuration file of the coordinator,
 * which owns the tenants it declares. A change passes the boot validations and every {@link
 * PhysicalTenantConfigValidator} on the node that receives it, and is then sent to the coordinator,
 * which rolls it out to every member through dynamic-config.
 *
 * <p>Once the broker started, this member also brings itself in line with the cluster
 * configuration: it records the version it already runs, and starts the tenants the cluster created
 * at runtime.
 *
 * <p>The coordinator brings the cluster in line with its configuration files, through the same path
 * as the API, when it starts, when it becomes the coordinator, and whenever the files change while
 * it runs (see {@link PhysicalTenantConfigFileWatcher}): a tenant the files declare is registered,
 * created, updated or enabled again, and a tenant the files owned and no longer declare is
 * disabled, keeping its data, as a node restart with the files would do. The other members do not
 * act on their own files. A tenant owned by the API ignores the files. The {@code default} tenant
 * is changed like any other, but never disabled.
 */
@NullMarked
public final class PhysicalTenantConfigService implements AutoCloseable {

  private static final Logger LOG = Loggers.SYSTEM_LOGGER;
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
  private static final Duration RETRY_DELAY = Duration.ofSeconds(1);
  static final String API_UPDATES_FILE_TENANTS =
      "physical-tenant-rollout.api-updates-file-owned-tenants";
  static final String FILE_WATCH_INTERVAL = "physical-tenant-rollout.file-watch.interval";
  static final String FILE_WATCH_QUIET_PERIOD = "physical-tenant-rollout.file-watch.quiet-period";

  private final PhysicalTenantLifecycleService lifecycle;
  private final PhysicalTenantConfigExecutor executor;
  private final ClusterConfigurationManagementRequestSender sender;
  private final List<PhysicalTenantConfigValidator> validators;
  private final MemberId localMemberId;
  private final boolean apiUpdatesFileTenants;
  private final Supplier<ClusterConfigurationService> clusterConfigurationService;
  private final ScheduledExecutorService background =
      Executors.newSingleThreadScheduledExecutor(
          Thread.ofPlatform().name("physical-tenant-config-sync").daemon().factory());
  private final PhysicalTenantConfigFiles files;
  private final @Nullable PhysicalTenantConfigFileWatcher watcher;
  private final Duration watchInterval;
  // what the configuration files declare, as last read and stable; only the coordinator acts on it
  private volatile Map<String, Map<String, String>> fileSubtrees;
  private final Set<String> rejectedFileTenants = new HashSet<>();
  private final AtomicBoolean retryScheduled = new AtomicBoolean();
  private boolean wasCoordinator;

  public PhysicalTenantConfigService(
      final Broker broker,
      final PhysicalTenantLifecycleService lifecycle,
      final PhysicalTenantConfigExecutor executor,
      final ClusterConfigurationManagementRequestSender sender,
      final List<PhysicalTenantConfigValidator> validators,
      final ConfigurableEnvironment bootEnvironment,
      final Collection<String> bootTenants,
      final MemberId localMemberId) {
    this.lifecycle = lifecycle;
    this.executor = executor;
    this.sender = sender;
    this.validators = List.copyOf(validators);
    this.localMemberId = localMemberId;
    apiUpdatesFileTenants =
        bootEnvironment.getProperty(API_UPDATES_FILE_TENANTS, Boolean.class, false);
    clusterConfigurationService = () -> broker.getBrokerContext().getClusterConfigurationService();
    final Map<String, Map<String, String>> bootSubtrees = new TreeMap<>();
    bootTenants.forEach(
        tenantId ->
            bootSubtrees.put(
                tenantId, PhysicalTenantEnvironments.subtreeOf(bootEnvironment, tenantId)));
    bootSubtrees.forEach(executor::runsFromStaticConfiguration);
    fileSubtrees = Map.copyOf(bootSubtrees);
    files = PhysicalTenantConfigFiles.of(bootEnvironment);
    watchInterval =
        bootEnvironment.getProperty(FILE_WATCH_INTERVAL, Duration.class, Duration.ofSeconds(1));
    watcher =
        files.files().isEmpty() || watchInterval.isZero() || watchInterval.isNegative()
            ? null
            : new PhysicalTenantConfigFileWatcher(
                files,
                bootEnvironment.getProperty(
                    FILE_WATCH_QUIET_PERIOD, Duration.class, Duration.ofSeconds(2)),
                fingerprintOrNull(files),
                System::nanoTime);
    broker
        .start()
        .thenRunAsync(
            () -> {
              final var service = clusterConfigurationService.get();
              service.registerPhysicalTenantConfigChangeExecutor(executor);
              catchUp();
              service.addUpdateListener(
                  configuration -> background.execute(() -> recordRunningVersions(configuration)));
              wasCoordinator = isCoordinator();
              if (wasCoordinator) {
                reconcileFileTenants();
              }
              if (watcher != null) {
                LOG.info(
                    "Watching the configuration files {} for changes of physical tenants",
                    files.files());
                background.scheduleWithFixedDelay(
                    this::watchFiles,
                    watchInterval.toMillis(),
                    watchInterval.toMillis(),
                    MILLISECONDS);
              }
            },
            background);
  }

  private static @Nullable String fingerprintOrNull(final PhysicalTenantConfigFiles files) {
    try {
      return files.fingerprint();
    } catch (final Exception e) {
      return null;
    }
  }

  /** Every physical tenant the cluster configuration holds a configuration reference for. */
  public List<PhysicalTenantStatus> list() {
    final var configuration = currentConfiguration();
    return configuration.partitionGroups().entrySet().stream()
        .filter(entry -> entry.getValue().tenantConfig().isPresent())
        .map(entry -> status(configuration, entry.getKey(), entry.getValue()))
        .sorted((left, right) -> left.id().compareTo(right.id()))
        .toList();
  }

  public Optional<PhysicalTenantStatus> get(final String physicalTenantId) {
    final var configuration = currentConfiguration();
    return Optional.ofNullable(configuration.partitionGroup(physicalTenantId))
        .filter(group -> group.tenantConfig().isPresent())
        .map(group -> status(configuration, physicalTenantId, group));
  }

  /** Creates a tenant owned by the management API. */
  public PhysicalTenantStatus create(
      final String physicalTenantId, final Map<String, String> subtree) {
    final var brokerCfg = validate(PhysicalTenantConfigChange.create(physicalTenantId, subtree));
    send(
        PhysicalTenantConfigRequest.create(
            physicalTenantId,
            Owner.API,
            PhysicalTenantConfigCodec.encode(subtree),
            brokerCfg.getCluster().getPartitionsCount(),
            brokerCfg.getExporters().keySet()));
    return get(physicalTenantId).orElseThrow();
  }

  /**
   * Replaces the whole subtree of a tenant owned by the management API. With {@value
   * #API_UPDATES_FILE_TENANTS} set, a tenant owned by the configuration file is replaced the same
   * way, on behalf of the file; the file sends its own version again when the coordinator restarts.
   * A proof-of-concept knob, so that a benchmark can roll out changes of a file-owned tenant.
   */
  public PhysicalTenantStatus update(
      final String physicalTenantId, final Map<String, String> subtree) {
    final var fileOwned =
        apiUpdatesFileTenants
            && get(physicalTenantId).map(status -> status.owner() == Owner.FILE).orElse(false);
    return update(physicalTenantId, subtree, fileOwned ? Owner.FILE : Owner.API);
  }

  /** Removes a tenant owned by the management API; its data stays and its id stays reserved. */
  public void delete(final String physicalTenantId) {
    send(PhysicalTenantConfigRequest.delete(physicalTenantId, Owner.API));
  }

  /**
   * Cancels the rollout of an update of a tenant owned by the management API: the tenant rolls back
   * to the configuration it had before, as the next version. Members that applied the cancelled
   * version restart the tenant on it; the others restart nothing.
   */
  public PhysicalTenantStatus cancel(final String physicalTenantId) {
    send(PhysicalTenantConfigRequest.cancel(physicalTenantId, Owner.API));
    return get(physicalTenantId).orElseThrow();
  }

  private PhysicalTenantStatus update(
      final String physicalTenantId, final Map<String, String> subtree, final Owner owner) {
    final var reference =
        get(physicalTenantId)
            .orElseThrow(
                () ->
                    new PhysicalTenantConfigRejectedException(
                        PhysicalTenantConfigRejectedException.Status.NOT_FOUND,
                        "Physical tenant '%s' does not exist".formatted(physicalTenantId),
                        List.of()));
    if (reference.owner() != owner) {
      throw new PhysicalTenantConfigRejectedException(
          PhysicalTenantConfigRejectedException.Status.NOT_OWNER,
          "Physical tenant '%s' is owned by %s and cannot be changed through %s"
              .formatted(physicalTenantId, reference.owner(), owner),
          List.of());
    }
    final var current =
        executor
            .applied(physicalTenantId)
            .filter(applied -> applied.sha256().equals(reference.sha256()))
            .orElseThrow(
                () ->
                    new PhysicalTenantConfigRejectedException(
                        PhysicalTenantConfigRejectedException.Status.IN_PROGRESS,
                        "This node does not run version %d of physical tenant '%s' yet; retry"
                            .formatted(reference.version(), physicalTenantId),
                        List.of()));
    validate(PhysicalTenantConfigChange.update(physicalTenantId, current.subtree(), subtree));
    send(
        PhysicalTenantConfigRequest.update(
            physicalTenantId,
            owner,
            PhysicalTenantConfigCodec.encode(subtree),
            reference.version()));
    return get(physicalTenantId).orElseThrow();
  }

  private BrokerCfg validate(final PhysicalTenantConfigChange change) {
    runValidators(change);
    try {
      return lifecycle.validate(
          change.physicalTenantId(), PhysicalTenantConfigCodec.asProperties(change.proposed()));
    } catch (final PhysicalTenantChangeException e) {
      throw new PhysicalTenantConfigRejectedException(
          PhysicalTenantConfigRejectedException.Status.INVALID,
          "The configuration of physical tenant '%s' fails the boot validations"
              .formatted(change.physicalTenantId()),
          List.of(String.valueOf(e.getMessage())));
    }
  }

  /**
   * Runs every plugged validator. The file registration of a tenant that already runs uses this
   * alone, since the tenant passed the boot validations when the node started.
   */
  private void runValidators(final PhysicalTenantConfigChange change) {
    final var violations = new ArrayList<String>();
    validators.forEach(validator -> violations.addAll(validator.validate(change)));
    if (!violations.isEmpty()) {
      throw new PhysicalTenantConfigRejectedException(
          PhysicalTenantConfigRejectedException.Status.INVALID,
          "The configuration of physical tenant '%s' cannot be applied"
              .formatted(change.physicalTenantId()),
          violations);
    }
  }

  private CurrentClusterConfiguration send(final PhysicalTenantConfigRequest request) {
    final var response = await(sender.changePhysicalTenantConfig(request));
    if (response.isLeft()) {
      throw PhysicalTenantConfigRejectedException.of(response.getLeft());
    }
    return response.get();
  }

  private CurrentClusterConfiguration currentConfiguration() {
    final var response = await(sender.getTopology());
    if (response.isLeft()) {
      throw PhysicalTenantConfigRejectedException.of(response.getLeft());
    }
    return response.get();
  }

  private PhysicalTenantStatus status(
      final CurrentClusterConfiguration configuration,
      final String physicalTenantId,
      final PartitionGroupConfiguration group) {
    final var reference = group.tenantConfig().orElseThrow();
    final Map<String, Long> applied = new TreeMap<>();
    configuration
        .liveMembers()
        .forEach(member -> applied.put(member.id(), reference.appliedVersion(member)));
    return new PhysicalTenantStatus(
        physicalTenantId,
        reference.owner(),
        reference.version(),
        reference.sha256(),
        reference.incarnationId(),
        group.availability().state(),
        applied,
        reference.isAppliedBy(configuration.liveMembers()),
        group.pendingChanges().isPresent(),
        pendingMembers(group),
        lifecycle.localStatus(physicalTenantId).orElse(null),
        executor.rolloutStep(physicalTenantId).orElse(null));
  }

  /** The members the tenant's rollout still has to run on, in the order it runs on them. */
  private static List<String> pendingMembers(final PartitionGroupConfiguration group) {
    return group
        .pendingChanges()
        .map(
            plan ->
                plan.pendingOperations().stream()
                    .filter(
                        operation ->
                            operation instanceof ApplyPhysicalTenantConfigOperation
                                || operation instanceof StopPhysicalTenantOperation)
                    .map(operation -> operation.memberId().id())
                    .distinct()
                    .toList())
        .orElse(List.of());
  }

  /** Starts every tenant the cluster runs that this member does not run with the same config. */
  private void catchUp() {
    final var service = clusterConfigurationService.get();
    service
        .getCurrentClusterConfiguration()
        .partitionGroups()
        .forEach(
            (tenantId, group) -> {
              final var reference = group.tenantConfig().orElse(null);
              if (reference == null
                  || group.isDisabled()
                  || hasPendingApplyForThisMember(group)
                  || runsHere(tenantId, reference)) {
                return;
              }
              LOG.info(
                  "Starting physical tenant '{}' with version {} of its configuration from the"
                      + " cluster configuration",
                  tenantId,
                  reference.version());
              try {
                final var bytes =
                    service
                        .fetchPhysicalTenantConfig(tenantId, reference)
                        .get(30, TimeUnit.SECONDS);
                executor.apply(tenantId, reference, bytes).join();
              } catch (final Exception e) {
                LOG.warn(
                    "Failed to start physical tenant '{}' from the cluster configuration",
                    tenantId,
                    e);
              }
            });
    recordRunningVersions(service.getCurrentClusterConfiguration());
  }

  /**
   * Records, for every tenant that this member runs with the configuration the cluster names, that
   * it applied that version: after a start, and after the file registered a tenant that already
   * ran.
   */
  private void recordRunningVersions(final CurrentClusterConfiguration configuration) {
    configuration
        .partitionGroups()
        .forEach(
            (tenantId, group) ->
                group
                    .tenantConfig()
                    .filter(
                        reference -> reference.appliedVersion(localMemberId) < reference.version())
                    .filter(reference -> !hasPendingApplyForThisMember(group))
                    .filter(reference -> runsHere(tenantId, reference))
                    .ifPresent(
                        reference -> {
                          executor.runsVersion(tenantId, reference);
                          clusterConfigurationService
                              .get()
                              .recordPhysicalTenantConfigApplied(tenantId, reference.version());
                        }));
  }

  private boolean runsHere(final String tenantId, final PhysicalTenantConfigReference reference) {
    return executor
        .applied(tenantId)
        .filter(applied -> applied.sha256().equals(reference.sha256()))
        .isPresent();
  }

  private boolean hasPendingApplyForThisMember(final PartitionGroupConfiguration group) {
    return group
        .pendingChanges()
        .map(
            plan ->
                plan.pendingOperations().stream()
                    .anyMatch(
                        operation ->
                            operation instanceof ApplyPhysicalTenantConfigOperation
                                && operation.memberId().equals(localMemberId)))
        .orElse(false);
  }

  /**
   * Picks up a stable change of the configuration files, and a change of the coordinator: the
   * member that becomes the coordinator brings the cluster in line with its own files.
   */
  private void watchFiles() {
    try {
      final var changed = Objects.requireNonNull(watcher).poll();
      final var coordinator = isCoordinator();
      final var becameCoordinator = coordinator && !wasCoordinator;
      wasCoordinator = coordinator;
      if (changed.isPresent()) {
        fileSubtrees = changed.get();
        LOG.info(
            "The configuration files declare physical tenants {}{}",
            fileSubtrees.keySet(),
            coordinator ? "; bringing the cluster in line" : "; only the coordinator acts on it");
      }
      if (coordinator && (changed.isPresent() || becameCoordinator)) {
        reconcileFileTenants();
      }
    } catch (final RuntimeException e) {
      LOG.warn("Failed to act on the configuration files {}", files.files(), e);
    }
  }

  private boolean isCoordinator() {
    final var configuration = clusterConfigurationService.get().getCurrentClusterConfiguration();
    return localMemberId.equals(
        ClusterConfigurationCoordinatorSupplier.from(() -> configuration).getDefaultCoordinator());
  }

  /**
   * Sends every difference between the configuration files and the cluster configuration to the
   * coordinator, one tenant at a time, and tries again later while another change of a tenant runs.
   */
  private void reconcileFileTenants() {
    if (!isCoordinator()) {
      return;
    }
    final var desired = fileSubtrees;
    var retry = false;
    for (final var tenant : desired.entrySet()) {
      retry |= !trySendFileTenant(tenant.getKey(), tenant.getValue());
    }
    final var configuration = clusterConfigurationService.get().getCurrentClusterConfiguration();
    for (final var group : configuration.partitionGroups().entrySet()) {
      final var tenantId = group.getKey();
      final var ownedByTheFile =
          group
              .getValue()
              .tenantConfig()
              .filter(reference -> reference.owner() == Owner.FILE)
              .isPresent();
      if (ownedByTheFile
          && !group.getValue().isDisabled()
          && !desired.containsKey(tenantId)
          && !PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID.equals(tenantId)) {
        retry |=
            !trySendFileChange(
                tenantId, PhysicalTenantConfigRequest.disable(tenantId, Owner.FILE), "disabled");
      }
    }
    if (retry && retryScheduled.compareAndSet(false, true)) {
      background.schedule(
          () -> {
            retryScheduled.set(false);
            reconcileFileTenants();
          },
          RETRY_DELAY.toMillis(),
          MILLISECONDS);
    }
  }

  /**
   * @return false if the tenant has to be sent again later
   */
  private boolean trySendFileTenant(final String tenantId, final Map<String, String> subtree) {
    final var group =
        clusterConfigurationService.get().getCurrentClusterConfiguration().partitionGroup(tenantId);
    if (group != null && group.isRemoved()) {
      return true;
    }
    final var reference =
        group == null ? Optional.<PhysicalTenantConfigReference>empty() : group.tenantConfig();
    if (reference.filter(r -> r.owner() == Owner.API).isPresent()) {
      return true;
    }
    final var encoded = PhysicalTenantConfigCodec.encode(subtree);
    final var sha256 = PhysicalTenantConfigCodec.sha256(encoded);
    final var disabled = group != null && group.isDisabled();
    if (!disabled && reference.filter(r -> r.sha256().equals(sha256)).isPresent()) {
      return true;
    }
    if (rejectedFileTenants.contains(tenantId + "@" + sha256)) {
      return true;
    }
    try {
      if (group == null) {
        final var brokerCfg = validate(PhysicalTenantConfigChange.create(tenantId, subtree));
        send(
            PhysicalTenantConfigRequest.create(
                tenantId,
                Owner.FILE,
                encoded,
                brokerCfg.getCluster().getPartitionsCount(),
                brokerCfg.getExporters().keySet()));
        LOG.info("Created physical tenant '{}' from the configuration files", tenantId);
      } else if (reference.isEmpty()) {
        runValidators(PhysicalTenantConfigChange.create(tenantId, subtree));
        send(PhysicalTenantConfigRequest.create(tenantId, Owner.FILE, encoded, 0, Set.of()));
        LOG.info("Registered physical tenant '{}' from the configuration files", tenantId);
      } else if (disabled) {
        validate(PhysicalTenantConfigChange.create(tenantId, subtree));
        send(PhysicalTenantConfigRequest.enable(tenantId, Owner.FILE, encoded));
        LOG.info("Enabled physical tenant '{}' again from the configuration files", tenantId);
      } else {
        update(tenantId, subtree, Owner.FILE);
        LOG.info("Sent the changed configuration of physical tenant '{}' to the cluster", tenantId);
      }
      return true;
    } catch (final PhysicalTenantConfigRejectedException e) {
      if (isTransient(e)) {
        return false;
      }
      rejectedFileTenants.add(tenantId + "@" + sha256);
      LOG.error(
          "The configuration files' configuration of physical tenant '{}' was rejected: {} {}",
          tenantId,
          e.getMessage(),
          e.violations());
      return true;
    }
  }

  private boolean trySendFileChange(
      final String tenantId, final PhysicalTenantConfigRequest request, final String done) {
    try {
      send(request);
      LOG.info(
          "Physical tenant '{}' is {}: the configuration files no longer declare it",
          tenantId,
          done);
      return true;
    } catch (final PhysicalTenantConfigRejectedException e) {
      if (isTransient(e)) {
        return false;
      }
      LOG.error("Physical tenant '{}' could not be {}: {}", tenantId, done, e.getMessage());
      return true;
    }
  }

  private static boolean isTransient(final PhysicalTenantConfigRejectedException e) {
    return e.status() == PhysicalTenantConfigRejectedException.Status.IN_PROGRESS
        || e.status() == PhysicalTenantConfigRejectedException.Status.UNAVAILABLE;
  }

  private static <T> Either<ErrorResponse, T> await(
      final CompletableFuture<Either<ErrorResponse, T>> future) {
    try {
      return future.get(REQUEST_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    } catch (final ExecutionException | TimeoutException e) {
      throw new PhysicalTenantConfigRejectedException(
          PhysicalTenantConfigRejectedException.Status.UNAVAILABLE,
          "The coordinator did not answer: " + e,
          List.of());
    }
  }

  @Override
  public void close() {
    background.shutdownNow();
  }

  /**
   * The rollout of a physical tenant's configuration.
   *
   * @param appliedVersions the version each live member applied, by member id
   * @param rolledOut whether every live member applied the current version
   * @param changing whether a change of the tenant is in progress
   * @param pendingMembers the members the change still has to run on, in order; the first one is
   *     the member it runs on or waits for
   * @param local the tenant's health on the member that answers; absent where the tenant does not
   *     run
   * @param rollout what the rollout waits for on the member that answers, if it waits there
   */
  public record PhysicalTenantStatus(
      String id,
      Owner owner,
      long version,
      String sha256,
      String incarnationId,
      TenantAvailability.State state,
      Map<String, Long> appliedVersions,
      boolean rolledOut,
      boolean changing,
      List<String> pendingMembers,
      PhysicalTenantLifecycleService.@Nullable LocalStatus local,
      PhysicalTenantConfigExecutor.@Nullable RolloutStep rollout) {}
}
