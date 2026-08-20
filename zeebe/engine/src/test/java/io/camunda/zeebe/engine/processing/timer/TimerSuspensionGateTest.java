/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.timer;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.TimerIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.time.Duration;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public final class TimerSuspensionGateTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule public final RecordingExporterTestWatcher watcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldTriggerStrandedTimerAfterResume() {
    // given - an instance with a timer that came due while suspended; the due-date checker's
    // resulting TRIGGER is rejected instead of firing, because the instance is suspended
    final long processInstanceKey = deployAndStartProcessWithTimer(Strings.newRandomValidBpmnId());
    RecordingExporter.timerRecords(TimerIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .await();
    ENGINE.processInstance().withInstanceKey(processInstanceKey).suspend();
    ENGINE.increaseTime(Duration.ofSeconds(1));
    final var rejection =
        RecordingExporter.timerRecords(TimerIntent.TRIGGER)
            .onlyCommandRejections()
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_STATE);

    // when - the instance resumes; no clock advance follows, so a TRIGGERED event can only come
    // from the resume-triggered rescan, not from the checker's regular polling
    ENGINE.processInstance().withInstanceKey(processInstanceKey).resume();

    // then - the stranded timer fires
    assertThat(
            RecordingExporter.timerRecords(TimerIntent.TRIGGERED)
                .withProcessInstanceKey(processInstanceKey)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldFireTimerThatBecomesDueAfterResume() {
    // given - an instance suspended and resumed before its timer is due
    final long processInstanceKey = deployAndStartProcessWithTimer(Strings.newRandomValidBpmnId());
    RecordingExporter.timerRecords(TimerIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .await();
    ENGINE.processInstance().withInstanceKey(processInstanceKey).suspend();
    ENGINE.processInstance().withInstanceKey(processInstanceKey).resume();

    // when - the timer becomes due after resume
    ENGINE.increaseTime(Duration.ofSeconds(1));

    // then - it fires through the normal path and the process completes
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementType(BpmnElementType.PROCESS)
        .await();
  }

  private long deployAndStartProcessWithTimer(final String processId) {
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .intermediateCatchEvent("timer", c -> c.timerWithDuration("PT1S"))
                .endEvent()
                .done())
        .deploy();
    return ENGINE.processInstance().ofBpmnProcessId(processId).create();
  }
}
