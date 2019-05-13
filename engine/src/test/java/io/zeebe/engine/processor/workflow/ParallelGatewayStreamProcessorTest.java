/*
 * Zeebe Workflow Engine
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
package io.zeebe.engine.processor.workflow;

import static io.zeebe.test.util.TestUtil.waitUntil;

import io.zeebe.engine.processor.TypedRecord;
import io.zeebe.engine.util.StreamProcessorControl;
import io.zeebe.engine.util.StreamProcessorRule;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.model.bpmn.instance.ExclusiveGateway;
import io.zeebe.model.bpmn.instance.ParallelGateway;
import io.zeebe.model.bpmn.instance.Process;
import io.zeebe.model.bpmn.instance.SequenceFlow;
import io.zeebe.protocol.BpmnElementType;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.util.buffer.BufferUtil;
import java.util.Optional;
import org.agrona.DirectBuffer;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class ParallelGatewayStreamProcessorTest {

  public static final String PROCESS_ID = "process";
  public static final DirectBuffer PROCESS_ID_BUFFER = BufferUtil.wrapString("process");

  public StreamProcessorRule envRule = new StreamProcessorRule();
  public WorkflowInstanceStreamProcessorRule streamProcessorRule =
      new WorkflowInstanceStreamProcessorRule(envRule);

  @Rule public RuleChain chain = RuleChain.outerRule(envRule).around(streamProcessorRule);

  private StreamProcessorControl streamProcessor;

  @Before
  public void setUp() {
    streamProcessor = streamProcessorRule.getStreamProcessor();
  }

  @Test
  public void shouldNotCompleteScopeWhenATokenWaitsAtAGateway() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .parallelGateway("fork")
            .sequenceFlowId("flowToJoin")
            .parallelGateway("join") // process should deadlock here
            .endEvent()
            .moveToNode("fork")
            .serviceTask("waitState", b -> b.zeebeTaskType("type"))
            .sequenceFlowId("flowToEnd")
            .endEvent()
            .done();

    final ExclusiveGateway deadBranch = process.newInstance(ExclusiveGateway.class);
    final ParallelGateway join = process.getModelElementById("join");
    final SequenceFlow connectingFlow = process.newInstance(SequenceFlow.class);

    final Process processElement = process.getModelElementsByType(Process.class).iterator().next();
    processElement.addChildElement(deadBranch);
    processElement.addChildElement(connectingFlow);

    connectingFlow.setSource(deadBranch);
    deadBranch.getOutgoing().add(connectingFlow);
    connectingFlow.setTarget(join);
    join.getIncoming().add(connectingFlow);

    streamProcessorRule.deploy(process);

    streamProcessorRule.createWorkflowInstance(r -> r.setBpmnProcessId(PROCESS_ID));
    streamProcessorRule.awaitElementInState(
        "flowToJoin", WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN);

    // when
    // waiting until the end event has been reached
    streamProcessor.blockAfterWorkflowInstanceRecord(
        r ->
            r.getMetadata().getIntent() == WorkflowInstanceIntent.ELEMENT_COMPLETED
                && r.getValue().getBpmnElementType() == BpmnElementType.END_EVENT);
    streamProcessorRule.completeFirstJob();

    waitUntil(() -> streamProcessor.isBlocked());

    // then
    // there should be no scope completing event
    final Optional<TypedRecord<WorkflowInstanceRecord>> processCompleting =
        envRule
            .events()
            .onlyWorkflowInstanceRecords()
            .withIntent(WorkflowInstanceIntent.ELEMENT_COMPLETING)
            .filter(r -> PROCESS_ID_BUFFER.equals(r.getValue().getElementId()))
            .findFirst();

    Assertions.assertThat(processCompleting).isNotPresent();
  }
}
