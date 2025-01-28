/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.processing;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.DeploymentEvent;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.qa.util.actuator.PartitionsActuator;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotId;
import io.camunda.zeebe.test.util.junit.RegressionTest;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.time.Duration;
import java.util.Optional;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;

@ZeebeIntegration
final class FormLinkingIT {

  @TestZeebe
  private final TestStandaloneBroker zeebe = new TestStandaloneBroker().withUnauthenticatedAccess();

  private final PartitionsActuator partitions = PartitionsActuator.of(zeebe);
  @AutoClose private CamundaClient client;

  @BeforeEach
  void beforeEach() {
    client = zeebe.newClientBuilder().build();
  }

  @RegressionTest("https://github.com/camunda/camunda/issues/16311")
  public void shouldActivateUserTaskWithCorrectFormKey() {
    // given
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

    final var formKey = deployment.getForm().getFirst().getFormKey();
    final var formKey2 = deployment.getForm().getLast().getFormKey();

    // take snapshot and start the engine from snapshot
    partitions.takeSnapshot();
    Awaitility.await("Snapshot is taken")
        .atMost(Duration.ofSeconds(60))
        .until(
            () ->
                Optional.ofNullable(partitions.query().get(1).snapshotId())
                    .flatMap(FileBasedSnapshotId::ofFileName),
            Optional::isPresent)
        .orElseThrow();
    zeebe.stop();

    // when
    zeebe.withRecordingExporter(true).start().awaitCompleteTopology();

    // then

    // fills the cache correctly
    assertCorrectFormLinkedForProcess("form_linking_test", formKey);

    // previously corrupted the cache
    assertCorrectFormLinkedForProcess("form_linking_test2", formKey2);

    // failed previously because of corrupted cache
    assertCorrectFormLinkedForProcess("form_linking_test", formKey);
  }

  private void assertCorrectFormLinkedForProcess(final String processId, final long formKey) {
    final long processInstanceKey =
        client
            .newCreateInstanceCommand()
            .bpmnProcessId(processId)
            .latestVersion()
            .send()
            .join()
            .getProcessInstanceKey();
    assertThat(
            RecordingExporter.jobRecords(JobIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .getFirst()
                .getValue()
                .getCustomHeaders())
        .containsValue(String.valueOf(formKey));
  }
}
