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

import static io.zeebe.broker.engine.incident.IncidentAssert.assertIOMappingIncidentWithNoData;
import static io.zeebe.broker.engine.incident.IncidentAssert.assertIncidentRecordValue;
import static io.zeebe.protocol.intent.IncidentIntent.CREATED;
import static io.zeebe.protocol.intent.IncidentIntent.RESOLVE;
import static io.zeebe.protocol.intent.IncidentIntent.RESOLVED;
import static io.zeebe.protocol.intent.WorkflowInstanceIntent.ELEMENT_COMPLETED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import io.zeebe.UnstableTest;
import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.exporter.api.record.Assertions;
import io.zeebe.exporter.api.record.Record;
import io.zeebe.exporter.api.record.value.IncidentRecordValue;
import io.zeebe.exporter.api.record.value.WorkflowInstanceRecordValue;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.BpmnElementType;
import io.zeebe.protocol.ErrorType;
import io.zeebe.protocol.intent.IncidentIntent;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.test.broker.protocol.clientapi.ClientApiRule;
import io.zeebe.test.broker.protocol.clientapi.PartitionTestClient;
import io.zeebe.test.util.MsgPackUtil;
import io.zeebe.test.util.collection.Maps;
import io.zeebe.util.buffer.BufferUtil;
import java.util.HashMap;
import java.util.Map;
import org.agrona.DirectBuffer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.RuleChain;

public class MappingIncidentTest {

  public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();
  public ClientApiRule apiRule = new ClientApiRule(brokerRule::getAtomix);

  @Rule public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(apiRule);

  private PartitionTestClient testClient;

  private static final BpmnModelInstance WORKFLOW_INPUT_MAPPING =
      Bpmn.createExecutableProcess("process")
          .startEvent()
          .serviceTask("failingTask", t -> t.zeebeTaskType("test").zeebeInput("foo", "foo"))
          .done();

  private static final BpmnModelInstance WORKFLOW_OUTPUT_MAPPING =
      Bpmn.createExecutableProcess("process")
          .startEvent()
          .serviceTask("failingTask", t -> t.zeebeTaskType("test").zeebeOutput("foo", "foo"))
          .done();

  private static final Map<String, Object> VARIABLES = Maps.of(entry("foo", "bar"));

  private static final DirectBuffer VARIABLES_MSGPACK =
      MsgPackUtil.asMsgPack("{'string':'value', 'jsonObject':{'testAttr':'test'}}");

  @Before
  public void init() {
    testClient = apiRule.partitionClient();
    apiRule.waitForPartition(1);
  }

  @Test
  public void shouldCreateIncidentForInputMappingFailure() {
    // given
    final long workflowKey = testClient.deployWorkflow(WORKFLOW_INPUT_MAPPING).getKey();

    // when
    final long workflowInstanceKey =
        testClient.createWorkflowInstance(r -> r.setBpmnProcessId("process")).getInstanceKey();

    // then
    final Record failureEvent =
        testClient.receiveElementInState("failingTask", WorkflowInstanceIntent.ELEMENT_ACTIVATING);
    final Record createIncidentEvent =
        testClient.receiveFirstIncidentCommand(IncidentIntent.CREATE);
    final Record<IncidentRecordValue> incidentEvent =
        testClient.receiveFirstIncidentEvent(IncidentIntent.CREATED);

    assertThat(incidentEvent.getKey()).isGreaterThan(0);
    assertThat(createIncidentEvent.getSourceRecordPosition()).isEqualTo(failureEvent.getPosition());
    assertThat(incidentEvent.getSourceRecordPosition())
        .isEqualTo(createIncidentEvent.getPosition());
    assertThat(incidentEvent.getValue().getVariableScopeKey()).isEqualTo(failureEvent.getKey());

    assertIOMappingIncidentWithNoData(
        workflowKey, workflowInstanceKey, failureEvent, incidentEvent);
  }

  @Test
  public void shouldCreateIncidentForNonMatchingAndMatchingValueOnInputMapping() {
    // given
    testClient.deploy(
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask(
                "service",
                t ->
                    t.zeebeTaskType("external")
                        .zeebeInput("notExisting", "nullVal")
                        .zeebeInput("string", "existing"))
            .endEvent()
            .done());

    // when
    testClient.createWorkflowInstance(
        r -> r.setBpmnProcessId("process").setVariables(VARIABLES_MSGPACK));
    final Record<WorkflowInstanceRecordValue> failureEvent =
        testClient.receiveElementInState("service", WorkflowInstanceIntent.ELEMENT_ACTIVATING);
    final Record<IncidentRecordValue> incidentEvent =
        testClient.receiveFirstIncidentEvent(IncidentIntent.CREATED);

    // then incident is created
    assertThat(incidentEvent.getKey()).isGreaterThan(0);
    assertThat(incidentEvent.getValue().getVariableScopeKey()).isEqualTo(failureEvent.getKey());
    assertIncidentRecordValue(
        ErrorType.IO_MAPPING_ERROR.name(),
        "No data found for query notExisting.",
        "service",
        incidentEvent);
  }

