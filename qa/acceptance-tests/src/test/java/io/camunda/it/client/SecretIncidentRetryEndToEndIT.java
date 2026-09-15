/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static io.camunda.it.util.TestHelper.deployResource;
import static io.camunda.it.util.TestHelper.waitForProcessesToBeDeployed;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.enums.BatchOperationItemState;
import io.camunda.client.api.search.enums.IncidentState;
import io.camunda.client.api.search.response.BatchOperationItems.BatchOperationItem;
import io.camunda.client.api.search.response.Incident;
import io.camunda.qa.util.multidb.CamundaMultiDBExtension;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

/**
 * Covers the retry an operator triggers in Operate for an incident raised because a secret could
 * not be resolved, end to end: a real broker with a real file-based secret store, the records
 * exported to secondary storage, and the v2 search API the UI reads back.
 *
 * <p>That last hop is why this exists next to the engine and integration suites. Those assert on
 * the partition's records, whereas what an operator acts on is the exported, searchable incident:
 * "the instance is healthy again" is a statement about {@code /v2/incidents/search}, not about the
 * log. See <a href="https://github.com/camunda/camunda/issues/62727">#62727</a>.
 *
 * <p>Both retry paths of the issue are covered — the per-incident button and the bulk {@code
 * RESOLVE_INCIDENT} batch operation, whose "every item completed" result is the stronger claim of
 * the two and was equally unfounded.
 *
 * <p>No worker is ever connected. Secret resolution used to be requested only from the activation
 * paths, so a retry re-read the store only when one happened to be attached; with none, the
 * incident stayed resolved and the instance read as healthy while the secret was still missing.
 */
@MultiDbTest
public class SecretIncidentRetryEndToEndIT {

  @MultiDbTestApplication
  static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker()
          // starts empty: each test writes only the secrets it wants to exist
          .withFileBasedSecretStore(directory -> {})
          .withProcessingConfig(
              processing ->
                  processing.getEngine().getSecrets().setInterval(Duration.ofMillis(200)));

  /** Only has to outlive the park; nothing is returned, so it is waited out in full. */
  private static final Duration PRIMING_POLL_TIMEOUT = Duration.ofSeconds(1);

  private static CamundaClient camundaClient;

  @Test
  void shouldSurfaceTheIncidentAgainAfterARetryThatCreatedNoSecret() {
    // given - an instance whose job is blocked on a secret the store does not hold
    final var scenario = new Scenario();
    scenario.deployAndRaiseIncidents(1);
    final long firstIncidentKey = scenario.onlyActiveIncidentKey();

    // when - the operator retries without creating the secret, and no worker is connected
    camundaClient.newResolveIncidentCommand(firstIncidentKey).send().join();

    // then - the UI reads back an active incident again, so the instance never looks healthy. A
    // different key: the original is resolved and the re-attempted resolution raises a new one.
    Awaitility.await("until an active incident is searchable again")
        .atMost(CamundaMultiDBExtension.TIMEOUT_DATA_AVAILABILITY)
        .untilAsserted(
            () ->
                assertThat(scenario.activeIncidentKeys())
                    .describedAs("active incidents of an instance whose secret is still missing")
                    .hasSize(1)
                    .doesNotContain(firstIncidentKey));
  }

  @Test
  void shouldSurfaceIncidentsAgainAfterABulkRetryThatReportedEveryItemCompleted() {
    // given - several instances all blocked on the same missing secret
    final var scenario = new Scenario();
    final int instances = 3;
    scenario.deployAndRaiseIncidents(instances);
    final List<Long> firstIncidentKeys = scenario.activeIncidentKeys();
    assertThat(firstIncidentKeys).hasSize(instances);

    // when - the operator selects them all and hits the toolbar Retry
    final var batchOperationKey =
        camundaClient
            .newCreateBatchOperationCommand()
            .resolveIncident()
            .filter(f -> f.processDefinitionId(scenario.processId).hasIncident(true))
            .send()
            .join()
            .getBatchOperationKey();

    // then - the batch reports every item completed, which it may: the resolve commands were
    // accepted. That is the success signal the operator reads, and on its own it says nothing
    // about the secret.
    Awaitility.await("until every batch item is completed")
        .atMost(CamundaMultiDBExtension.TIMEOUT_DATA_AVAILABILITY)
        .untilAsserted(
            () -> {
              final var items =
                  camundaClient
                      .newBatchOperationItemsSearchRequest()
                      .filter(f -> f.batchOperationKey(batchOperationKey))
                      .send()
                      .join()
                      .items();
              assertThat(items).hasSize(instances);
              assertThat(items)
                  .extracting(BatchOperationItem::getStatus)
                  .containsOnly(BatchOperationItemState.COMPLETED);
            });

    // and - the incidents come back regardless, so a completed batch cannot leave the operator
    // believing instances were fixed that were not
    Awaitility.await("until every incident is searchable again")
        .atMost(CamundaMultiDBExtension.TIMEOUT_DATA_AVAILABILITY)
        .untilAsserted(
            () ->
                assertThat(scenario.activeIncidentKeys())
                    .describedAs("active incidents after a bulk retry that created no secret")
                    .hasSize(instances)
                    .doesNotContainAnyElementsOf(firstIncidentKeys));
  }

