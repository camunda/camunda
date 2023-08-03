/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.client.command;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.it.clustering.ClusteringRuleExtension;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.ArrayList;
import java.util.function.Consumer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

final class StreamJobsTest {
  @RegisterExtension
  private static final ClusteringRuleExtension CLUSTER = new ClusteringRuleExtension(1, 1, 1);

  @Test
  void shouldStreamSequencedJobs() {
    // given
    final var jobs = new ArrayList<ActivatedJob>();
    final var process =
        Bpmn.createExecutableProcess("sequence")
            .startEvent()
            .serviceTask("task01", b -> b.zeebeJobType("sequence"))
            .serviceTask("task02", b -> b.zeebeJobType("sequence"))
            .serviceTask("task03", b -> b.zeebeJobType("sequence"))
            .endEvent()
            .done();
    final Consumer<ActivatedJob> jobHandler =
        job -> {
          jobs.add(job);
          CLUSTER.getClient().newCompleteCommand(job).send();
        };
    deployProcess(process);

    // when
    final var stream =
        CLUSTER.getClient().newStreamJobsCommand().jobType("sequence").consumer(jobHandler).send();
    final boolean processInstanceCompleted;
    awaitStreamRegistered();

    try {
      final var processInstanceKey = createProcessInstance("sequence");
      processInstanceCompleted =
          RecordingExporter.processInstanceRecords()
              .withProcessInstanceKey(processInstanceKey)
              .limitToProcessInstanceCompleted()
              .findFirst()
              .isPresent();
    } finally {
      stream.cancel(true);
    }

    // then
    assertThat(processInstanceCompleted)
        .as("all tasks were completed to complete the process instance")
        .isTrue();
    assertThat(jobs).hasSize(3);
  }

  private void awaitStreamRegistered() {
    final var brokerBridge = CLUSTER.getBrokerBridge(0);
    final var jobStreamService = brokerBridge.getJobStreamService().orElseThrow();
    Awaitility.await("until a job stream is registered")
        .untilAsserted(
            () -> assertThat(jobStreamService.remoteStreamService().streams()).hasSize(1));
  }

  private long createProcessInstance(final String processId) {
    return CLUSTER
        .getClient()
        .newCreateInstanceCommand()
        .bpmnProcessId(processId)
        .latestVersion()
        .send()
        .join()
        .getProcessInstanceKey();
  }

  private void deployProcess(final BpmnModelInstance process) {
    CLUSTER
        .getClient()
        .newDeployResourceCommand()
        .addProcessModel(process, "sequence.bpmn")
        .send()
        .join();
  }
}
