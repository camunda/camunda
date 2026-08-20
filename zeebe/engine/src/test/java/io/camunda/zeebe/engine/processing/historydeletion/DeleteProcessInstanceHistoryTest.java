/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.historydeletion;

import static io.camunda.zeebe.protocol.record.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.value.HistoryDeletionType;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class DeleteProcessInstanceHistoryTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();
  @Rule public final TestWatcher recordingExporterTestWatcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldDeleteProcessInstanceHistory() {
    // given
    final var processId = Strings.newRandomValidBpmnId();
    ENGINE
        .deployment()
        .withXmlResource(Bpmn.createExecutableProcess(processId).startEvent().endEvent().done())
        .deploy();
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    RecordingExporter.processInstanceRecords()
        .withProcessInstanceKey(processInstanceKey)
        .limitToProcessInstanceCompleted()
        .await();

    // when
    final var deletedEvent = ENGINE.historyDeletion().processInstance(processInstanceKey).delete();

    // then
    assertThat(deletedEvent.getValue())
        .hasResourceKey(processInstanceKey)
        .hasResourceType(HistoryDeletionType.PROCESS_INSTANCE);
  }

  @Test
  public void shouldRejectProcessInstanceDeletionWhenInstanceIsActive() {
    // given
    final var processId = Strings.newRandomValidBpmnId();
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId).startEvent().userTask().endEvent().done())
        .deploy();
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    // when
    final var rejection =
        ENGINE.historyDeletion().processInstance(processInstanceKey).expectRejection().delete();

    // then
    assertThat(rejection)
        .hasRejectionType(RejectionType.INVALID_STATE)
        .hasRejectionReason(
            String.format(
                "Expected to delete history for process instance with key '%d', but it is still active.",
                processInstanceKey));
  }
}
