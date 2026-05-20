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

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.response.Job;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.zeebe.model.bpmn.Bpmn;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

/**
 * Verifies that the engine-internal {@code NO_CATCH_EVENT_FOUND} sentinel (set by {@link
 * io.camunda.zeebe.engine.processing.job.JobThrowErrorProcessor} when a BPMN error has no matching
 * catch event) never surfaces in the jobs search API as the job's {@code elementId}.
 *
 * <p>The bug: when {@code RETRIES_UPDATED} fires after {@code ERROR_THROWN}, the job record still
 * carries {@code elementId="NO_CATCH_EVENT_FOUND"}. Both {@code JobHandler} (ES/OS exporter) and
 * {@code JobExportHandler} (RDBMS exporter) used to write that value verbatim into secondary
 * storage, corrupting the {@code elementId} field returned by the jobs search API.
 */
@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "rdbms.*$")
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
public class UnhandledBpmnErrorJobElementIdIT {

  private static CamundaClient camundaClient;

  @Test
  void shouldNotExposeNoCatchEventFoundMarkerAsJobElementId() {
    final var jobType = "service-task-throw-unhandled-error";
    final var serviceTaskId = "ServiceTaskThrowingUnhandledError";

    // given - a process whose service task throws a BPMN error with no matching catch event
    final var processModel =
        Bpmn.createExecutableProcess("process-unhandled-error-job-element-id")
            .startEvent("StartEvent")
            .serviceTask(serviceTaskId, t -> t.zeebeJobType(jobType))
            .endEvent("EndEvent")
            .done();

    camundaClient
        .newDeployResourceCommand()
        .addProcessModel(processModel, "process-unhandled-error-job-element-id.bpmn")
        .send()
        .join();

    camundaClient
        .newCreateInstanceCommand()
        .bpmnProcessId("process-unhandled-error-job-element-id")
        .latestVersion()
        .send()
        .join();

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
                jobs -> !jobs.isEmpty())
            .getFirst();

    // when - throw a BPMN error for which there is no catch event; the engine sets the job's
    // elementId to the NO_CATCH_EVENT_FOUND sentinel and creates an incident
    camundaClient
        .newThrowErrorCommand(activatedJob.getKey())
        .errorCode("unhandled-error-code")
        .send()
        .join();

    // wait until the incident appears so we know ERROR_THROWN has been processed
    Awaitility.await("incident created for unhandled BPMN error")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptions()
        .untilAsserted(
            () ->
                assertThat(
                        camundaClient
                            .newIncidentSearchRequest()
                            .filter(f -> f.jobKey(activatedJob.getKey()))
                            .send()
                            .join()
                            .items())
                    .hasSize(1));

    // update retries - this triggers RETRIES_UPDATED which was the source of the leak
    camundaClient.newUpdateRetriesCommand(activatedJob.getKey()).retries(1).send().join();

    // then - the job search API must return the real service-task elementId, never the marker
    Awaitility.await("job search reflects the RETRIES_UPDATED state")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var jobs =
                  camundaClient
                      .newJobSearchRequest()
                      .filter(f -> f.jobKey(activatedJob.getKey()))
                      .send()
                      .join()
                      .items();
              assertThat(jobs).hasSize(1);
              assertThat(jobs.stream().map(Job::getElementId).toList())
                  .as("elementId should not be overwritten with NO_CATCH_EVENT_FOUND marker")
                  .doesNotContain("NO_CATCH_EVENT_FOUND")
                  .containsOnly(serviceTaskId);
            });
  }
}
