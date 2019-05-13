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
package io.zeebe.broker.engine.message;

import static io.zeebe.test.util.MsgPackUtil.asMsgPack;

import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.exporter.api.record.Assertions;
import io.zeebe.exporter.api.record.Record;
import io.zeebe.exporter.api.record.value.VariableRecordValue;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.model.bpmn.builder.ZeebeVariablesMappingBuilder;
import io.zeebe.model.bpmn.instance.BoundaryEvent;
import io.zeebe.model.bpmn.instance.IntermediateCatchEvent;
import io.zeebe.model.bpmn.instance.ReceiveTask;
import io.zeebe.model.bpmn.instance.StartEvent;
import io.zeebe.protocol.intent.VariableIntent;
import io.zeebe.test.broker.protocol.clientapi.ClientApiRule;
import io.zeebe.test.util.record.RecordingExporter;
import io.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.UUID;
import java.util.function.Consumer;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.junit.Before;
import org.junit.ClassRule;
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
  private static final String MESSAGE_NAME = "message";
  private static final String CORRELATION_VARIABLE = "key";

  private static final BpmnModelInstance CATCH_EVENT_WORKFLOW =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent()
          .intermediateCatchEvent("catch")
          .message(m -> m.name(MESSAGE_NAME).zeebeCorrelationKey(CORRELATION_VARIABLE))
          .done();

  private static final BpmnModelInstance RECEIVE_TASK_WORKFLOW =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent()
          .receiveTask("catch")
          .message(m -> m.name(MESSAGE_NAME).zeebeCorrelationKey(CORRELATION_VARIABLE))
          .done();

  private static final BpmnModelInstance INTERRUPTING_BOUNDARY_EVENT_WORKFLOW =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent()
          .serviceTask("task", b -> b.zeebeTaskType("type"))
          .boundaryEvent("catch")
          .message(m -> m.name(MESSAGE_NAME).zeebeCorrelationKey(CORRELATION_VARIABLE))
          .endEvent()
          .done();

  private static final BpmnModelInstance NON_INTERRUPTING_BOUNDARY_EVENT_WORKFLOW =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent()
          .serviceTask("task", b -> b.zeebeTaskType("type"))
          .boundaryEvent("catch", b -> b.cancelActivity(false))
          .message(m -> m.name(MESSAGE_NAME).zeebeCorrelationKey(CORRELATION_VARIABLE))
          .endEvent()
          .done();

  private static final BpmnModelInstance EVENT_BASED_GATEWAY_WORKFLOW =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent("start")
          .eventBasedGateway()
          .id("gateway")
          .intermediateCatchEvent(
              "catch",
              c -> c.message(m -> m.name(MESSAGE_NAME).zeebeCorrelationKey(CORRELATION_VARIABLE)))
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

  public static EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();
  public static ClientApiRule apiRule = new ClientApiRule(brokerRule::getAtomix);

  @ClassRule public static RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(apiRule);

  @Rule
  public RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  private String correlationKey;

  @Before
  public void setUp() {
    correlationKey = UUID.randomUUID().toString();
  }

  @Test
  public void shouldMergeMessageVariablesByDefault() {
    // given
    final long workflowKey = deployWorkflowWithMapping(e -> {});

    final long workflowInstanceKey =
        apiRule
            .partitionClient()
            .createWorkflowInstance(
                r ->
                    r.setKey(workflowKey)
                        .setVariables(asMsgPack(CORRELATION_VARIABLE, correlationKey)))
            .getInstanceKey();

    // when
    apiRule.publishMessage(MESSAGE_NAME, correlationKey, asMsgPack("foo", "bar"));

    // then
    final Record<VariableRecordValue> variableEvent =
        RecordingExporter.variableRecords(VariableIntent.CREATED)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .withName("foo")
            .getFirst();

    Assertions.assertThat(variableEvent.getValue())
        .hasValue("\"bar\"")
        .hasScopeKey(workflowInstanceKey);
  }

  @Test
  public void shouldMergeMessageVariables() {
    // given
    final long workflowKey = deployWorkflowWithMapping(e -> {});

    final long workflowInstanceKey =
        apiRule
            .partitionClient()
            .createWorkflowInstance(
                r ->
                    r.setKey(workflowKey)
                        .setVariables(asMsgPack(CORRELATION_VARIABLE, correlationKey)))
            .getInstanceKey();

    // when
    apiRule.publishMessage(MESSAGE_NAME, correlationKey, asMsgPack("foo", "bar"));

    // then
    final Record<VariableRecordValue> variableEvent =
        RecordingExporter.variableRecords(VariableIntent.CREATED)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .withName("foo")
            .getFirst();

    Assertions.assertThat(variableEvent.getValue())
        .hasValue("\"bar\"")
        .hasScopeKey(workflowInstanceKey);
  }

  @Test
  public void shouldMapMessageVariablesIntoInstanceVariables() {
    // given
    final long workflowKey = deployWorkflowWithMapping(e -> e.zeebeOutput("foo", MESSAGE_NAME));
    final long workflowInstanceKey =
        apiRule
            .partitionClient()
            .createWorkflowInstance(
                r ->
                    r.setKey(workflowKey)
                        .setVariables(asMsgPack(CORRELATION_VARIABLE, correlationKey)))
            .getInstanceKey();

    // when
    apiRule.publishMessage(MESSAGE_NAME, correlationKey, asMsgPack("foo", "bar"));

    // then
    final Record<VariableRecordValue> variableEvent =
        RecordingExporter.variableRecords(VariableIntent.CREATED)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .withName(MESSAGE_NAME)
            .getFirst();

    Assertions.assertThat(variableEvent.getValue())
        .hasValue("\"bar\"")
        .hasScopeKey(workflowInstanceKey);
  }

  private long deployWorkflowWithMapping(Consumer<ZeebeVariablesMappingBuilder<?>> c) {
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
    return apiRule
        .deployWorkflow(modifiedWorkflow)
        .getValue()
        .getDeployedWorkflows()
        .get(0)
        .getWorkflowKey();
  }
}
