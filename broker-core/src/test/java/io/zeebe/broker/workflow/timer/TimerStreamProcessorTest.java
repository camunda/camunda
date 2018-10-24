/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker.workflow.timer;

import static io.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.broker.logstreams.processor.TypedRecord;
import io.zeebe.broker.topic.StreamProcessorControl;
import io.zeebe.broker.util.StreamProcessorRule;
import io.zeebe.broker.workflow.data.TimerRecord;
import io.zeebe.broker.workflow.processor.WorkflowInstanceStreamProcessorRule;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.intent.TimerIntent;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.util.buffer.BufferUtil;
import java.time.Duration;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class TimerStreamProcessorTest {

  private static final String PROCESS_ID = "process";

  public StreamProcessorRule envRule = new StreamProcessorRule();
  public WorkflowInstanceStreamProcessorRule streamProcessorRule =
      new WorkflowInstanceStreamProcessorRule(envRule);

  @Rule public RuleChain chain = RuleChain.outerRule(envRule).around(streamProcessorRule);

  private StreamProcessorControl streamProcessor;

  @Before
  public void setUp() {
    streamProcessor = streamProcessorRule.getStreamProcessor();

    // given
    streamProcessorRule.deploy(
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .intermediateCatchEvent("timer", c -> c.timerWithDuration("PT10S"))
            .endEvent()
            .done());

    streamProcessor.blockAfterTimerEvent(r -> r.getMetadata().getIntent() == TimerIntent.CREATED);

    streamProcessorRule.createWorkflowInstance(PROCESS_ID);

    waitUntil(() -> streamProcessor.isBlocked());
  }

  @Test
  public void shouldRejectTriggerCommand() {
    // when
    final TimerRecord timerRecord = timerRecordForActivity("timer");

    envRule.writeCommand(TimerIntent.CANCEL, timerRecord);

    final long secondCommandPosition = envRule.writeCommand(TimerIntent.TRIGGER, timerRecord);

    streamProcessor.unblock();

    // then
    final TypedRecord<TimerRecord> rejection = findTimerCommandRejection();

    assertThat(rejection.getMetadata().getIntent()).isEqualTo(TimerIntent.TRIGGER);
    assertThat(rejection.getSourcePosition()).isEqualTo(secondCommandPosition);
    assertThat(BufferUtil.bufferAsString(rejection.getMetadata().getRejectionReason()))
        .isEqualTo("timer is already triggered or canceled");
  }

  @Test
  public void shouldRejectDuplicatedTriggerCommand() {
    // when
    final TimerRecord timerRecord = timerRecordForActivity("timer");

    envRule.writeCommand(TimerIntent.TRIGGER, timerRecord);

    final long secondCommandPosition = envRule.writeCommand(TimerIntent.TRIGGER, timerRecord);

    streamProcessor.unblock();

    // then
    final TypedRecord<TimerRecord> rejection = findTimerCommandRejection();

    assertThat(rejection.getMetadata().getIntent()).isEqualTo(TimerIntent.TRIGGER);
    assertThat(rejection.getSourcePosition()).isEqualTo(secondCommandPosition);
    assertThat(BufferUtil.bufferAsString(rejection.getMetadata().getRejectionReason()))
        .isEqualTo("timer is already triggered or canceled");
  }

  @Test
  public void shouldRejectCancelCommand() {
    // when
    final TimerRecord timerRecord = timerRecordForActivity("timer");

    envRule.writeCommand(TimerIntent.TRIGGER, timerRecord);

    final long secondCommandPosition = envRule.writeCommand(TimerIntent.CANCEL, timerRecord);

    streamProcessor.unblock();

    // then
    final TypedRecord<TimerRecord> rejection = findTimerCommandRejection();

    assertThat(rejection.getMetadata().getIntent()).isEqualTo(TimerIntent.CANCEL);
    assertThat(rejection.getSourcePosition()).isEqualTo(secondCommandPosition);
    assertThat(BufferUtil.bufferAsString(rejection.getMetadata().getRejectionReason()))
        .isEqualTo("timer is already triggered or canceled");
  }

  private TimerRecord timerRecordForActivity(final String activityId) {
    final TypedRecord<WorkflowInstanceRecord> activatedEvent =
        streamProcessorRule.awaitElementInState(
            activityId, WorkflowInstanceIntent.ELEMENT_ACTIVATED);

    return new TimerRecord()
        .setActivityInstanceKey(activatedEvent.getKey())
        .setDueDate(activatedEvent.getTimestamp() + Duration.ofSeconds(10).toMillis());
  }

  private TypedRecord<TimerRecord> findTimerCommandRejection() {
    waitUntil(() -> envRule.events().onlyTimerRecords().onlyRejections().findFirst().isPresent());

    return envRule.events().onlyTimerRecords().onlyRejections().findFirst().get();
  }
}
