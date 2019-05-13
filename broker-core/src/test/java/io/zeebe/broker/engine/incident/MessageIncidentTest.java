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

import static io.zeebe.protocol.intent.IncidentIntent.RESOLVED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.exporter.api.record.Assertions;
import io.zeebe.exporter.api.record.Record;
import io.zeebe.exporter.api.record.value.IncidentRecordValue;
import io.zeebe.exporter.api.record.value.WorkflowInstanceRecordValue;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.ErrorType;
import io.zeebe.protocol.intent.IncidentIntent;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.protocol.intent.WorkflowInstanceSubscriptionIntent;
import io.zeebe.test.broker.protocol.clientapi.ClientApiRule;
import io.zeebe.test.broker.protocol.clientapi.PartitionTestClient;
import io.zeebe.test.util.MsgPackUtil;
import io.zeebe.test.util.collection.Maps;
import io.zeebe.test.util.record.RecordingExporter;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class MessageIncidentTest {

  private static final String PROCESS_ID = "process";

  private static final BpmnModelInstance WORKFLOW =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent()
          .intermediateCatchEvent(
              "catch", e -> e.message(m -> m.name("cancel").zeebeCorrelationKey("orderId")))
          .done();

  public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();
  public ClientApiRule apiRule = new ClientApiRule(brokerRule::getAtomix);

  @Rule public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(apiRule);

  private PartitionTestClient testClient;

  @Before
  public void init() {
    testClient = apiRule.partitionClient();
    apiRule.waitForPartition(1);

    // given
    testClient.deploy(WORKFLOW);
  }

  @Test
  public void shouldCreateIncidentIfCorrelationKeyNotFound() {
    // when
    final long workflowInstanceKey =
        testClient.createWorkflowInstance(r -> r.setBpmnProcessId(PROCESS_ID)).getInstanceKey();

    final Record<WorkflowInstanceRecordValue> failureEvent =
        RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_ACTIVATING)
            .withElementId("catch")
            .getFirst();

    // then
    final Record<IncidentRecordValue> incidentRecord =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED).getFirst();

    Assertions.assertThat(incidentRecord.getValue())
        .hasErrorType(ErrorType.EXTRACT_VALUE_ERROR.name())
        .hasErrorMessage("Failed to extract the correlation-key by 'orderId': no value found")
        .hasBpmnProcessId(PROCESS_ID)
        .hasWorkflowInstanceKey(workflowInstanceKey)
        .hasElementId("catch")
        .hasElementInstanceKey(failureEvent.getKey())
        .hasJobKey(-1L)
        .hasVariableScopeKey(failureEvent.getKey());
  }

  @Test
  public void shouldCreateIncidentIfCorrelationKeyOfInvalidType() {
    // when
    final long workflowInstanceKey =
        testClient
            .createWorkflowInstance(
                r ->
                    r.setBpmnProcessId(PROCESS_ID)
                        .setVariables(MsgPackUtil.asMsgPack("orderId", true)))
            .getInstanceKey();

    final Record<WorkflowInstanceRecordValue> failureEvent =
        RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_ACTIVATING)
            .withElementId("catch")
            .getFirst();

    // then
    final Record<IncidentRecordValue> incidentRecord =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED).getFirst();

    Assertions.assertThat(incidentRecord.getValue())
        .hasErrorType(ErrorType.EXTRACT_VALUE_ERROR.name())
        .hasErrorMessage(
            "Failed to extract the correlation-key by 'orderId': the value must be either a string or a number")
        .hasBpmnProcessId(PROCESS_ID)
        .hasWorkflowInstanceKey(workflowInstanceKey)
        .hasElementId("catch")
        .hasElementInstanceKey(failureEvent.getKey())
        .hasJobKey(-1L)
        .hasVariableScopeKey(failureEvent.getKey());
  }

  @Test
  public void shouldResolveIncidentIfCorrelationKeyNotFound() {
    // given
    final long workflowInstance =
        testClient.createWorkflowInstance(r -> r.setBpmnProcessId(PROCESS_ID)).getInstanceKey();

    final Record<IncidentRecordValue> incidentCreatedRecord =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED).getFirst();

    testClient.updateVariables(
        incidentCreatedRecord.getValue().getElementInstanceKey(),
        Maps.of(entry("orderId", "order123")));

    // when
    testClient.resolveIncident(incidentCreatedRecord.getKey());

    // then
    assertThat(
            RecordingExporter.workflowInstanceSubscriptionRecords(
                    WorkflowInstanceSubscriptionIntent.OPENED)
                .exists())
        .isTrue();

    final Record<IncidentRecordValue> incidentResolvedEvent =
        testClient.receiveFirstIncidentEvent(workflowInstance, RESOLVED);
    assertThat(incidentResolvedEvent.getKey()).isEqualTo(incidentCreatedRecord.getKey());
  }
}