  @Test
  void shouldClearTheIncidentForGoodWhenTheSecretIsCreatedBeforeRetrying() {
    // given - the operator fixes the actual cause first, which must keep working
    final var scenario = new Scenario();
    scenario.deployAndRaiseIncidents(1);
    final long incidentKey = scenario.onlyActiveIncidentKey();
    scenario.createSecret();

    // when
    camundaClient.newResolveIncidentCommand(incidentKey).send().join();

    // then - the instance is genuinely healthy: no active incident, and the job is delivered with
    // the resolved value
    Awaitility.await("until no active incident remains")
        .atMost(CamundaMultiDBExtension.TIMEOUT_DATA_AVAILABILITY)
        .untilAsserted(
            () ->
                assertThat(scenario.activeIncidentKeys())
                    .describedAs("active incidents once the secret exists")
                    .isEmpty());

    final var activated =
        Awaitility.await("until the job is delivered with the secret")
            .atMost(CamundaMultiDBExtension.TIMEOUT_DATA_AVAILABILITY)
            .until(() -> scenario.pollOnce(Duration.ofSeconds(5)), jobs -> !jobs.isEmpty());
    assertThat(activated.getFirst().getVariablesAsMap())
        .containsEntry("authorization", scenario.secretValue);
  }

  /** One test's own process, job type and secret, so tests cannot see each other's incidents. */
  private static final class Scenario {

    private final String suffix = UUID.randomUUID().toString().replace("-", "");
    private final String processId = "process" + suffix;
    private final String jobType = "jobType" + suffix;
    private final String secretName = "secret" + suffix;
    private final String secretValue = "value-of-" + suffix;

    /**
     * Deploys the process, starts {@code count} instances and drives the one poll that checks the
     * references, parks the jobs and requests the resolution that then fails. With no worker
     * attached a created job is announced unchecked, so without that poll nothing would ever look
     * at the secret.
     */
    private void deployAndRaiseIncidents(final int count) {
      deployResource(camundaClient, process(), processId + ".bpmn");
      waitForProcessesToBeDeployed(camundaClient, 1);
      for (int i = 0; i < count; i++) {
        camundaClient
            .newCreateInstanceCommand()
            .bpmnProcessId(processId)
            .latestVersion()
            .send()
            .join();
      }
      assertThat(pollOnce(PRIMING_POLL_TIMEOUT))
          .describedAs("a job blocked on a missing secret is never handed out")
          .isEmpty();
      Awaitility.await("until every incident is searchable")
          .atMost(CamundaMultiDBExtension.TIMEOUT_DATA_AVAILABILITY)
          .untilAsserted(() -> assertThat(activeIncidentKeys()).hasSize(count));
    }

    private List<io.camunda.client.api.response.ActivatedJob> pollOnce(final Duration timeout) {
      return camundaClient
          .newActivateJobsCommand()
          .jobType(jobType)
          .maxJobsToActivate(10)
          .timeout(Duration.ofMinutes(5))
          .requestTimeout(timeout)
          .send()
          .join()
          .getJobs();
    }

    /** The active incidents of this scenario's process, as the UI would search for them. */
    private List<Long> activeIncidentKeys() {
      return camundaClient
          .newIncidentSearchRequest()
          .filter(f -> f.processDefinitionId(processId).state(IncidentState.ACTIVE))
          .send()
          .join()
          .items()
          .stream()
          .map(Incident::getIncidentKey)
          .toList();
    }

    private long onlyActiveIncidentKey() {
      final var keys = activeIncidentKeys();
      assertThat(keys).hasSize(1);
      return keys.getFirst();
    }

    private void createSecret() {
      try {
        Files.writeString(
            BROKER.getFileBasedSecretStoreDirectory().resolve(secretName),
            secretValue,
            StandardCharsets.UTF_8);
      } catch (final IOException e) {
        throw new UncheckedIOException("Failed to write the secret '" + secretName + "'", e);
      }
    }

    private BpmnModelInstance process() {
      return Bpmn.createExecutableProcess(processId)
          .startEvent()
          .serviceTask(
              "task",
              task ->
                  task.zeebeJobType(jobType)
                      .zeebeInputExpression("camunda.secrets." + secretName, "authorization"))
          .endEvent()
          .done();
    }
  }
}
