/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.RecordToWrite;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceModificationActivateInstruction;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceModificationRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.BufferedCommandIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceModificationIntent;
import io.camunda.zeebe.protocol.record.value.BufferedCommandRecordValue;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public final class ProcessInstanceModificationSuspensionGateTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule public final RecordingExporterTestWatcher watcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldRejectExternalModifyWhileSuspended() {
    // given
    final String processId = Strings.newRandomValidBpmnId();
    final long processInstanceKey = deployAndStartProcessWithUnreachedElement(processId);
    ENGINE.processInstance().withInstanceKey(processInstanceKey).suspend();

    // when
    final var rejection =
        ENGINE
            .processInstance()
            .withInstanceKey(processInstanceKey)
            .modification()
            .activateElement("B")
            .expectRejection()
            .modify("test-user");

    // then
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_STATE);
  }

  @Test
  public void shouldBufferInternalModifyWhileSuspended() {
    // given
    final String processId = Strings.newRandomValidBpmnId();
    final long processInstanceKey = deployAndStartProcessWithUnreachedElement(processId);
    ENGINE.processInstance().withInstanceKey(processInstanceKey).suspend();

    // when
    final var record =
        new ProcessInstanceModificationRecord()
            .setProcessInstanceKey(processInstanceKey)
            .addActivateInstruction(
                new ProcessInstanceModificationActivateInstruction().setElementId("B"));

    ENGINE.writeRecords(RecordToWrite.command().modification(record).key(processInstanceKey));

    // then
    assertThat(bufferedCommandIntent(processInstanceKey))
        .isEqualTo(ProcessInstanceModificationIntent.MODIFY);
  }

  private static ProcessInstanceModificationIntent bufferedCommandIntent(
      final long processInstanceKey) {
    return (ProcessInstanceModificationIntent)
        ((BufferedCommandRecordValue) bufferedCommand(processInstanceKey).getValue()).getIntent();
  }

  private static Record<RecordValue> bufferedCommand(final long processInstanceKey) {
    return RecordingExporter.records()
        .withValueType(ValueType.BUFFERED_COMMAND)
        .withIntent(BufferedCommandIntent.BUFFERED)
        .filter(
            r ->
                ((BufferedCommandRecordValue) r.getValue()).getProcessInstanceKey()
                    == processInstanceKey)
        .getFirst();
  }

  private long deployAndStartProcessWithUnreachedElement(final String processId) {
    final var process =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .exclusiveGateway("gateway")
            .sequenceFlowId("toB")
            .conditionExpression("false")
            .serviceTask("B", t -> t.zeebeJobType("B"))
            .endEvent()
            .moveToLastExclusiveGateway()
            .defaultFlow()
            .serviceTask("A", t -> t.zeebeJobType("A"))
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementId("A")
        .await();

    return processInstanceKey;
  }
}
