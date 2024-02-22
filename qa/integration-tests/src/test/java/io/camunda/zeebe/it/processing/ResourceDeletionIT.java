/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.processing;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.ZeebeFuture;
import io.camunda.zeebe.client.api.response.DeleteResourceResponse;
import io.camunda.zeebe.client.api.response.DeploymentEvent;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ResourceDeletionIntent;
import io.camunda.zeebe.qa.util.actuator.PartitionsActuator;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotId;
import io.camunda.zeebe.test.util.junit.AutoCloseResources;
import io.camunda.zeebe.test.util.junit.AutoCloseResources.AutoCloseResource;
import io.camunda.zeebe.test.util.junit.RegressionTest;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Future;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;

@AutoCloseResources
@ZeebeIntegration
final class ResourceDeletionIT {

  @TestZeebe private final TestStandaloneBroker zeebe = new TestStandaloneBroker()
      .withGatewayConfig(cfg -> cfg.getLongPolling().setEnabled(false))
      .withRecordingExporter(true);
  private final PartitionsActuator partitions = PartitionsActuator.of(zeebe);
  @AutoCloseResource private ZeebeClient client;

  @BeforeEach
  void beforeEach() {
    client = zeebe.newClientBuilder().build();
  }

  @RegressionTest("dsadsa")
  public void shouldRejectResourceDeletion() {

    client.newWorker().jobType("test").handler((jobClient, job) -> {
      jobClient.newCompleteCommand(job.getKey()).send().join();
    }).open();

    client.newWorker().jobType("test1").handler((jobClient, job) -> {
      jobClient.newCompleteCommand(job.getKey()).send().join();
    }).open();

    client.newWorker().jobType("test2").handler((jobClient, job) -> {
      jobClient.newCompleteCommand(job.getKey()).send().join();
    }).open();


    final DeploymentEvent deployment =
        client
            .newDeployResourceCommand()
            .addProcessModel(
                Bpmn.createExecutableProcess("form_linking_test")
                    .startEvent()
                    .userTask()
                    .zeebeFormId("formId1")
                    .endEvent()
                    .done(),
                "form_linking_test.bpmn")
            .addProcessModel(
                Bpmn.createExecutableProcess("form_linking_test2")
                    .startEvent()
                    .userTask()
                    .zeebeFormId("formId2")
                    .endEvent()
                    .done(),
                "form_linking_test2.bpmn")
            .addResourceFromClasspath("form/form-linking-test-form-1.form")
            .addResourceFromClasspath("form/form-linking-test-form-2.form")
            .send()
            .join();

    while (true) {
      client.newDeleteResourceCommand(Protocol.encodePartitionId(1, 999999L)).send();
    }

  }
}
