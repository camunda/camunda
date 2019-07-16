/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.timer;

import static io.zeebe.test.util.TestUtil.waitUntil;

import io.zeebe.engine.processor.workflow.WorkflowInstanceStreamProcessorRule;
import io.zeebe.engine.util.StreamProcessorRule;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.protocol.impl.record.value.timer.TimerRecord;
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.RejectionType;
import io.zeebe.protocol.record.intent.TimerIntent;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class TimerStreamProcessorTest {

  private static final String PROCESS_ID = "process";

  private final StreamProcessorRule envRule = new StreamProcessorRule();
  private final WorkflowInstanceStreamProcessorRule streamProcessorRule =
      new WorkflowInstanceStreamProcessorRule(envRule);

  @Rule public RuleChain chain = RuleChain.outerRule(envRule).around(streamProcessorRule);

  @Before
  public void setUp() {
    // given
    streamProcessorRule.deploy(
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .intermediateCatchEvent("timer", c -> c.timerWithDuration("PT10S"))
            .endEvent()
            .done());

    streamProcessorRule.createWorkflowInstance(r -> r.setBpmnProcessId(PROCESS_ID));
    waitUntil(() -> envRule.events().onlyTimerRecords().withIntent(TimerIntent.CREATED).exists());
  }

  @Test
  public void shouldRejectTriggerCommand() {
    // when
    final Record<TimerRecord> timerRecord = timerRecordForActivity("timer");

    envRule.writeCommand(timerRecord.getKey(), TimerIntent.CANCEL, timerRecord.getValue());
    envRule.writeCommand(timerRecord.getKey(), TimerIntent.TRIGGER, timerRecord.getValue());

    // then
    final Record<TimerRecord> rejection = findTimerCommandRejection();

    Assertions.assertThat(rejection.getIntent()).isEqualTo(TimerIntent.TRIGGER);
    Assertions.assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.NOT_FOUND);
  }

  @Test
  public void shouldRejectDuplicatedTriggerCommand() {
    // when
    final Record<TimerRecord> timerRecord = timerRecordForActivity("timer");

    envRule.writeCommand(timerRecord.getKey(), TimerIntent.TRIGGER, timerRecord.getValue());
    envRule.writeCommand(timerRecord.getKey(), TimerIntent.TRIGGER, timerRecord.getValue());

    // then
    final Record<TimerRecord> rejection = findTimerCommandRejection();

    Assertions.assertThat(rejection.getIntent()).isEqualTo(TimerIntent.TRIGGER);
    Assertions.assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.NOT_FOUND);
  }

  @Test
  public void shouldRejectCancelCommand() {
    // when
    final Record<TimerRecord> timerRecord = timerRecordForActivity("timer");

    envRule.writeCommand(timerRecord.getKey(), TimerIntent.TRIGGER, timerRecord.getValue());
    envRule.writeCommand(timerRecord.getKey(), TimerIntent.CANCEL, timerRecord.getValue());

    // then
    final Record<TimerRecord> rejection = findTimerCommandRejection();

    Assertions.assertThat(rejection.getIntent()).isEqualTo(TimerIntent.CANCEL);
    Assertions.assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.NOT_FOUND);
  }

  private Record<TimerRecord> timerRecordForActivity(final String activityId) {
    return streamProcessorRule.awaitTimerInState(activityId, TimerIntent.CREATED);
  }

  private Record<TimerRecord> findTimerCommandRejection() {
    waitUntil(() -> envRule.events().onlyTimerRecords().onlyRejections().findFirst().isPresent());

    return envRule.events().onlyTimerRecords().onlyRejections().findFirst().get();
  }
}
