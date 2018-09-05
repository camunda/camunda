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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.model.bpmn.builder.AbstractFlowNodeBuilder;
import io.zeebe.model.bpmn.instance.ServiceTask;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.test.broker.protocol.clientapi.ClientApiRule;
import io.zeebe.test.broker.protocol.clientapi.SubscribedRecord;
import io.zeebe.test.broker.protocol.clientapi.TestPartitionClient;
import io.zeebe.test.util.MsgPackUtil;
import io.zeebe.util.buffer.BufferUtil;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class ParallelGatewayTest {

  private static final String PROCESS_ID = "process";

  private static final BpmnModelInstance FORK_PROCESS;

  static {
    final AbstractFlowNodeBuilder<?, ?> builder =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent("start")
            .parallelGateway("fork")
            .serviceTask("task1", b -> b.zeebeTaskType("type1"))
            .endEvent("end1")
            .moveToNode("fork");

    FORK_PROCESS =
        builder.serviceTask("task2", b -> b.zeebeTaskType("type2")).endEvent("end2").done();
  }

  public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();
  public ClientApiRule apiRule = new ClientApiRule(brokerRule::getClientAddress);

  @Rule public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(apiRule);

  private TestPartitionClient testClient;

  @Before
  public void init() {
    testClient = apiRule.partition();
  }

  @Test
  public void shouldActivateTasksOnParallelBranches() {
    // given
    testClient.deploy(FORK_PROCESS);

    // when
    testClient.createWorkflowInstance(PROCESS_ID);

    // then
    final List<SubscribedRecord> taskEvents =
        testClient
            .receiveEvents()
            .ofTypeWorkflowInstance()
            .withIntent(WorkflowInstanceIntent.ELEMENT_ACTIVATED)
            .filter(e -> isServiceTaskInProcess((String) e.value().get("activityId"), FORK_PROCESS))
            .limit(2)
            .collect(Collectors.toList());

    assertThat(taskEvents).hasSize(2);
    assertThat(taskEvents)
        .extracting(e -> e.value().get("activityId"))
        .containsExactlyInAnyOrder("task1", "task2");
    assertThat(taskEvents.get(0).key()).isNotEqualTo(taskEvents.get(1).key());
  }

  @Test
  public void shouldCompleteScopeWhenAllPathsCompleted() {
    // given
    testClient.deploy(FORK_PROCESS);
    testClient.createWorkflowInstance(PROCESS_ID);
    testClient.completeJobOfType("type1");

    // when
    testClient.completeJobOfType("type2");

    // then
    final List<SubscribedRecord> completedEvents =
        testClient
            .receiveEvents()
            .ofTypeWorkflowInstance()
            .withIntent(WorkflowInstanceIntent.ELEMENT_COMPLETED)
            .limit(3)
            .collect(Collectors.toList());

    assertThat(completedEvents)
        .extracting(e -> e.value().get("activityId"))
        .containsExactly("task1", "task2", PROCESS_ID);
  }

  @Test
  public void shouldCompleteScopeWithMultipleTokensOnSamePath() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .parallelGateway("fork")
            .exclusiveGateway("join")
            .endEvent("end")
            .moveToNode("fork")
            .connectTo("join")
            .done();

    testClient.deploy(process);

    // when
    testClient.createWorkflowInstance(PROCESS_ID);

    // then
    final SubscribedRecord completedEvent =
        testClient.receiveElementInState(PROCESS_ID, WorkflowInstanceIntent.ELEMENT_COMPLETED);
    final List<SubscribedRecord> workflowInstanceEvents =
        testClient
            .receiveEvents()
            .ofTypeWorkflowInstance()
            .limit(r -> r.position() == completedEvent.position())
            .collect(Collectors.toList());

    assertThat(workflowInstanceEvents)
        .extracting(e -> e.value().get("activityId"), e -> e.intent())
        .containsSubsequence(
            tuple("end", WorkflowInstanceIntent.END_EVENT_OCCURRED),
            tuple("end", WorkflowInstanceIntent.END_EVENT_OCCURRED),
            tuple(PROCESS_ID, WorkflowInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldPropagatePayloadOnSplit() {
    // given
    testClient.deploy(FORK_PROCESS);
    final byte[] payload = BufferUtil.bufferAsArray(MsgPackUtil.asMsgPack("key", "val"));

    // when
    testClient.createWorkflowInstance(PROCESS_ID, payload);

    // then
    final List<SubscribedRecord> taskEvents =
        testClient
            .receiveEvents()
            .ofTypeWorkflowInstance()
            .withIntent(WorkflowInstanceIntent.ELEMENT_ACTIVATED)
            .filter(e -> isServiceTaskInProcess((String) e.value().get("activityId"), FORK_PROCESS))
            .limit(2)
            .collect(Collectors.toList());

    assertThat(taskEvents)
        .extracting(e -> e.value().get("payload"))
        .allSatisfy(p -> p.equals(payload));
  }

  @Test
  public void shouldPassThroughParallelGateway() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent("start")
            .sequenceFlowId("flow1")
            .parallelGateway("fork")
            .sequenceFlowId("flow2")
            .endEvent("end")
            .done();

    testClient.deploy(process);

    // when
    testClient.createWorkflowInstance(PROCESS_ID);

    // then
    final SubscribedRecord completedEvent =
        testClient.receiveElementInState(PROCESS_ID, WorkflowInstanceIntent.ELEMENT_COMPLETED);
    final List<SubscribedRecord> workflowInstanceEvents =
        testClient
            .receiveEvents()
            .ofTypeWorkflowInstance()
            .limit(r -> r.position() == completedEvent.position())
            .collect(Collectors.toList());

    assertThat(workflowInstanceEvents)
        .extracting(e -> e.value().get("activityId"), e -> e.intent())
        .containsSequence(
            tuple("fork", WorkflowInstanceIntent.GATEWAY_ACTIVATED),
            tuple("flow2", WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN),
            tuple("end", WorkflowInstanceIntent.END_EVENT_OCCURRED),
            tuple(PROCESS_ID, WorkflowInstanceIntent.ELEMENT_COMPLETING));
  }

  @Test
  public void shouldCompleteScopeOnParallelGateway() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent("start")
            .sequenceFlowId("flow1")
            .parallelGateway("fork")
            .done();

    testClient.deploy(process);

    // when
    testClient.createWorkflowInstance(PROCESS_ID);

    // then
    final SubscribedRecord completedEvent =
        testClient.receiveElementInState(PROCESS_ID, WorkflowInstanceIntent.ELEMENT_COMPLETED);
    final List<SubscribedRecord> workflowInstanceEvents =
        testClient
            .receiveEvents()
            .ofTypeWorkflowInstance()
            .limit(r -> r.position() == completedEvent.position())
            .collect(Collectors.toList());

    assertThat(workflowInstanceEvents)
        .extracting(e -> e.value().get("activityId"), e -> e.intent())
        .containsSequence(
            tuple("fork", WorkflowInstanceIntent.GATEWAY_ACTIVATED),
            tuple(PROCESS_ID, WorkflowInstanceIntent.ELEMENT_COMPLETING));
  }

  private static boolean isServiceTaskInProcess(
      final String activityId, final BpmnModelInstance process) {
    return process
        .getModelElementsByType(ServiceTask.class)
        .stream()
        .anyMatch(t -> t.getId().equals(activityId));
  }
}