  @Test
  public void shouldCreateIncidentForOutputMappingFailure() {
    // given
    final long workflowKey = testClient.deployWorkflow(WORKFLOW_OUTPUT_MAPPING).getKey();

    // when
    final long workflowInstanceKey =
        testClient.createWorkflowInstance(r -> r.setBpmnProcessId("process")).getInstanceKey();

    testClient.completeJobOfType("test", BufferUtil.bufferAsArray(VARIABLES_MSGPACK));

    // then
    final Record failureEvent =
        testClient.receiveFirstWorkflowInstanceEvent(
            WorkflowInstanceIntent.ELEMENT_COMPLETING, BpmnElementType.SERVICE_TASK);
    final Record createIncidentEvent =
        testClient.receiveFirstIncidentCommand(IncidentIntent.CREATE);
    final Record<IncidentRecordValue> incidentEvent =
        testClient.receiveFirstIncidentEvent(IncidentIntent.CREATED);

    assertThat(incidentEvent.getKey()).isGreaterThan(0);
    assertThat(createIncidentEvent.getSourceRecordPosition()).isEqualTo(failureEvent.getPosition());
    assertThat(incidentEvent.getSourceRecordPosition())
        .isEqualTo(createIncidentEvent.getPosition());

    assertIOMappingIncidentWithNoData(
        workflowKey, workflowInstanceKey, failureEvent, incidentEvent);
  }

  @Test
  public void shouldResolveIncidentForInputMappingFailure() {
    // given
    final long workflowKey = testClient.deployWorkflow(WORKFLOW_INPUT_MAPPING).getKey();

    final long workflowInstanceKey =
        testClient.createWorkflowInstance(r -> r.setBpmnProcessId("process")).getInstanceKey();

    final Record<WorkflowInstanceRecordValue> failureEvent =
        testClient.receiveElementInState("failingTask", WorkflowInstanceIntent.ELEMENT_ACTIVATING);
    final Record<IncidentRecordValue> incidentEvent =
        testClient.receiveFirstIncidentEvent(IncidentIntent.CREATED);

    // when
    testClient.updateVariables(failureEvent.getValue().getFlowScopeKey(), VARIABLES);
    testClient.resolveIncident(incidentEvent.getKey());

    // then
    final Record<WorkflowInstanceRecordValue> followUpEvent =
        testClient.receiveElementInState("failingTask", WorkflowInstanceIntent.ELEMENT_ACTIVATED);

    final Record incidentResolveCommand = testClient.receiveFirstIncidentCommand(RESOLVE);
    final Record incidentResolvedEvent = testClient.receiveFirstIncidentEvent(RESOLVED);
    assertThat(incidentResolvedEvent.getKey()).isEqualTo(incidentEvent.getKey());
    assertThat(incidentResolveCommand.getPosition())
        .isEqualTo(followUpEvent.getSourceRecordPosition());
    assertThat(incidentResolveCommand.getPosition())
        .isEqualTo(incidentResolvedEvent.getSourceRecordPosition());

    assertIOMappingIncidentWithNoData(
        workflowKey, workflowInstanceKey, followUpEvent, incidentResolvedEvent);
  }

  @Test
  public void shouldResolveIncidentForOutputMappingFailure() {
    // given
    final long workflowKey = testClient.deployWorkflow(WORKFLOW_OUTPUT_MAPPING).getKey();

    final long workflowInstanceKey =
        testClient.createWorkflowInstance(r -> r.setBpmnProcessId("process")).getInstanceKey();

    testClient.completeJobOfType("test", BufferUtil.bufferAsArray(VARIABLES_MSGPACK));

    final Record failureEvent =
        testClient.receiveFirstWorkflowInstanceEvent(
            WorkflowInstanceIntent.ELEMENT_COMPLETING, BpmnElementType.SERVICE_TASK);
    final Record incidentEvent = testClient.receiveFirstIncidentEvent(IncidentIntent.CREATED);

    // when
    testClient.updateVariables(failureEvent.getKey(), VARIABLES);
    testClient.resolveIncident(incidentEvent.getKey());

    // then
    final Record<WorkflowInstanceRecordValue> followUpEvent =
        testClient.receiveFirstWorkflowInstanceEvent(
            ELEMENT_COMPLETED, BpmnElementType.SERVICE_TASK);

    final Record incidentResolveCommand = testClient.receiveFirstIncidentCommand(RESOLVE);
    final Record incidentResolvedEvent = testClient.receiveFirstIncidentEvent(RESOLVED);
    assertThat(incidentResolvedEvent.getKey()).isEqualTo(incidentEvent.getKey());
    assertThat(incidentResolveCommand.getPosition())
        .isEqualTo(followUpEvent.getSourceRecordPosition());
    assertThat(incidentResolveCommand.getPosition())
        .isEqualTo(incidentResolvedEvent.getSourceRecordPosition());

    assertIOMappingIncidentWithNoData(
        workflowKey, workflowInstanceKey, followUpEvent, incidentResolvedEvent);
  }

