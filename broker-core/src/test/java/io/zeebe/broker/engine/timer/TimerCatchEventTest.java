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
package io.zeebe.broker.engine.timer;

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.exporter.api.record.Assertions;
import io.zeebe.exporter.api.record.Record;
import io.zeebe.exporter.api.record.value.TimerRecordValue;
import io.zeebe.exporter.api.record.value.WorkflowInstanceRecordValue;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.BpmnElementType;
import io.zeebe.protocol.intent.TimerIntent;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.test.broker.protocol.clientapi.ClientApiRule;
import io.zeebe.test.broker.protocol.clientapi.PartitionTestClient;
import io.zeebe.test.util.record.RecordingExporter;
import io.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.time.Duration;
import java.util.stream.IntStream;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class TimerCatchEventTest {
  private static EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();
  private static ClientApiRule apiRule = new ClientApiRule(brokerRule::getAtomix);
  @ClassRule public static RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(apiRule);

  @Rule
  public RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  private static final BpmnModelInstance SINGLE_TIMER_WORKFLOW =
      Bpmn.createExecutableProcess("single-timer-workflow")
          .startEvent()
          .intermediateCatchEvent("timer", c -> c.timerWithDuration("PT0.1S"))
          .endEvent()
          .done();

  private static final BpmnModelInstance BOUNDARY_EVENT_WORKFLOW =
      Bpmn.createExecutableProcess("boundary-event-workflow")
          .startEvent()
          .serviceTask("task", b -> b.zeebeTaskType("type"))
          .boundaryEvent("timer")
          .cancelActivity(true)
          .timerWithDuration("PT1S")
          .endEvent("eventEnd")
          .moveToActivity("task")
          .endEvent("taskEnd")
          .done();

  private static final BpmnModelInstance TWO_REPS_CYCLE_WORKFLOW =
      Bpmn.createExecutableProcess("two-reps-cycle-workflow")
          .startEvent()
          .serviceTask("task", b -> b.zeebeTaskType("type"))
          .boundaryEvent("timer")
          .cancelActivity(false)
          .timerWithCycle("R2/PT1S")
          .endEvent()
          .moveToNode("task")
          .endEvent()
          .done();

  private static final BpmnModelInstance INFINITE_CYCLE_WORKFLOW =
      Bpmn.createExecutableProcess("infinite-cycle-workflow")
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

    assertThat(
            RecordingExporter.timerRecords().withWorkflowInstanceKey(workflowInstanceKey).limit(4))
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
        .isBetween(
            brokerRule.getClock().getCurrentTimeInMillis(),
            createdEvent.getTimestamp().plusSeconds(10).toEpochMilli());
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
        .isGreaterThanOrEqualTo(Duration.ofSeconds(1));
  }

  @Test
  public void shouldCompleteTimerEvent() {
    // given
    final long workflowKey = testClient.deployWorkflow(SINGLE_TIMER_WORKFLOW).getKey();
    final long workflowInstanceKey =
        testClient.createWorkflowInstance(r -> r.setKey(workflowKey)).getInstanceKey();

    // when
    RecordingExporter.timerRecords(TimerIntent.CREATED)
        .withWorkflowInstanceKey(workflowInstanceKey)
        .getFirst();

    brokerRule.getClock().addTime(Duration.ofSeconds(1));

    // then
    final Record<WorkflowInstanceRecordValue> activatedEvent =
        RecordingExporter.workflowInstanceRecords()
            .withWorkflowInstanceKey(workflowInstanceKey)
            .withElementId("timer")
            .withIntent(WorkflowInstanceIntent.ELEMENT_ACTIVATED)
            .getFirst();

    final Record<WorkflowInstanceRecordValue> completedEvent =
        RecordingExporter.workflowInstanceRecords()
            .withWorkflowInstanceKey(workflowInstanceKey)
            .withElementId("timer")
            .withIntent(WorkflowInstanceIntent.ELEMENT_COMPLETED)
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
            .intermediateCatchEvent("timer1", c -> c.timerWithDuration("PT1S"))
            .endEvent()
            .moveToLastGateway()
            .intermediateCatchEvent("timer2", c -> c.timerWithDuration("PT2S"))
            .endEvent()
            .done();

    final long workflowKey = testClient.deployWorkflow(workflow).getKey();
    final long workflowInstanceKey =
        testClient.createWorkflowInstance(r -> r.setKey(workflowKey)).getInstanceKey();

    // when
    assertThat(
            RecordingExporter.timerRecords(TimerIntent.CREATED)
                .withWorkflowInstanceKey(workflowInstanceKey)
                .limit(2))
        .hasSize(2);

    brokerRule.getClock().addTime(Duration.ofSeconds(1));

    final Record<TimerRecordValue> triggeredTimer1 =
        RecordingExporter.timerRecords(TimerIntent.TRIGGERED)
            .withHandlerNodeId("timer1")
            .withWorkflowInstanceKey(workflowInstanceKey)
            .getFirst();

    brokerRule.getClock().addTime(Duration.ofSeconds(1));

    final Record<TimerRecordValue> triggeredTimer2 =
        RecordingExporter.timerRecords(TimerIntent.TRIGGERED)
            .withHandlerNodeId("timer2")
            .withWorkflowInstanceKey(workflowInstanceKey)
            .getFirst();

    // then
    assertThat(
            RecordingExporter.workflowInstanceRecords()
                .withWorkflowInstanceKey(workflowInstanceKey)
                .withElementType(BpmnElementType.INTERMEDIATE_CATCH_EVENT)
                .withIntent(WorkflowInstanceIntent.ELEMENT_COMPLETED)
                .limit(2))
        .extracting(r -> r.getValue().getElementId())
        .contains("timer1", "timer2");

    final long timer1DueDate = triggeredTimer1.getValue().getDueDate();
    assertThat(triggeredTimer2.getValue().getDueDate())
        .isBetween(timer1DueDate, timer1DueDate + Duration.ofSeconds(1).toMillis());
  }

  @Test
  public void shouldCancelTimer() {
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
            .getFirst();

    assertThat(canceledEvent.getKey()).isEqualTo(createdEvent.getKey());
    assertThat(canceledEvent.getValue()).isEqualTo(createdEvent.getValue());
  }

  @Test
  public void shouldCreateTimerBasedOnBoundaryEvent() {
    // given/when
    final long workflowKey = testClient.deployWorkflow(BOUNDARY_EVENT_WORKFLOW).getKey();
    final long workflowInstanceKey =
        testClient.createWorkflowInstance(r -> r.setKey(workflowKey)).getInstanceKey();

    // then
    final Record<TimerRecordValue> timerRecord =
        RecordingExporter.timerRecords(TimerIntent.CREATED)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .getFirst();

    final Record<WorkflowInstanceRecordValue> activityRecord =
        RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_ACTIVATED)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .withElementId("task")
            .getFirst();

    Assertions.assertThat(timerRecord.getValue())
        .hasElementInstanceKey(activityRecord.getKey())
        .hasHandlerFlowNodeId("timer");

    assertThat(timerRecord.getValue().getDueDate())
        .isBetween(
            brokerRule.getClock().getCurrentTimeInMillis(),
            timerRecord.getTimestamp().plusSeconds(1).toEpochMilli());
  }

  @Test
  public void shouldTriggerHandlerNodeWhenAttachedToActivity() {
    // given
    final long workflowKey = testClient.deployWorkflow(BOUNDARY_EVENT_WORKFLOW).getKey();
    final long workflowInstanceKey =
        testClient.createWorkflowInstance(r -> r.setKey(workflowKey)).getInstanceKey();

    // when
    final Record<TimerRecordValue> timerCreated =
        RecordingExporter.timerRecords(TimerIntent.CREATED)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .getFirst();

    brokerRule.getClock().addTime(Duration.ofSeconds(1));

    // then
    final Record<TimerRecordValue> timerTriggered =
        RecordingExporter.timerRecords(TimerIntent.TRIGGERED)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .getFirst();

    assertThat(timerTriggered.getKey()).isEqualTo(timerCreated.getKey());
    assertThat(timerTriggered.getValue()).isEqualTo(timerCreated.getValue());

    assertThat(
            RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_COMPLETING)
                .withWorkflowInstanceKey(workflowInstanceKey)
                .withElementId("timer")
                .exists())
        .isTrue();
  }

  @Test
  public void shouldRecreateTimerWithCycle() {
    // given
    final long workflowKey = testClient.deployWorkflow(TWO_REPS_CYCLE_WORKFLOW).getKey();
    final long workflowInstanceKey =
        testClient.createWorkflowInstance(r -> r.setKey(workflowKey)).getInstanceKey();

    // when
    final Record<TimerRecordValue> timerCreated =
        RecordingExporter.timerRecords(TimerIntent.CREATED)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .getFirst();

    brokerRule.getClock().addTime(Duration.ofSeconds(1));

    final Record<TimerRecordValue> timerRescheduled =
        RecordingExporter.timerRecords(TimerIntent.CREATED)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .limit(2)
            .getLast();

    // then
    assertThat(timerRescheduled.getKey()).isGreaterThan(timerCreated.getKey());
    Assertions.assertThat(timerRescheduled.getValue())
        .hasHandlerFlowNodeId(timerCreated.getValue().getHandlerFlowNodeId())
        .hasElementInstanceKey(timerCreated.getValue().getElementInstanceKey());

    assertThat(timerRescheduled.getValue().getDueDate())
        .isGreaterThanOrEqualTo(
            timerCreated.getValue().getDueDate() + Duration.ofSeconds(1).toMillis());
  }

  @Test
  public void shouldRecreateTimerForTheSpecifiedAmountOfRepetitions() {
    // given
    final BpmnModelInstance workflow =
        Bpmn.createExecutableProcess()
            .startEvent()
            .parallelGateway("gw")
            .serviceTask("task-1", b -> b.zeebeTaskType("type"))
            .boundaryEvent("timer-1")
            .cancelActivity(false)
            .timerWithCycle("R1/PT1S")
            .endEvent()
            .moveToNode("gw")
            .serviceTask("task-2", b -> b.zeebeTaskType("type"))
            .boundaryEvent("timer-2")
            .cancelActivity(false)
            .timerWithCycle("R3/PT1S")
            .endEvent()
            .done();

    final long workflowKey = testClient.deployWorkflow(workflow).getKey();
    final long workflowInstanceKey =
        testClient.createWorkflowInstance(r -> r.setKey(workflowKey)).getInstanceKey();

    // when
    assertThat(
            RecordingExporter.timerRecords(TimerIntent.CREATED)
                .withWorkflowInstanceKey(workflowInstanceKey)
                .limit(2))
        .hasSize(2);

    brokerRule.getClock().addTime(Duration.ofSeconds(1));

    assertThat(
            RecordingExporter.timerRecords(TimerIntent.CREATED)
                .withWorkflowInstanceKey(workflowInstanceKey)
                .limit(3))
        .hasSize(3);

    brokerRule.getClock().addTime(Duration.ofSeconds(1));

    // then
    assertThat(
            RecordingExporter.timerRecords(TimerIntent.CREATED)
                .withWorkflowInstanceKey(workflowInstanceKey)
                .limit(4))
        .hasSize(4)
        .extracting(r -> r.getValue().getHandlerFlowNodeId())
        .containsExactlyInAnyOrder("timer-1", "timer-2", "timer-2", "timer-2");
  }

  @Test
  public void shouldRecreateTimerInfinitely() {
    // given
    final int expectedRepetitions = 10;

    final long workflowKey = testClient.deployWorkflow(INFINITE_CYCLE_WORKFLOW).getKey();
    final long workflowInstanceKey =
        testClient.createWorkflowInstance(r -> r.setKey(workflowKey)).getInstanceKey();

    // when
    IntStream.range(1, expectedRepetitions + 1)
        .forEach(
            i -> {
              RecordingExporter.timerRecords(TimerIntent.CREATED)
                  .withWorkflowInstanceKey(workflowInstanceKey)
                  .limit(i)
                  .count();

              brokerRule.getClock().addTime(Duration.ofSeconds(1));
            });

    // then
    assertThat(
            RecordingExporter.timerRecords(TimerIntent.CREATED)
                .withWorkflowInstanceKey(workflowInstanceKey)
                .limit(expectedRepetitions))
        .hasSize(expectedRepetitions);

    assertThat(
            RecordingExporter.timerRecords(TimerIntent.TRIGGERED)
                .withWorkflowInstanceKey(workflowInstanceKey)
                .limit(expectedRepetitions))
        .hasSize(expectedRepetitions);
  }
}
