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
import io.camunda.zeebe.protocol.impl.record.value.processinstance.BufferedCommandRecord;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.BufferedCommandIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.BufferedCommandRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

/**
 * Covers the commands that drive suspension and resumption while the process instance is {@code
 * SUSPENDING}. Suspension currently completes within one batch, so no command leaves an instance in
 * {@code SUSPENDING}. The tests write the {@code SUSPENDING} event to the log while the engine is
 * stopped, so it is applied on restart, to simulate a suspension that did not finish.
 */
public final class ProcessInstanceSuspendingGateTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule public final TestWatcher watcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldRestartSuspensionWhenSuspendingInstanceIsSuspendedAgain() {
    // given
    final long processInstanceKey = createInstanceWithJob();
    leaveInstanceSuspending(processInstanceKey);

    // when
    final Record<ProcessInstanceRecordValue> suspended =
        ENGINE.processInstance().withInstanceKey(processInstanceKey).suspend();

    // then - the suspension steps run again and finish with SUSPENDED
    assertThat(suspended.getIntent()).isEqualTo(ProcessInstanceIntent.SUSPENDED);
    assertThat(
            RecordingExporter.jobRecords(JobIntent.SUSPENDED)
                .withProcessInstanceKey(processInstanceKey)
                .exists())
        .isTrue();

    // and - no second SUSPENDING event is written, as the instance was already suspending
    assertThat(
            RecordingExporter.processInstanceRecords()
                .limit(r -> r.getPosition() >= suspended.getPosition())
                .withIntent(ProcessInstanceIntent.SUSPENDING)
                .withRecordKey(processInstanceKey)
                .count())
        .isOne();
  }

  @Test
  public void shouldRejectResumeWhileSuspending() {
    // given
    final long processInstanceKey = createInstanceWithJob();
    leaveInstanceSuspending(processInstanceKey);

    // when
    final var rejection =
        ENGINE
            .processInstance()
            .withInstanceKey(processInstanceKey)
            .expectResumeRejection()
            .resume();

    // then
    Assertions.assertThat(rejection)
        .hasIntent(ProcessInstanceIntent.RESUME)
        .hasRejectionType(RejectionType.INVALID_STATE);
    assertThat(rejection.getRejectionReason()).contains("it is still suspending");
  }

  @Test
  public void shouldRejectDrainWhileSuspending() {
    // given
    final long processInstanceKey = createInstanceWithJob();
    leaveInstanceSuspending(processInstanceKey);

    // when
    writeDrain(processInstanceKey);

    // then
    assertDrainRejected(processInstanceKey);
  }

  @Test
  public void shouldRejectDrainWhileSuspended() {
    // given
    final long processInstanceKey = createInstanceWithJob();
    ENGINE.processInstance().withInstanceKey(processInstanceKey).suspend();

    // when
    writeDrain(processInstanceKey);

    // then
    assertDrainRejected(processInstanceKey);
  }

  private static long createInstanceWithJob() {
    final String processId = Strings.newRandomValidBpmnId();
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType(processId))
                .endEvent()
                .done())
        .deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();
    RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .await();
    return processInstanceKey;
  }

  /**
   * Events written while the engine is stopped are applied when it restarts, unlike events written
   * while it is processing.
   */
  private static void leaveInstanceSuspending(final long processInstanceKey) {
    final var processInstance =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.PROCESS)
            .getFirst();
    ENGINE.stop();
    ENGINE.writeRecords(
        RecordToWrite.event()
            .key(processInstanceKey)
            .processInstance(ProcessInstanceIntent.SUSPENDING, processInstance.getValue()));
    ENGINE.start();
  }

  private static void writeDrain(final long processInstanceKey) {
    ENGINE.writeRecords(
        RecordToWrite.command()
            .bufferedCommand(
                BufferedCommandIntent.DRAIN,
                new BufferedCommandRecord().setProcessInstanceKey(processInstanceKey))
            .key(processInstanceKey));
  }

  private static void assertDrainRejected(final long processInstanceKey) {
    final var rejection =
        RecordingExporter.records()
            .onlyCommandRejections()
            .withValueType(ValueType.BUFFERED_COMMAND)
            .withIntent(BufferedCommandIntent.DRAIN)
            .filter(
                r ->
                    ((BufferedCommandRecordValue) r.getValue()).getProcessInstanceKey()
                        == processInstanceKey)
            .getFirst();
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_STATE);
    assertThat(rejection.getRejectionReason()).contains("it is not resuming");
  }
}
