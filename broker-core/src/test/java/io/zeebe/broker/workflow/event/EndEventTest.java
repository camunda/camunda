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
package io.zeebe.broker.workflow.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.exporter.record.Record;
import io.zeebe.exporter.record.value.WorkflowInstanceRecordValue;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.model.bpmn.instance.zeebe.ZeebeMappingType;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.test.broker.protocol.clientapi.ClientApiRule;
import io.zeebe.test.broker.protocol.clientapi.PartitionTestClient;
import io.zeebe.test.util.JsonUtil;
import io.zeebe.test.util.record.RecordingExporter;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class EndEventTest {

  private static final String PROCESS_ID = "process";

  public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();
  public ClientApiRule apiRule = new ClientApiRule(brokerRule::getClientAddress);

  @Rule public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(apiRule);

  private PartitionTestClient testClient;

  @Before
  public void init() {
    testClient = apiRule.partitionClient();
  }

  @Test
  public void shouldMergePayloadsOnCompletion() {
    // given
    final BpmnModelInstance model =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .parallelGateway()
            .serviceTask("task1", b -> b.zeebeTaskType("type1"))
            .endEvent()
            .moveToLastGateway()
            .serviceTask("task2", b -> b.zeebeTaskType("type2"))
            .endEvent()
            .done();

    testClient.deploy(model);
    testClient.createWorkflowInstance(PROCESS_ID);

    // when
    testClient.completeJobOfType("type1", "{'key1': 'val1'}");
    testClient.completeJobOfType("type2", "{'key2': 'val2'}");

    // then
    final Record<WorkflowInstanceRecordValue> completedEvent =
        RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_COMPLETED)
            .withElementId(PROCESS_ID)
            .getFirst();

    final String actualPayload = completedEvent.getValue().getPayload();
    final String expectedPayload = "{'key1': 'val1', 'key2': 'val2'}";

    JsonUtil.assertEquality(actualPayload, expectedPayload);
  }

  @Test
  public void shouldMergePayloadIfMappingHasNoResult() {
    // given
    final BpmnModelInstance model =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .endEvent(b -> b.payloadMapping("$.notAKey", "$.key"))
            .done();

    testClient.deploy(model);

    // when
    testClient.createWorkflowInstance(PROCESS_ID);

    // then
    final Record<WorkflowInstanceRecordValue> completedEvent =
        RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_COMPLETED)
            .withElementId(PROCESS_ID)
            .getFirst();

    final String actualPayload = completedEvent.getValue().getPayload();
    final String expectedPayload = "{'key': null}";

    JsonUtil.assertEquality(actualPayload, expectedPayload);
  }

  @Test
  public void shouldMergePayloadsOnCompletionWithMappingInstructions() {
    // given
    final BpmnModelInstance model =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .parallelGateway()
            .serviceTask("task1", b -> b.zeebeTaskType("type1"))
            .endEvent(b -> b.payloadMapping("$.key", "$.key1"))
            .moveToLastGateway()
            .serviceTask("task2", b -> b.zeebeTaskType("type2"))
            .endEvent(b -> b.payloadMapping("$.key", "$.key2"))
            .done();

    testClient.deploy(model);
    testClient.createWorkflowInstance(PROCESS_ID);

    // when
    testClient.completeJobOfType("type1", "{'key': 'val1'}");
    testClient.completeJobOfType("type2", "{'key': 'val2'}");

    // then
    final Record<WorkflowInstanceRecordValue> completedEvent =
        RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_COMPLETED)
            .withElementId(PROCESS_ID)
            .getFirst();

    final String actualPayload = completedEvent.getValue().getPayload();
    final JsonNode actualPayloadNode = JsonUtil.asJsonNode(actualPayload);

    assertThat(actualPayloadNode.has("key1")).isTrue();
    assertThat(actualPayloadNode.get("key1").textValue()).isEqualTo("val1");

    assertThat(actualPayloadNode.has("key2")).isTrue();
    assertThat(actualPayloadNode.get("key2").textValue()).isEqualTo("val2");
  }

  @Test
  public void shouldMergePaylodsWhenMultipleTokensReachSameEndEvent() {
    // given
    final BpmnModelInstance model =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .parallelGateway("fork")
            .serviceTask("task1", b -> b.zeebeTaskType("type1"))
            .exclusiveGateway("join")
            .endEvent(b -> b.payloadMapping("$.key", "$.arr", ZeebeMappingType.COLLECT))
            .moveToNode("fork")
            .serviceTask("task2", b -> b.zeebeTaskType("type2"))
            .connectTo("join")
            .done();

    testClient.deploy(model);
    testClient.createWorkflowInstance(PROCESS_ID);

    // when
    testClient.completeJobOfType("type1", "{'key': 'val1'}");
    testClient.completeJobOfType("type2", "{'key': 'val2'}");

    // then
    final Record<WorkflowInstanceRecordValue> completedEvent =
        RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_COMPLETED)
            .withElementId(PROCESS_ID)
            .getFirst();

    final String actualPayload = completedEvent.getValue().getPayload();
    final JsonNode actualPayloadNode = JsonUtil.asJsonNode(actualPayload);

    assertThat(actualPayloadNode.has("arr"));

    final JsonNode arrayNode = actualPayloadNode.get("arr");
    assertThat(arrayNode.isArray()).isTrue();
    assertThat(arrayNode.elements())
        .hasSize(2)
        .extracting(n -> n.textValue())
        .containsExactlyInAnyOrder("val1", "val2");
  }
}
