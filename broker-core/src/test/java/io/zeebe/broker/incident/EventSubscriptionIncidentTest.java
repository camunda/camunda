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
package io.zeebe.broker.incident;

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.exporter.record.Assertions;
import io.zeebe.exporter.record.Record;
import io.zeebe.exporter.record.value.IncidentRecordValue;
import io.zeebe.exporter.record.value.WorkflowInstanceRecordValue;
import io.zeebe.exporter.record.value.WorkflowInstanceSubscriptionRecordValue;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.impl.record.value.incident.ErrorType;
import io.zeebe.protocol.intent.IncidentIntent;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.protocol.intent.WorkflowInstanceSubscriptionIntent;
import io.zeebe.test.broker.protocol.clientapi.ClientApiRule;
import io.zeebe.test.broker.protocol.clientapi.PartitionTestClient;
import io.zeebe.test.util.MsgPackUtil;
import io.zeebe.test.util.record.RecordingExporter;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class EventSubscriptionIncidentTest {

  private static final String PROCESS_ID = "process";

  private static final BpmnModelInstance WF_RECEIVE_TASK =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent()
          .receiveTask("task")
          .message(m -> m.name("msg-1").zeebeCorrelationKey("$.key-1"))
          .boundaryEvent(
              "msg-2", c -> c.message(m -> m.name("msg-2").zeebeCorrelationKey("$.key-2")))
          .endEvent()
          .done();

  private static final BpmnModelInstance WF_RECEIVE_TASK_2 =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent()
          .receiveTask("task")
          .message(m -> m.name("msg-2").zeebeCorrelationKey("$.key-2"))
          .boundaryEvent(
              "msg-1", c -> c.message(m -> m.name("msg-1").zeebeCorrelationKey("$.key-1")))
          .endEvent()
          .done();

  private static final BpmnModelInstance WF_EVENT_BASED_GATEWAY =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent()
          .eventBasedGateway("gateway")
          .intermediateCatchEvent(
              "msg-1", i -> i.message(m -> m.name("msg-1").zeebeCorrelationKey("$.key-1")))
          .endEvent()
          .moveToLastGateway()
          .intermediateCatchEvent(
              "msg-2", i -> i.message(m -> m.name("msg-2").zeebeCorrelationKey("$.key-2")))
          .endEvent()
          .done();

  private static final BpmnModelInstance WF_EVENT_BASED_GATEWAY_2 =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent()
          .eventBasedGateway("gateway")
          .intermediateCatchEvent(
              "msg-2", i -> i.message(m -> m.name("msg-2").zeebeCorrelationKey("$.key-2")))
          .endEvent()
          .moveToLastGateway()
          .intermediateCatchEvent(
              "msg-1", i -> i.message(m -> m.name("msg-1").zeebeCorrelationKey("$.key-1")))
          .endEvent()
          .done();

  private static final BpmnModelInstance WF_BOUNDARY_EVENT =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent()
          .serviceTask("task", t -> t.zeebeTaskType("test"))
          .boundaryEvent(
              "msg-1", c -> c.message(m -> m.name("msg-1").zeebeCorrelationKey("$.key-1")))
          .endEvent()
          .moveToActivity("task")
          .boundaryEvent(
              "msg-2", c -> c.message(m -> m.name("msg-2").zeebeCorrelationKey("$.key-2")))
          .endEvent()
          .done();

  private static final BpmnModelInstance WF_BOUNDARY_EVENT_2 =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent()
          .serviceTask("task", t -> t.zeebeTaskType("test"))
          .boundaryEvent(
              "msg-2", c -> c.message(m -> m.name("msg-2").zeebeCorrelationKey("$.key-2")))
          .endEvent()
          .moveToActivity("task")
          .boundaryEvent(
              "msg-1", c -> c.message(m -> m.name("msg-1").zeebeCorrelationKey("$.key-1")))
          .endEvent()
          .done();

  public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();
  public ClientApiRule apiRule = new ClientApiRule(brokerRule::getClientAddress);
  @Rule public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(apiRule);

  private PartitionTestClient testClient;

  @Parameters(name = "{0}")
  public static Object[][] parameters() {
    return new Object[][] {
      {
        "boundary catch event (first event)",
        WF_BOUNDARY_EVENT,
        "task",
        WorkflowInstanceIntent.ELEMENT_READY,
        WorkflowInstanceIntent.ELEMENT_ACTIVATED
      },
      {
        "boundary catch event (second event)",
        WF_BOUNDARY_EVENT_2,
        "task",
        WorkflowInstanceIntent.ELEMENT_READY,
        WorkflowInstanceIntent.ELEMENT_ACTIVATED
      },
      {
        "receive task (boundary event)",
        WF_RECEIVE_TASK,
        "task",
        WorkflowInstanceIntent.ELEMENT_READY,
        WorkflowInstanceIntent.ELEMENT_ACTIVATED
      },
      {
        "receive task (task)",
        WF_RECEIVE_TASK_2,
        "task",
        WorkflowInstanceIntent.ELEMENT_READY,
        WorkflowInstanceIntent.ELEMENT_ACTIVATED
      },
      {
        "event-based gateway (first event)",
        WF_EVENT_BASED_GATEWAY,
        "gateway",
        WorkflowInstanceIntent.GATEWAY_ACTIVATED,
        null
      },
      {
        "event-based gateway (second event)",
        WF_EVENT_BASED_GATEWAY_2,
        "gateway",
        WorkflowInstanceIntent.GATEWAY_ACTIVATED,
        null
      }
    };
  }

  @Parameter(0)
  public String elementType;

  @Parameter(1)
  public BpmnModelInstance workflow;

  @Parameter(2)
  public String elementId;

  @Parameter(3)
  public WorkflowInstanceIntent failureEventIntent;

  @Parameter(4)
  public WorkflowInstanceIntent resolvedEventIntent;

  @Before
  public void init() {
    testClient = apiRule.partitionClient();

    testClient.deploy(workflow);
  }

  @Test
  public void shouldCreateIncidentIfMessageCorrelationKeyNotFound() {
    // when
    final long workflowInstanceKey =
        testClient.createWorkflowInstance(PROCESS_ID, MsgPackUtil.asMsgPack("key-1", "k1"));

    final Record<WorkflowInstanceRecordValue> failureEvent =
        RecordingExporter.workflowInstanceRecords(failureEventIntent)
            .withElementId(elementId)
            .getFirst();

    // then
    final Record<IncidentRecordValue> incidentRecord =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED).getFirst();

    Assertions.assertThat(incidentRecord.getValue())
        .hasErrorType(ErrorType.EXTRACT_VALUE_ERROR.name())
        .hasErrorMessage("Failed to extract the correlation-key by '$.key-2': no value found")
        .hasBpmnProcessId(PROCESS_ID)
        .hasWorkflowInstanceKey(workflowInstanceKey)
        .hasElementId(failureEvent.getValue().getElementId())
        .hasElementInstanceKey(failureEvent.getKey())
        .hasJobKey(-1L);
  }

  @Test
  public void shouldCreateIncidentIfMessageCorrelationKeyHasInvalidType() {
    // when
    final long workflowInstanceKey =
        testClient.createWorkflowInstance(
            PROCESS_ID, MsgPackUtil.asMsgPack("{'key-1':'k1', 'key-2':[1,2,3]}"));

    final Record<WorkflowInstanceRecordValue> failureEvent =
        RecordingExporter.workflowInstanceRecords(failureEventIntent)
            .withElementId(elementId)
            .getFirst();

    // then
    final Record<IncidentRecordValue> incidentRecord =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED).getFirst();

    Assertions.assertThat(incidentRecord.getValue())
        .hasErrorType(ErrorType.EXTRACT_VALUE_ERROR.name())
        .hasErrorMessage(
            "Failed to extract the correlation-key by '$.key-2': the value must be either a string or a number")
        .hasBpmnProcessId(PROCESS_ID)
        .hasWorkflowInstanceKey(workflowInstanceKey)
        .hasElementId(failureEvent.getValue().getElementId())
        .hasElementInstanceKey(failureEvent.getKey())
        .hasJobKey(-1L);
  }

  @Test
  public void shouldOpenSubscriptionsWhenIncidentIsResolved() {
    // given
    testClient.createWorkflowInstance(PROCESS_ID, MsgPackUtil.asMsgPack("key-1", "k1"));

    final Record<IncidentRecordValue> incidentCreatedRecord =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED).getFirst();

    // when
    testClient.updatePayload(
        incidentCreatedRecord.getValue().getElementInstanceKey(), "{'key-1':'k1', 'key-2':'k2'}");

    testClient.resolveIncident(incidentCreatedRecord.getKey());

    // then
    assertThat(
            RecordingExporter.workflowInstanceSubscriptionRecords(
                    WorkflowInstanceSubscriptionIntent.OPENED)
                .limit(2))
        .extracting(Record::getValue)
        .extracting(WorkflowInstanceSubscriptionRecordValue::getMessageName)
        .containsExactlyInAnyOrder("msg-1", "msg-2");

    // and
    testClient.publishMessage("msg-2", "k2");

    assertThat(
            RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_COMPLETED)
                .withElementId(PROCESS_ID)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldNotOpenSubscriptionsWhenIncidentIsCreated() {
    // given
    testClient.createWorkflowInstance(PROCESS_ID, MsgPackUtil.asMsgPack("key-1", "k1"));

    final Record<IncidentRecordValue> incidentCreatedRecord =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED).getFirst();

    // when
    testClient.updatePayload(
        incidentCreatedRecord.getValue().getElementInstanceKey(), "{'key-1':'k1', 'key-2':'k2'}");

    testClient.resolveIncident(incidentCreatedRecord.getKey());

    // then
    final Record<IncidentRecordValue> incidentResolvedRecord =
        RecordingExporter.incidentRecords(IncidentIntent.RESOLVED).getFirst();

    assertThat(
            RecordingExporter.workflowInstanceSubscriptionRecords(
                    WorkflowInstanceSubscriptionIntent.OPENED)
                .limit(2))
        .allMatch(r -> r.getPosition() > incidentResolvedRecord.getPosition());

    // and
    if (resolvedEventIntent != null) {
      assertThat(
              RecordingExporter.workflowInstanceRecords(resolvedEventIntent)
                  .withElementId(elementId)
                  .getFirst()
                  .getPosition())
          .isGreaterThan(incidentResolvedRecord.getPosition());
    }
  }
}
