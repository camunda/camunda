/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.restore;

import static io.camunda.zeebe.restore.ClusterRestore.TargetDataPolicy.REPLACE_SELECTED;
import static io.camunda.zeebe.restore.ClusterRestore.TargetDataPolicy.REQUIRE_EMPTY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.atomix.cluster.MemberId;
import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.dynamic.config.ClusterConfigurationManagerService;
import io.camunda.zeebe.dynamic.config.PersistedCurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.serializer.ProtoBufSerializer;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.UpdateRoutingState;
import io.camunda.zeebe.restore.ClusterRestore.PhysicalTenantRestoreTarget;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ClusterRestoreTest {

  private static final String OTHER_TENANT = "tenanta";
  private static final MemberId LOCAL_MEMBER = MemberId.from("0");

  /**
   * The data directory is the node's, not any one physical tenant's, so {@link ClusterRestore}
   * verifies it rather than each {@link RestoreManager} — after the first tenant restores into it,
   * it is no longer empty.
   */
  @Test
  void shouldRejectRestoringNoPhysicalTenantAtAll(@TempDir final Path dir) {
    assertThatThrownBy(() -> clusterRestore(dir, Map.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("at least one physical tenant");
  }

  private static Map<String, RestoreSelection> selection(final String... physicalTenantIds) {
    final Map<String, RestoreSelection> selection = new LinkedHashMap<>();
    for (final var physicalTenantId : physicalTenantIds) {
      selection.put(physicalTenantId, RestoreSelection.ofBackupIds(List.of(1L)));
    }
    return selection;
  }

  private static byte[] fileContent(final Path dir) throws IOException {
    return Files.readAllBytes(dir.resolve(ClusterConfigurationManagerService.TOPOLOGY_FILE_NAME));
  }

  private static CurrentClusterConfiguration read(final Path dir) {
    return PersistedCurrentClusterConfiguration.ofFile(
            dir.resolve(ClusterConfigurationManagerService.TOPOLOGY_FILE_NAME),
            new ProtoBufSerializer())
        .getConfiguration();
  }

  /**
   * A single-broker cluster restoring the given physical tenants, each with its own partition count
   * so the written groups can be told apart by the partitions they carry.
   */
  private static ClusterRestore clusterRestore(
      final Path dir, final Map<String, Integer> partitionCountPerTenant) {
    final var root = brokerCfg(dir, partitionCountPerTenant.getOrDefault("default", 1));
    final Map<String, PhysicalTenantRestoreTarget> targets = new LinkedHashMap<>();
    partitionCountPerTenant.forEach(
        (physicalTenantId, partitionCount) ->
            targets.put(
                physicalTenantId,
                new PhysicalTenantRestoreTarget(
                    PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID.equals(physicalTenantId)
                        ? root
                        : brokerCfg(dir, partitionCount),
                    TestRestorableBackupStore::new,
                    null)));
    return new ClusterRestore(root, targets, new SimpleMeterRegistry());
  }

  private static BrokerCfg brokerCfg(final Path dir, final int partitionCount) {
    final var configuration = new BrokerCfg();
    configuration.getData().setDirectory(dir.toString());
    final var cluster = configuration.getCluster();
    cluster.setNodeId(0);
    cluster.setClusterSize(1);
    cluster.setReplicationFactor(1);
    cluster.setPartitionsCount(partitionCount);
    return configuration;
  }

  @Nested
  class DataDirectory {

    @Test
    void shouldFailWhenDirectoryIsNotEmpty(@TempDir final Path dir) throws IOException {
      // given
      final var clusterRestore = clusterRestore(dir, Map.of("default", 1));

      // when
      Files.createDirectory(dir.resolve("other-data"));

      // then
      assertThatThrownBy(
              () -> clusterRestore.restore(selection("default"), REQUIRE_EMPTY, false, List.of()))
          .isInstanceOf(DirectoryNotEmptyException.class);
    }

    @Test
    void shouldIgnoreConfigurableFilesInTarget(@TempDir final Path dir) throws IOException {
      // given
      final var clusterRestore = clusterRestore(dir, Map.of("default", 1));

      // when - create ignored files
      Files.createDirectory(dir.resolve("lost+found"));
      Files.createFile(dir.resolve(".DS_Store"));
      Files.createFile(dir.resolve("Thumbs.db"));

      // then - the directory check passes and the restore proceeds far enough to look for backups,
      // which is where it fails: the fake backup store holds none
      assertThatThrownBy(
              () ->
                  clusterRestore.restore(
                      selection("default"),
                      REQUIRE_EMPTY,
                      false,
                      List.of("lost+found", ".DS_Store", "Thumbs.db")))
          .isInstanceOf(ExecutionException.class)
          .cause()
          .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void shouldFailWhenNonIgnoredFileExists(@TempDir final Path dir) throws IOException {
      // given
      final var clusterRestore = clusterRestore(dir, Map.of("default", 1));

      // when
      Files.createDirectory(dir.resolve("lost+found"));
      Files.createFile(dir.resolve("important-data.txt"));

      // then
      assertThatThrownBy(
              () ->
                  clusterRestore.restore(
                      selection("default"), REQUIRE_EMPTY, false, List.of("lost+found")))
          .isInstanceOf(DirectoryNotEmptyException.class);
    }
  }

  /**
   * The topology file the restored brokers boot from.
   *
   * <p>Three properties matter to them, and none is observable from the restored partition data:
   * every restored physical tenant has a partition group, each group's restore operation is
   * actually pending, and the whole configuration is recognizable as post-restore. Miss the second
   * and no group ever gets a routing state; miss the third and every non-coordinator races the
   * coordinator's post-restore version bump and shuts itself down.
   */
  @Nested
  class TopologyFile {

    @Test
    void shouldWriteAGroupForEveryRestoredPhysicalTenant(@TempDir final Path dir)
        throws IOException {
      // given
      final var clusterRestore = clusterRestore(dir, Map.of("default", 3, OTHER_TENANT, 2));

      // when
      clusterRestore.restoreTopologyFile();

      // then
      final var written = read(dir);
      assertThat(written.partitionGroups())
          .containsOnlyKeys(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID, OTHER_TENANT);
      assertThat(
              written
                  .partitionGroup(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID)
                  .members()
                  .get(LOCAL_MEMBER)
                  .partitions())
          .containsOnlyKeys(1, 2, 3);
      assertThat(written.partitionGroup(OTHER_TENANT).members().get(LOCAL_MEMBER).partitions())
          .containsOnlyKeys(1, 2);
    }

    @Test
    void shouldActivateTheRestoreOperationOfEveryGroup(@TempDir final Path dir) throws IOException {
      // given
      final var clusterRestore = clusterRestore(dir, Map.of("default", 1, OTHER_TENANT, 1));

      // when
      clusterRestore.restoreTopologyFile();

      // then — activated, not merely pending on the plan: nothing activates it later, because only
      // a legacy-format file is migrated (and activated) on read
      read(dir)
          .partitionGroups()
          .forEach(
              (groupId, group) -> {
                assertThat(group.pendingChanges())
                    .describedAs("group %s has an activated change", groupId)
                    .isPresent();
                assertThat(group.pendingChanges().orElseThrow().operations().values())
                    .describedAs("group %s runs exactly one UpdateRoutingState", groupId)
                    .singleElement()
                    .extracting(planned -> planned.operation())
                    .isInstanceOf(UpdateRoutingState.class);
                assertThat(group.version())
                    .describedAs("group %s version moved at the plan boundary", groupId)
                    .isGreaterThan(PartitionGroupConfiguration.INITIAL_VERSION);
              });
    }

    @Test
    void shouldWriteAConfigurationRecognizableAsPostRestore(@TempDir final Path dir)
        throws IOException {
      // given — more than one physical tenant, which is what a single-group-only check would miss
      final var clusterRestore = clusterRestore(dir, Map.of("default", 2, OTHER_TENANT, 2));

      // when
      clusterRestore.restoreTopologyFile();

      // then
      assertThat(read(dir).isAfterRestore()).isTrue();
    }

    @Test
    void shouldWriteASingleTenantConfigurationRecognizableAsPostRestore(@TempDir final Path dir)
        throws IOException {
      // given — the unchanged single-tenant case must keep behaving the same
      final var clusterRestore = clusterRestore(dir, Map.of("default", 2));

      // when
      clusterRestore.restoreTopologyFile();

      // then
      final var written = read(dir);
      assertThat(written.partitionGroups())
          .containsOnlyKeys(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID);
      assertThat(written.isAfterRestore()).isTrue();
    }
  }

  /**
   * Which partitions a restore of one physical tenant touches. Getting this wrong is silent: the
   * restore reads the right tenant's backup store and writes the partitions somewhere else.
   */
  @Nested
  class PartitionRouting {

    @Test
    void shouldNotTouchAnotherTenantsPartitionsWhenRestoringOne(@TempDir final Path dir) {
      // given — two tenants with different partition counts
      final var clusterRestore = clusterRestore(dir, Map.of("default", 1, OTHER_TENANT, 3));

      // when — restoring tenanta. It has no real backups, so this fails; the failure cleanup then
      // removes tenanta's own directory, which is scoped to the tenants this run targeted.
      assertThatThrownBy(
              () ->
                  clusterRestore.restore(
                      selection(OTHER_TENANT), REPLACE_SELECTED, false, List.of()))
          .isInstanceOf(Exception.class);

      // then — the default tenant's directory was never created. What makes this the regression
      // assertion: a restore that derived its partitions from the default tenant would have
      // created default/partitions/1, and the cleanup — scoped to tenanta — would have left it
      // behind for this check to find.
      assertThat(dir.resolve("default")).doesNotExist();
    }

    @Test
    void shouldPlacePartitionsAsTheWholeClusterDistributionDoes() {
      // given — a three-broker cluster whose default tenant holds 1 partition and tenanta 3
      final var configuration = brokerCfg(Path.of("/tmp/unused"), 1);
      configuration.getCluster().setClusterSize(3);
      configuration.getCluster().setReplicationFactor(1);
      final var tenantA = brokerCfg(Path.of("/tmp/unused"), 3);
      tenantA.getCluster().setClusterSize(3);
      tenantA.getCluster().setReplicationFactor(1);
      final var configurations = Map.of("default", configuration, OTHER_TENANT, tenantA);

      // when
      final var local =
          ClusterRestore.localPartitionsOf(configuration, configurations, OTHER_TENANT);

      // then — placement is round-robin over the cluster's whole sorted partition list, so
      // node 0 gets tenanta's partition 3 here. Generating tenanta's distribution on its own
      // would have given node 0 its partition 1, and the restore would write to the wrong broker.
      assertThat(local)
          .describedAs("tenanta partitions assigned to node 0 by the full distribution")
          .singleElement()
          .satisfies(
              partition -> {
                assertThat(partition.id().group()).isEqualTo(OTHER_TENANT);
                assertThat(partition.id().number()).isEqualTo(3);
              });
    }

    @Test
    void shouldSelectEveryPartitionOfTheTenantOnASingleBrokerCluster() {
      // given — one broker, so every partition of every tenant is local
      final var configuration = brokerCfg(Path.of("/tmp/unused"), 1);
      final var tenantA = brokerCfg(Path.of("/tmp/unused"), 3);
      final var configurations = Map.of("default", configuration, OTHER_TENANT, tenantA);

      // when
      final var local =
          ClusterRestore.localPartitionsOf(configuration, configurations, OTHER_TENANT);

      // then — the selected tenant's own partitions, by its own partition count, and none of the
      // default tenant's
      assertThat(local)
          .extracting(partition -> partition.id().group() + "/" + partition.id().number())
          .containsExactlyInAnyOrder(OTHER_TENANT + "/1", OTHER_TENANT + "/2", OTHER_TENANT + "/3");
    }
  }

  /**
   * What a restore of one tenant does to the topology file: nothing.
   *
   * <p>It has changed nothing the file describes — the restored tenant's partitions sit on the same
   * brokers as before — and only the coordinator writes it, so a coordinator-side rewrite would
   * reach the other brokers as a change to their own member state that they were not party to, and
   * they would shut down as inconsistent.
   */
  @Nested
  class PartialRestoreLeavesTheTopologyAlone {

    @Test
    void shouldNotWriteTheTopologyFileWhenRestoringASubset(@TempDir final Path dir)
        throws IOException {
      // given — a cluster of two tenants whose topology file already exists
      final var clusterRestore = clusterRestore(dir, Map.of("default", 1, OTHER_TENANT, 1));
      clusterRestore.restoreTopologyFile();
      final var before = fileContent(dir);

      // when — one tenant is restored. It has no real backups, so the restore fails, but only
      // after the point where the topology file would have been written.
      assertThatThrownBy(
              () ->
                  clusterRestore.restore(
                      selection(OTHER_TENANT), REPLACE_SELECTED, false, List.of()))
          .isInstanceOf(Exception.class);

      // then — untouched, byte for byte
      assertThat(fileContent(dir)).isEqualTo(before);
    }

    @Test
    void shouldNotCreateATopologyFileWhenRestoringASubsetWithoutOne(@TempDir final Path dir) {
      // given — no topology file yet
      final var clusterRestore = clusterRestore(dir, Map.of("default", 1, OTHER_TENANT, 1));

      // when
      assertThatThrownBy(
              () ->
                  clusterRestore.restore(
                      selection(OTHER_TENANT), REPLACE_SELECTED, false, List.of()))
          .isInstanceOf(Exception.class);

      // then — a partial run does not write one either; the brokers generate what they need on
      // boot, exactly as they do for a tenant that was never provisioned
      assertThat(dir.resolve(ClusterConfigurationManagerService.TOPOLOGY_FILE_NAME)).doesNotExist();
    }
  }

  /**
   * Whether a restore rewrites the topology file — extracted from {@link
   * ClusterRestore#restore(Map, ClusterRestore.TargetDataPolicy, boolean, List)} so its decision
   * table is testable directly, independent of whether a particular restore call happens to
   * succeed. It has to be: with {@link TestRestorableBackupStore} holding no real backups, every
   * {@code restore(...)} call in this file fails before reaching this decision, so a test that only
   * asserted through a full restore could never exercise the wrong branch of this decision — the
   * exact gap that let it ship wrong in the first place.
   */
  @Nested
  class TopologyRewriteDecision {

    @Test
    void shouldRewriteOnAFreshRestoreCoveringEveryTenant() {
      assertThat(ClusterRestore.shouldRewriteTopologyFile(REQUIRE_EMPTY, true)).isTrue();
    }

    @Test
    void shouldNotRewriteWhenReplacingTheOnlyConfiguredTenant() {
      // the exact bug this guards: --tenant-id=default on a single-tenant cluster. wholeCluster is
      // trivially true (the selection covers the cluster's only tenant), but this is a targeted
      // replacement (REPLACE_SELECTED) — it asked to replace one tenant, not rebuild the cluster —
      // and rewriting the whole topology file would discard whatever dynamic state (routing,
      // exporters, mode) the cluster had accumulated outside static configuration.
      assertThat(ClusterRestore.shouldRewriteTopologyFile(REPLACE_SELECTED, true)).isFalse();
    }

    @Test
    void shouldNotRewriteWhenTheImplicitDefaultCoversOnlyPartOfAMultiTenantCluster() {
      assertThat(ClusterRestore.shouldRewriteTopologyFile(REQUIRE_EMPTY, false)).isFalse();
    }

    @Test
    void shouldNotRewriteATargetedReplacementRegardlessOfCoverage() {
      assertThat(ClusterRestore.shouldRewriteTopologyFile(REPLACE_SELECTED, false)).isFalse();
    }
  }

  @Nested
  class TenantSelection {

    @Test
    void shouldRejectAPhysicalTenantTheClusterHasNoConfigurationFor(@TempDir final Path dir) {
      // given
      final var clusterRestore = clusterRestore(dir, Map.of("default", 1));

      // when / then — there would be no backup store to read it from
      assertThatThrownBy(
              () ->
                  clusterRestore.restore(
                      selection("nosuchtenant"), REQUIRE_EMPTY, false, List.of()))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("nosuchtenant")
          .hasMessageContaining("not configured");
    }

    @Test
    void shouldRejectAnEmptySelection(@TempDir final Path dir) {
      // given
      final var clusterRestore = clusterRestore(dir, Map.of("default", 1));

      // when / then
      assertThatThrownBy(() -> clusterRestore.restore(Map.of(), REQUIRE_EMPTY, false, List.of()))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("at least one physical tenant");
    }

    @Test
    void shouldReplaceOnlyTheNamedTenantsData(@TempDir final Path dir) throws IOException {
      // given — a multi-tenant cluster where both tenants have data and a valid topology file on
      // disk, as a live cluster does
      final var clusterRestore = clusterRestore(dir, Map.of("default", 1, OTHER_TENANT, 1));
      clusterRestore.restoreTopologyFile();
      final var defaultData = Files.createDirectories(dir.resolve("default").resolve("partitions"));
      final var defaultFile = Files.createFile(defaultData.resolve("keep-me"));
      final var tenantData =
          Files.createDirectories(dir.resolve(OTHER_TENANT).resolve("partitions"));
      final var tenantFile = Files.createFile(tenantData.resolve("replace-me"));

      // when — naming one tenant. It has no real backups, so the restore fails, but only after
      // its data has been cleared.
      assertThatThrownBy(
              () ->
                  clusterRestore.restore(
                      selection(OTHER_TENANT), REPLACE_SELECTED, false, List.of()))
          .isInstanceOf(Exception.class);

      // then — the named tenant's data is gone, and nothing else is. Naming a tenant is asking to
      // replace it on a node whose other tenants are live, so an empty-directory requirement could
      // never be satisfied and their data must survive.
      assertThat(tenantFile).doesNotExist();
      assertThat(defaultFile).exists();
      assertThat(dir.resolve(ClusterConfigurationManagerService.TOPOLOGY_FILE_NAME)).exists();
    }
  }
}