  @Test
  public void shouldCreateNewIncidentAfterResolvedFirstOne() {
    // given
    final BpmnModelInstance modelInstance =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask(
                "failingTask",
                t -> t.zeebeTaskType("external").zeebeInput("foo", "foo").zeebeInput("bar", "bar"))
            .done();

    final long workflowKey = testClient.deployWorkflow(modelInstance).getKey();

    final long workflowInstanceKey =
        testClient.createWorkflowInstance(r -> r.setBpmnProcessId("process")).getInstanceKey();

    final Record failureEvent =
        testClient.receiveElementInState("failingTask", WorkflowInstanceIntent.ELEMENT_ACTIVATING);
    final Record<IncidentRecordValue> incidentEvent =
        testClient.receiveFirstIncidentEvent(IncidentIntent.CREATED);

    Assertions.assertThat(incidentEvent.getValue()).hasErrorMessage("No data found for query foo.");

    // when
    testClient.updateVariables(failureEvent.getKey(), VARIABLES);
    testClient.resolveIncident(incidentEvent.getKey());

    // then
    final Record<IncidentRecordValue> resolveFailedEvent =
        testClient.receiveFirstIncidentEvent(IncidentIntent.RESOLVED);
    assertThat(resolveFailedEvent.getKey()).isEqualTo(incidentEvent.getKey());

    testClient
        .receiveIncidents()
        .skipUntil(e -> e.getMetadata().getIntent() == RESOLVED)
        .withIntent(IncidentIntent.CREATED)
        .getFirst();

    assertIncidentRecordValue(
        ErrorType.IO_MAPPING_ERROR.name(),
        "No data found for query foo.",
        workflowKey,
        workflowInstanceKey,
        "failingTask",
        failureEvent,
        incidentEvent);
  }

  @Test
  public void shouldResolveIncidentAfterPreviousResolvingFailed() {
    // given
    final long workflowKey = testClient.deployWorkflow(WORKFLOW_INPUT_MAPPING).getKey();

    final long workflowInstanceKey =
        testClient.createWorkflowInstance(r -> r.setBpmnProcessId("process")).getInstanceKey();

    final Record failureEvent =
        testClient.receiveElementInState("failingTask", WorkflowInstanceIntent.ELEMENT_ACTIVATING);
    final Record firstIncident = testClient.receiveFirstIncidentEvent(IncidentIntent.CREATED);
    testClient.updateVariables(failureEvent.getKey(), new HashMap<>());
    testClient.resolveIncident(firstIncident.getKey());
    testClient.receiveFirstIncidentEvent(RESOLVED);
    final Record<IncidentRecordValue> secondIncident =
        testClient
            .receiveIncidents()
            .skipUntil(e -> e.getMetadata().getIntent() == RESOLVED)
            .withIntent(IncidentIntent.CREATED)
            .getFirst();

    // when
    testClient.updateVariables(failureEvent.getKey(), VARIABLES);
    testClient.resolveIncident(secondIncident.getKey());

    // then
    final Record secondResolvedIncident =
        testClient
            .receiveIncidents()
            .skipUntil(e -> e.getMetadata().getIntent() == RESOLVED)
            .skipUntil(e -> e.getMetadata().getIntent() == CREATED)
            .withIntent(RESOLVED)
            .getFirst();

    assertThat(secondResolvedIncident.getKey()).isGreaterThan(firstIncident.getKey());
    assertIncidentRecordValue(
        ErrorType.IO_MAPPING_ERROR.name(),
        "No data found for query foo.",
        workflowKey,
        workflowInstanceKey,
        "failingTask",
        failureEvent,
        firstIncident);
  }

