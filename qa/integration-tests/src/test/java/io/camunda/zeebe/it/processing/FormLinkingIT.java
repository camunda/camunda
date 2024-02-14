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
import io.camunda.zeebe.client.api.response.DeploymentEvent;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.intent.FormIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.qa.util.actuator.PartitionsActuator;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotId;
import io.camunda.zeebe.test.util.junit.AutoCloseResources;
import io.camunda.zeebe.test.util.junit.AutoCloseResources.AutoCloseResource;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.time.Duration;
import java.util.Optional;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@AutoCloseResources
@ZeebeIntegration
final class FormLinkingIT {

  @TestZeebe private final TestStandaloneBroker zeebe = new TestStandaloneBroker();
  private final PartitionsActuator partitions = PartitionsActuator.of(zeebe);
  @AutoCloseResource private ZeebeClient client;

  @BeforeEach
  void beforeEach() {
    client = zeebe.newClientBuilder().build();
  }

  @Test
  public void shouldActivateUserTaskWithCorrectFormKey() {
    // given
    final String form1Path = "form/form-linking-test-form-1.form";

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
            .addResourceFromClasspath(form1Path)
            .send()
            .join();

    final Long formKey = deployment.getForm().getFirst().getFormKey();

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

    // create a process instance to trigger form linking where the actual caching issue exists
    final long processInstanceKey =
        client
            .newCreateInstanceCommand()
            .bpmnProcessId("form_linking_test")
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
        .containsValue(formKey.toString());

    // deploy another form to update form object referenced by formId1 in the cache (the wrong
    // behaviour)
    final String form2Path = "form/form-linking-test-form-2.form";
    client.newDeployResourceCommand().addResourceFromClasspath(form2Path).send().join();
    RecordingExporter.formRecords().withIntent(FormIntent.CREATED).withFormId("formId2").await();

    // create another process instance to verify it works as expected (e.g. cache data is not
    // changed)
    final long processInstanceKey2 =
        client
            .newCreateInstanceCommand()
            .bpmnProcessId("form_linking_test")
            .latestVersion()
            .send()
            .join()
            .getProcessInstanceKey();

    assertThat(
            RecordingExporter.jobRecords(JobIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey2)
                .getFirst()
                .getValue()
                .getCustomHeaders())
        .containsValue(formKey.toString());
  }
}
