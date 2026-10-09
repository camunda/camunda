/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.restore;

import static java.util.Objects.requireNonNull;

import io.atomix.cluster.MemberId;
import io.atomix.primitive.partition.PartitionMetadata;
import io.camunda.db.rdbms.sql.ExporterPositionMapper;
import io.camunda.zeebe.backup.api.BackupStore;
import io.camunda.zeebe.broker.partitioning.topology.PartitionDistribution;
import io.camunda.zeebe.broker.partitioning.topology.StaticConfigurationGenerator;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.dynamic.config.ClusterConfigurationManagerService;
import io.camunda.zeebe.dynamic.config.PersistedCurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.serializer.ProtoBufSerializer;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.UpdateRoutingState;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.PartitionGroupPhase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.Phase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangeState;
import io.camunda.zeebe.util.FileUtil;
import io.camunda.zeebe.util.VisibleForTesting;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Restores a whole cluster: every configured physical tenant, plus the two things that belong to
 * the node rather than to any one tenant — the shared data directory and the cluster configuration
 * file the restored brokers boot from.
 *
 * <p>Each tenant's partitions are restored by its own {@link RestoreManager}; this only decides
 * what each one restores from and in what order the shared steps happen around them.
 */
@NullMarked
public final class ClusterRestore {

  private static final Logger LOG = LoggerFactory.getLogger(ClusterRestore.class);

  /** The member that writes the topology file; see {@link #restoreTopologyFile}. */
  private static final MemberId COORDINATOR_ID = MemberId.from("0");

  private final BrokerCfg configuration;
  private final Map<String, PhysicalTenantRestoreTarget> targets;
  private final MeterRegistry meterRegistry;

  /**
   * @param configuration the broker-wide configuration: data directory, node id and cluster
   *     membership, all shared by every physical tenant
   * @param targets what each physical tenant is restored from, keyed by physical tenant id
   */
  public ClusterRestore(
      final BrokerCfg configuration,
      final Map<String, PhysicalTenantRestoreTarget> targets,
      final MeterRegistry meterRegistry) {
    if (targets.isEmpty()) {
      throw new IllegalArgumentException(
          "Expected at least one physical tenant to restore, but got none");
    }
    this.configuration = configuration;
    this.targets = Map.copyOf(targets);
    this.meterRegistry = meterRegistry;
  }

  /**
   * Restores the physical tenants named in {@code selectionPerTenant}, each from what its own
   * selection names, and then writes the topology file if this broker is the coordinator.
   *
   * <p>The selection may cover a strict subset of the configured tenants — restoring one tenant of
   * a multi-tenant cluster is a supported operation. What it may not do is name a tenant this
   * cluster has no configuration for: there would be no backup store to read it from and no
   * partition count to restore.
   *
   * @param selectionPerTenant which backups each targeted physical tenant is restored from
   */
  public void restore(
      final Map<String, RestoreSelection> selectionPerTenant,
      final TargetDataPolicy targetDataPolicy,
      final boolean validateConfig,
      final List<String> ignoreFilesInTarget)
      throws IOException, ExecutionException, InterruptedException {
    final var unknown =
        selectionPerTenant.keySet().stream()
            .filter(id -> !targets.containsKey(id))
            .sorted()
            .toList();
    if (!unknown.isEmpty()) {
      throw new IllegalArgumentException(
          "Cannot restore physical tenants %s: not configured in this cluster, which has %s"
              .formatted(unknown, targets.keySet().stream().sorted().toList()));
    }
    if (selectionPerTenant.isEmpty()) {
      throw new IllegalArgumentException(
          "Expected at least one physical tenant to restore, but got none");
    }

    final var wholeCluster = selectionPerTenant.keySet().containsAll(targets.keySet());
    final var dataDirectory = Path.of(configuration.getData().getDirectory());
    prepareTarget(
        dataDirectory, selectionPerTenant.keySet(), targetDataPolicy, ignoreFilesInTarget);

    try {
      for (final var entry : selectionPerTenant.entrySet()) {
        final var physicalTenantId = entry.getKey();
        // non-null: every key was checked against targets above
        final var target = requireNonNull(targets.get(physicalTenantId));
        LOG.info("Restoring physical tenant '{}'", physicalTenantId);
        try (final var manager =
            new RestoreManager(
                configuration,
                physicalTenantId,
                target.configuration(),
                localPartitionsOf(configuration, physicalTenantConfigurations(), physicalTenantId),
                target.backupStore().get(),
                target.exporterPositionMapper(),
                meterRegistry)) {
          manager.restore(entry.getValue(), validateConfig);
        }
        LOG.info("Successfully restored physical tenant '{}'", physicalTenantId);
      }

      if (shouldRewriteTopologyFile(targetDataPolicy, wholeCluster)
          && configuration.getCluster().getNodeId() == 0) {
        restoreTopologyFile();
      }
    } catch (final ExecutionException | InterruptedException | RuntimeException e) {
      LOG.error(
          "Failed to restore physical tenants {}. Deleting their data",
          selectionPerTenant.keySet(),
          e);
      deleteRestoredData(
          dataDirectory, selectionPerTenant.keySet(), targetDataPolicy, ignoreFilesInTarget);
      throw e;
    }
  }

