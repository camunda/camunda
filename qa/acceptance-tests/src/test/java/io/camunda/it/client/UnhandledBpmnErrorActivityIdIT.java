/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static io.camunda.qa.util.multidb.CamundaMultiDBExtension.TIMEOUT_DATA_AVAILABILITY;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.api.statistics.response.ProcessElementStatistics;
import io.camunda.exporter.CamundaExporter;
import io.camunda.qa.util.cluster.TestCamundaApplication;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.security.api.model.config.AuthenticationMethod;
import io.camunda.zeebe.model.bpmn.Bpmn;
import java.util.HashMap;
import java.util.Map;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

/**
 * Reproduces the production bug where the list-view flow-node document for a service task ends up
 * with {@code activityId="NO_CATCH_EVENT_FOUND"} after a BPMN error is thrown by a job that has no
 * matching catch event. The marker is a sentinel that {@code JobThrowErrorProcessor} sets on the
 * JOB record's {@code elementId} so it can short-circuit subsequent catch-event lookups; it is
 * never meant to surface in user-facing data.
 *
 * <h2>Why the bug happens (production)</h2>
 *
 * Inside the camunda-exporter, all three list-view flow-node handlers ({@code
 * ListViewFlowNodeFromProcessInstanceHandler}, {@code ListViewFlowNodeFromJobHandler}, {@code
 * ListViewFlowNodeFromIncidentHandler}) share a single {@code FlowNodeInstanceForListViewEntity}
 * cache slot keyed by {@code (elementInstanceKey, entityType)}. They all mutate the same entity
 * during one batch, and at flush time each handler reads the entity in its <em>final</em> state.
 *
 * <p>Of the three handlers, only the <strong>process-instance</strong> handler puts {@code
 * ACTIVITY_ID} in its {@code updateFields} — that handler is the only one that can overwrite the
 * activityId of an existing list-view document.
 *
 * <p>The engine emits {@code JOB.ERROR_THROWN} (with {@code elementId="NO_CATCH_EVENT_FOUND"}) and
 * {@code INCIDENT.CREATED} (with the real elementId) at <em>consecutive</em> log positions inside
 * one processor cycle of {@link io.camunda.zeebe.engine.processing.job.JobThrowErrorProcessor}. The
 * bug requires all three of the following to occur together for one process instance:
 *
 * <ol>
 *   <li><strong>Co-residence</strong> — {@code PI.ELEMENT_ACTIVATING(serviceTask)} and {@code
 *       JOB.ERROR_THROWN} land in the same exporter batch, so the PI handler is enrolled in {@code
 *       cachedEntitiesToFlush} for that entity and its {@code flush} <em>will</em> run for this
 *       batch.
 *   <li><strong>Mid-pair cut</strong> — the batch flushes between {@code JOB.ERROR_THROWN} and
 *       {@code INCIDENT.CREATED}, leaving {@code INCIDENT.CREATED} for the next batch.
 *   <li><strong>No healing</strong> — because {@code INCIDENT.CREATED} is not in this batch, the
 *       {@code ListViewFlowNodeFromIncidentHandler} cannot heal the shared entity back to the real
 *       elementId before the PI handler's {@code flush} reads {@code entity.getActivityId()} and
 *       writes the marker into {@code updateFields[ACTIVITY_ID]}.
 * </ol>
 *
 * <p>If {@code INCIDENT.CREATED} were in the same batch, its {@code updateEntity} would re-set
 * {@code activityId} to the real id and the PI handler would write the real id. If {@code
 * PI.ELEMENT_ACTIVATING} were not in the same batch as {@code JOB.ERROR_THROWN}, the PI handler
 * would not flush at all and the existing document's {@code activityId} would remain untouched (no
 * other handler writes that field).
 *
 * <h2>How this IT engineers conditions (1) + (2)</h2>
 *
 * The exporter's {@code shouldFlush()} fires when batch-size, memory or scheduled-delay thresholds
 * are reached.
 *
 * <ul>
 *   <li>Size cannot trigger between {@code JOB.ERROR_THROWN} and {@code INCIDENT.CREATED} for the
 *       same {@code elementInstanceKey} because both records share the cache slot already created
 *       by the earlier PI activation — {@code JOB.ERROR_THROWN} does not grow {@code
 *       getBatchSize()}. So size threshold must be high enough to keep PI activation in the same
 *       batch as {@code JOB.ERROR_THROWN}.
 *   <li>Time cannot trigger because {@code JOB.ERROR_THROWN} and {@code INCIDENT.CREATED} are
 *       written at adjacent log positions inside one processor cycle.
 *   <li>Memory <em>can</em> trigger if a single record is large enough to push {@code
 *       totalMemoryEstimate} over the threshold. {@code JobThrowErrorProcessor.throwError} attaches
 *       the throwError command's variables to the {@code JOB.ERROR_THROWN} record (see {@code
 *       job.setVariables(command.getValue().getVariablesBuffer())}), so a {@code
 *       throwError(variables=~1.5 MB)} produces a {@code JOB.ERROR_THROWN} record whose raw size
 *       crosses the {@code memoryLimit=1 MB} threshold by itself.
 * </ul>
 *
 * The exporter is therefore configured as follows:
 *
 * <ul>
 *   <li>{@code bulk.size = 5000} (production default) — keeps PI activation and {@code
 *       JOB.ERROR_THROWN} in the same batch.
 *   <li>{@code bulk.delay = 5 s} — eliminates the scheduled-flush variable.
 *   <li>{@code bulk.memoryLimit = 1 MB} — combined with the large variables payload below, the
 *       single {@code JOB.ERROR_THROWN} record causes {@code shouldFlush()} to return {@code true}
 *       immediately after the JOB record but before {@code INCIDENT.CREATED} is added.
 * </ul>
 *
 * With this configuration, throwing one BPMN error with ~1.5 MB of variables on a single PI
 * deterministically reproduces the bug: the document for the service task ends up with {@code
 * activityId="NO_CATCH_EVENT_FOUND"}.
 */
