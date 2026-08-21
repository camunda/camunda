/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.backup;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.protocol.rest.ClusterModeChangeResponse;
import io.camunda.client.protocol.rest.ClusterRestoreRequest;
import io.camunda.client.protocol.rest.ClusterRestoreResponse;
import io.camunda.client.protocol.rest.RestoreRequest;
import io.camunda.client.protocol.rest.RestoreStatusResponse;
import io.camunda.zeebe.it.util.ZeebeResourcesHelper;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.Protocol;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.awaitility.Awaitility;

/**
 * Shared helpers for the in-process restore tests ({@link InProcessRestoreAcceptance}, {@link
 * InProcessRestoreRetryOnCorruptionIT}, {@link InProcessRdbmsRangeRestoreIT}, {@link
 * InProcessRestoreStatusIT} and {@link ClusterAdminRestoreAcceptanceIT}), which all trigger a
 * restore over the cluster's REST endpoint while a broker is in {@code RECOVERING} mode. Also
 * exposes {@link #changeMode} for triggering a mode change over {@code v2/mode}, reused by {@code
 * ModeChangeAcceptanceIT} outside this package, and {@link #changeClusterMode} for the
 * cluster-admin equivalent over {@code cluster/v2/mode}.
 */
public final class InProcessRestoreTestUtil {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private InProcessRestoreTestUtil() {}