  /**
   * Makes the data directory ready for what this run is about to write.
   *
   * <p>{@link TargetDataPolicy#REQUIRE_EMPTY} refuses unless the whole directory is empty — the
   * long-standing behaviour, and the only check that catches a node whose data was left behind
   * under some other name. It is what a restore of the default tenant or of every tenant gets: both
   * are recoveries of a node that is meant to start from nothing.
   *
   * <p>{@link TargetDataPolicy#REPLACE_SELECTED} instead deletes the named tenants' own partition
   * group directories. Naming a tenant explicitly is asking to replace that tenant on a node whose
   * other tenants are live and must keep their data, so an empty-directory requirement could never
   * be satisfied. Only the named tenants' directories are touched, and — same as the
   * whole-directory check above — {@code ignoreFilesInTarget} entries inside them are left alone
   * rather than deleted.
   */
  private void prepareTarget(
      final Path dataDirectory,
      final Set<String> physicalTenantIds,
      final TargetDataPolicy targetDataPolicy,
      final List<String> ignoreFilesInTarget)
      throws IOException {
    if (targetDataPolicy == TargetDataPolicy.REQUIRE_EMPTY) {
      verifyDirectoryIsEmpty(dataDirectory, ignoreFilesInTarget);
      return;
    }
    for (final var physicalTenantId : physicalTenantIds) {
      final var groupDirectory = dataDirectory.resolve(physicalTenantId);
      if (Files.exists(groupDirectory)) {
        LOG.info(
            "Replacing the data of physical tenant '{}' in {}", physicalTenantId, groupDirectory);
        deleteFolderContentsExcept(groupDirectory, ignoreFilesInTarget);
      }
    }
  }

  /**
   * Deletes what this run was restoring, and no more. A run that required an empty directory
   * empties it again, as it always has; a run that replaced named tenants deletes only their
   * partition group directories, so a failure restoring one tenant does not discard another's data
   * that was already there. Named tenants' directories keep {@code ignoreFilesInTarget} entries,
   * the same as the initial clear in {@link #prepareTarget}.
   */
  private void deleteRestoredData(
      final Path dataDirectory,
      final Set<String> physicalTenantIds,
      final TargetDataPolicy targetDataPolicy,
      final List<String> ignoreFilesInTarget)
      throws IOException {
    if (targetDataPolicy == TargetDataPolicy.REQUIRE_EMPTY) {
      FileUtil.deleteFolderContents(dataDirectory);
      return;
    }
    for (final var physicalTenantId : physicalTenantIds) {
      final var groupDirectory = dataDirectory.resolve(physicalTenantId);
      if (Files.exists(groupDirectory)) {
        deleteFolderContentsExcept(groupDirectory, ignoreFilesInTarget);
      }
    }
  }