  @Test
  public void shouldResolveMultipleIncidents() {
    // given
    testClient.deploy(WORKFLOW_INPUT_MAPPING);

    // create and resolve an first incident
    testClient.createWorkflowInstance(r -> r.setBpmnProcessId("process"));
    Record failureEvent =
        testClient.receiveElementInState("failingTask", WorkflowInstanceIntent.ELEMENT_ACTIVATING);
    final Record<IncidentRecordValue> firstIncident = testClient.receiveFirstIncidentEvent(CREATED);
    testClient.updateVariables(failureEvent.getKey(), VARIABLES);
    testClient.resolveIncident(firstIncident.getKey());

    // create a second incident
    final long workflowInstanceKey =
        testClient.createWorkflowInstance(r -> r.setBpmnProcessId("process")).getInstanceKey();
    failureEvent =
        testClient.receiveFirstWorkflowInstanceEvent(
            workflowInstanceKey, "failingTask", WorkflowInstanceIntent.ELEMENT_ACTIVATING);
    final Record secondIncidentEvent =
        testClient.receiveFirstIncidentEvent(workflowInstanceKey, IncidentIntent.CREATED);

    // when
    testClient.updateVariables(failureEvent.getKey(), VARIABLES);
    testClient.resolveIncident(secondIncidentEvent.getKey());

    // then
    final Record incidentResolvedEvent =
        testClient.receiveFirstIncidentEvent(workflowInstanceKey, RESOLVED);
    assertThat(incidentResolvedEvent.getKey()).isEqualTo(secondIncidentEvent.getKey());
  }

  @Test
  public void shouldResolveIncidentIfActivityTerminated() {
    // given
    final long workflowKey = testClient.deployWorkflow(WORKFLOW_INPUT_MAPPING).getKey();

    final long workflowInstanceKey =
        testClient.createWorkflowInstance(r -> r.setBpmnProcessId("process")).getInstanceKey();

    final Record incidentCreatedEvent =
        testClient.receiveFirstIncidentEvent(IncidentIntent.CREATED);

    // when
    testClient.cancelWorkflowInstance(workflowInstanceKey);

    // then
    final Record activityTerminating =
        testClient.receiveFirstWorkflowInstanceEvent(
            workflowInstanceKey, "failingTask", WorkflowInstanceIntent.ELEMENT_TERMINATED);
    final Record<IncidentRecordValue> incidentResolvedEvent =
        testClient.receiveFirstIncidentEvent(RESOLVED);

    assertThat(incidentResolvedEvent.getKey()).isEqualTo(incidentCreatedEvent.getKey());
    assertThat(activityTerminating.getPosition())
        .isEqualTo(incidentResolvedEvent.getSourceRecordPosition());

    assertIncidentRecordValue(
        ErrorType.IO_MAPPING_ERROR.name(),
        "No data found for query foo.",
        workflowKey,
        workflowInstanceKey,
        "failingTask",
        incidentResolvedEvent.getValue().getElementInstanceKey(),
        incidentResolvedEvent);
  }

  @Test
  @Category(UnstableTest.class)
  public void shouldProcessIncidentsAfterMultipleTerminations() {
    // given
    final long workflowKey = testClient.deployWorkflow(WORKFLOW_INPUT_MAPPING).getKey();

    // create and cancel instance with incident
    long workflowInstanceKey =
        testClient.createWorkflowInstance(r -> r.setBpmnProcessId("process")).getInstanceKey();
    testClient.cancelWorkflowInstance(workflowInstanceKey);

    // create and cancel instance without incident
    workflowInstanceKey =
        testClient
            .createWorkflowInstance(
                r -> r.setBpmnProcessId("process").setVariables(MsgPackUtil.asMsgPack(VARIABLES)))
            .getInstanceKey();
    testClient.cancelWorkflowInstance(workflowInstanceKey);

    // create another instance which creates an incident
    workflowInstanceKey =
        testClient.createWorkflowInstance(r -> r.setBpmnProcessId("process")).getInstanceKey();
    final Record incidentCreatedEvent =
        testClient.receiveFirstIncidentEvent(workflowInstanceKey, IncidentIntent.CREATED);

    // when
    testClient.cancelWorkflowInstance(workflowInstanceKey);

    // then
    final Record<IncidentRecordValue> incidentEvent =
        testClient.receiveFirstIncidentEvent(workflowInstanceKey, RESOLVED);

    assertThat(incidentEvent.getKey()).isEqualTo(incidentCreatedEvent.getKey());
    assertIncidentRecordValue(
        ErrorType.IO_MAPPING_ERROR.name(),
        "No data found for query foo.",
        workflowKey,
        workflowInstanceKey,
        "failingTask",
        incidentEvent.getValue().getElementInstanceKey(),
        incidentEvent);
  }
}
