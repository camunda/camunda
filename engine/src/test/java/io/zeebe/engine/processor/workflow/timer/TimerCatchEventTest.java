/*
 * Zeebe Workflow Engine
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
package io.zeebe.engine.processor.workflow.timer;

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.engine.util.EngineRule;
import io.zeebe.exporter.api.record.Assertions;
import io.zeebe.exporter.api.record.Record;
import io.zeebe.exporter.api.record.value.TimerRecordValue;
import io.zeebe.exporter.api.record.value.WorkflowInstanceRecordValue;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.BpmnElementType;
import io.zeebe.protocol.intent.TimerIntent;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.test.util.record.RecordingExporter;
import java.time.Duration;
import java.util.stream.IntStream;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

public class TimerCatchEventTest {
  @ClassRule public static final EngineRule ENGINE = new EngineRule();

  private static final BpmnModelInstance SINGLE_TIMER_WORKFLOW =
      Bpmn.createExecutableProcess("SINGLE_TIMER_WORKFLOW")
          .startEvent()
          .intermediateCatchEvent("timer", c -> c.timerWithDuration("PT0.1S"))
          .endEvent()
          .done();

  private static final BpmnModelInstance BOUNDARY_EVENT_WORKFLOW =
      Bpmn.createExecutableProcess("BOUNDARY_EVENT_WORKFLOW")
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
      Bpmn.createExecutableProcess("TWO_REPS_CYCLE_WORKFLOW")
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
      Bpmn.createExecutableProcess("INFINITE_CYCLE_WORKFLOW")
          .startEvent()
          .serviceTask("task", b -> b.zeebeTaskType("type"))
          .boundaryEvent("timer")
          .cancelActivity(false)
          .timerWithCycle("R/PT1S")
          .endEvent()
          .moveToNode("task")
          .endEvent()
          .done();

  @BeforeClass
  public static void init() {
    ENGINE.deploy(SINGLE_TIMER_WORKFLOW);
    ENGINE.deploy(BOUNDARY_EVENT_WORKFLOW);
    ENGINE.deploy(TWO_REPS_CYCLE_WORKFLOW);
    ENGINE.deploy(INFINITE_CYCLE_WORKFLOW);
  }

  @Test
  public void testLifeCycle() {
    // given
    final BpmnModelInstance workflow =
        Bpmn.createExecutableProcess("testLifeCycle")
            .startEvent()
            .intermediateCatchEvent("timer", c -> c.timerWithDuration("PT0S"))
            .endEvent()
            .done();

    ENGINE.deploy(workflow);
    final long workflowInstanceKey =
        ENGINE.createWorkflowInstance(r -> r.setBpmnProcessId("testLifeCycle"));

    // then
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
        Bpmn.createExecutableProcess("shouldCreateTimer")
            .startEvent()
            .intermediateCatchEvent("timer", c -> c.timerWithDuration("PT10S"))
            .endEvent()
            .done();

    ENGINE.deploy(workflow);
    final long workflowInstanceKey =
        ENGINE.createWorkflowInstance(r -> r.setBpmnProcessId("shouldCreateTimer"));

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
            System.currentTimeMillis(), createdEvent.getTimestamp().plusSeconds(10).toEpochMilli());
  }

  @Test
  public void shouldTriggerTimer() {
    // given
    final long workflowInstanceKey =
        ENGINE.createWorkflowInstance(r -> r.setBpmnProcessId("SINGLE_TIMER_WORKFLOW"));

    // when
    final Record<TimerRecordValue> createdEvent =
        RecordingExporter.timerRecords(TimerIntent.CREATED)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .getFirst();

    ENGINE.increaseTime(Duration.ofSeconds(1));

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
    final long workflowInstanceKey =
        ENGINE.createWorkflowInstance(r -> r.setBpmnProcessId("SINGLE_TIMER_WORKFLOW"));

    // when
    RecordingExporter.timerRecords(TimerIntent.CREATED)
        .withWorkflowInstanceKey(workflowInstanceKey)
        .getFirst();

    ENGINE.increaseTime(Duration.ofSeconds(1));

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
        Bpmn.createExecutableProcess("shouldTriggerTimerWithZeroDuration")
            .startEvent()
            .intermediateCatchEvent("timer", c -> c.timerWithDuration("PT0S"))
            .endEvent()
            .done();

    ENGINE.deploy(workflow);
    final long workflowInstanceKey =
        ENGINE.createWorkflowInstance(
            r -> r.setBpmnProcessId("shouldTriggerTimerWithZeroDuration"));

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
        Bpmn.createExecutableProcess("shouldTriggerTimerWithNegativeDuration")
            .startEvent()
            .intermediateCatchEvent("timer", c -> c.timerWithDuration("-PT1H"))
            .endEvent()
            .done();

    ENGINE.deploy(workflow);
    final long workflowInstanceKey =
        ENGINE.createWorkflowInstance(
            r -> r.setBpmnProcessId("shouldTriggerTimerWithNegativeDuration"));

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
        Bpmn.createExecutableProcess("shouldTriggerMultipleTimers")
            .startEvent()
            .parallelGateway()
            .intermediateCatchEvent("timer1", c -> c.timerWithDuration("PT1S"))
            .endEvent()
            .moveToLastGateway()
            .intermediateCatchEvent("timer2", c -> c.timerWithDuration("PT2S"))
            .endEvent()
            .done();

    ENGINE.deploy(workflow);
    final long workflowInstanceKey =
        ENGINE.createWorkflowInstance(r -> r.setBpmnProcessId("shouldTriggerMultipleTimers"));

    // when
    assertThat(
            RecordingExporter.timerRecords(TimerIntent.CREATED)
                .withWorkflowInstanceKey(workflowInstanceKey)
                .limit(2))
        .hasSize(2);

    ENGINE.increaseTime(Duration.ofSeconds(1));

    final Record<TimerRecordValue> triggeredTimer1 =
        RecordingExporter.timerRecords(TimerIntent.TRIGGERED)
            .withHandlerNodeId("timer1")
            .withWorkflowInstanceKey(workflowInstanceKey)
            .getFirst();

    ENGINE.increaseTime(Duration.ofSeconds(1));

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
        Bpmn.createExecutableProcess("shouldCancelTimer")
            .startEvent()
            .intermediateCatchEvent("timer", c -> c.timerWithDuration("PT10S"))
            .endEvent()
            .done();

    ENGINE.deploy(workflow);
    final long workflowInstanceKey =
        ENGINE.createWorkflowInstance(r -> r.setBpmnProcessId("shouldCancelTimer"));

    // when
    final Record<TimerRecordValue> createdEvent =
        RecordingExporter.timerRecords(TimerIntent.CREATED)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .withHandlerNodeId("timer")
            .getFirst();

    ENGINE.cancelWorkflowInstance(workflowInstanceKey);

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
    final long workflowInstanceKey =
        ENGINE.createWorkflowInstance(r -> r.setBpmnProcessId("BOUNDARY_EVENT_WORKFLOW"));

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
            System.currentTimeMillis(), timerRecord.getTimestamp().plusSeconds(1).toEpochMilli());
  }

  @Test
  public void shouldTriggerHandlerNodeWhenAttachedToActivity() {
    // given
    final long workflowInstanceKey =
        ENGINE.createWorkflowInstance(r -> r.setBpmnProcessId("BOUNDARY_EVENT_WORKFLOW"));

    // when
    final Record<TimerRecordValue> timerCreated =
        RecordingExporter.timerRecords(TimerIntent.CREATED)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .getFirst();

    ENGINE.increaseTime(Duration.ofSeconds(1));

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
    final long workflowInstanceKey =
        ENGINE.createWorkflowInstance(r -> r.setBpmnProcessId("TWO_REPS_CYCLE_WORKFLOW"));

    // when
    final Record<TimerRecordValue> timerCreated =
        RecordingExporter.timerRecords(TimerIntent.CREATED)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .getFirst();

    ENGINE.increaseTime(Duration.ofSeconds(1));

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
        Bpmn.createExecutableProcess("shouldRecreateTimerForTheSpecifiedAmountOfRepetitions")
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

    ENGINE.deploy(workflow);
    final long workflowInstanceKey =
        ENGINE.createWorkflowInstance(
            r -> r.setBpmnProcessId("shouldRecreateTimerForTheSpecifiedAmountOfRepetitions"));

    // when
    assertThat(
            RecordingExporter.timerRecords(TimerIntent.CREATED)
                .withWorkflowInstanceKey(workflowInstanceKey)
                .limit(2))
        .hasSize(2);

    ENGINE.increaseTime(Duration.ofSeconds(1));

    assertThat(
            RecordingExporter.timerRecords(TimerIntent.CREATED)
                .withWorkflowInstanceKey(workflowInstanceKey)
                .limit(3))
        .hasSize(3);

    ENGINE.increaseTime(Duration.ofSeconds(1));

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

    final long workflowInstanceKey =
        ENGINE.createWorkflowInstance(r -> r.setBpmnProcessId("INFINITE_CYCLE_WORKFLOW"));

    // when
    IntStream.range(1, expectedRepetitions + 1)
        .forEach(
            i -> {
              RecordingExporter.timerRecords(TimerIntent.CREATED)
                  .withWorkflowInstanceKey(workflowInstanceKey)
                  .limit(i)
                  .count();

              ENGINE.increaseTime(Duration.ofSeconds(1));
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
