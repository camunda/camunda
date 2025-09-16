/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.bpmn.activity;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public final class SendTaskTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();
  @Rule public final TestWatcher watcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldCreateIncidentForNotSupportedSendTaskImplementation() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            "process.bpmn",
            Bpmn.createExecutableProcess("process")
                .startEvent()
                .sendTask("task")
                // sendTask with publish message implementation isn't supported yet
                .message(m -> m.name("msg").zeebeCorrelationKeyExpression("123"))
                .endEvent()
                .done())
        .deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId("process").create();

    // then
    final var incident =
        RecordingExporter.incidentRecords()
            .withProcessInstanceKey(processInstanceKey)
            .withElementId("task")
            .getFirst();

    Assertions.assertThat(incident.getValue())
        .hasErrorType(ErrorType.UNKNOWN)
        .hasErrorMessage(
            "Currently, only job worker-based implementation is supported for 'sendTask'.");
  }

  @Test
  public void shouldCancelProcessWithIncidentOnUnsupportedSendTask() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            "process.bpmn",
            Bpmn.createExecutableProcess("process")
                .startEvent()
                .sendTask("task")
                .message(m -> m.name("msg").zeebeCorrelationKeyExpression("123"))
                .endEvent()
                .done())
        .deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId("process").create();

    // wait for incident on unsupported sendTask
    RecordingExporter.incidentRecords(IncidentIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementId("task")
        .withErrorType(ErrorType.UNKNOWN)
        .await();

    // when
    ENGINE.processInstance().withInstanceKey(processInstanceKey).cancel();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceTerminated()
                .withIntent(ProcessInstanceIntent.ELEMENT_TERMINATED))
        .extracting(r -> r.getValue().getElementId())
        .as("SendTask and process instance should be terminated")
        .containsExactly("task", "process");

    assertThat(
            RecordingExporter.incidentRecords(IncidentIntent.RESOLVED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementId("task")
                .withErrorType(ErrorType.UNKNOWN)
                .exists())
        .as("Incident should be resolved during sendTask termination")
        .isTrue();
  }
}