  /** POSTs a restore request with the given body to {@code v2/restore} and returns the response. */
  static HttpResponse<String> sendRestoreRequest(
      final CamundaClient client, final Map<String, Object> body) {
    try (final var httpClient = HttpClient.newHttpClient()) {
      final var uri =
          URI.create(
              "%sv2/restore?dryRun=false".formatted(client.getConfiguration().getRestAddress()));
      final var request =
          HttpRequest.newBuilder(uri)
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(OBJECT_MAPPER.writeValueAsString(body)))
              .build();
      return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to trigger restore via REST endpoint", e);
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("Interrupted while triggering restore via REST endpoint", e);
    }
  }

  /**
   * Triggers a restore for the given backup id, asserting the request is accepted (202), and
   * returns the id of the cluster configuration change it started.
   */
  static long triggerRestore(final CamundaClient client, final long backupId) {
    return triggerRestore(client, Map.of("backupIds", List.of(backupId)));
  }

  /**
   * Triggers a restore with the given request body, asserting the request is accepted (202), and
   * returns the id of the cluster configuration change it started.
   */
  static long triggerRestore(final CamundaClient client, final Map<String, Object> body) {
    final var response = sendRestoreRequest(client, body);
    assertThat(response.statusCode())
        .describedAs("restore REST response: %s".formatted(response.body()))
        .isEqualTo(202);
    try {
      return Long.parseLong(
          OBJECT_MAPPER.readValue(response.body(), ClusterRestoreResponse.class).getChangeId());
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to parse restore REST response", e);
    }
  }

  /**
   * POSTs a cluster-admin restore request with the given body to {@code cluster/v2/restore}, scoped
   * to the given physical tenant, or to every physical tenant of the cluster when {@code
   * physicalTenantId} is {@code null}. Returns the raw response.
   */
  static HttpResponse<String> sendClusterRestoreRequest(
      final CamundaClient client, final String physicalTenantId, final ClusterRestoreRequest body) {
    try (final var httpClient = HttpClient.newHttpClient()) {
      final var tenantQueryParam =
          physicalTenantId == null ? "" : "&physicalTenantId=" + physicalTenantId;
      final var uri =
          URI.create(
              "%scluster/v2/restore?dryRun=false%s"
                  .formatted(client.getConfiguration().getRestAddress(), tenantQueryParam));
      final var request =
          HttpRequest.newBuilder(uri)
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(OBJECT_MAPPER.writeValueAsString(body)))
              .build();
      return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to trigger cluster restore via REST endpoint", e);
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException(
          "Interrupted while triggering cluster restore via REST endpoint", e);
    }
  }

  /**
   * Triggers a cluster-admin restore of the given physical tenant (or every physical tenant of the
   * cluster, when {@code physicalTenantId} is {@code null}) for the given backup id over {@code
   * cluster/v2/restore}, asserting the request is accepted (202), and returns the id of the cluster
   * configuration change it started.
   */
  static long triggerClusterRestore(
      final CamundaClient client, final String physicalTenantId, final long backupId) {
    final var body = new ClusterRestoreRequest().backupIds(List.of(backupId));
    final var response = sendClusterRestoreRequest(client, physicalTenantId, body);
    assertThat(response.statusCode())
        .describedAs("cluster restore REST response: %s".formatted(response.body()))
        .isEqualTo(202);
    try {
      return Long.parseLong(
          OBJECT_MAPPER.readValue(response.body(), ClusterRestoreResponse.class).getChangeId());
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to parse cluster restore REST response", e);
    }
  }

  /**
   * Triggers a cluster-wide restore for the given top-level backup id, overriding it with its own
   * backup id for every physical tenant named in {@code overrideBackupIdByTenant}, over {@code
   * cluster/v2/restore}. {@code overrides} is only accepted on a cluster-wide restore, so this
   * always omits {@code physicalTenantId}. Asserts the request is accepted (202) and returns the id
   * of the cluster configuration change it started.
   */
  static long triggerClusterRestore(
      final CamundaClient client,
      final long backupId,
      final Map<String, Long> overrideBackupIdByTenant) {
    final var body = new ClusterRestoreRequest().backupIds(List.of(backupId));
    overrideBackupIdByTenant.forEach(
        (tenantId, overrideBackupId) ->
            body.putOverridesItem(
                tenantId, new RestoreRequest().backupIds(List.of(overrideBackupId))));
    final var response = sendClusterRestoreRequest(client, null, body);
    assertThat(response.statusCode())
        .describedAs("cluster restore REST response: %s".formatted(response.body()))
        .isEqualTo(202);
    try {
      return Long.parseLong(
          OBJECT_MAPPER.readValue(response.body(), ClusterRestoreResponse.class).getChangeId());
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to parse cluster restore REST response", e);
    }
  }

  /** GETs the current restore status from {@code v2/restore} and returns the parsed response. */
  static RestoreStatusResponse getRestoreStatus(final CamundaClient client) {
    try (final var httpClient = HttpClient.newHttpClient()) {
      final var uri =
          URI.create("%sv2/restore".formatted(client.getConfiguration().getRestAddress()));
      final var request = HttpRequest.newBuilder(uri).GET().build();
      final var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      assertThat(response.statusCode())
          .describedAs("restore status REST response: %s".formatted(response.body()))
          .isEqualTo(200);
      return OBJECT_MAPPER.readValue(response.body(), RestoreStatusResponse.class);
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to fetch restore status via REST endpoint", e);
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("Interrupted while fetching restore status via REST endpoint", e);
    }
  }

  /**
   * Triggers a cluster mode change to the given mode over {@code v2/mode}, asserting the request is
   * accepted (200), and returns the id of the cluster configuration change it started.
   */
  public static long changeMode(
      final CamundaClient client, final String mode, final boolean dryRun) {
    try (final var httpClient = HttpClient.newHttpClient()) {
      final var uri =
          URI.create(
              "%sv2/mode?mode=%s&dryRun=%s"
                  .formatted(client.getConfiguration().getRestAddress(), mode, dryRun));
      final var request =
          HttpRequest.newBuilder(uri).method("PATCH", HttpRequest.BodyPublishers.noBody()).build();
      final var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      assertThat(response.statusCode())
          .describedAs("mode change REST response: %s".formatted(response.body()))
          .isEqualTo(200);
      return Long.parseLong(
          OBJECT_MAPPER.readValue(response.body(), ClusterModeChangeResponse.class).getChangeId());
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to trigger mode change via REST endpoint", e);
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("Interrupted while triggering mode change via REST endpoint", e);
    }
  }

  /**
   * Triggers a cluster-admin mode change to the given mode over {@code cluster/v2/mode}, scoped to
   * the given physical tenant, or to every physical tenant of the cluster when {@code
   * physicalTenantId} is {@code null}. Unlike {@link #changeMode}, which is derived from the
   * client's own configured REST address and so only ever reaches the tenant that address is
   * already scoped to, this always targets the bare cluster-admin path — the only way to move more
   * than one physical tenant at once. Asserts the request is accepted (200) and returns the id of
   * the cluster configuration change it started.
   */
  static long changeClusterMode(
      final CamundaClient client,
      final String physicalTenantId,
      final String mode,
      final boolean dryRun) {
    try (final var httpClient = HttpClient.newHttpClient()) {
      final var tenantQueryParam =
          physicalTenantId == null ? "" : "&physicalTenantId=" + physicalTenantId;
      final var uri =
          URI.create(
              "%scluster/v2/mode?mode=%s&dryRun=%s%s"
                  .formatted(
                      client.getConfiguration().getRestAddress(), mode, dryRun, tenantQueryParam));
      final var request =
          HttpRequest.newBuilder(uri).method("PATCH", HttpRequest.BodyPublishers.noBody()).build();
      final var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      assertThat(response.statusCode())
          .describedAs("cluster mode change REST response: %s".formatted(response.body()))
          .isEqualTo(200);
      return Long.parseLong(
          OBJECT_MAPPER.readValue(response.body(), ClusterModeChangeResponse.class).getChangeId());
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to trigger cluster mode change via REST endpoint", e);
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException(
          "Interrupted while triggering cluster mode change via REST endpoint", e);
    }
  }

  /**
   * Deploys a single-service-task process and creates instances until every partition has at least
   * one instance with a pending job, returning the created process instance keys. Instances are
   * created one at a time and re-checked on every poll, so no fixed instance count has to be
   * guessed up front (round-robin distribution does not guarantee full coverage after a fixed
   * number).
   */
  static List<Long> deployAndCreateInstancesOnEveryPartition(
      final CamundaClient client,
      final String processId,
      final String jobType,
      final int partitionsCount) {
    final var process =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType(jobType))
            .endEvent()
            .done();
    final var deploymentKey =
        client
            .newDeployResourceCommand()
            .addProcessModel(process, "process.bpmn")
            .send()
            .join()
            .getKey();
    new ZeebeResourcesHelper(client).waitUntilDeploymentIsDone(deploymentKey);

    final List<Long> processInstanceKeys = new ArrayList<>();
    Awaitility.await("every partition has at least one process instance with a pending job")
        .timeout(Duration.ofSeconds(60))
        // might throw an exception when a partition has not yet received deployment distribution
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var result =
                  client
                      .newCreateInstanceCommand()
                      .bpmnProcessId(processId)
                      .latestVersion()
                      .send()
                      .join();
              processInstanceKeys.add(result.getProcessInstanceKey());

              final var partitionsWithInstance =
                  processInstanceKeys.stream()
                      .map(Protocol::decodePartitionId)
                      .collect(Collectors.toSet());
              assertThat(partitionsWithInstance)
                  .describedAs(
                      "every partition has at least one process instance with a pending job")
                  .containsExactlyInAnyOrderElementsOf(
                      IntStream.rangeClosed(1, partitionsCount).boxed().toList());
            });

    return processInstanceKeys;
  }

  /**
   * Activates and completes the pending jobs, asserting that jobs from every partition are
   * activated - proving the partition data (not just topology/mode) was actually restored - and
   * that the processes spawned earlier can run to completion again.
   */
  static void activateAndCompleteJobsFromEveryPartition(
      final CamundaClient client, final String jobType, final int partitionsCount) {
    final Set<Long> activatedJobKeys = new HashSet<>();
    Awaitility.await("jobs from every partition are activated and completed after restore")
        .timeout(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              activateAndComplete(client, jobType, partitionsCount, activatedJobKeys);
              assertEveryPartitionActivated(activatedJobKeys, partitionsCount);
            });
  }

  /**
   * Completes <em>every</em> pending job of the given type, asserting that one came from every
   * partition and that an activation afterwards finds nothing left.
   *
   * <p>Drains to exhaustion, where {@link #activateAndCompleteJobsFromEveryPartition} drains only
   * to partition coverage: that one stops at the first batch spanning every partition, so a job on
   * an already-covered partition can outlive it. A caller that goes on to assert the type is gone —
   * because a backup taken at this point must not capture it — needs exhaustion, not coverage.
   */
  static void completeEveryJob(
      final CamundaClient client, final String jobType, final int partitionsCount) {
    final Set<Long> activatedJobKeys = new HashSet<>();
    Awaitility.await("every job of type '%s' is completed".formatted(jobType))
        .timeout(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              final var activated =
                  activateAndComplete(client, jobType, partitionsCount, activatedJobKeys);
              assertEveryPartitionActivated(activatedJobKeys, partitionsCount);
              assertThat(activated)
                  .describedAs("no job of type '%s' is left pending", jobType)
                  .isEmpty();
            });
  }

  /**
   * Activates up to a batch of jobs of the given type, completes each of them, and records their
   * keys in {@code activatedJobKeys}. Returns the batch, so a caller can tell a poll that found
   * work from one that found none.
   */
  private static List<ActivatedJob> activateAndComplete(
      final CamundaClient client,
      final String jobType,
      final int partitionsCount,
      final Set<Long> activatedJobKeys) {
    final var jobs =
        client
            .newActivateJobsCommand()
            .jobType(jobType)
            .maxJobsToActivate(2 * partitionsCount)
            .send()
            .join()
            .getJobs();
    jobs.forEach(
        job -> {
          activatedJobKeys.add(job.getKey());
          client.newCompleteCommand(job.getKey()).send().join();
        });
    return jobs;
  }

  private static void assertEveryPartitionActivated(
      final Set<Long> activatedJobKeys, final int partitionsCount) {
    final var partitionsWithActivatedJob =
        activatedJobKeys.stream().map(Protocol::decodePartitionId).collect(Collectors.toSet());
    assertThat(partitionsWithActivatedJob)
        .describedAs(
            "jobs are activated from every partition, proving partition data was actually restored")
        .containsExactlyInAnyOrderElementsOf(
            IntStream.rangeClosed(1, partitionsCount).boxed().toList());
  }

  /**
   * Truncates every {@code .sst} file in the given node's completed backup snapshot for the given
   * partition on disk, overwriting it with garbage bytes so a later restore's RocksDB sanity check
   * fails with a checksum/corruption error. Returns the original file contents so they can be
   * restored later with {@link #restoreOriginalSnapshotFiles}.
   */
  static Map<Path, byte[]> corruptPartitionSnapshot(
      final Path backupDir, final long backupId, final int nodeId, final int partitionId)
      throws IOException {
    final var snapshotDir =
        backupDir
            .resolve("contents")
            .resolve(String.valueOf(partitionId))
            .resolve(String.valueOf(backupId))
            .resolve(String.valueOf(nodeId))
            .resolve("snapshot");

    final List<Path> sstFiles;
    try (final var files = Files.list(snapshotDir)) {
      sstFiles = files.filter(p -> p.toString().endsWith(".sst")).sorted().toList();
    }
    assertThat(sstFiles)
        .describedAs(
            "node %d's backup snapshot for partition %d at %s must contain .sst files to corrupt",
            nodeId, partitionId, snapshotDir)
        .isNotEmpty();

    final var originalContents = new HashMap<Path, byte[]>();
    for (final var sstFile : sstFiles) {
      originalContents.put(sstFile, Files.readAllBytes(sstFile));
      Files.write(
          sstFile,
          "<--corrupted-by-InProcessRestoreTestUtil-->".getBytes(),
          StandardOpenOption.TRUNCATE_EXISTING);
    }
    return originalContents;
  }

  /** Restores the file contents captured by {@link #corruptPartitionSnapshot}. */
  static void restoreOriginalSnapshotFiles(final Map<Path, byte[]> originalContents)
      throws IOException {
    for (final var entry : originalContents.entrySet()) {
      Files.write(entry.getKey(), entry.getValue(), StandardOpenOption.TRUNCATE_EXISTING);
    }
  }
}