@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "rdbms.*$")
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
public class UnhandledBpmnErrorActivityIdIT {

  @MultiDbTestApplication(managedLifecycle = false)
  private static final TestCamundaApplication STANDALONE_CAMUNDA =
      new TestCamundaApplication()
          .withAuthenticationMethod(AuthenticationMethod.BASIC)
          .withUnauthenticatedAccess();

  @BeforeAll
  static void setUp() {
    final var camundaExporter = CamundaExporter.class.getSimpleName().toLowerCase();
    STANDALONE_CAMUNDA.withUnifiedConfig(
        c -> {
          final var newArgs =
              new HashMap<>(c.getData().getExporters().get(camundaExporter).getArgs());
          // See class javadoc for why these specific values reproduce the bug.
          newArgs.put("bulk", Map.of("size", 5000, "delay", 5, "memoryLimit", 1));
          c.getData().getExporters().get(camundaExporter).setArgs(newArgs);
        });

    STANDALONE_CAMUNDA.start();
    STANDALONE_CAMUNDA.awaitCompleteTopology();
  }

  @AfterAll
  static void tearDown() {
    STANDALONE_CAMUNDA.stop();
  }

  @Test
  void shouldNeverExposeNoCatchEventFoundMarkerAsElementId() {
    final var jobType = "throw-unhandled-error";
    final var serviceTaskId = "ServiceTaskThrowingError";

    try (final var camundaClient = STANDALONE_CAMUNDA.newClientBuilder().build()) {
      // given - a process whose service task throws a BPMN error with no matching catch event
      final var processModel =
          Bpmn.createExecutableProcess("process-with-unhandled-error")
              .startEvent("StartEvent")
              .serviceTask(serviceTaskId, t -> t.zeebeJobType(jobType))
              .endEvent("EndEvent")
              .done();
      final var processDefinitionKey =
          camundaClient
              .newDeployResourceCommand()
              .addProcessModel(processModel, "process-with-unhandled-error.bpmn")
              .send()
              .join()
              .getProcesses()
              .getFirst()
              .getProcessDefinitionKey();

      final var processInstanceKey =
          camundaClient
              .newCreateInstanceCommand()
              .processDefinitionKey(processDefinitionKey)
              .send()
              .join()
              .getProcessInstanceKey();

      // activate the job so the engine emits JOB.ACTIVATED
      final var activatedJob =
          Awaitility.await("the service task job is activatable")
              .atMost(TIMEOUT_DATA_AVAILABILITY)
              .ignoreExceptions()
              .until(
                  () ->
                      camundaClient
                          .newActivateJobsCommand()
                          .jobType(jobType)
                          .maxJobsToActivate(1)
                          .send()
                          .join()
                          .getJobs(),
                  jobs -> jobs.size() == 1)
              .getFirst();

      // when - throw an unhandled error WITH a large variables payload (~1.5 MB). The engine
      // copies the variables into JOB.ERROR_THROWN (JobThrowErrorProcessor:156), so the raw
      // record size crosses the exporter's bulk.memoryLimit=1MB threshold. shouldFlush() then
      // returns true between JOB.ERROR_THROWN and INCIDENT.CREATED, which sit at consecutive
      // log positions inside one processor cycle. INCIDENT.CREATED is pushed to the next batch
      // and therefore cannot heal the shared FlowNodeInstanceForListViewEntity before the PI
      // handler's flush reads the poisoned activityId.
      camundaClient
          .newThrowErrorCommand(activatedJob.getKey())
          .errorCode("unhandled-error")
          .errorMessage("no catch event for this error")
          .variables(largeJsonVariables())
          .send()
          .join();

      // wait until the incident is visible (so we know the second batch has flushed too)
      Awaitility.await("the incident for the unhandled error is visible")
          .atMost(TIMEOUT_DATA_AVAILABILITY)
          .ignoreExceptions()
          .untilAsserted(
              () ->
                  assertThat(
                          camundaClient
                              .newProcessInstanceSearchRequest()
                              .filter(
                                  f -> f.processInstanceKey(processInstanceKey).hasIncident(true))
                              .send()
                              .join()
                              .items())
                      .hasSize(1));

      // then - the statistics endpoint must never expose the engine-internal marker as an
      // elementId. With the bug present, the list-view document for the service task carries
      // activityId="NO_CATCH_EVENT_FOUND", which surfaces here verbatim.
      Awaitility.await("statistics report the real service task elementId, not the marker")
          .atMost(TIMEOUT_DATA_AVAILABILITY)
          .ignoreExceptions()
          .untilAsserted(
              () ->
                  assertThat(
                          camundaClient
                              .newProcessInstanceElementStatisticsRequest(processInstanceKey)
                              .send()
                              .join()
                              .stream()
                              .map(ProcessElementStatistics::getElementId)
                              .toList())
                      .as(
                          "the list-view document for the service task must not expose the "
                              + "engine-internal NO_CATCH_EVENT_FOUND marker as activityId")
                      .doesNotContain("NO_CATCH_EVENT_FOUND")
                      .contains(serviceTaskId));
    }
  }

  /**
   * Builds a JSON object payload of roughly 1.5 MB so that, once serialized into the engine's
   * {@code JOB.ERROR_THROWN} record, the single record exceeds the exporter's 1 MB memory threshold
   * all by itself.
   */
  private static String largeJsonVariables() {
    final var oneKilobyte = "x".repeat(1024);
    final var sb = new StringBuilder(1_600_000);
    sb.append("{\"payload\":\"");
    for (int i = 0; i < 1500; i++) {
      sb.append(oneKilobyte);
    }
    sb.append("\"}");
    return sb.toString();
  }
}
