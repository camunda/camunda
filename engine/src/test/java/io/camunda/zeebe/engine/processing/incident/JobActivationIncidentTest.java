/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.processing.incident;

import static io.zeebe.protocol.record.Assertions.assertThat;

import io.zeebe.engine.util.EngineRule;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.record.intent.IncidentIntent;
import io.zeebe.protocol.record.intent.JobIntent;
import io.zeebe.protocol.record.value.ErrorType;
import io.zeebe.test.util.BrokerClassRuleHelper;
import io.zeebe.test.util.record.RecordingExporter;
import io.zeebe.test.util.record.RecordingExporterTestWatcher;
import io.zeebe.util.ByteValue;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public final class JobActivationIncidentTest {
  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();
  private static final int VARIABLE_COUNT = 4;
  private static final long MAX_MESSAGE_SIZE = ByteValue.ofMegabytes(4);
  private static final String LARGE_TEXT = "x".repeat((int) (MAX_MESSAGE_SIZE / VARIABLE_COUNT));

  @Rule
  public RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  private long processDefinitionKey;
  private String jobType;
  private String processId;

  static BpmnModelInstance createProcess(final String processId, final String jobType) {
    return Bpmn.createExecutableProcess(processId)
        .startEvent()
        .serviceTask("task", t -> t.zeebeJobType(jobType))
        .endEvent()
        .done();
  }

  @Before
  public void init() {
    jobType = helper.getJobType();
    processId = helper.getBpmnProcessId();

    processDefinitionKey =
        ENGINE
            .deployment()
            .withXmlResource(createProcess(processId, jobType))
            .deploy()
            .getValue()
            .getProcessesMetadata()
            .get(0)
            .getProcessDefinitionKey();
  }

  @Test
  public void shouldRaiseIncidentWhenActivatingJobThatIsTooBigForMessageSize() {
    // given
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .await();

    for (int i = 0; i < VARIABLE_COUNT; i++) {
      ENGINE
          .variables()
          .ofScope(processInstanceKey)
          .withDocument(Map.of(String.valueOf(i), LARGE_TEXT))
          .update();
    }

    // when
    final var activationResult =
        ENGINE.jobs().withMaxJobsToActivate(1).withType(jobType).byWorker("dummy").activate();

    // then
    Assertions.assertThat(activationResult.getValue().getJobs()).isEmpty();
    Assertions.assertThat(activationResult.getValue().isTruncated()).isTrue();

    final var incidentCommand =
        RecordingExporter.incidentRecords()
            .withIntent(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    assertThat(incidentCommand.getValue())
        .hasErrorType(ErrorType.MESSAGE_SIZE_EXCEEDED)
        .hasBpmnProcessId(processId)
        .hasProcessDefinitionKey(processDefinitionKey)
        .hasProcessInstanceKey(processInstanceKey)
        .hasElementId("task");
  }

  @Test
  public void shouldActivateJobIfFetchVariablesFitIntoMessage() {
    // given (a process with variables that together don't fit into the message size)
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .await();

    for (int i = 0; i < VARIABLE_COUNT; i++) {
      ENGINE
          .variables()
          .ofScope(processInstanceKey)
          .withDocument(Map.of(String.valueOf(i), LARGE_TEXT))
          .update();
    }

    // when (we activate the job, but request only a subset of the variables)
    final var activationResult =
        ENGINE
            .jobs()
            .withMaxJobsToActivate(1)
            .withType(jobType)
            .withFetchVariables("0")
            .byWorker("dummy")
            .activate();

    // then (no incident will be raised and the job will be activated)
    Assertions.assertThat(activationResult.getValue().getJobs()).hasSize(1);
    Assertions.assertThat(activationResult.getValue().isTruncated()).isFalse();
  }

  @Test
  public void shouldMakeJobActivatableAfterIncidentIsResolved() {
    // given
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .await();

    for (int i = 0; i < VARIABLE_COUNT; i++) {
      ENGINE
          .variables()
          .ofScope(processInstanceKey)
          .withDocument(Map.of(String.valueOf(i), LARGE_TEXT))
          .update();
    }
    ENGINE.jobs().withMaxJobsToActivate(1).withType(jobType).byWorker("dummy").activate();

    // when
    final var incidentCommand =
        RecordingExporter.incidentRecords()
            .withIntent(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    ENGINE
        .variables()
        .ofScope(processInstanceKey)
        .withDocument(
            Map.of("0", "lorem ipsum", "1", "lorem ipsum", "2", "lorem ipsum", "3", "lorem ipsum"))
        .update();

    ENGINE.incident().ofInstance(processInstanceKey).withKey(incidentCommand.getKey()).resolve();

    final var activationResult =
        ENGINE.jobs().withMaxJobsToActivate(1).withType(jobType).byWorker("dummy").activate();

    // then
    Assertions.assertThat(activationResult.getValue().getJobs()).hasSize(1);
    Assertions.assertThat(activationResult.getValue().isTruncated()).isFalse();
  }
}
