/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.camunda.zeebe.engine.state.immutable.SuspensionState.State;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
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
 * SUSPENDING}. Suspension currently completes within one batch, so the tests set the {@code
 * SUSPENDING} marker directly to simulate a suspension that did not finish.
 */
public final class ProcessInstanceSuspendingGateTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule public final TestWatcher watcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldRestartSuspensionWhenSuspendingInstanceIsSuspendedAgain() {
    // given
    final long processInstanceKey = createInstanceWithJob();
    setSuspensionState(processInstanceKey, State.SUSPENDING);

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

    // and - no second SUSPENDING event is written, as the marker was already present
    assertThat(
            RecordingExporter.processInstanceRecords()
                .limit(r -> r.getPosition() >= suspended.getPosition())
                .withIntent(ProcessInstanceIntent.SUSPENDING)
                .withRecordKey(processInstanceKey)
                .exists())
        .isFalse();
  }

  @Test
  public void shouldRejectResumeWhileSuspending() {
    // given
    final long processInstanceKey = createInstanceWithJob();
    setSuspensionState(processInstanceKey, State.SUSPENDING);

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

  private static void setSuspensionState(final long processInstanceKey, final State state) {
    await().until(ENGINE::hasReachedEnd);
    ((MutableProcessingState) ENGINE.getProcessingState())
        .getSuspensionState()
        .setSuspensionState(processInstanceKey, state);
  }
}
