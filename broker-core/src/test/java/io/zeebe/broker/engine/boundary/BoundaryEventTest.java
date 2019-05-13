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
package io.zeebe.broker.engine.boundary;

import static io.zeebe.test.util.MsgPackUtil.asMsgPack;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.exporter.api.record.Assertions;
import io.zeebe.exporter.api.record.Record;
import io.zeebe.exporter.api.record.value.IncidentRecordValue;
import io.zeebe.exporter.api.record.value.JobRecordValue;
import io.zeebe.exporter.api.record.value.TimerRecordValue;
import io.zeebe.exporter.api.record.value.VariableRecordValue;
import io.zeebe.exporter.api.record.value.WorkflowInstanceRecordValue;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.BpmnElementType;
import io.zeebe.protocol.ErrorType;
import io.zeebe.protocol.intent.IncidentIntent;
import io.zeebe.protocol.intent.JobIntent;
import io.zeebe.protocol.intent.TimerIntent;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.protocol.intent.WorkflowInstanceSubscriptionIntent;
import io.zeebe.test.broker.protocol.clientapi.ClientApiRule;
import io.zeebe.test.broker.protocol.clientapi.PartitionTestClient;
import io.zeebe.test.util.MsgPackUtil;
import io.zeebe.test.util.record.RecordingExporter;
import io.zeebe.test.util.record.WorkflowInstances;
import java.time.Duration;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class BoundaryEventTest {
  private static final String PROCESS_ID = "process";

  private static final BpmnModelInstance MULTIPLE_SEQUENCE_FLOWS =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent()
          .serviceTask("task", b -> b.zeebeTaskType("type"))
          .boundaryEvent("timer")
          .cancelActivity(true)
          .timerWithDuration("PT0.1S")
          .endEvent("end1")
          .moveToNode("timer")
          .endEvent("end2")
          .moveToActivity("task")
          .endEvent()
          .done();

  private static final BpmnModelInstance NON_INTERRUPTING_WORKFLOW =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent()
          .serviceTask("task", b -> b.zeebeTaskType("type"))
          .boundaryEvent("event")
          .cancelActivity(false)
          .timerWithCycle("R/PT1S")
          .endEvent()
          .done();

  public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();
  public ClientApiRule apiRule = new ClientApiRule(brokerRule::getAtomix);

  @Rule public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(apiRule);

  private PartitionTestClient testClient;

  @Before
  public void init() {
    testClient = apiRule.partitionClient();
  }

  @Test
  public void shouldTakeAllOutgoingSequenceFlowsIfTriggered() {
    // given
    testClient.deploy(MULTIPLE_SEQUENCE_FLOWS);
    testClient.createWorkflowInstance(r -> r.setBpmnProcessId(PROCESS_ID));

    // when
    testClient.receiveTimerRecord("timer", TimerIntent.CREATED);
    brokerRule.getClock().addTime(Duration.ofMinutes(1));
    awaitProcessCompleted();

    // then
    assertThat(
            RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_COMPLETED)
                .withElementType(BpmnElementType.END_EVENT)
                .limit(2))
        .extracting(Record::getValue)
        .extracting(WorkflowInstanceRecordValue::getElementId)
        .contains("end1", "end2");
  }

  @Test
  public void shouldActivateBoundaryEventWhenEventTriggered() {
    // given
    testClient.deploy(MULTIPLE_SEQUENCE_FLOWS);
    testClient.createWorkflowInstance(r -> r.setBpmnProcessId(PROCESS_ID));

    // when
    testClient.receiveTimerRecord("timer", TimerIntent.CREATED);
    brokerRule.getClock().addTime(Duration.ofMinutes(1));

    final Record<TimerRecordValue> timerTriggered =
        testClient.receiveTimerRecord("timer", TimerIntent.TRIGGERED);
    final Record<WorkflowInstanceRecordValue> activityEventOccurred =
        testClient.receiveElementInState("task", WorkflowInstanceIntent.EVENT_OCCURRED);
    final Record<WorkflowInstanceRecordValue> activityTerminating =
        testClient.receiveElementInState("task", WorkflowInstanceIntent.ELEMENT_TERMINATED);
    final Record<WorkflowInstanceRecordValue> boundaryTriggering =
        testClient.receiveElementInState("timer", WorkflowInstanceIntent.ELEMENT_ACTIVATING);

    awaitProcessCompleted();

    // then
    assertRecordsPublishedInOrder(
        timerTriggered, activityEventOccurred, activityTerminating, boundaryTriggering);
  }

  @Test
  public void shouldApplyOutputMappingOnTriggering() {
    // given
    final BpmnModelInstance workflow =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .serviceTask("task", b -> b.zeebeTaskType("type"))
            .boundaryEvent("event")
            .message(m -> m.name("message").zeebeCorrelationKey("key"))
            .zeebeOutput("foo", "bar")
            .endEvent("endTimer")
            .moveToActivity("task")
            .endEvent()
            .done();
    testClient.deploy(workflow);
    testClient
        .createWorkflowInstance(
            r -> r.setBpmnProcessId(PROCESS_ID).setVariables(asMsgPack("key", "123")))
        .getInstanceKey();

    // when
    assertThat(
            testClient
                .receiveWorkflowInstanceSubscriptions()
                .withMessageName("message")
                .withIntent(WorkflowInstanceSubscriptionIntent.OPENED)
                .exists())
        .isTrue();
    testClient.publishMessage("message", "123", asMsgPack("foo", 3));
    awaitProcessCompleted();

    // then
    final Record<VariableRecordValue> variableEvent =
        RecordingExporter.variableRecords().withName("bar").getFirst();
    Assertions.assertThat(variableEvent.getValue()).hasValue("3");
  }

  @Test
  public void shouldUseScopeVariablesWhenApplyingOutputMappings() {
    // given
    final BpmnModelInstance workflow =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .serviceTask("task", b -> b.zeebeTaskType("type").zeebeInput("oof", "baz"))
            .boundaryEvent("timer")
            .cancelActivity(true)
            .timerWithDuration("PT1S")
            .endEvent("endTimer")
            .moveToActivity("task")
            .endEvent()
            .done();
    testClient.deploy(workflow);
    final long workflowInstanceKey =
        testClient
            .createWorkflowInstance(
                r ->
                    r.setBpmnProcessId(PROCESS_ID)
                        .setVariables(asMsgPack("{ \"foo\": 1, \"oof\": 2 }")))
            .getInstanceKey();

    // when
    testClient.receiveTimerRecord("timer", TimerIntent.CREATED);
    brokerRule.getClock().addTime(Duration.ofMinutes(1));
    awaitProcessCompleted();

    // then
    final Record<WorkflowInstanceRecordValue> boundaryTriggered =
        testClient.receiveElementInState("timer", WorkflowInstanceIntent.ELEMENT_COMPLETED);
    final Map<String, String> variables =
        WorkflowInstances.getCurrentVariables(workflowInstanceKey, boundaryTriggered.getPosition());
    assertThat(variables).contains(entry("foo", "1"), entry("oof", "2"));
  }

  @Test
  public void shouldTerminateSubProcessBeforeTriggeringBoundaryEvent() {
    // given
    final BpmnModelInstance workflow =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .subProcess("sub")
            .embeddedSubProcess()
            .startEvent()
            .serviceTask("task", t -> t.zeebeTaskType("type"))
            .endEvent()
            .subProcessDone()
            .boundaryEvent("timer")
            .cancelActivity(true)
            .timerWithDuration("PT1S")
            .endEvent("endTimer")
            .moveToActivity("sub")
            .endEvent()
            .done();
    testClient.deploy(workflow);
    testClient.createWorkflowInstance(r -> r.setBpmnProcessId(PROCESS_ID));

    // when
    testClient.receiveTimerRecord("timer", TimerIntent.CREATED);
    brokerRule.getClock().addTime(Duration.ofMinutes(1));

    awaitProcessCompleted();

    // then
    final Record<TimerRecordValue> timerTriggered =
        testClient.receiveTimerRecord("timer", TimerIntent.TRIGGERED);
    final Record<WorkflowInstanceRecordValue> subProcessTerminating =
        testClient.receiveElementInState("sub", WorkflowInstanceIntent.ELEMENT_TERMINATING);
    final Record<WorkflowInstanceRecordValue> subProcessTerminated =
        testClient.receiveElementInState("sub", WorkflowInstanceIntent.ELEMENT_TERMINATED);
    final Record<WorkflowInstanceRecordValue> boundaryTriggering =
        testClient.receiveElementInState("timer", WorkflowInstanceIntent.ELEMENT_ACTIVATING);
    final Record<WorkflowInstanceRecordValue> boundaryTriggered =
        testClient.receiveElementInState("timer", WorkflowInstanceIntent.ELEMENT_COMPLETED);

    assertRecordsPublishedInOrder(
        timerTriggered,
        subProcessTerminating,
        subProcessTerminated,
        boundaryTriggering,
        boundaryTriggered);
  }

  @Test
  public void shouldNotTerminateActivityForNonInterruptingBoundaryEvents() {
    // given
    testClient.deploy(NON_INTERRUPTING_WORKFLOW);
    brokerRule.getClock().pinCurrentTime();
    testClient.createWorkflowInstance(r -> r.setBpmnProcessId(PROCESS_ID));

    // when
    testClient.receiveTimerRecord("event", TimerIntent.CREATED);
    brokerRule.getClock().addTime(Duration.ofSeconds(1));
    testClient.completeJobOfType("type");
    awaitProcessCompleted();

    // then
    final Record<TimerRecordValue> timerTriggered =
        testClient.receiveTimerRecord("event", TimerIntent.TRIGGERED);
    final Record<JobRecordValue> jobCompleted =
        testClient.receiveFirstJobEvent(JobIntent.COMPLETED);
    final Record<WorkflowInstanceRecordValue> activityCompleted =
        testClient.receiveElementInState("task", WorkflowInstanceIntent.ELEMENT_COMPLETED);

    assertRecordsPublishedInOrder(timerTriggered, jobCompleted, activityCompleted);
  }

  @Test
  public void shouldUseScopeToExtractCorrelationKeys() {
    // given
    final String processId = "shouldHaveScopeKeyIfBoundaryEvent";
    final BpmnModelInstance workflow =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .serviceTask("task", c -> c.zeebeTaskType("type").zeebeInput("bar", "foo"))
            .boundaryEvent(
                "event", b -> b.message(m -> m.zeebeCorrelationKey("foo").name("message")))
            .endEvent()
            .moveToActivity("task")
            .endEvent()
            .done();
    testClient.deploy(workflow);

    // when
    testClient
        .createWorkflowInstance(
            r ->
                r.setBpmnProcessId(processId)
                    .setVariables(MsgPackUtil.asMsgPack(m -> m.put("foo", 1).put("bar", 2))))
        .getInstanceKey();
    testClient.publishMessage("message", "1");

    // then
    // if correlation key was extracted from the task, then foo in the task scope would be 2 and
    // no event occurred would be published
    assertThat(testClient.receiveElementInState("task", WorkflowInstanceIntent.EVENT_OCCURRED))
        .isNotNull();
  }

  @Test
  public void shouldHaveScopeKeyIfBoundaryEvent() {
    // given
    final String processId = "shouldHaveScopeKeyIfBoundaryEvent";
    final BpmnModelInstance workflow =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .serviceTask("task", c -> c.zeebeTaskType("type"))
            .boundaryEvent(
                "event", b -> b.message(m -> m.zeebeCorrelationKey("orderId").name("message")))
            .endEvent()
            .moveToActivity("task")
            .endEvent()
            .done();
    testClient.deploy(workflow);

    // when
    final long workflowInstanceKey =
        testClient
            .createWorkflowInstance(
                r ->
                    r.setBpmnProcessId(processId)
                        .setVariables(MsgPackUtil.asMsgPack("orderId", true)))
            .getInstanceKey();
    final Record<WorkflowInstanceRecordValue> failureEvent =
        RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_ACTIVATING)
            .withElementId("task")
            .getFirst();

    // then
    final Record<IncidentRecordValue> incidentRecord =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED).getFirst();

    Assertions.assertThat(incidentRecord.getValue())
        .hasErrorType(ErrorType.EXTRACT_VALUE_ERROR.name())
        .hasWorkflowInstanceKey(workflowInstanceKey)
        .hasElementId("task")
        .hasElementInstanceKey(failureEvent.getKey())
        .hasJobKey(-1L)
        .hasVariableScopeKey(workflowInstanceKey);
  }

  private void awaitProcessCompleted() {
    testClient.receiveElementInState(PROCESS_ID, WorkflowInstanceIntent.ELEMENT_COMPLETED);
  }

  private void assertRecordsPublishedInOrder(final Record<?>... records) {
    final List<Record<?>> sorted =
        Arrays.stream(records)
            .sorted(Comparator.comparingLong(Record::getPosition))
            .collect(Collectors.toList());

    assertThat(sorted).containsExactly(records);
  }
}
