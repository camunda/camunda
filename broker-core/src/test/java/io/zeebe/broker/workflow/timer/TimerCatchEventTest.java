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
package io.zeebe.broker.workflow.timer;

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.exporter.api.record.Assertions;
import io.zeebe.exporter.api.record.Record;
import io.zeebe.exporter.api.record.value.TimerRecordValue;
import io.zeebe.exporter.api.record.value.WorkflowInstanceRecordValue;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.intent.TimerIntent;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.test.broker.protocol.clientapi.ClientApiRule;
import io.zeebe.test.broker.protocol.clientapi.PartitionTestClient;
import io.zeebe.test.util.record.RecordingExporter;
import io.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.time.Duration;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class TimerCatchEventTest {
  private static EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();
  private static ClientApiRule apiRule = new ClientApiRule(brokerRule::getClientAddress);
  @ClassRule public static RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(apiRule);

  @Rule
  public RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  private static final String SINGLE_TIMER_WORKFLOW_PROCESS_ID = "single-timer-workflow";
  private static final BpmnModelInstance SINGLE_TIMER_WORKFLOW =
      Bpmn.createExecutableProcess(SINGLE_TIMER_WORKFLOW_PROCESS_ID)
          .startEvent()
          .intermediateCatchEvent("timer", c -> c.timerWithDuration("PT0.1S"))
          .endEvent()
          .done();

  private static final String BOUNDARY_EVENT_WORKFLOW_PROCESS_ID = "boundary-event-workflow";
  private static final BpmnModelInstance BOUNDARY_EVENT_WORKFLOW =
      Bpmn.createExecutableProcess(BOUNDARY_EVENT_WORKFLOW_PROCESS_ID)
          .startEvent()
          .serviceTask("task", b -> b.zeebeTaskType("type"))
          .boundaryEvent("timer")
          .cancelActivity(true)
          .timerWithDuration("PT1S")
          .endEvent("eventEnd")
          .moveToActivity("task")
          .endEvent("taskEnd")
          .done();

  private static final String TWO_REPS_CYCLE_WORKFLOW_PROCESS_ID = "two-reps-cycle-workflow";
  private static final BpmnModelInstance TWO_REPS_CYCLE_WORKFLOW =
      Bpmn.createExecutableProcess(TWO_REPS_CYCLE_WORKFLOW_PROCESS_ID)
          .startEvent()
          .serviceTask("task", b -> b.zeebeTaskType("type"))
          .boundaryEvent("timer")
          .cancelActivity(false)
          .timerWithCycle("R2/PT1S")
          .endEvent()
          .moveToNode("task")
          .endEvent()
          .done();

  private static final String INFINITE_CYCLE_WORKFLOW_PROCESS_ID = "infinite-cycle-workflow";
  private static final BpmnModelInstance INFINITE_CYCLE_WORKFLOW =
      Bpmn.createExecutableProcess(INFINITE_CYCLE_WORKFLOW_PROCESS_ID)
          .startEvent()
          .serviceTask("task", b -> b.zeebeTaskType("type"))
          .boundaryEvent("timer")
          .cancelActivity(false)
          .timerWithCycle("R/PT1S")
          .endEvent()
          .moveToNode("task")
          .endEvent()
          .done();

  private PartitionTestClient testClient;

  @Before
  public void init() {
    testClient = apiRule.partitionClient();
  }

  @After
  public void tearDown() {
    brokerRule.getClock().reset();
  }

  @Test
  public void testLifeCycle() {
    // given
    final BpmnModelInstance workflow =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .intermediateCatchEvent("timer", c -> c.timerWithDuration("PT0S"))
            .endEvent()
            .done();

    final long workflowKey = testClient.deployWorkflow(workflow).getKey();
    final long workflowInstanceKey =
        testClient.createWorkflowInstance(r -> r.setKey(workflowKey)).getInstanceKey();

    // then
    assertThat(
            RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_COMPLETED)
                .withWorkflowInstanceKey(workflowInstanceKey)
                .withElementId("process")
                .exists())
        .isTrue();

    assertThat(
            RecordingExporter.workflowInstanceRecords()
                .withWorkflowInstanceKey(workflowInstanceKey)
                .withElementId("timer")
                .limit(5))
        .extracting(r -> r.getMetadata().getIntent())
        .containsExactly(
            WorkflowInstanceIntent.ELEMENT_ACTIVATING,
            WorkflowInstanceIntent.ELEMENT_ACTIVATED,
            WorkflowInstanceIntent.EVENT_OCCURRED,
            WorkflowInstanceIntent.ELEMENT_COMPLETING,
            WorkflowInstanceIntent.ELEMENT_COMPLETED);

    assertThat(RecordingExporter.timerRecords().limit(4))
        .extracting(r -> r.getMetadata().getIntent())
        .containsExactly(
            TimerIntent.CREATE, TimerIntent.CREATED, TimerIntent.TRIGGER, TimerIntent.TRIGGERED);
  }

  @Test
  public void shouldCreateTimer() {
    // given
    final BpmnModelInstance workflow =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .intermediateCatchEvent("timer", c -> c.timerWithDuration("PT10S"))
            .endEvent()
            .done();

    final long workflowKey = testClient.deployWorkflow(workflow).getKey();
    final long workflowInstanceKey =
        testClient.createWorkflowInstance(r -> r.setKey(workflowKey)).getInstanceKey();

    // when
    final Record<WorkflowInstanceRecordValue> activatedEvent =
        RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_ACTIVATED)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .withElementId("timer")
            .getFirst();

    // then
    final Record<TimerRecordValue> createdEvent =
        RecordingExporter.timerRecords(TimerIntent.CREATED)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .getFirst();

    Assertions.assertThat(createdEvent.getValue())
        .hasElementInstanceKey(activatedEvent.getKey())
        .hasWorkflowInstanceKey(workflowInstanceKey);
    assertThat(createdEvent.getValue().getDueDate())
        .isGreaterThan(brokerRule.getClock().getCurrentTimeInMillis());
  }

  @Test
  public void shouldTriggerTimer() {
    // given
    final long workflowKey = testClient.deployWorkflow(SINGLE_TIMER_WORKFLOW).getKey();
    final long workflowInstanceKey =
        testClient.createWorkflowInstance(r -> r.setKey(workflowKey)).getInstanceKey();

    // when
    final Record<TimerRecordValue> createdEvent =
        RecordingExporter.timerRecords(TimerIntent.CREATED)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .getFirst();

    brokerRule.getClock().addTime(Duration.ofSeconds(1));

    // then
    final Record<TimerRecordValue> triggeredEvent =
        RecordingExporter.timerRecords(TimerIntent.TRIGGERED)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .getFirst();

    assertThat(triggeredEvent.getKey()).isEqualTo(createdEvent.getKey());
    assertThat(triggeredEvent.getValue()).isEqualTo(createdEvent.getValue());
    assertThat(Duration.between(createdEvent.getTimestamp(), triggeredEvent.getTimestamp()))
        .isGreaterThanOrEqualTo(Duration.ofMillis(100));
  }

  @Test
  public void shouldCompleteTimerEvent() {
    // given
    final long workflowKey = testClient.deployWorkflow(SINGLE_TIMER_WORKFLOW).getKey();
    final long workflowInstanceKey =
        testClient.createWorkflowInstance(r -> r.setKey(workflowKey)).getInstanceKey();

    // when
    final Record<WorkflowInstanceRecordValue> activatedEvent =
        RecordingExporter.workflowInstanceRecords()
            .withWorkflowInstanceKey(workflowInstanceKey)
            .withElementId("timer")
            .withIntent(WorkflowInstanceIntent.ELEMENT_ACTIVATED)
            .limitToWorkflowInstanceCompleted()
            .getFirst();

    brokerRule.getClock().addTime(Duration.ofSeconds(1));

    // then
    final Record<WorkflowInstanceRecordValue> completedEvent =
        RecordingExporter.workflowInstanceRecords()
            .withWorkflowInstanceKey(workflowInstanceKey)
            .withElementId("timer")
            .withIntent(WorkflowInstanceIntent.ELEMENT_COMPLETED)
            .limitToWorkflowInstanceCompleted()
            .getFirst();

    assertThat(completedEvent.getKey()).isEqualTo(activatedEvent.getKey());
    assertThat(completedEvent.getValue()).isEqualTo(activatedEvent.getValue());
  }

  @Test
  public void shouldTriggerTimerWithZeroDuration() {
    // given
    final BpmnModelInstance workflow =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .intermediateCatchEvent("timer", c -> c.timerWithDuration("PT0S"))
            .endEvent()
            .done();

    final long workflowKey = testClient.deployWorkflow(workflow).getKey();
    final long workflowInstanceKey =
        testClient.createWorkflowInstance(r -> r.setKey(workflowKey)).getInstanceKey();

    // then
    assertThat(
            RecordingExporter.timerRecords(TimerIntent.TRIGGERED)
                .withWorkflowInstanceKey(workflowInstanceKey)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldTriggerTimerWithNegativeDuration() {
    // given
    final BpmnModelInstance workflow =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .intermediateCatchEvent("timer", c -> c.timerWithDuration("-PT1H"))
            .endEvent()
            .done();

    final long workflowKey = testClient.deployWorkflow(workflow).getKey();
    final long workflowInstanceKey =
        testClient.createWorkflowInstance(r -> r.setKey(workflowKey)).getInstanceKey();

    // then
    assertThat(
            RecordingExporter.timerRecords(TimerIntent.TRIGGERED)
                .withWorkflowInstanceKey(workflowInstanceKey)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldTriggerMultipleTimers() {
    // given
    final BpmnModelInstance workflow =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .parallelGateway()
            .intermediateCatchEvent("timer1", c -> c.timerWithDuration("PT0.1S"))
            .endEvent()
            .moveToLastGateway()
            .intermediateCatchEvent("timer2", c -> c.timerWithDuration("PT0.2S"))
            .endEvent()
            .done();

    final long workflowKey = testClient.deployWorkflow(workflow).getKey();
    final long workflowInstanceKey =
        testClient.createWorkflowInstance(r -> r.setKey(workflowKey)).getInstanceKey();

    // when
    brokerRule.getClock().addTime(Duration.ofSeconds(1));

    // then
    final Record<WorkflowInstanceRecordValue> timer1 =
        RecordingExporter.workflowInstanceRecords()
            .withWorkflowInstanceKey(workflowInstanceKey)
            .limitToWorkflowInstanceCompleted()
            .withElementId("timer1")
            .withIntent(WorkflowInstanceIntent.ELEMENT_COMPLETED)
            .getFirst();
    final Record<WorkflowInstanceRecordValue> timer2 =
        RecordingExporter.workflowInstanceRecords()
            .withWorkflowInstanceKey(workflowInstanceKey)
            .withElementId("timer2")
            .withIntent(WorkflowInstanceIntent.ELEMENT_COMPLETED)
            .getFirst();

    final Record<TimerRecordValue> triggeredTimer1 =
        RecordingExporter.timerRecords(TimerIntent.TRIGGERED)
            .withElementInstanceKey(timer1.getKey())
            .withWorkflowInstanceKey(workflowInstanceKey)
            .getFirst();
    final Record<TimerRecordValue> triggeredTimer2 =
        RecordingExporter.timerRecords(TimerIntent.TRIGGERED)
            .withElementInstanceKey(timer2.getKey())
            .withWorkflowInstanceKey(workflowInstanceKey)
            .getFirst();

    assertThat(triggeredTimer1.getValue().getDueDate())
        .isLessThan(triggeredTimer2.getValue().getDueDate());
  }

  @Test
  public void shouldCancelTimer() {
    // given
    final BpmnModelInstance workflow =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .parallelGateway()
            .intermediateCatchEvent("timer", c -> c.timerWithDuration("PT10S"))
            .endEvent()
            .done();

    final long workflowKey = testClient.deployWorkflow(workflow).getKey();
    final long workflowInstanceKey =
        testClient.createWorkflowInstance(r -> r.setKey(workflowKey)).getInstanceKey();

    // when
    final Record<TimerRecordValue> createdEvent =
        RecordingExporter.timerRecords(TimerIntent.CREATED)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .withHandlerNodeId("timer")
            .getFirst();
    testClient.cancelWorkflowInstance(workflowInstanceKey);

    // then
    final Record<TimerRecordValue> canceledEvent =
        RecordingExporter.timerRecords(TimerIntent.CANCELED)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .withHandlerNodeId("timer")
            .getFirst();
    assertThat(canceledEvent.getKey()).isEqualTo(createdEvent.getKey());
    assertThat(canceledEvent.getValue()).isEqualTo(createdEvent.getValue());
    assertThat(canceledEvent.getValue().getDueDate())
        .isGreaterThan(brokerRule.getClock().getCurrentTimeInMillis());
  }

  @Test
  public void shouldCreateTimerBasedOnBoundaryEvent() {
    // given
    final long workflowKey = testClient.deployWorkflow(BOUNDARY_EVENT_WORKFLOW).getKey();
    brokerRule.getClock().pinCurrentTime();
    final long nowMs = brokerRule.getClock().getCurrentTimeInMillis();
    final long workflowInstanceKey =
        testClient.createWorkflowInstance(r -> r.setKey(workflowKey)).getInstanceKey();

    // when
    final Record<TimerRecordValue> timerCreatedRecord =
        RecordingExporter.timerRecords(TimerIntent.CREATED)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .getFirst();
    final Record<WorkflowInstanceRecordValue> activityRecord =
        RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_ACTIVATED)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .withElementId("task")
            .getFirst();

    // then
    assertThat(timerCreatedRecord.getValue().getDueDate()).isEqualTo(nowMs + 1000);
    assertThat(timerCreatedRecord.getValue().getElementInstanceKey())
        .isEqualTo(activityRecord.getKey());
    assertThat(timerCreatedRecord.getValue().getHandlerFlowNodeId()).isEqualTo("timer");
  }

  @Test
  public void shouldTriggerHandlerNodeWhenAttachedToActivity() {
    // given
    final long workflowKey = testClient.deployWorkflow(BOUNDARY_EVENT_WORKFLOW).getKey();
    final long workflowInstanceKey =
        testClient.createWorkflowInstance(r -> r.setKey(workflowKey)).getInstanceKey();

    // when
    assertThat(
            RecordingExporter.timerRecords(TimerIntent.CREATED)
                .withWorkflowInstanceKey(workflowInstanceKey)
                .getFirst())
        .isNotNull();
    brokerRule.getClock().addTime(Duration.ofSeconds(10));

    // then
    assertThat(
            RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_COMPLETING)
                .withWorkflowInstanceKey(workflowInstanceKey)
                .withElementId("timer")
                .getFirst())
        .isNotNull();
  }

  @Test
  public void shouldRecreateATimerWithCycle() {
    // given
    final long workflowKey = testClient.deployWorkflow(TWO_REPS_CYCLE_WORKFLOW).getKey();
    brokerRule.getClock().pinCurrentTime();
    final long nowMs = brokerRule.getClock().getCurrentTimeInMillis();
    final long workflowInstanceKey =
        testClient.createWorkflowInstance(r -> r.setKey(workflowKey)).getInstanceKey();

    // when
    final Record<TimerRecordValue> timerCreatedRecord =
        RecordingExporter.timerRecords(TimerIntent.CREATED)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .getFirst();
    brokerRule.getClock().addTime(Duration.ofSeconds(5));
    final Record<TimerRecordValue> timerRescheduledRecord =
        RecordingExporter.timerRecords(TimerIntent.CREATED)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .limit(2)
            .getLast();

    // then
    assertThat(timerCreatedRecord).isNotEqualTo(timerRescheduledRecord);
    assertThat(timerCreatedRecord.getValue().getDueDate()).isEqualTo(nowMs + 1000);
    assertThat(timerRescheduledRecord.getValue().getDueDate()).isEqualTo(nowMs + 6000);
  }

  @Test
  public void shouldRecreateATimerForTheSpecifiedAmountOfRepetitions() {
    // given
    final long workflowKey = testClient.deployWorkflow(TWO_REPS_CYCLE_WORKFLOW).getKey();
    brokerRule.getClock().pinCurrentTime();
    final long workflowInstanceKey =
        testClient.createWorkflowInstance(r -> r.setKey(workflowKey)).getInstanceKey();

    // when
    assertThat(
            RecordingExporter.timerRecords(TimerIntent.CREATED)
                .withWorkflowInstanceKey(workflowInstanceKey)
                .getFirst())
        .isNotNull();
    brokerRule.getClock().addTime(Duration.ofSeconds(5));
    assertThat(
            RecordingExporter.timerRecords(TimerIntent.CREATED)
                .withWorkflowInstanceKey(workflowInstanceKey)
                .limit(2))
        .hasSize(2);
    brokerRule.getClock().addTime(Duration.ofSeconds(5));
    testClient.completeJobOfType("type");

    // then
    assertThat(
            RecordingExporter.workflowInstanceRecords()
                .withWorkflowInstanceKey(workflowInstanceKey)
                .limitToWorkflowInstanceCompleted()
                .withIntent(WorkflowInstanceIntent.ELEMENT_COMPLETED)
                .exists())
        .isTrue();
    assertThat(
            RecordingExporter.timerRecords(TimerIntent.CREATED)
                .withWorkflowInstanceKey(workflowInstanceKey)
                .limit(2)
                .count())
        .isEqualTo(2);
  }

  @Test
  public void shouldRecreateATimerInfinitely() {
    // given
    final int expectedRepetitions = 5;
    final long workflowKey = testClient.deployWorkflow(INFINITE_CYCLE_WORKFLOW).getKey();
    brokerRule.getClock().pinCurrentTime();
    final long workflowInstanceKey =
        testClient.createWorkflowInstance(r -> r.setKey(workflowKey)).getInstanceKey();

    // when
    for (int i = 1; i <= expectedRepetitions; i++) {
      brokerRule.getClock().addTime(Duration.ofSeconds(1));
      assertThat(
              RecordingExporter.timerRecords()
                  .withWorkflowInstanceKey(workflowInstanceKey)
                  .withHandlerNodeId("timer")
                  .withIntent(TimerIntent.CREATED)
                  .limit(i)
                  .count())
          .isEqualTo(i);
    }
    testClient.completeJobOfType("type");

    // then
    assertThat(
            RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_COMPLETED)
                .withWorkflowInstanceKey(workflowInstanceKey)
                .withElementId(INFINITE_CYCLE_WORKFLOW_PROCESS_ID)
                .exists())
        .isTrue();
    assertThat(
            RecordingExporter.records()
                .limitToWorkflowInstance(workflowInstanceKey)
                .timerRecords()
                .withWorkflowInstanceKey(workflowInstanceKey)
                .withHandlerNodeId("timer")
                .withIntent(TimerIntent.CREATED)
                .count())
        .isEqualTo(expectedRepetitions);
  }
}
