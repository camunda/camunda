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
package io.zeebe.broker.workflow.message;

import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.exporter.record.Assertions;
import io.zeebe.exporter.record.Record;
import io.zeebe.exporter.record.value.VariableRecordValue;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.model.bpmn.builder.ZeebePayloadMappingBuilder;
import io.zeebe.model.bpmn.instance.BoundaryEvent;
import io.zeebe.model.bpmn.instance.IntermediateCatchEvent;
import io.zeebe.model.bpmn.instance.ReceiveTask;
import io.zeebe.model.bpmn.instance.StartEvent;
import io.zeebe.model.bpmn.instance.zeebe.ZeebeOutputBehavior;
import io.zeebe.test.broker.protocol.clientapi.ClientApiRule;
import io.zeebe.test.broker.protocol.clientapi.PartitionTestClient;
import io.zeebe.test.util.record.RecordingExporter;
import java.util.function.Consumer;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class MessageMappingTest {

  private static final String PROCESS_ID = "process";

  private static final BpmnModelInstance CATCH_EVENT_WORKFLOW =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent()
          .intermediateCatchEvent("catch")
          .message(m -> m.name("message").zeebeCorrelationKey("$.key"))
          .done();

  private static final BpmnModelInstance RECEIVE_TASK_WORKFLOW =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent()
          .receiveTask("catch")
          .message(m -> m.name("message").zeebeCorrelationKey("$.key"))
          .done();

  private static final BpmnModelInstance INTERRUPTING_BOUNDARY_EVENT_WORKFLOW =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent()
          .serviceTask("task", b -> b.zeebeTaskType("type"))
          .boundaryEvent("catch")
          .message(m -> m.name("message").zeebeCorrelationKey("$.key"))
          .endEvent()
          .done();

  private static final BpmnModelInstance NON_INTERRUPTING_BOUNDARY_EVENT_WORKFLOW =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent()
          .serviceTask("task", b -> b.zeebeTaskType("type"))
          .boundaryEvent("catch", b -> b.cancelActivity(false))
          .message(m -> m.name("message").zeebeCorrelationKey("$.key"))
          .endEvent()
          .done();

  private static final BpmnModelInstance EVENT_BASED_GATEWAY_WORKFLOW =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent("start")
          .eventBasedGateway()
          .id("gateway")
          .intermediateCatchEvent(
              "catch", c -> c.message(m -> m.name("message").zeebeCorrelationKey("$.key")))
          .sequenceFlowId("to-end1")
          .endEvent("end1")
          .moveToLastGateway()
          .intermediateCatchEvent("timer", c -> c.timerWithDuration("PT10S"))
          .sequenceFlowId("to-end2")
          .endEvent("end2")
          .done();

  @Parameter(0)
  public String elementType;

  @Parameter(1)
  public BpmnModelInstance workflow;

  @Parameters(name = "{0}")
  public static Object[][] parameters() {
    return new Object[][] {
      {"intermediate catch event", CATCH_EVENT_WORKFLOW},
      {"receive task", RECEIVE_TASK_WORKFLOW},
      {"event-based gateway", EVENT_BASED_GATEWAY_WORKFLOW},
      {"interrupting boundary event", INTERRUPTING_BOUNDARY_EVENT_WORKFLOW},
      {"non-interrupting boundary event", NON_INTERRUPTING_BOUNDARY_EVENT_WORKFLOW},
    };
  }

  public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();

  public ClientApiRule apiRule = new ClientApiRule(brokerRule::getAtomixAddress);

  @Rule public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(apiRule);

  private PartitionTestClient testClient;

  @Before
  public void init() {
    testClient = apiRule.partitionClient();
  }

  @Test
  public void shouldMergeMessagePayloadByDefault() {
    // given
    deployWorkflowWithMapping(e -> {});

    final long workflowInstanceKey =
        testClient.createWorkflowInstance(PROCESS_ID, "{'key': 'order-66'}");

    // when
    testClient.publishMessage("message", "order-66", "{'foo': 'bar'}");

    // then
    final Record<VariableRecordValue> variableEvent =
        RecordingExporter.variableRecords().withName("foo").getFirst();

    Assertions.assertThat(variableEvent.getValue())
        .hasValue("\"bar\"")
        .hasScopeInstanceKey(workflowInstanceKey);
  }

  @Test
  public void shouldMergeMessagePayload() {
    // given
    deployWorkflowWithMapping(e -> e.zeebeOutputBehavior(ZeebeOutputBehavior.merge));

    final long workflowInstanceKey =
        testClient.createWorkflowInstance(PROCESS_ID, "{'key': 'order-66'}");

    // when
    testClient.publishMessage("message", "order-66", "{'foo': 'bar'}");

    // then
    final Record<VariableRecordValue> variableEvent =
        RecordingExporter.variableRecords().withName("foo").getFirst();

    Assertions.assertThat(variableEvent.getValue())
        .hasValue("\"bar\"")
        .hasScopeInstanceKey(workflowInstanceKey);
  }

  @Test
  public void shouldMapMessagePayloadIntoInstancePayload() {
    // given
    deployWorkflowWithMapping(e -> e.zeebeOutput("$.foo", "$.message"));

    final long workflowInstanceKey =
        testClient.createWorkflowInstance(PROCESS_ID, "{'key': 'order-66'}");

    // when
    testClient.publishMessage("message", "order-66", "{'foo': 'bar'}");

    // then
    final Record<VariableRecordValue> variableEvent =
        RecordingExporter.variableRecords().withName("message").getFirst();

    Assertions.assertThat(variableEvent.getValue())
        .hasValue("\"bar\"")
        .hasScopeInstanceKey(workflowInstanceKey);
  }

  private void deployWorkflowWithMapping(Consumer<ZeebePayloadMappingBuilder<?>> c) {
    final BpmnModelInstance modifiedWorkflow = workflow.clone();
    final ModelElementInstance element = modifiedWorkflow.getModelElementById("catch");
    if (element instanceof IntermediateCatchEvent) {
      c.accept(((IntermediateCatchEvent) element).builder());
    } else if (element instanceof StartEvent) {
      c.accept(((StartEvent) element).builder());
    } else if (element instanceof BoundaryEvent) {
      c.accept(((BoundaryEvent) element).builder());
    } else {
      c.accept(((ReceiveTask) element).builder());
    }
    testClient.deploy(modifiedWorkflow);
  }
}
