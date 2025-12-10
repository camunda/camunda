/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.DeploymentEvent;
import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

@MultiDbTest
public class ActivateJobIT {

  @MultiDbTestApplication
  private static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker()
          .withBasicAuth()
          .withAuthorizationsEnabled()
          .withUnifiedConfig(c -> c.getApi().getLongPolling().setEnabled(true));

  private static CamundaClient camundaClient;

  @Test
  public void shouldActivateJobsIfBatchIsTruncated(
      @Authenticated final CamundaClient camundaClient) {
    // given
    final int availableJobs = 1;

    final int maxMessageSize =
        (int) BROKER.unifiedConfig().getCluster().getNetwork().getMaxMessageSize().toBytes();
    final var largeVariableValue = "x".repeat(maxMessageSize);
    final var largeVariableValue2 = "x".repeat(maxMessageSize);
    final String variablesJson =
        String.format(
            "{\"variablesJson\":\"%s\",\"variablesJson2\":\"%s\"}",
            largeVariableValue, largeVariableValue2);
    createSingleJobModelInstance();

    final DeploymentEvent deploymentEvent =
        camundaClient
            .newDeployResourceCommand()
            .addProcessModel(createSingleJobModelInstance(), "process.bpmn")
            .send()
            .join();

    final long processDefinitionKey =
        deploymentEvent.getProcesses().getFirst().getProcessDefinitionKey();

    Awaitility.await("deployment is created")
        .until(
            () ->
                RecordingExporter.deploymentRecords()
                    .withIntent(DeploymentIntent.CREATED)
                    .withRecordKey(deploymentEvent.getKey())
                    .exists());

    final var processInstanceKeys =
        IntStream.range(0, 1)
            .boxed()
            .map(i -> createProcessInstance(camundaClient, processDefinitionKey, variablesJson))
            .toList();

    // then
    final List<Long> jobKeys =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withType("type")
            .filter(r -> processInstanceKeys.contains(r.getValue().getProcessInstanceKey()))
            .limit(1)
            .map(Record::getKey)
            .collect(Collectors.toList());

    assertThat(jobKeys).describedAs("Expected %d created jobs", 1).hasSize(1);
    assertThat(deploymentEvent).isNotNull();
  }

  private BpmnModelInstance createSingleJobModelInstance() {
    return Bpmn.createExecutableProcess("process")
        .startEvent("start")
        .serviceTask("task", t -> t.zeebeJobType("type"))
        .endEvent("end")
        .done();
  }

  private long createProcessInstance(
      final CamundaClient camundaClient, final long processDefinitionKey, final String variables) {
    return camundaClient
        .newCreateInstanceCommand()
        .processDefinitionKey(processDefinitionKey)
        .variables(variables)
        .send()
        .join()
        .getProcessInstanceKey();
  }
}
