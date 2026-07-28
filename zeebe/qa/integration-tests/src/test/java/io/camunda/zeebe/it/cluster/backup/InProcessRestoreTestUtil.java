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
import io.camunda.zeebe.it.util.ZeebeResourcesHelper;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.Protocol;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.awaitility.Awaitility;

/**
 * Shared helpers for the in-process restore tests ({@link InProcessRestoreAcceptance}, {@link
 * InProcessRestoreRetryOnCorruptionIT} and {@link InProcessRdbmsRangeRestoreIT}), which all trigger
 * a restore over the cluster's REST endpoint while a broker is in {@code RECOVERING} mode. Also
 * exposes {@link #changeMode} for triggering a cluster mode change over {@code v2/mode}, reused by
 * {@code ModeChangeAcceptanceIT} outside this package.
 */
public final class InProcessRestoreTestUtil {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

  private InProcessRestoreTestUtil() {}

  /** POSTs a restore request with the given body to {@code v2/restore} and returns the response. */
  static HttpResponse<String> sendRestoreRequest(
      final CamundaClient client, final Map<String, Object> body) {
    try {
      final var uri =
          URI.create(
              "%sv2/restore?dryRun=false".formatted(client.getConfiguration().getRestAddress()));
      final var request =
          HttpRequest.newBuilder(uri)
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(OBJECT_MAPPER.writeValueAsString(body)))
              .build();
      return HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
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
      return OBJECT_MAPPER.readTree(response.body()).get("changeId").asLong();
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to parse restore REST response", e);
    }
  }

  /**
   * Triggers a cluster mode change to the given mode over {@code v2/mode}, asserting the request is
   * accepted (200), and returns the id of the cluster configuration change it started.
   */
  public static long changeMode(
      final CamundaClient client, final String mode, final boolean dryRun) {
    try {
      final var uri =
          URI.create(
              "%sv2/mode?mode=%s&dryRun=%s"
                  .formatted(client.getConfiguration().getRestAddress(), mode, dryRun));
      final var request =
          HttpRequest.newBuilder(uri).method("PATCH", HttpRequest.BodyPublishers.noBody()).build();
      final var response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
      assertThat(response.statusCode())
          .describedAs("mode change REST response: %s".formatted(response.body()))
          .isEqualTo(200);
      return OBJECT_MAPPER.readTree(response.body()).get("changeId").asLong();
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to trigger mode change via REST endpoint", e);
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("Interrupted while triggering mode change via REST endpoint", e);
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
              final var jobs =
                  client
                      .newActivateJobsCommand()
                      .jobType(jobType)
                      .maxJobsToActivate(2 * partitionsCount)
                      .send()
                      .join();
              jobs.getJobs()
                  .forEach(
                      job -> {
                        activatedJobKeys.add(job.getKey());
                        client.newCompleteCommand(job.getKey()).send().join();
                      });

              final var partitionsWithActivatedJob =
                  activatedJobKeys.stream()
                      .map(Protocol::decodePartitionId)
                      .collect(Collectors.toSet());
              assertThat(partitionsWithActivatedJob)
                  .describedAs(
                      "jobs are activated from every partition, proving partition data was"
                          + " actually restored")
                  .containsExactlyInAnyOrderElementsOf(
                      IntStream.rangeClosed(1, partitionsCount).boxed().toList());
            });
  }
}
