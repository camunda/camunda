/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.exporter;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.filter.IncidentFilter;
import io.camunda.client.api.search.response.IncidentErrorType;
import io.camunda.it.utils.BrokerITInvocationProvider;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import java.time.Duration;
import java.util.UUID;
import java.util.function.Consumer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.RegisterExtension;

public class IncidentExporterIT {

  // RDBMS doesn't support Incidents up to now
  @RegisterExtension
  static final BrokerITInvocationProvider PROVIDER =
      new BrokerITInvocationProvider().withoutRdbmsExporter();

  @TestTemplate
  void shouldExportIncident(final TestStandaloneBroker testBroker) {
    final var client = testBroker.newClientBuilder().build();

    final var resource =
        client
            .newDeployResourceCommand()
            .addResourceFromClasspath("process/error-end-event.bpmn")
            .send()
            .join();

    final var processInstanceKey =
        client
            .newCreateInstanceCommand()
            .bpmnProcessId(resource.getProcesses().getFirst().getBpmnProcessId())
            .latestVersion()
            .send()
            .join()
            .getProcessInstanceKey();

    waitForIncident(client, f -> f.processInstanceKey(processInstanceKey));

    final var incidents =
        client
            .newIncidentQuery()
            .filter(f -> f.processInstanceKey(processInstanceKey))
            .send()
            .join()
            .items();

    // then
    assertThat(incidents).isNotEmpty();
    assertThat(incidents.size()).isEqualTo(1);
    assertThat(incidents.getFirst().getErrorType())
        .isEqualTo(IncidentErrorType.UNHANDLED_ERROR_EVENT);
    assertThat(incidents.getFirst().getProcessInstanceKey()).isEqualTo(processInstanceKey);
  }

  @TestTemplate
  void shouldExportUnhandledErrorIncident(final TestStandaloneBroker testBroker) {
    final var client = testBroker.newClientBuilder().build();

    final var resource =
        client
            .newDeployResourceCommand()
            .addResourceFromClasspath("process/errorProcess.bpmn")
            .send()
            .join();

    final var processInstanceKey =
        client
            .newCreateInstanceCommand()
            .bpmnProcessId(resource.getProcesses().getFirst().getBpmnProcessId())
            .latestVersion()
            .send()
            .join()
            .getProcessInstanceKey();

    throwIncident(client, "errorTask", "this-errorcode-does-not-exists", "Process error");

    waitForIncident(client, f -> f.processInstanceKey(processInstanceKey));

    final var incidents =
        client
            .newIncidentQuery()
            .filter(f -> f.processInstanceKey(processInstanceKey))
            .send()
            .join()
            .items();

    // then
    assertThat(incidents).isNotEmpty();
    assertThat(incidents.size()).isEqualTo(1);
    assertThat(incidents.getFirst().getErrorType())
        .isEqualTo(IncidentErrorType.UNHANDLED_ERROR_EVENT);
    assertThat(incidents.getFirst().getProcessInstanceKey()).isEqualTo(processInstanceKey);
  }

  private void waitForIncident(
      final CamundaClient client, final Consumer<IncidentFilter> filterFn) {
    Awaitility.await()
        .ignoreExceptions()
        .timeout(Duration.ofSeconds(30))
        .until(() -> !client.newIncidentQuery().filter(filterFn).send().join().items().isEmpty());
  }

  private void throwIncident(
      final CamundaClient client,
      final String jobType,
      final String errorCode,
      final String errorMessage) {
    client
        .newActivateJobsCommand()
        .jobType(jobType)
        .maxJobsToActivate(1)
        .workerName(UUID.randomUUID().toString())
        .send()
        .join()
        .getJobs()
        .forEach(
            j -> {
              final var inc =
                  client
                      .newThrowErrorCommand(j.getKey())
                      .errorCode(errorCode)
                      .errorMessage(errorMessage)
                      .send()
                      .join();
            });
  }
}
