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

import static io.zeebe.broker.incident.IncidentAssert.*;
import static io.zeebe.broker.test.MsgPackConstants.MSGPACK_PAYLOAD;
import static io.zeebe.broker.test.MsgPackConstants.NODE_STRING_PATH;
import static io.zeebe.broker.workflow.WorkflowAssert.assertWorkflowInstancePayload;
import static io.zeebe.protocol.intent.IncidentIntent.*;
import static io.zeebe.protocol.intent.WorkflowInstanceIntent.ELEMENT_COMPLETED;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.UnstableTest;
import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.exporter.record.Assertions;
import io.zeebe.exporter.record.Record;
import io.zeebe.exporter.record.value.IncidentRecordValue;
import io.zeebe.exporter.record.value.WorkflowInstanceRecordValue;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.model.bpmn.instance.zeebe.ZeebeOutputBehavior;
import io.zeebe.msgpack.spec.MsgPackHelper;
import io.zeebe.protocol.impl.record.value.incident.ErrorType;
import io.zeebe.protocol.intent.IncidentIntent;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.test.broker.protocol.clientapi.ClientApiRule;
import io.zeebe.test.broker.protocol.clientapi.PartitionTestClient;
import io.zeebe.test.util.MsgPackUtil;
import org.agrona.DirectBuffer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.RuleChain;

public class MappingIncidentTest {

  public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();
  public ClientApiRule apiRule = new ClientApiRule(brokerRule::getClientAddress);

  @Rule public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(apiRule);

  private PartitionTestClient testClient;

  private static final BpmnModelInstance WORKFLOW_INPUT_MAPPING =
      Bpmn.createExecutableProcess("process")
          .startEvent()
          .serviceTask("failingTask", t -> t.zeebeTaskType("test").zeebeInput("$.foo", "$.foo"))
          .done();

  private static final BpmnModelInstance WORKFLOW_OUTPUT_MAPPING =
      Bpmn.createExecutableProcess("process")
          .startEvent()
          .serviceTask("failingTask", t -> t.zeebeTaskType("test").zeebeOutput("$.foo", "$.foo"))
          .done();

  private static final byte[] PAYLOAD;

  static {
    final DirectBuffer buffer =
        MsgPackUtil.encodeMsgPack(
            p -> {
              p.packMapHeader(1);
              p.packString("foo");
              p.packString("bar");
            });
    PAYLOAD = new byte[buffer.capacity()];
    buffer.getBytes(0, PAYLOAD);
  }

  @Before
  public void init() {
    testClient = apiRule.partitionClient();
    apiRule.waitForPartition(1);
  }