  /**
   * Whether this run should replace the topology file with one freshly generated from static
   * configuration, rather than leave the persisted file exactly as it was.
   *
   * <p>Both conditions are required, not just one:
   *
   * <ul>
   *   <li>{@code wholeCluster} alone is not enough. An explicit {@code --tenant-id=<the-only
   *       -configured-tenant>} also covers every tenant, trivially, on a single-tenant cluster —
   *       but it is a targeted replacement ({@link TargetDataPolicy#REPLACE_SELECTED}), asking to
   *       replace only that tenant. Regenerating the whole topology file from static configuration
   *       in that case would discard whatever dynamic state the cluster had accumulated outside
   *       partition placement — routing state, exporter state, mode — for a request that named one
   *       tenant, not the cluster.
   *   <li>{@code targetDataPolicy == REQUIRE_EMPTY} alone is not enough either. The implicit
   *       default-tenant case is also {@code REQUIRE_EMPTY} (see {@code
   *       RestoreArguments#targetDataPolicy}), and on a multi-tenant cluster it restores only the
   *       default tenant — {@code wholeCluster} is what catches that the other tenants were left
   *       alone and must keep their entry in the topology file.
   * </ul>
   */
  @VisibleForTesting
  static boolean shouldRewriteTopologyFile(
      final TargetDataPolicy targetDataPolicy, final boolean wholeCluster) {
    return targetDataPolicy == TargetDataPolicy.REQUIRE_EMPTY && wholeCluster;
  }

  /**
   * Writes the topology file the restored brokers boot from: one generated fresh from static
   * configuration, carrying a restore plan with one {@link UpdateRoutingState} per partition group.
   *
   * <p>Only reached when the run covered every configured tenant, so nothing of the old cluster is
   * worth keeping and nothing else on disk still describes it. A partial run does not write this
   * file at all — see the call site for why.
   *
   * <p>The plan's phase is activated here rather than left pending. A pending phase is only
   * activated on read when a legacy (version 1) file is migrated; this is written in the current
   * format, so nothing would activate it later and the {@link UpdateRoutingState}s would never run,
   * leaving every group without a routing state.
   *
   * <p>All groups go in one phase, which is what makes the result recognizable as {@link
   * CurrentClusterConfiguration#isAfterRestore()} — the signal {@code
   * PartitionGroupExporterStateInitializer} needs to keep non-coordinators from racing the
   * post-restore version bump. One phase per group would not be post-restore by that definition,
   * and phases run strictly in sequence, so it would also serialize the groups for no reason.
   */
  @VisibleForTesting
  void restoreTopologyFile() throws IOException {
    LOG.info("Restoring topology file");
    final var file =
        Path.of(configuration.getData().getDirectory())
            .resolve(ClusterConfigurationManagerService.TOPOLOGY_FILE_NAME);
    final var generated =
        StaticConfigurationGenerator.getStaticConfiguration(
                configuration, physicalTenantConfigurations(), COORDINATOR_ID)
            .generateCurrentClusterConfiguration();
    final var restored = withRestorePlan(generated, generated.partitionGroups().keySet());

    PersistedCurrentClusterConfiguration.ofFile(file, new ProtoBufSerializer()).update(restored);
    LOG.info(
        "Successfully restored topology file {} for physical tenants {}",
        file,
        restored.partitionGroups().keySet());
  }

  /** {@code base} carrying an activated restore plan over the restored groups. */
  private CurrentClusterConfiguration withRestorePlan(
      final CurrentClusterConfiguration base, final Set<String> restoredPhysicalTenantIds) {
    final Map<String, List<PartitionGroupOperation>> operationsPerGroup =
        restoredPhysicalTenantIds.stream()
            .collect(
                Collectors.toMap(
                    groupId -> groupId,
                    groupId -> List.of(new UpdateRoutingState(COORDINATOR_ID, Optional.empty()))));
    final List<Phase> phases = List.of(PartitionGroupPhase.sequential(operationsPerGroup));
    final var plan = PhasedChangePlan.initForRestore(phases, Instant.now());
    return new CurrentClusterConfiguration(
            base.version(),
            base.globalConfiguration(),
            base.partitionGroups(),
            new PhasedChangeState(
                PhasedChangePlan.INITIAL_PLAN_ID, Map.of(plan.id(), plan), List.of()))
        .activatePendingPhase();
  }

