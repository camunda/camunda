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
package io.zeebe.broker.engine.incident;

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.exporter.api.record.Assertions;
import io.zeebe.exporter.api.record.Record;
import io.zeebe.exporter.api.record.value.IncidentRecordValue;
import io.zeebe.exporter.api.record.value.WorkflowInstanceRecordValue;
import io.zeebe.exporter.api.record.value.WorkflowInstanceSubscriptionRecordValue;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.ErrorType;
import io.zeebe.protocol.intent.IncidentIntent;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.protocol.intent.WorkflowInstanceSubscriptionIntent;
import io.zeebe.test.broker.protocol.clientapi.ClientApiRule;
import io.zeebe.test.util.MsgPackUtil;
import io.zeebe.test.util.record.RecordingExporter;
import io.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class EventSubscriptionIncidentTest {

  private static final String MESSAGE_NAME_1 = "msg-1";
  private static final String MESSAGE_NAME_2 = "msg-2";
  private static final String CORRELATION_VARIABLE_1 = "key1";
  private static final String CORRELATION_VARIABLE_2 = "key2";

  private static final String WF_RECEIVE_TASK_ID = "wf-receive-task";
  private static final BpmnModelInstance WF_RECEIVE_TASK =
      Bpmn.createExecutableProcess(WF_RECEIVE_TASK_ID)
          .startEvent()
          .receiveTask("task")
          .message(m -> m.name(MESSAGE_NAME_1).zeebeCorrelationKey(CORRELATION_VARIABLE_1))
          .boundaryEvent(
              MESSAGE_NAME_2,
              c ->
                  c.message(
                      m -> m.name(MESSAGE_NAME_2).zeebeCorrelationKey(CORRELATION_VARIABLE_2)))
          .endEvent()
          .done();

  private static final String WF_RECEIVE_TASK_2_ID = "wf-receive-task-2";
  private static final BpmnModelInstance WF_RECEIVE_TASK_2 =
      Bpmn.createExecutableProcess(WF_RECEIVE_TASK_2_ID)
          .startEvent()
          .receiveTask("task")
          .message(m -> m.name(MESSAGE_NAME_2).zeebeCorrelationKey(CORRELATION_VARIABLE_2))
          .boundaryEvent(
              MESSAGE_NAME_1,
              c ->
                  c.message(
                      m -> m.name(MESSAGE_NAME_1).zeebeCorrelationKey(CORRELATION_VARIABLE_1)))
          .endEvent()
          .done();

  private static final String WF_EVENT_BASED_GATEWAY_ID = "wf-event-based-gateway";
  private static final BpmnModelInstance WF_EVENT_BASED_GATEWAY =
      Bpmn.createExecutableProcess(WF_EVENT_BASED_GATEWAY_ID)
          .startEvent()
          .eventBasedGateway("gateway")
          .intermediateCatchEvent(
              MESSAGE_NAME_1,
              i ->
                  i.message(
                      m -> m.name(MESSAGE_NAME_1).zeebeCorrelationKey(CORRELATION_VARIABLE_1)))
          .endEvent()
          .moveToLastGateway()
          .intermediateCatchEvent(
              MESSAGE_NAME_2,
              i ->
                  i.message(
                      m -> m.name(MESSAGE_NAME_2).zeebeCorrelationKey(CORRELATION_VARIABLE_2)))
          .endEvent()
          .done();

  private static final String WF_EVENT_BASED_GATEWAY_2_ID = "wf-event-based-gateway-2";
  private static final BpmnModelInstance WF_EVENT_BASED_GATEWAY_2 =
      Bpmn.createExecutableProcess(WF_EVENT_BASED_GATEWAY_2_ID)
          .startEvent()
          .eventBasedGateway("gateway")
          .intermediateCatchEvent(
              MESSAGE_NAME_2,
              i ->
                  i.message(
                      m -> m.name(MESSAGE_NAME_2).zeebeCorrelationKey(CORRELATION_VARIABLE_2)))
          .endEvent()
          .moveToLastGateway()
          .intermediateCatchEvent(
              MESSAGE_NAME_1,
              i ->
                  i.message(
                      m -> m.name(MESSAGE_NAME_1).zeebeCorrelationKey(CORRELATION_VARIABLE_1)))
          .endEvent()
          .done();

  private static final String WF_BOUNDARY_EVENT_ID = "wf-boundary-event";
  private static final BpmnModelInstance WF_BOUNDARY_EVENT =
      Bpmn.createExecutableProcess(WF_BOUNDARY_EVENT_ID)
          .startEvent()
          .serviceTask("task", t -> t.zeebeTaskType("test"))
          .boundaryEvent(
              MESSAGE_NAME_1,
              c ->
                  c.message(
                      m -> m.name(MESSAGE_NAME_1).zeebeCorrelationKey(CORRELATION_VARIABLE_1)))
          .endEvent()
          .moveToActivity("task")
          .boundaryEvent(
              MESSAGE_NAME_2,
              c ->
                  c.message(
                      m -> m.name(MESSAGE_NAME_2).zeebeCorrelationKey(CORRELATION_VARIABLE_2)))
          .endEvent()
          .done();

  private static final String WF_BOUNDARY_EVENT_2_ID = "wf-boundary-event-2";
  private static final BpmnModelInstance WF_BOUNDARY_EVENT_2 =
      Bpmn.createExecutableProcess(WF_BOUNDARY_EVENT_2_ID)
          .startEvent()
          .serviceTask("task", t -> t.zeebeTaskType("test"))
          .boundaryEvent(
              MESSAGE_NAME_2,
              c ->
                  c.message(
                      m -> m.name(MESSAGE_NAME_2).zeebeCorrelationKey(CORRELATION_VARIABLE_2)))
          .endEvent()
          .moveToActivity("task")
          .boundaryEvent(
              MESSAGE_NAME_1,
              c ->
                  c.message(
                      m -> m.name(MESSAGE_NAME_1).zeebeCorrelationKey(CORRELATION_VARIABLE_1)))
          .endEvent()
          .done();

  public static EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();
  public static ClientApiRule apiRule = new ClientApiRule(brokerRule::getAtomix);
  @ClassRule public static RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(apiRule);

  @Rule
  public RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Parameters(name = "{0}")
  public static Object[][] parameters() {
    return new Object[][] {
      {
        "boundary catch event (first event)",
        WF_BOUNDARY_EVENT_ID,
        "task",
        WorkflowInstanceIntent.ELEMENT_ACTIVATING,
        WorkflowInstanceIntent.ELEMENT_ACTIVATED
      },
      {
        "boundary catch event (second event)",
        WF_BOUNDARY_EVENT_2_ID,
        "task",
        WorkflowInstanceIntent.ELEMENT_ACTIVATING,
        WorkflowInstanceIntent.ELEMENT_ACTIVATED
      },
      {
        "receive task (boundary event)",
        WF_RECEIVE_TASK_ID,
        "task",
        WorkflowInstanceIntent.ELEMENT_ACTIVATING,
        WorkflowInstanceIntent.ELEMENT_ACTIVATED
      },
      {
        "receive task (task)",
        WF_RECEIVE_TASK_2_ID,
        "task",
        WorkflowInstanceIntent.ELEMENT_ACTIVATING,
        WorkflowInstanceIntent.ELEMENT_ACTIVATED
      },
      {
        "event-based gateway (first event)",
        WF_EVENT_BASED_GATEWAY_ID,
        "gateway",
        WorkflowInstanceIntent.ELEMENT_ACTIVATING,
        null
      },
      {
        "event-based gateway (second event)",
        WF_EVENT_BASED_GATEWAY_2_ID,
        "gateway",
        WorkflowInstanceIntent.ELEMENT_ACTIVATING,
        null
      }
    };
  }

  @Parameter(0)
  public String elementType;

  @Parameter(1)
  public String processId;

  @Parameter(2)
  public String elementId;

  @Parameter(3)
  public WorkflowInstanceIntent failureEventIntent;

  @Parameter(4)
  public WorkflowInstanceIntent resolvedEventIntent;

  @BeforeClass
  public static void deployWorkflows() {
    for (BpmnModelInstance modelInstance :
        Arrays.asList(
            WF_RECEIVE_TASK,
            WF_RECEIVE_TASK_2,
            WF_BOUNDARY_EVENT,
            WF_BOUNDARY_EVENT_2,
            WF_EVENT_BASED_GATEWAY,
            WF_EVENT_BASED_GATEWAY_2)) {
      apiRule.deployWorkflow(modelInstance);
    }
  }

  private String correlationKey1;
  private String correlationKey2;

  @Before
  public void init() {
    correlationKey1 = UUID.randomUUID().toString();
    correlationKey2 = UUID.randomUUID().toString();
  }

  @Test
  public void shouldCreateIncidentIfMessageCorrelationKeyNotFound() {
    // when
    final long workflowInstanceKey =
        apiRule
            .partitionClient()
            .createWorkflowInstance(
                r ->
                    r.setBpmnProcessId(processId)
                        .setVariables(
                            MsgPackUtil.asMsgPack(CORRELATION_VARIABLE_1, correlationKey1)))
            .getInstanceKey();

    final Record<WorkflowInstanceRecordValue> failureEvent =
        RecordingExporter.workflowInstanceRecords(failureEventIntent)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .withElementId(elementId)
            .getFirst();

    // then
    final Record<IncidentRecordValue> incidentRecord =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .getFirst();

    Assertions.assertThat(incidentRecord.getValue())
        .hasErrorType(ErrorType.EXTRACT_VALUE_ERROR.name())
        .hasErrorMessage(
            "Failed to extract the correlation-key by '"
                + CORRELATION_VARIABLE_2
                + "': no value found")
        .hasBpmnProcessId(processId)
        .hasWorkflowInstanceKey(workflowInstanceKey)
        .hasElementId(failureEvent.getValue().getElementId())
        .hasElementInstanceKey(failureEvent.getKey())
        .hasJobKey(-1L);
  }

  @Test
  public void shouldCreateIncidentIfMessageCorrelationKeyHasInvalidType() {
    // when
    final Map<String, Object> variables = new HashMap<>();
    variables.put(CORRELATION_VARIABLE_1, correlationKey1);
    variables.put(CORRELATION_VARIABLE_2, Arrays.asList(1, 2, 3));

    final long workflowInstanceKey =
        apiRule
            .partitionClient()
            .createWorkflowInstance(
                r -> r.setBpmnProcessId(processId).setVariables(MsgPackUtil.asMsgPack(variables)))
            .getInstanceKey();

    final Record<WorkflowInstanceRecordValue> failureEvent =
        RecordingExporter.workflowInstanceRecords(failureEventIntent)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .withElementId(elementId)
            .getFirst();

    // then
    final Record<IncidentRecordValue> incidentRecord =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .getFirst();

    Assertions.assertThat(incidentRecord.getValue())
        .hasErrorType(ErrorType.EXTRACT_VALUE_ERROR.name())
        .hasErrorMessage(
            "Failed to extract the correlation-key by '"
                + CORRELATION_VARIABLE_2
                + "': the value must be either a string or a number")
        .hasBpmnProcessId(processId)
        .hasWorkflowInstanceKey(workflowInstanceKey)
        .hasElementId(failureEvent.getValue().getElementId())
        .hasElementInstanceKey(failureEvent.getKey())
        .hasJobKey(-1L);
  }

  @Test
  public void shouldOpenSubscriptionsWhenIncidentIsResolved() {
    // given
    final String correlationKey1 = UUID.randomUUID().toString();
    final String correlationKey2 = UUID.randomUUID().toString();
    final long workflowInstanceKey =
        apiRule
            .partitionClient()
            .createWorkflowInstance(
                r ->
                    r.setBpmnProcessId(processId)
                        .setVariables(
                            MsgPackUtil.asMsgPack(CORRELATION_VARIABLE_1, correlationKey1)))
            .getInstanceKey();

    final Record<IncidentRecordValue> incidentCreatedRecord =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .getFirst();

    // when
    final Map<String, Object> document = new HashMap<>();
    document.put(CORRELATION_VARIABLE_1, correlationKey1);
    document.put(CORRELATION_VARIABLE_2, correlationKey2);
    apiRule
        .partitionClient()
        .updateVariables(incidentCreatedRecord.getValue().getElementInstanceKey(), document);

    apiRule.resolveIncident(incidentCreatedRecord.getKey());

    // then
    assertThat(
            RecordingExporter.workflowInstanceSubscriptionRecords(
                    WorkflowInstanceSubscriptionIntent.OPENED)
                .withWorkflowInstanceKey(workflowInstanceKey)
                .limit(2))
        .extracting(Record::getValue)
        .extracting(WorkflowInstanceSubscriptionRecordValue::getMessageName)
        .containsExactlyInAnyOrder(MESSAGE_NAME_1, MESSAGE_NAME_2);

    // and
    apiRule.publishMessage(MESSAGE_NAME_2, correlationKey2);

    assertThat(
            RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_COMPLETED)
                .withWorkflowInstanceKey(workflowInstanceKey)
                .withElementId(processId)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldNotOpenSubscriptionsWhenIncidentIsCreated() {
    // given
    final long workflowInstanceKey =
        apiRule
            .partitionClient()
            .createWorkflowInstance(
                r ->
                    r.setBpmnProcessId(processId)
                        .setVariables(
                            MsgPackUtil.asMsgPack(CORRELATION_VARIABLE_1, correlationKey1)))
            .getInstanceKey();

    final Record<IncidentRecordValue> incidentCreatedRecord =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .getFirst();

    // when
    final Map<String, Object> document = new HashMap<>();
    document.put(CORRELATION_VARIABLE_1, correlationKey1);
    document.put(CORRELATION_VARIABLE_2, correlationKey2);

    apiRule
        .partitionClient()
        .updateVariables(incidentCreatedRecord.getValue().getElementInstanceKey(), document);

    apiRule.resolveIncident(incidentCreatedRecord.getKey());

    // then
    final Record<IncidentRecordValue> incidentResolvedRecord =
        RecordingExporter.incidentRecords(IncidentIntent.RESOLVED)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .getFirst();

    assertThat(
            RecordingExporter.workflowInstanceSubscriptionRecords(
                    WorkflowInstanceSubscriptionIntent.OPENED)
                .withWorkflowInstanceKey(workflowInstanceKey)
                .limit(2))
        .allMatch(r -> r.getPosition() > incidentResolvedRecord.getPosition());

    // and
    if (resolvedEventIntent != null) {
      assertThat(
              RecordingExporter.workflowInstanceRecords(resolvedEventIntent)
                  .withWorkflowInstanceKey(workflowInstanceKey)
                  .withElementId(elementId)
                  .getFirst()
                  .getPosition())
          .isGreaterThan(incidentResolvedRecord.getPosition());
    }
  }
}
