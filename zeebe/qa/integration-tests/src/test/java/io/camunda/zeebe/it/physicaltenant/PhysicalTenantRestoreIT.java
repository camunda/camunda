/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.physicaltenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.atomix.cluster.MemberId;
import io.camunda.client.CamundaClient;
import io.camunda.configuration.Camunda;
import io.camunda.configuration.PrimaryStorageBackup.BackupStoreType;
import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import io.camunda.zeebe.it.cluster.backup.InProcessRestoreTestUtil;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper.Storage;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.TestClusterBuilder;
import io.camunda.zeebe.qa.util.cluster.TestGateway;
import io.camunda.zeebe.qa.util.cluster.TestRestoreApp;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * Restoring a multi-physical-tenant cluster through the standalone restore application.
 *
 * <p>The three scenarios are the three ways the application can be pointed at such a cluster: the
 * default tenant alone (no {@code --tenant-id}), one named tenant, and every tenant ({@code
 * --all-tenants}). All three share the cluster shape built by {@link #startCluster()}.
 *
 * <p>Two brokers, replication factor 2, so every partition is replicated on both. Quorum is
 * therefore 2: a partition becomes usable again only if <em>both</em> brokers restored it, which is
 * what makes this worth running on more than one node. Each broker must end up holding every
 * partition of every tenant the run restored, and the cluster must form quorum afterwards.
 *
 * <p>Both tenants have the same shape, so neither partition count nor broker placement
 * distinguishes them here — with two brokers and full replication every partition is on both. What
 * distinguishes a tenant is the directory its partitions live in ({@code
 * <dataDirectory>/<physicalTenantId>/partitions/<n>}), which is what these tests assert on: a
 * restore that read the wrong tenant puts data under a tenant this run never named, and one that
 * skipped a tenant leaves its directory absent. The asymmetric partition counts and round-robin
 * placement that catch a misrouted distribution are covered by {@code
 * ClusterRestoreTest.PartitionRouting}, against three members with unequal tenants.
 *
 * <p>Each tenant owns its own filesystem backup store, shared by both brokers — backups are keyed
 * by node id within a store. Per tenant is not tidiness: {@code BackupIdentifier} names no tenant
 * and partition numbers restart at 1 in every partition group, so two tenants sharing a store would
 * read each other's backups.
 *
 * <p>The restore application runs once per broker, as an operator would run it on every node. Only
 * node 0 writes the topology file; the other broker picks it up by gossip when it starts, which is
 * the coordinator/non-coordinator split the post-restore exporter-state handling exists for.
 */
@Timeout(600)
final class PhysicalTenantRestoreIT {

  private static final String DEFAULT_TENANT = PhysicalTenantsITHelper.DEFAULT_TENANT_ID;
  private static final String TENANT_A = "tenanta";
  private static final int BROKERS = 2;
  private static final int REPLICATION_FACTOR = 2;

  /** Both tenants: a 2-broker, 2-partition, replication-factor-2 cluster per physical tenant. */
  private static final int PARTITIONS = 2;

  private static final long DEFAULT_BACKUP_ID = 11L;
  private static final long TENANT_A_BACKUP_ID = 22L;

  private static final HttpClient HTTP = HttpClient.newHttpClient();
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static final PhysicalTenantsITHelper TENANTS =
      PhysicalTenantsITHelper.builder()
          .withTenant(DEFAULT_TENANT, Storage.none(), PARTITIONS)
          .withTenant(TENANT_A, Storage.none(), PARTITIONS)
          .build();

  @TempDir private Path dataRoot;
  @TempDir private Path defaultBackupDirectory;
  @TempDir private Path tenantABackupDirectory;

  @Test
  void shouldRestoreTheDefaultTenantWhenNoTenantIsNamed() throws Exception {
    // given — both tenants backed up, then every node wiped
    try (final var cluster = buildCluster()) {
      backUpBothTenants(cluster);
      cluster.shutdown();
      for (final var node : nodeIds()) {
        deleteContents(dataDirectoryOf(node));
      }

      // when — no --tenant-id, which is every pre-existing invocation of this application
      restoreOnEveryNode(restoreApp -> restoreApp.withBackupId(DEFAULT_BACKUP_ID));

      // then — the default tenant's partitions are back on both brokers, and the tenant that was
      // not named has nothing restored for it anywhere
      assertThat(restoredPartitionsOf(DEFAULT_TENANT)).containsExactly(1, 2);
      assertThat(restoredPartitionsOf(TENANT_A)).isEmpty();
      // No topology file: restoring the default tenant of a two-tenant cluster is a partial
      // restore, and a partial restore leaves the topology alone. The brokers generate what they
      // need on boot.
      assertThat(dataDirectoryOf(MemberId.from("0")).resolve(".topology.meta")).doesNotExist();

      // and the brokers start and the default tenant is back at the state the backup captured
      awaitReady(cluster.start());
      assertStateRestoredFromBackup(cluster, DEFAULT_TENANT);
      // tenanta was wiped and not restored, so it comes up as an empty tenant — usable, with
      // neither the state the backup held nor the state written after it
      assertNoJobsOf(cluster, TENANT_A, jobTypeBeforeBackup(TENANT_A));
      assertNoJobsOf(cluster, TENANT_A, jobTypeAfterBackup(TENANT_A));
      assertOperational(cluster, TENANT_A, PARTITIONS);
    }
  }

  @Test
  void shouldRestoreOneNamedTenantAndLeaveTheOtherUntouched() throws Exception {
    // given — both tenants backed up, then only the named tenant's data removed on every node, as
    // an operator recovering one tenant of a live cluster would
    try (final var cluster = buildCluster()) {
      backUpBothTenants(cluster);
      cluster.shutdown();
      final var defaultTenantFingerprint = fingerprintOf(DEFAULT_TENANT);
      assertThat(defaultTenantFingerprint).isNotEmpty();
      assertThat(fingerprintOf(TENANT_A)).isNotEmpty();

      // when — nothing is deleted by hand. Naming a tenant is asking to replace it on a node whose
      // other tenants are live, so the application clears that tenant's own data itself; requiring
      // an empty directory here could never be satisfied.
      restoreOnEveryNode(
          restoreApp -> restoreApp.withPhysicalTenant(TENANT_A).withBackupId(TENANT_A_BACKUP_ID));

      // then — the named tenant is restored with its own three partitions, spread over the cluster
      assertThat(restoredPartitionsOf(TENANT_A)).containsExactly(1, 2);

      // ... and the untouched tenant's data is what it was, on both brokers
      assertThat(fingerprintOf(DEFAULT_TENANT)).isEqualTo(defaultTenantFingerprint);

      // and the brokers start, with each tenant at the state it should be at
      awaitReady(cluster.start());
      // the restored tenant is back at the backup: the work pending when the backup was taken is
      // activatable again on every partition, and the work created afterwards is gone
      assertStateRestoredFromBackup(cluster, TENANT_A);
      // the untouched tenant kept going: it was never rolled back, so the work created after the
      // backup is still there. This is the assertion that a restore bleeding into another tenant
      // fails on.
      assertJobsFromEveryPartition(cluster, DEFAULT_TENANT, jobTypeAfterBackup(DEFAULT_TENANT));
    }
  }

  @Test
  void shouldRestoreEveryTenantWhenAllAreRequested() throws Exception {
    // given
    try (final var cluster = buildCluster()) {
      backUpBothTenants(cluster);
      cluster.shutdown();
      for (final var node : nodeIds()) {
        deleteContents(dataDirectoryOf(node));
      }

      // when — one backup id per tenant, since each tenant's checkpoints are its own
      restoreOnEveryNode(
          restoreApp -> restoreApp.withAllPhysicalTenants().withBackupId(DEFAULT_BACKUP_ID),
          properties ->
              properties.put(
                  "override." + TENANT_A + ".backupId", String.valueOf(TENANT_A_BACKUP_ID)));

      // then — both tenants restored, each with its own count, one copy of each partition
      assertThat(restoredPartitionsOf(DEFAULT_TENANT)).containsExactly(1, 2);
      assertThat(restoredPartitionsOf(TENANT_A)).containsExactly(1, 2);

      // and the brokers start with both tenants back at the state their own backup captured
      awaitReady(cluster.start());
      assertStateRestoredFromBackup(cluster, DEFAULT_TENANT);
      assertStateRestoredFromBackup(cluster, TENANT_A);
    }
  }

  /** Starts the cluster, gives each tenant something to back up, and backs both up. */
  private void backUpBothTenants(final TestCluster cluster) throws Exception {
    awaitReady(cluster.start());
    assertOperational(cluster, DEFAULT_TENANT, PARTITIONS);
    assertOperational(cluster, TENANT_A, PARTITIONS);

    // State on both sides of the backup, so a restore can be told from a no-op: work pending when
    // the backup is taken must come back, and work created after it must not.
    for (final var tenantId : List.of(DEFAULT_TENANT, TENANT_A)) {
      createPendingWorkOnEveryPartition(cluster, tenantId, jobTypeBeforeBackup(tenantId));
    }
    takeBackupAndAwait(cluster, DEFAULT_TENANT, DEFAULT_BACKUP_ID);
    takeBackupAndAwait(cluster, TENANT_A, TENANT_A_BACKUP_ID);
    for (final var tenantId : List.of(DEFAULT_TENANT, TENANT_A)) {
      createPendingWorkOnEveryPartition(cluster, tenantId, jobTypeAfterBackup(tenantId));
    }
  }

  /**
   * Leaves one pending job of {@code jobType} on every partition of the tenant, via a deployed
   * process — engine state, unlike a published message, and per partition, so a restore that
   * brought back one partition can be told from one that brought back all of them.
   */
  private void createPendingWorkOnEveryPartition(
      final TestCluster cluster, final String tenantId, final String jobType) {
    try (final CamundaClient client = clientFor(cluster, tenantId)) {
      InProcessRestoreTestUtil.deployAndCreateInstancesOnEveryPartition(
          client, jobType + "-process", jobType, PARTITIONS);
    }
  }

  /**
   * The tenant is back at exactly the state its backup captured: the work that was pending when the
   * backup was taken runs again on every partition, and the work created after it is absent.
   *
   * <p>Job types are per tenant, so work belonging to another tenant showing up here — a restore
   * that wrote into the wrong partition group — fails this too.
   */
  private void assertStateRestoredFromBackup(final TestCluster cluster, final String tenantId) {
    assertJobsFromEveryPartition(cluster, tenantId, jobTypeBeforeBackup(tenantId));
    assertNoJobsOf(cluster, tenantId, jobTypeAfterBackup(tenantId));
  }

  /** Activates and completes the pending jobs, asserting one came from every partition. */
  private void assertJobsFromEveryPartition(
      final TestCluster cluster, final String tenantId, final String jobType) {
    try (final CamundaClient client = clientFor(cluster, tenantId)) {
      InProcessRestoreTestUtil.activateAndCompleteJobsFromEveryPartition(
          client, jobType, PARTITIONS);
    }
  }

  /** No job of the type is activatable — the state that held it was not restored. */
  private void assertNoJobsOf(
      final TestCluster cluster, final String tenantId, final String jobType) {
    try (final CamundaClient client = clientFor(cluster, tenantId)) {
      assertThat(
              client
                  .newActivateJobsCommand()
                  .jobType(jobType)
                  .maxJobsToActivate(2 * PARTITIONS)
                  .send()
                  .join()
                  .getJobs())
          .describedAs("jobs of type '%s' in tenant '%s'", jobType, tenantId)
          .isEmpty();
    }
  }

  private static String jobTypeBeforeBackup(final String tenantId) {
    return tenantId + "-before-backup";
  }

  private static String jobTypeAfterBackup(final String tenantId) {
    return tenantId + "-after-backup";
  }

  /**
   * The cluster topology describes the default tenant's partitions; a multi-PT broker's
   * partition-id-keyed topology cannot represent another tenant's raft groups at all, which is why
   * each tenant's readiness is asserted behaviourally instead.
   */
  private static TestCluster awaitReady(final TestCluster cluster) {
    return cluster.awaitCompleteTopology(BROKERS, PARTITIONS, REPLICATION_FACTOR, ofSeconds(180));
  }

  /**
   * The cluster, not started. One instance is built per test and started twice — before the backup
   * and again after the restore — rather than replaced: a second instance would be allocated its
   * own ports while the first one's are still settling.
   */
  private TestCluster buildCluster() {
    return new TestClusterBuilder()
        .withBrokersCount(BROKERS)
        .withGatewaysCount(0)
        .withEmbeddedGateway(true)
        .withPartitionsCount(PARTITIONS)
        .withReplicationFactor(REPLICATION_FACTOR)
        .withBrokerConfig(
            (memberId, broker) -> {
              TENANTS.configureStatic(broker);
              broker
                  .withUnauthenticatedAccess()
                  .withDataConfig(
                      data -> {
                        data.getPrimaryStorage().setDirectory(dataDirectoryOf(memberId).toString());
                        configureBackup(
                            data.getPrimaryStorage().getBackup(), defaultBackupDirectory);
                      })
                  .withPtConfig(TENANT_A, this::configureTenantA);
            })
        .build();
  }

  private void configureTenantA(final Camunda camunda) {
    camunda.getData().getSecondaryStorage().setType(SecondaryStorageType.none);
    configureBackup(camunda.getData().getPrimaryStorage().getBackup(), tenantABackupDirectory);
  }

  private static void configureBackup(
      final io.camunda.configuration.PrimaryStorageBackup backup, final Path directory) {
    backup.setStore(BackupStoreType.FILESYSTEM);
    backup.getFilesystem().setBasePath(directory.toString());
  }

  private void restoreOnEveryNode(final UnaryOperator<TestRestoreApp> arguments) {
    restoreOnEveryNode(arguments, properties -> {});
  }

  /**
   * Runs the restore application on every node, each against that node's own data directory.
   *
   * <p>A node restores only the partitions it replicates, so restoring a cluster means restoring
   * every node — and it is the per-node split of partitions that these tests are checking.
   */
  private void restoreOnEveryNode(
      final UnaryOperator<TestRestoreApp> arguments,
      final Consumer<Map<String, Object>> extraProperties) {
    for (final var memberId : nodeIds()) {
      final Map<String, Object> properties = new LinkedHashMap<>();
      extraProperties.accept(properties);

      var restoreApp =
          new TestRestoreApp()
              .withUnifiedConfig(
                  config -> {
                    config.getCluster().setNodeId(Integer.parseInt(memberId.id()));
                    config.getCluster().setSize(BROKERS);
                    config.getCluster().setReplicationFactor(REPLICATION_FACTOR);
                    config.getCluster().setPartitionCount(PARTITIONS);
                    config
                        .getData()
                        .getPrimaryStorage()
                        .setDirectory(dataDirectoryOf(memberId).toString());
                    configureBackup(
                        config.getData().getPrimaryStorage().getBackup(), defaultBackupDirectory);
                    // A restore from explicit backup ids reads no secondary storage; declaring it
                    // absent also keeps the tenants out of the cross-tenant isolation rules, which
                    // would otherwise see them both pointed at the same default database.
                    config.getData().getSecondaryStorage().setType(SecondaryStorageType.none);
                  })
              .withPtConfig(
                  TENANT_A,
                  camunda -> {
                    camunda.getCluster().setPartitionCount(PARTITIONS);
                    configureTenantA(camunda);
                  })
              // An explicitly configured tenant must either declare its own security
              // initialization or opt out of authorization. A restore performs no authorization at
              // all, so opting out is the honest choice; it does not touch what gets restored.
              .withProperty(
                  "camunda.physical-tenants." + TENANT_A + ".security.authorizations.enabled",
                  false);
      restoreApp = arguments.apply(restoreApp);
      for (final var property : properties.entrySet()) {
        restoreApp = restoreApp.withProperty(property.getKey(), property.getValue());
      }

      try (final var app = restoreApp) {
        final var node = memberId;
        assertThatNoException().describedAs("restore on node %s", node.id()).isThrownBy(app::start);
      }
    }
  }

  /** Publishes messages so the tenant's partitions hold state worth restoring. */
  private void publishMessages(
      final TestCluster cluster, final String tenantId, final int count, final String tag) {
    try (final CamundaClient client = clientFor(cluster, tenantId)) {
      for (int i = 0; i < count; i++) {
        client
            .newPublishMessageCommand()
            .messageName("restore-it")
            .correlationKey(tenantId + "-" + tag + "-" + i)
            .send()
            .join();
      }
    }
  }

  /**
   * A physical tenant is operational when it accepts work on every one of its partitions.
   *
   * <p>Asserted by publishing, not by reading the cluster topology, which is keyed by bare
   * partition id and so describes the default tenant's raft groups only. Enough distinct
   * correlation keys are used to spread across the tenant's partitions and therefore across both
   * brokers, so a tenant whose partitions were restored onto the wrong broker cannot pass by having
   * one usable partition.
   */
  private void assertOperational(
      final TestCluster cluster, final String tenantId, final int partitions) {
    Awaitility.await("physical tenant '" + tenantId + "' accepts work on all partitions")
        .atMost(ofSeconds(180))
        .ignoreExceptions()
        .untilAsserted(() -> publishMessages(cluster, tenantId, partitions * 5, "operational"));
  }

  private CamundaClient clientFor(final TestCluster cluster, final String tenantId) {
    return TENANTS.newClientBuilder(cluster.availableGateway(), tenantId).build();
  }

  private void takeBackupAndAwait(
      final TestCluster cluster, final String tenantId, final long backupId) throws Exception {
    final var uri = backupsUri(cluster.availableGateway(), tenantId);
    final var response =
        HTTP.send(
            HttpRequest.newBuilder(uri)
                .header("Content-Type", "application/json")
                .POST(BodyPublishers.ofString("{\"backupId\": " + backupId + "}"))
                .build(),
            BodyHandlers.ofString());
    assertThat(response.statusCode())
        .describedAs("take backup %d of tenant '%s': %s", backupId, tenantId, response.body())
        .isEqualTo(202);

    Awaitility.await("backup " + backupId + " of tenant '" + tenantId + "' completes")
        .atMost(ofSeconds(180))
        .untilAsserted(
            () -> {
              final var status =
                  HTTP.send(
                      HttpRequest.newBuilder(URI.create(uri + "/" + backupId)).GET().build(),
                      BodyHandlers.ofString());
              assertThat(status.statusCode()).isEqualTo(200);
              assertThat(MAPPER.readTree(status.body()).path("state").asText())
                  .isEqualTo("COMPLETED");
            });
  }

  private static URI backupsUri(final TestGateway<?> gateway, final String tenantId) {
    final var base = gateway.restAddress().toString().replaceAll("/+$", "");
    return DEFAULT_TENANT.equals(tenantId)
        ? URI.create(base + "/v2/backups/runtime")
        : URI.create(base + "/physical-tenants/" + tenantId + "/v2/backups/runtime");
  }

  private static List<MemberId> nodeIds() {
    return IntStream.range(0, BROKERS).mapToObj(id -> MemberId.from(String.valueOf(id))).toList();
  }

  private Path dataDirectoryOf(final MemberId memberId) {
    return dataRoot.resolve("node-" + memberId.id());
  }

  /**
   * Every partition of one tenant restored in the cluster, sorted — having first checked that every
   * broker holds all of them.
   *
   * <p>Deliberately not an expected per-broker placement: with replication factor 2 across two
   * brokers there is nothing to place, every replica is on both. The count is what catches a
   * restore that read the wrong tenant; {@link #assertEveryBrokerHolds} catches one that restored a
   * replica short.
   */
  private List<Integer> restoredPartitionsOf(final String tenantId) {
    final var perNode =
        nodeIds().stream()
            .collect(Collectors.toMap(MemberId::id, node -> partitionsOn(node, tenantId)));
    assertEveryBrokerHolds(tenantId, perNode);
    return perNode.values().stream().flatMap(List::stream).distinct().sorted().toList();
  }

  /**
   * With replication factor 2 on two brokers, every partition has a replica on each. A partition
   * missing from one broker is a replica that was not restored, and that partition cannot reach
   * quorum — the cluster would come up unable to serve the tenant.
   */
  private void assertEveryBrokerHolds(
      final String tenantId, final Map<String, List<Integer>> perNode) {
    final var expected =
        perNode.values().stream().flatMap(List::stream).distinct().sorted().toList();
    assertThat(perNode)
        .describedAs("every broker holds every restored partition of tenant '%s'", tenantId)
        .allSatisfy((node, partitions) -> assertThat(partitions).isEqualTo(expected));
  }

  private List<Integer> partitionsOn(final MemberId memberId, final String tenantId) {
    final var partitions = dataDirectoryOf(memberId).resolve(tenantId).resolve("partitions");
    if (!Files.isDirectory(partitions)) {
      return List.of();
    }
    try (final var entries = Files.list(partitions)) {
      return entries
          .map(path -> path.getFileName().toString())
          .filter(name -> name.chars().allMatch(Character::isDigit))
          .map(Integer::parseInt)
          .sorted()
          .toList();
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** Every file of a tenant across every node, with its size — enough to see the data change. */
  private Map<String, Long> fingerprintOf(final String tenantId) throws IOException {
    final Map<String, Long> fingerprint = new LinkedHashMap<>();
    for (final var memberId : nodeIds()) {
      final var root = dataDirectoryOf(memberId).resolve(tenantId);
      if (!Files.isDirectory(root)) {
        continue;
      }
      try (final Stream<Path> files = Files.walk(root)) {
        files
            .filter(Files::isRegularFile)
            .forEach(
                path -> {
                  try {
                    fingerprint.put(memberId.id() + "/" + root.relativize(path), Files.size(path));
                  } catch (final IOException e) {
                    throw new UncheckedIOException(e);
                  }
                });
      }
    }
    return fingerprint;
  }

  private static void deleteContents(final Path root) throws IOException {
    deleteRecursively(root);
    Files.createDirectories(root);
  }

  private static void deleteRecursively(final Path root) throws IOException {
    if (!Files.exists(root)) {
      return;
    }
    try (final Stream<Path> paths = Files.walk(root)) {
      paths
          .sorted(Comparator.reverseOrder())
          .forEach(
              path -> {
                try {
                  Files.delete(path);
                } catch (final IOException e) {
                  throw new UncheckedIOException(e);
                }
              });
    }
  }

  private static Duration ofSeconds(final long seconds) {
    return Duration.ofSeconds(seconds);
  }
}