  /**
   * The partitions of {@code physicalTenantId} that the local broker replicates according to static
   * configuration.
   *
   * <p>Derived from the distribution of <em>every</em> configured tenant, then filtered, rather
   * than from the named tenant alone. Placement is round-robin over the cluster's whole sorted
   * partition list, so the two differ: with {@code default} holding 1 partition and {@code tenanta}
   * 3, generating {@code tenanta} alone puts its partition 1 on the broker that the full
   * distribution gives its partition 3. Restoring against the narrow distribution would write each
   * partition to a broker the topology file does not expect it on.
   */
  static Set<PartitionMetadata> localPartitionsOf(
      final BrokerCfg configuration,
      final Map<String, BrokerCfg> physicalTenantConfigurations,
      final String physicalTenantId) {
    final var cluster = configuration.getCluster();
    final var localMember = MemberId.from(cluster.getZone(), cluster.getNodeId());
    final var distribution =
        new PartitionDistribution(
            StaticConfigurationGenerator.getStaticConfiguration(
                    configuration, physicalTenantConfigurations, localMember)
                .generatePartitionDistribution());
    return distribution.partitions().stream()
        .filter(partition -> partition.id().group().equals(physicalTenantId))
        .filter(partition -> partition.members().contains(localMember))
        .collect(Collectors.toUnmodifiableSet());
  }

  private Map<String, BrokerCfg> physicalTenantConfigurations() {
    return targets.entrySet().stream()
        .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().configuration()));
  }

  private void verifyDirectoryIsEmpty(final Path directory, final List<String> ignoreFilesInTarget)
      throws IOException {
    if (!directoryIsEmpty(directory, ignoreFilesInTarget)) {
      LOG.error(
          "Directory {} is not empty. Aborting restore to avoid overwriting data. Please restart with a clean directory.",
          directory);
      throw new DirectoryNotEmptyException(directory.toString());
    }
  }

  private boolean directoryIsEmpty(final Path dir, final List<String> ignoreFilesInTarget)
      throws IOException {
    if (!Files.exists(dir)) {
      return true;
    }

    try (final var entries = Files.list(dir)) {
      return entries
          // ignore configured files/directories that we don't care about, e.g. `lost+found`.
          .filter(path -> ignoreFilesInTarget.stream().noneMatch(path::endsWith))
          .findFirst()
          .isEmpty();
    }
  }

  /**
   * Deletes {@code folder}'s immediate entries, except any matching {@code ignoreFilesInTarget} —
   * the same stray entries {@link #directoryIsEmpty} leaves out of its check (e.g. {@code
   * lost+found}) are left in place here too, rather than deleted out from under whatever put them
   * there.
   */
  private static void deleteFolderContentsExcept(
      final Path folder, final List<String> ignoreFilesInTarget) throws IOException {
    if (ignoreFilesInTarget.isEmpty()) {
      FileUtil.deleteFolderContents(folder);
      return;
    }
    final List<Path> entries;
    try (final var stream = Files.list(folder)) {
      entries = stream.toList();
    }
    for (final var entry : entries) {
      if (ignoreFilesInTarget.stream().anyMatch(entry::endsWith)) {
        continue;
      }
      if (Files.isDirectory(entry)) {
        FileUtil.deleteFolder(entry);
      } else {
        Files.delete(entry);
      }
    }
  }

  /**
   * What one physical tenant is restored from.
   *
   * @param configuration that tenant's configuration, which carries its own partition count
   * @param backupStore that tenant's backup store, resolved only when the tenant is actually
   *     restored. A supplier rather than a store: a tenant may have no backup store configured at
   *     all, which must not fail a restore that does not name it
   * @param exporterPositionMapper that tenant's exported positions when its secondary storage is
   *     RDBMS, otherwise {@code null}
   */
  public record PhysicalTenantRestoreTarget(
      BrokerCfg configuration,
      Supplier<BackupStore> backupStore,
      @Nullable ExporterPositionMapper exporterPositionMapper) {}

  /** What this run may do to data already in the target directory. */
  public enum TargetDataPolicy {
    /** Refuse unless the whole data directory is empty. */
    REQUIRE_EMPTY,
    /** Delete the selected physical tenants' own directories, leaving every other tenant's. */
    REPLACE_SELECTED
  }
}
