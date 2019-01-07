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
package io.zeebe.broker.workflow.gateway;

import static io.zeebe.test.util.MsgPackUtil.asMsgPack;
import static io.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.broker.logstreams.processor.TypedRecord;
import io.zeebe.broker.subscription.message.data.WorkflowInstanceSubscriptionRecord;
import io.zeebe.broker.util.StreamProcessorControl;
import io.zeebe.broker.util.StreamProcessorRule;
import io.zeebe.broker.workflow.processor.WorkflowInstanceStreamProcessorRule;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.protocol.intent.WorkflowInstanceSubscriptionIntent;
import io.zeebe.test.util.TestUtil;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class EventbasedGatewayStreamProcessorTest {
  public static final String PROCESS_ID = "process";

  public StreamProcessorRule envRule = new StreamProcessorRule();
  public WorkflowInstanceStreamProcessorRule streamProcessorRule =
      new WorkflowInstanceStreamProcessorRule(envRule);

  @Rule public RuleChain chain = RuleChain.outerRule(envRule).around(streamProcessorRule);

  private StreamProcessorControl streamProcessor;

  public static final BpmnModelInstance MESSAGE_WORKFLOW =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent("start")
          .eventBasedGateway()
          .id("gateway")
          .intermediateCatchEvent(
              "message-1", c -> c.message(m -> m.name("msg-1").zeebeCorrelationKey("$.key")))
          .sequenceFlowId("to-end1")
          .endEvent("end1")
          .moveToLastGateway()
          .intermediateCatchEvent(
              "message-2", c -> c.message(m -> m.name("msg-2").zeebeCorrelationKey("$.key")))
          .sequenceFlowId("to-end2")
          .endEvent("end2")
          .done();

  @Before
  public void setUp() {
    streamProcessor = streamProcessorRule.getStreamProcessor();
  }

  @Test
  public void shouldOnlyExecuteOneBranchWithSimultaneousMessages() {
    // given
    streamProcessorRule.deploy(MESSAGE_WORKFLOW);
    streamProcessor.blockAfterWorkflowInstanceRecord(
        m -> m.getMetadata().getIntent() == WorkflowInstanceIntent.GATEWAY_ACTIVATED);
    final TypedRecord<WorkflowInstanceRecord> instance =
        streamProcessorRule.createWorkflowInstance(PROCESS_ID, asMsgPack("key", "123"));

    TestUtil.waitUntil(() -> streamProcessor.isBlocked());

    final long gatewayElementKey =
        envRule.events().withIntent(WorkflowInstanceIntent.GATEWAY_ACTIVATED).getFirst().getKey();

    final WorkflowInstanceSubscriptionRecord subscriptionRecord =
        new WorkflowInstanceSubscriptionRecord();
    subscriptionRecord
        .setSubscriptionPartitionId(1)
        .setPayload(asMsgPack("key", "123"))
        .setElementInstanceKey(gatewayElementKey)
        .setWorkflowInstanceKey(instance.getValue().getWorkflowInstanceKey())
        .setMessageName(new UnsafeBuffer("msg-1".getBytes()));
    envRule.writeCommand(WorkflowInstanceSubscriptionIntent.CORRELATE, subscriptionRecord);

    final WorkflowInstanceSubscriptionRecord secondSubscriptionRecord =
        new WorkflowInstanceSubscriptionRecord();
    secondSubscriptionRecord
        .setPayload(asMsgPack("key", "123"))
        .setSubscriptionPartitionId(1)
        .setElementInstanceKey(gatewayElementKey)
        .setWorkflowInstanceKey(instance.getValue().getWorkflowInstanceKey())
        .setMessageName(new UnsafeBuffer("msg-2".getBytes()));
    envRule.writeCommand(WorkflowInstanceSubscriptionIntent.CORRELATE, secondSubscriptionRecord);

    streamProcessor.unblock();

    // then
    TestUtil.waitUntil(
        () ->
            envRule
                .events()
                .onlyWorkflowInstanceRecords()
                .withIntent(WorkflowInstanceIntent.ELEMENT_COMPLETED)
                .filter(r -> r.getValue().getElementId().equals(wrapString(PROCESS_ID)))
                .exists());

    assertThat(
            envRule
                    .events()
                    .onlyWorkflowInstanceRecords()
                    .withIntent(WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN)
                    .filter(r -> r.getValue().getElementId().equals(wrapString("to-end1")))
                    .exists()
                ^ envRule
                    .events()
                    .onlyWorkflowInstanceRecords()
                    .withIntent(WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN)
                    .filter(r -> r.getValue().getElementId().equals(wrapString("to-end2")))
                    .exists())
        .isTrue();
  }
}