  @Test
  public void shouldCreateIncidentForInputMappingFailure() {
    // given
    testClient.deploy(WORKFLOW_INPUT_MAPPING);

    // when
    final long workflowInstanceKey = testClient.createWorkflowInstance("process");

    // then
    final Record failureEvent =
        testClient.receiveElementInState("failingTask", WorkflowInstanceIntent.ELEMENT_READY);
    final Record createIncidentEvent =
        testClient.receiveFirstIncidentCommand(IncidentIntent.CREATE);
    final Record<IncidentRecordValue> incidentEvent =
        testClient.receiveFirstIncidentEvent(IncidentIntent.CREATED);

    assertThat(incidentEvent.getKey()).isGreaterThan(0);
    assertThat(createIncidentEvent.getSourceRecordPosition()).isEqualTo(failureEvent.getPosition());
    assertThat(incidentEvent.getSourceRecordPosition())
        .isEqualTo(createIncidentEvent.getPosition());

    assertIOMappingIncidentWithNoData(workflowInstanceKey, failureEvent, incidentEvent);
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
                        .zeebeInput("$.notExisting", "$.nullVal")
                        .zeebeInput(NODE_STRING_PATH, "$.existing"))
            .endEvent()
            .done());

    // when
    testClient.createWorkflowInstance("process", MSGPACK_PAYLOAD);
    final Record incidentEvent = testClient.receiveFirstIncidentEvent(IncidentIntent.CREATED);

    // then incident is created
    assertThat(incidentEvent.getKey()).isGreaterThan(0);
    assertIncidentRecordValue(
        ErrorType.IO_MAPPING_ERROR.name(),
        "No data found for query $.notExisting.",
        "service",
        incidentEvent);
  }

  @Test
  public void shouldCreateIncidentForOutputMappingFailure() {
    // given
    testClient.deploy(WORKFLOW_OUTPUT_MAPPING);

    // when
    final long workflowInstanceKey = testClient.createWorkflowInstance("process");

    testClient.completeJobOfType("test", MSGPACK_PAYLOAD);

    // then
    final Record failureEvent =
        testClient.receiveFirstWorkflowInstanceEvent(WorkflowInstanceIntent.ELEMENT_COMPLETING);
    final Record createIncidentEvent =
        testClient.receiveFirstIncidentCommand(IncidentIntent.CREATE);
    final Record<IncidentRecordValue> incidentEvent =
        testClient.receiveFirstIncidentEvent(IncidentIntent.CREATED);

    assertThat(incidentEvent.getKey()).isGreaterThan(0);
    assertThat(createIncidentEvent.getSourceRecordPosition()).isEqualTo(failureEvent.getPosition());
    assertThat(incidentEvent.getSourceRecordPosition())
        .isEqualTo(createIncidentEvent.getPosition());

    assertIOMappingIncidentWithNoData(workflowInstanceKey, failureEvent, incidentEvent);
  }

  @Test
  public void shouldCreateIncidentWithOverwriteOutputBehaviorWithoutCompletedPayload() {
    // given
    testClient.deploy(
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask(
                "service",
                t ->
                    t.zeebeTaskType("external")
                        .zeebeOutputBehavior(ZeebeOutputBehavior.overwrite)
                        .zeebeOutput("$.string", "$.foo"))
            .endEvent()
            .done());

    testClient.createWorkflowInstance("process", MSGPACK_PAYLOAD);

    // when
    testClient.completeJobOfType("external");

    // then incident is created
    final Record incidentEvent = testClient.receiveFirstIncidentEvent(IncidentIntent.CREATED);

    assertThat(incidentEvent.getKey()).isGreaterThan(0);
    assertIncidentRecordValue(
        ErrorType.IO_MAPPING_ERROR.name(),
        "No data found for query $.string.",
        "service",
        incidentEvent);
  }

  @Test
  public void shouldResolveIncidentForInputMappingFailure() {
    // given
    testClient.deploy(WORKFLOW_INPUT_MAPPING);

    final long workflowInstanceKey = testClient.createWorkflowInstance("process");

    final Record<WorkflowInstanceRecordValue> failureEvent =
        testClient.receiveElementInState("failingTask", WorkflowInstanceIntent.ELEMENT_READY);
    final Record<IncidentRecordValue> incidentEvent =
        testClient.receiveFirstIncidentEvent(IncidentIntent.CREATED);

    // when
    testClient.updatePayload(failureEvent.getKey(), PAYLOAD);
    testClient.resolveIncident(incidentEvent.getKey());

    // then
    final Record<WorkflowInstanceRecordValue> followUpEvent =
        testClient.receiveElementInState("failingTask", WorkflowInstanceIntent.ELEMENT_ACTIVATED);
    assertWorkflowInstancePayload(followUpEvent, "{'foo':'bar'}");

    final Record incidentResolveCommand = testClient.receiveFirstIncidentCommand(RESOLVE);
    final Record incidentResolvedEvent = testClient.receiveFirstIncidentEvent(RESOLVED);
    assertThat(incidentResolvedEvent.getKey()).isEqualTo(incidentEvent.getKey());
    assertThat(incidentResolveCommand.getPosition())
        .isEqualTo(followUpEvent.getSourceRecordPosition());
    assertThat(incidentResolveCommand.getPosition())
        .isEqualTo(incidentResolvedEvent.getSourceRecordPosition());

    assertIOMappingIncidentWithNoData(workflowInstanceKey, followUpEvent, incidentResolvedEvent);
  }

  @Test
  public void shouldResolveIncidentForOutputMappingFailure() {
    // given
    testClient.deploy(WORKFLOW_OUTPUT_MAPPING);

    final long workflowInstanceKey = testClient.createWorkflowInstance("process");

    testClient.completeJobOfType("test", MSGPACK_PAYLOAD);

    final Record failureEvent =
        testClient.receiveFirstWorkflowInstanceEvent(WorkflowInstanceIntent.ELEMENT_COMPLETING);
    final Record incidentEvent = testClient.receiveFirstIncidentEvent(IncidentIntent.CREATED);

    // when
    testClient.updatePayload(failureEvent.getKey(), PAYLOAD);
    testClient.resolveIncident(incidentEvent.getKey());

    // then
    final Record<WorkflowInstanceRecordValue> followUpEvent =
        testClient.receiveFirstWorkflowInstanceEvent(ELEMENT_COMPLETED);
    assertWorkflowInstancePayload(followUpEvent, "{'foo':'bar'}");

    final Record incidentResolveCommand = testClient.receiveFirstIncidentCommand(RESOLVE);
    final Record incidentResolvedEvent = testClient.receiveFirstIncidentEvent(RESOLVED);
    assertThat(incidentResolvedEvent.getKey()).isEqualTo(incidentEvent.getKey());
    assertThat(incidentResolveCommand.getPosition())
        .isEqualTo(followUpEvent.getSourceRecordPosition());
    assertThat(incidentResolveCommand.getPosition())
        .isEqualTo(incidentResolvedEvent.getSourceRecordPosition());

    assertIOMappingIncidentWithNoData(workflowInstanceKey, followUpEvent, incidentResolvedEvent);
  }

  @Test
  public void shouldCreateIncidentForInvalidResultOnInputMapping() {
    // given
    testClient.deploy(
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask(
                "failingTask", t -> t.zeebeTaskType("external").zeebeInput("$.string", "$"))
            .done());

    // when
    testClient.createWorkflowInstance("process", MSGPACK_PAYLOAD);

    // then incident is created
    final Record<IncidentRecordValue> incidentEvent =
        testClient.receiveFirstIncidentEvent(IncidentIntent.CREATED);

    assertThat(incidentEvent.getKey()).isGreaterThan(0);
    assertIncidentContainErrorDetails(incidentEvent);
  }

  @Test
  public void shouldResolveIncidentForInvalidResultOnInputMapping() {
    // given
    testClient.deploy(
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask("service", t -> t.zeebeTaskType("external").zeebeInput("$.string", "$"))
            .done());

    // when
    final long workflowInstanceKey = testClient.createWorkflowInstance("process", MSGPACK_PAYLOAD);

    // then incident is created
    final Record failureEvent =
        testClient.receiveElementInState("service", WorkflowInstanceIntent.ELEMENT_READY);
    final Record incidentEvent = testClient.receiveFirstIncidentEvent(IncidentIntent.CREATED);

    // when
    testClient.updatePayload(failureEvent.getKey(), "{'string':{'obj':'test'}}");
    testClient.resolveIncident(incidentEvent.getKey());

    // then
    final Record<WorkflowInstanceRecordValue> followUpEvent =
        testClient.receiveElementInState("service", WorkflowInstanceIntent.ELEMENT_ACTIVATED);
    assertWorkflowInstancePayload(followUpEvent, "{'obj':'test'}");

    final Record<IncidentRecordValue> incidentResolvedEvent =
        testClient.receiveFirstIncidentEvent(RESOLVED);
    assertThat(incidentResolvedEvent.getKey()).isEqualTo(incidentEvent.getKey());
    assertIncidentRecordValue(
        ErrorType.IO_MAPPING_ERROR.name(),
        "Processing failed, since mapping will result in a non map object (json object).",
        workflowInstanceKey,
        "service",
        followUpEvent,
        incidentResolvedEvent);
  }

  @Test
  public void shouldCreateIncidentForInvalidResultOnOutputMapping() {
    // given
    testClient.deploy(
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask(
                "failingTask",
                t ->
                    t.zeebeTaskType("external")
                        .zeebeInput("$.jsonObject", "$")
                        .zeebeOutput("$.testAttr", "$"))
            .done());

    testClient.createWorkflowInstance("process", MSGPACK_PAYLOAD);

    // when
    testClient.completeJobOfType(
        "external", MsgPackUtil.asMsgPackReturnArray("{'testAttr':'test'}"));
    testClient.receiveFirstWorkflowInstanceEvent(WorkflowInstanceIntent.ELEMENT_ACTIVATED);

    // then incident is created
    final Record incidentEvent = testClient.receiveFirstIncidentEvent(IncidentIntent.CREATED);

    assertThat(incidentEvent.getKey()).isGreaterThan(0);
    assertIncidentContainErrorDetails(incidentEvent);
  }

  @Test
  public void shouldResolveIncidentForInvalidResultOnOutputMapping() {
    // given
    testClient.deploy(
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask(
                "service",
                t ->
                    t.zeebeTaskType("external")
                        .zeebeInput("$.jsonObject", "$")
                        .zeebeOutput("$.testAttr", "$"))
            .done());

    final long workflowInstanceKey = testClient.createWorkflowInstance("process", MSGPACK_PAYLOAD);

    // when
    testClient.completeJobOfType(
        "external", MsgPackUtil.asMsgPackReturnArray("{'testAttr':'test'}"));
    testClient.receiveFirstWorkflowInstanceEvent(WorkflowInstanceIntent.ELEMENT_ACTIVATED);

    // then incident is created
    final Record failureEvent =
        testClient.receiveFirstWorkflowInstanceEvent(WorkflowInstanceIntent.ELEMENT_COMPLETING);
    final Record incidentEvent = testClient.receiveFirstIncidentEvent(IncidentIntent.CREATED);

    // when
    testClient.updatePayload(failureEvent.getKey(), "{'testAttr':{'obj':'test'}}");
    testClient.resolveIncident(incidentEvent.getKey());

    // then
    final Record<WorkflowInstanceRecordValue> followUpEvent =
        testClient.receiveFirstWorkflowInstanceEvent(WorkflowInstanceIntent.ELEMENT_COMPLETED);
    assertWorkflowInstancePayload(followUpEvent, "{'obj':'test'}");

    final Record incidentResolvedEvent = testClient.receiveFirstIncidentEvent(RESOLVED);
    assertThat(incidentResolvedEvent.getKey()).isEqualTo(incidentEvent.getKey());
    assertIncidentRecordValue(
        ErrorType.IO_MAPPING_ERROR.name(),
        "Processing failed, since mapping will result in a non map object (json object).",
        workflowInstanceKey,
        "service",
        followUpEvent,
        incidentResolvedEvent);
  }

  @Test
  public void shouldCreateIncidentForInAndOutputMappingAndNoTaskCompletePayload() {
    // given
    testClient.deploy(
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask(
                "failingTask",
                t ->
                    t.zeebeTaskType("external")
                        .zeebeInput("$.jsonObject", "$")
                        .zeebeOutput("$.testAttr", "$"))
            .done());

    testClient.createWorkflowInstance("process", MSGPACK_PAYLOAD);

    // when
    testClient.completeJobOfType("external");

    // then incident is created
    final Record incidentEvent = testClient.receiveFirstIncidentEvent(IncidentIntent.CREATED);

    assertThat(incidentEvent.getKey()).isGreaterThan(0);
    assertIncidentContainErrorDetails(incidentEvent, "No data found for query $.testAttr.");
  }

  @Test
  public void shouldResolveIncidentForInAndOutputMappingAndNoTaskCompletePayload() {
    // given
    testClient.deploy(
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask(
                "service",
                t ->
                    t.zeebeTaskType("external")
                        .zeebeInput("$.jsonObject", "$")
                        .zeebeOutput("$.foo", "$"))
            .done());

    final long workflowInstanceKey = testClient.createWorkflowInstance("process", MSGPACK_PAYLOAD);

    // when
    testClient.completeJobOfType("external");

    // then incident is created
    final Record failureEvent =
        testClient.receiveFirstWorkflowInstanceEvent(WorkflowInstanceIntent.ELEMENT_COMPLETING);
    final Record incidentEvent = testClient.receiveFirstIncidentEvent(IncidentIntent.CREATED);

    // when
    testClient.updatePayload(failureEvent.getKey(), "{'foo':{'obj':'test'}}");
    testClient.resolveIncident(incidentEvent.getKey());

    // then
    final Record<WorkflowInstanceRecordValue> followUpEvent =
        testClient.receiveFirstWorkflowInstanceEvent(ELEMENT_COMPLETED);
    assertWorkflowInstancePayload(followUpEvent, "{'obj':'test'}");

    final Record incidentResolvedEvent = testClient.receiveFirstIncidentEvent(RESOLVED);
    assertThat(incidentResolvedEvent.getKey()).isEqualTo(incidentEvent.getKey());

    assertIOMappingIncidentWithNoData(
        workflowInstanceKey, "service", followUpEvent, incidentResolvedEvent);
  }

  @Test
  public void shouldCreateIncidentForOutputMappingAndNoTaskCompletePayload() {
    // given
    testClient.deploy(
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask(
                "failingTask", t -> t.zeebeTaskType("external").zeebeOutput("$.testAttr", "$"))
            .done());

    testClient.createWorkflowInstance("process", MSGPACK_PAYLOAD);

    // when
    testClient.completeJobOfType("external");

    // then incident is created
    final Record incidentEvent = testClient.receiveFirstIncidentEvent(IncidentIntent.CREATED);
    assertThat(incidentEvent.getKey()).isGreaterThan(0);
    assertIncidentContainErrorDetails(incidentEvent, "No data found for query $.testAttr.");
  }

  @Test
  public void shouldResolveIncidentForOutputMappingAndNoTaskCompletePayload() {
    // given
    testClient.deploy(
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask("service", t -> t.zeebeTaskType("external").zeebeOutput("$.testAttr", "$"))
            .done());

    final long workflowInstanceKey = testClient.createWorkflowInstance("process", MSGPACK_PAYLOAD);

    // when
    testClient.completeJobOfType("external");

    // then incident is created
    final Record failureEvent =
        testClient.receiveFirstWorkflowInstanceEvent(WorkflowInstanceIntent.ELEMENT_COMPLETING);
    final Record incidentEvent = testClient.receiveFirstIncidentEvent(IncidentIntent.CREATED);

    // when
    testClient.updatePayload(failureEvent.getKey(), "{'testAttr':{'obj':'test'}}");
    testClient.resolveIncident(incidentEvent.getKey());

    // then
    final Record<WorkflowInstanceRecordValue> followUpEvent =
        testClient.receiveFirstWorkflowInstanceEvent(ELEMENT_COMPLETED);
    assertWorkflowInstancePayload(followUpEvent, "{'obj':'test'}");

    final Record incidentResolvedEvent = testClient.receiveFirstIncidentEvent(RESOLVED);
    assertThat(incidentResolvedEvent.getKey()).isEqualTo(incidentEvent.getKey());

    assertIncidentRecordValue(
        ErrorType.IO_MAPPING_ERROR.name(),
        "No data found for query $.testAttr.",
        workflowInstanceKey,
        "service",
        followUpEvent,
        incidentResolvedEvent);
  }

  @Test
  public void shouldCreateNewIncidentAfterResolvedFirstOne() {
    // given
    final BpmnModelInstance modelInstance =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask(
                "failingTask",
                t ->
                    t.zeebeTaskType("external")
                        .zeebeInput("$.foo", "$.foo")
                        .zeebeInput("$.bar", "$.bar"))
            .done();

    testClient.deploy(modelInstance);

    final long workflowInstanceKey = testClient.createWorkflowInstance("process");

    final Record failureEvent =
        testClient.receiveElementInState("failingTask", WorkflowInstanceIntent.ELEMENT_READY);
    final Record<IncidentRecordValue> incidentEvent =
        testClient.receiveFirstIncidentEvent(IncidentIntent.CREATED);

    Assertions.assertThat(incidentEvent.getValue())
        .hasErrorMessage("No data found for query $.foo.");

    // when
    testClient.updatePayload(failureEvent.getKey(), PAYLOAD);
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
        "No data found for query $.foo.",
        workflowInstanceKey,
        "failingTask",
        failureEvent,
        incidentEvent);
  }

  @Test
  public void shouldResolveIncidentAfterPreviousResolvingFailed() {
    // given
    testClient.deploy(WORKFLOW_INPUT_MAPPING);

    final long workflowInstanceKey = testClient.createWorkflowInstance("process");

    final Record failureEvent =
        testClient.receiveElementInState("failingTask", WorkflowInstanceIntent.ELEMENT_READY);
    final Record firstIncident = testClient.receiveFirstIncidentEvent(IncidentIntent.CREATED);
    testClient.updatePayload(failureEvent.getKey(), MsgPackHelper.EMTPY_OBJECT);
    testClient.resolveIncident(firstIncident.getKey());
    testClient.receiveFirstIncidentEvent(RESOLVED);
    final Record<IncidentRecordValue> secondIncident =
        testClient
            .receiveIncidents()
            .skipUntil(e -> e.getMetadata().getIntent() == RESOLVED)
            .withIntent(IncidentIntent.CREATED)
            .getFirst();

    // when
    testClient.updatePayload(failureEvent.getKey(), PAYLOAD);
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
        "No data found for query $.foo.",
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
    long workflowInstanceKey = testClient.createWorkflowInstance("process");
    Record failureEvent =
        testClient.receiveElementInState("failingTask", WorkflowInstanceIntent.ELEMENT_READY);
    final Record<IncidentRecordValue> firstIncident = testClient.receiveFirstIncidentEvent(CREATED);
    testClient.updatePayload(failureEvent.getKey(), PAYLOAD);
    testClient.resolveIncident(firstIncident.getKey());

    // create a second incident
    workflowInstanceKey = testClient.createWorkflowInstance("process");
    failureEvent =
        testClient.receiveFirstWorkflowInstanceEvent(
            workflowInstanceKey, "failingTask", WorkflowInstanceIntent.ELEMENT_READY);
    final Record secondIncidentEvent =
        testClient.receiveFirstIncidentEvent(workflowInstanceKey, IncidentIntent.CREATED);

    // when
    testClient.updatePayload(failureEvent.getKey(), PAYLOAD);
    testClient.resolveIncident(secondIncidentEvent.getKey());

    // then
    final Record incidentResolvedEvent =
        testClient.receiveFirstIncidentEvent(workflowInstanceKey, RESOLVED);
    assertThat(incidentResolvedEvent.getKey()).isEqualTo(secondIncidentEvent.getKey());
  }

  @Test
  public void shouldResolveIncidentIfActivityTerminated() {
    // given
    testClient.deploy(WORKFLOW_INPUT_MAPPING);

    final long workflowInstanceKey = testClient.createWorkflowInstance("process");

    final Record incidentCreatedEvent =
        testClient.receiveFirstIncidentEvent(IncidentIntent.CREATED);

    // when
    testClient.cancelWorkflowInstance(workflowInstanceKey);

    // then
    final Record activityTerminating =
        testClient.receiveFirstWorkflowInstanceEvent(
            workflowInstanceKey, "failingTask", WorkflowInstanceIntent.ELEMENT_TERMINATING);
    final Record<IncidentRecordValue> incidentResolvedEvent =
        testClient.receiveFirstIncidentEvent(RESOLVED);

    assertThat(incidentResolvedEvent.getKey()).isEqualTo(incidentCreatedEvent.getKey());
    assertThat(activityTerminating.getPosition())
        .isEqualTo(incidentResolvedEvent.getSourceRecordPosition());

    assertIncidentRecordValue(
        ErrorType.IO_MAPPING_ERROR.name(),
        "No data found for query $.foo.",
        workflowInstanceKey,
        "failingTask",
        incidentResolvedEvent.getValue().getElementInstanceKey(),
        incidentResolvedEvent);
  }

  @Test
  @Category(UnstableTest.class)
  public void shouldProcessIncidentsAfterMultipleTerminations() {
    // given
    testClient.deploy(WORKFLOW_INPUT_MAPPING);

    // create and cancel instance with incident
    long workflowInstanceKey = testClient.createWorkflowInstance("process");
    testClient.cancelWorkflowInstance(workflowInstanceKey);

    // create and cancel instance without incident
    workflowInstanceKey = testClient.createWorkflowInstance("process", PAYLOAD);
    testClient.cancelWorkflowInstance(workflowInstanceKey);

    // create another instance which creates an incident
    workflowInstanceKey = testClient.createWorkflowInstance("process");
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
        "No data found for query $.foo.",
        workflowInstanceKey,
        "failingTask",
        incidentEvent.getValue().getElementInstanceKey(),
        incidentEvent);
  }
}
