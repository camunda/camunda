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
import io.zeebe.broker.util.StreamProcessorControl;
import io.zeebe.broker.util.StreamProcessorRule;
import io.zeebe.broker.workflow.data.TimerRecord;
import io.zeebe.broker.workflow.processor.WorkflowInstanceStreamProcessorRule;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.protocol.clientapi.RejectionType;
import io.zeebe.protocol.intent.TimerIntent;
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
    final TypedRecord<TimerRecord> timerRecord = timerRecordForActivity("timer");

    envRule.writeCommand(timerRecord.getKey(), TimerIntent.CANCEL, timerRecord.getValue());

    envRule.writeCommand(timerRecord.getKey(), TimerIntent.TRIGGER, timerRecord.getValue());

    streamProcessor.unblock();

    // then
    final TypedRecord<TimerRecord> rejection = findTimerCommandRejection();

    assertThat(rejection.getMetadata().getIntent()).isEqualTo(TimerIntent.TRIGGER);
    assertThat(rejection.getMetadata().getRejectionType()).isEqualTo(RejectionType.NOT_FOUND);
  }

  @Test
  public void shouldRejectDuplicatedTriggerCommand() {
    // when
    final TypedRecord<TimerRecord> timerRecord = timerRecordForActivity("timer");

    envRule.writeCommand(timerRecord.getKey(), TimerIntent.TRIGGER, timerRecord.getValue());

    envRule.writeCommand(timerRecord.getKey(), TimerIntent.TRIGGER, timerRecord.getValue());

    streamProcessor.unblock();

    // then
    final TypedRecord<TimerRecord> rejection = findTimerCommandRejection();

    assertThat(rejection.getMetadata().getIntent()).isEqualTo(TimerIntent.TRIGGER);
    assertThat(rejection.getMetadata().getRejectionType()).isEqualTo(RejectionType.NOT_FOUND);
  }

  @Test
  public void shouldRejectCancelCommand() {
    // when
    final TypedRecord<TimerRecord> timerRecord = timerRecordForActivity("timer");

    envRule.writeCommand(timerRecord.getKey(), TimerIntent.TRIGGER, timerRecord.getValue());

    envRule.writeCommand(timerRecord.getKey(), TimerIntent.CANCEL, timerRecord.getValue());

    streamProcessor.unblock();

    // then
    final TypedRecord<TimerRecord> rejection = findTimerCommandRejection();

    assertThat(rejection.getMetadata().getIntent()).isEqualTo(TimerIntent.CANCEL);
    assertThat(rejection.getMetadata().getRejectionType()).isEqualTo(RejectionType.NOT_FOUND);
  }

  private TypedRecord<TimerRecord> timerRecordForActivity(final String activityId) {
    return streamProcessorRule.awaitTimerInState(activityId, TimerIntent.CREATED);
  }

  private TypedRecord<TimerRecord> findTimerCommandRejection() {
    waitUntil(() -> envRule.events().onlyTimerRecords().onlyRejections().findFirst().isPresent());

    return envRule.events().onlyTimerRecords().onlyRejections().findFirst().get();
  }
}
