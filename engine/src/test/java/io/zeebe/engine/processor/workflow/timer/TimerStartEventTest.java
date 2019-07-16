/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.timer;

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.engine.state.instance.TimerInstance;
import io.zeebe.engine.util.EngineRule;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.model.bpmn.builder.ProcessBuilder;
import io.zeebe.protocol.record.Assertions;
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.intent.MessageStartEventSubscriptionIntent;
import io.zeebe.protocol.record.intent.TimerIntent;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import io.zeebe.protocol.record.value.BpmnElementType;
import io.zeebe.protocol.record.value.TimerRecordValue;
import io.zeebe.protocol.record.value.WorkflowInstanceRecordValue;
import io.zeebe.protocol.record.value.deployment.DeployedWorkflow;
import io.zeebe.test.util.record.RecordingExporter;
import java.time.Duration;
import java.time.Instant;
import org.junit.Rule;
import org.junit.Test;

public class TimerStartEventTest {

  private static final BpmnModelInstance SIMPLE_MODEL =
      Bpmn.createExecutableProcess("process")
          .startEvent("start_1")
          .timerWithCycle("R1/PT1S")
          .endEvent("end_1")
          .done();

  private static final BpmnModelInstance REPEATING_MODEL =
      Bpmn.createExecutableProcess("process")
          .startEvent("start_2")
          .timerWithCycle("R/PT1S")
          .endEvent("end_2")
          .done();

  private static final BpmnModelInstance THREE_SEC_MODEL =
      Bpmn.createExecutableProcess("process_3")
          .startEvent("start_3")
          .timerWithCycle("R2/PT3S")
          .endEvent("end_3")
          .done();

  private static final BpmnModelInstance TIMER_AND_MESSAGE_MODEL =
      createTimerAndMessageStartEventsModel();

  private static final BpmnModelInstance MULTI_TIMER_START_MODEL = createMultipleTimerStartModel();

  @Rule public final EngineRule engine = EngineRule.singlePartition();

  private static BpmnModelInstance createTimerAndMessageStartEventsModel() {
    final ProcessBuilder builder = Bpmn.createExecutableProcess("process");
    builder.startEvent("timer_start").timerWithCycle("R/PT1S").endEvent("timer_end");
    return builder.startEvent("msg_start").message("msg1").endEvent("msg_end").done();
  }

  private static BpmnModelInstance createMultipleTimerStartModel() {
    final ProcessBuilder builder = Bpmn.createExecutableProcess("process_4");
    builder.startEvent("start_4").timerWithCycle("R/PT2S").endEvent("end_4");
    return builder.startEvent("start_5").timerWithCycle("R/PT3S").endEvent("end_5").done();
  }

  @Test
  public void shouldCreateTimer() {
    // when
    final DeployedWorkflow deployedWorkflow =
        engine
            .deployment()
            .withXmlResource(SIMPLE_MODEL)
            .deploy()
            .getValue()
            .getDeployedWorkflows()
            .get(0);

    // then
    final TimerRecordValue timerRecord =
        RecordingExporter.timerRecords(TimerIntent.CREATED)
            .withWorkflowKey(deployedWorkflow.getWorkflowKey())
            .getFirst()
            .getValue();

    Assertions.assertThat(timerRecord)
        .hasWorkflowInstanceKey(TimerInstance.NO_ELEMENT_INSTANCE)
        .hasTargetElementId("start_1")
        .hasElementInstanceKey(TimerInstance.NO_ELEMENT_INSTANCE);

    final long now = System.currentTimeMillis();
    assertThat(timerRecord.getDueDate()).isBetween(now, now + 1000L);
  }

  @Test
  public void shouldTriggerAndCreateWorkflowInstance() {
    // given
    final DeployedWorkflow deployedWorkflow =
        engine
            .deployment()
            .withXmlResource(SIMPLE_MODEL)
            .deploy()
            .getValue()
            .getDeployedWorkflows()
            .get(0);

    final long workflowKey = deployedWorkflow.getWorkflowKey();
    assertThat(
            RecordingExporter.timerRecords(TimerIntent.CREATED)
                .withWorkflowKey(workflowKey)
                .exists())
        .isTrue();

    // when
    engine.increaseTime(Duration.ofSeconds(2));

    // then
    final WorkflowInstanceRecordValue startEventActivating =
        RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_ACTIVATING)
            .withElementType(BpmnElementType.START_EVENT)
            .withWorkflowKey(workflowKey)
            .getFirst()
            .getValue();

    Assertions.assertThat(startEventActivating)
        .hasElementId("start_1")
        .hasBpmnProcessId("process")
        .hasVersion(deployedWorkflow.getVersion())
        .hasWorkflowKey(workflowKey);

    final Record<WorkflowInstanceRecordValue> startEventOccurred =
        RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.EVENT_OCCURRED)
            .withWorkflowKey(workflowKey)
            .getFirst();
    assertThat(startEventOccurred.getKey())
        .isLessThan(startEventActivating.getWorkflowInstanceKey());

    final long triggerRecordPosition =
        RecordingExporter.timerRecords(TimerIntent.TRIGGER)
            .withWorkflowKey(workflowKey)
            .getFirst()
            .getPosition();

    assertThat(
            RecordingExporter.timerRecords()
                .withWorkflowKey(workflowKey)
                .skipUntil(r -> r.getPosition() >= triggerRecordPosition)
                .limit(2))
        .extracting(Record::getIntent)
        .containsExactly(TimerIntent.TRIGGER, TimerIntent.TRIGGERED);

    assertThat(
            RecordingExporter.workflowInstanceRecords()
                .withWorkflowKey(workflowKey)
                .skipUntil(r -> r.getPosition() >= triggerRecordPosition)
                .limit(4))
        .extracting(Record::getIntent)
        .containsExactly(
            WorkflowInstanceIntent.EVENT_OCCURRED, // causes the instance creation
            WorkflowInstanceIntent.ELEMENT_ACTIVATING, // causes the flow node activation
            WorkflowInstanceIntent.ELEMENT_ACTIVATED, // input mappings applied
            WorkflowInstanceIntent.ELEMENT_ACTIVATING); // triggers the start event
  }

  @Test
  public void shouldCreateMultipleWorkflowInstancesWithRepeatingTimer() {
    // given
    final DeployedWorkflow deployedWorkflow =
        engine
            .deployment()
            .withXmlResource(THREE_SEC_MODEL)
            .deploy()
            .getValue()
            .getDeployedWorkflows()
            .get(0);
    final long workflowKey = deployedWorkflow.getWorkflowKey();

    // when
    assertThat(
            RecordingExporter.timerRecords(TimerIntent.CREATED)
                .withWorkflowKey(workflowKey)
                .exists())
        .isTrue();
    engine.increaseTime(Duration.ofSeconds(3));

    // then
    assertThat(
            RecordingExporter.timerRecords(TimerIntent.TRIGGERED)
                .withWorkflowKey(workflowKey)
                .exists())
        .isTrue();
    assertThat(
            RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_ACTIVATING)
                .withElementId("process_3")
                .withWorkflowKey(workflowKey)
                .exists())
        .isTrue();
    assertThat(
            RecordingExporter.timerRecords(TimerIntent.CREATED)
                .withWorkflowKey(workflowKey)
                .limit(2)
                .count())
        .isEqualTo(2);

    // when
    engine.increaseTime(Duration.ofSeconds(3));

    // then
    assertThat(
            RecordingExporter.timerRecords(TimerIntent.TRIGGERED)
                .withWorkflowKey(workflowKey)
                .limit(2)
                .count())
        .isEqualTo(2);
    assertThat(
            RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_ACTIVATING)
                .withElementId("process_3")
                .withWorkflowKey(workflowKey)
                .limit(2)
                .count())
        .isEqualTo(2);
  }

  @Test
  public void shouldCompleteWorkflow() {
    // given
    final DeployedWorkflow deployedWorkflow =
        engine
            .deployment()
            .withXmlResource(SIMPLE_MODEL)
            .deploy()
            .getValue()
            .getDeployedWorkflows()
            .get(0);
    assertThat(RecordingExporter.timerRecords(TimerIntent.CREATED).exists()).isTrue();

    // when
    engine.increaseTime(Duration.ofSeconds(1));

    // then
    final WorkflowInstanceRecordValue instanceCompleted =
        RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_COMPLETED)
            .withWorkflowKey(deployedWorkflow.getWorkflowKey())
            .withElementId("process")
            .getFirst()
            .getValue();

    Assertions.assertThat(instanceCompleted)
        .hasBpmnProcessId("process")
        .hasVersion(1)
        .hasWorkflowKey(deployedWorkflow.getWorkflowKey());
  }

  @Test
  public void shouldUpdateWorkflow() {
    // when
    final DeployedWorkflow deployedWorkflow =
        engine
            .deployment()
            .withXmlResource(SIMPLE_MODEL)
            .deploy()
            .getValue()
            .getDeployedWorkflows()
            .get(0);
    assertThat(
            RecordingExporter.timerRecords(TimerIntent.CREATED)
                .withWorkflowKey(deployedWorkflow.getWorkflowKey())
                .exists())
        .isTrue();
    engine.increaseTime(Duration.ofSeconds(1));

    // then
    assertThat(
            RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_ACTIVATED)
                .withElementId("end_1")
                .withBpmnProcessId("process")
                .withWorkflowKey(deployedWorkflow.getWorkflowKey())
                .withVersion(1)
                .exists())
        .isTrue();

    // when
    final DeployedWorkflow repeatingWorkflow =
        engine
            .deployment()
            .withXmlResource(REPEATING_MODEL)
            .deploy()
            .getValue()
            .getDeployedWorkflows()
            .get(0);
    assertThat(
            RecordingExporter.timerRecords(TimerIntent.CREATED)
                .withWorkflowKey(repeatingWorkflow.getWorkflowKey())
                .exists())
        .isTrue();
    engine.increaseTime(Duration.ofSeconds(2));

    assertThat(
            RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_ACTIVATED)
                .withElementId("end_2")
                .withBpmnProcessId("process")
                .withWorkflowKey(repeatingWorkflow.getWorkflowKey())
                .withVersion(2)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldReplaceTimerStartWithNoneStart() {
    // when
    final DeployedWorkflow repeatingWorkflow =
        engine
            .deployment()
            .withXmlResource(REPEATING_MODEL)
            .deploy()
            .getValue()
            .getDeployedWorkflows()
            .get(0);
    final long repeatingWorkflowkey = repeatingWorkflow.getWorkflowKey();
    assertThat(
            RecordingExporter.timerRecords(TimerIntent.CREATED)
                .withWorkflowKey(repeatingWorkflowkey)
                .exists())
        .isTrue();
    engine.increaseTime(Duration.ofSeconds(1));

    // then
    assertThat(
            RecordingExporter.timerRecords(TimerIntent.TRIGGERED)
                .withWorkflowKey(repeatingWorkflowkey)
                .exists())
        .isTrue();

    // when
    final BpmnModelInstance nonTimerModel =
        Bpmn.createExecutableProcess("process").startEvent("start_4").endEvent("end_4").done();
    final DeployedWorkflow notTimerDeployment =
        engine
            .deployment()
            .withXmlResource(nonTimerModel)
            .deploy()
            .getValue()
            .getDeployedWorkflows()
            .get(0);
    engine.increaseTime(Duration.ofSeconds(2));

    // then
    assertThat(
            RecordingExporter.timerRecords(TimerIntent.CANCELED)
                .withWorkflowKey(repeatingWorkflowkey)
                .exists())
        .isTrue();
    assertThat(
            RecordingExporter.timerRecords(TimerIntent.TRIGGERED)
                .withWorkflowKey(repeatingWorkflowkey)
                .exists())
        .isTrue();

    final long workflowInstanceKey = engine.workflowInstance().ofBpmnProcessId("process").create();

    final WorkflowInstanceRecordValue lastRecord =
        RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_ACTIVATED)
            .withElementId("end_4")
            .withWorkflowKey(notTimerDeployment.getWorkflowKey())
            .getFirst()
            .getValue();

    Assertions.assertThat(lastRecord)
        .hasVersion(2)
        .hasBpmnProcessId("process")
        .hasWorkflowInstanceKey(workflowInstanceKey);
  }

  @Test
  public void shouldUpdateTimerPeriod() {
    // given
    final DeployedWorkflow deployedWorkflow =
        engine
            .deployment()
            .withXmlResource(THREE_SEC_MODEL)
            .deploy()
            .getValue()
            .getDeployedWorkflows()
            .get(0);

    final long workflowKey = deployedWorkflow.getWorkflowKey();
    assertThat(
            RecordingExporter.timerRecords(TimerIntent.CREATED)
                .withWorkflowKey(workflowKey)
                .exists())
        .isTrue();

    // when
    engine.increaseTime(Duration.ofSeconds(3));

    // then
    final long now = System.currentTimeMillis();
    TimerRecordValue timerRecord =
        RecordingExporter.timerRecords(TimerIntent.TRIGGERED)
            .withWorkflowKey(workflowKey)
            .getFirst()
            .getValue();

    assertThat(timerRecord.getDueDate()).isBetween(now, now + 3000);

    // when
    final BpmnModelInstance slowerModel =
        Bpmn.createExecutableProcess("process_3")
            .startEvent("start_4")
            .timerWithCycle("R2/PT4S")
            .endEvent("end_4")
            .done();
    final DeployedWorkflow slowerDeployment =
        engine
            .deployment()
            .withXmlResource(slowerModel)
            .deploy()
            .getValue()
            .getDeployedWorkflows()
            .get(0);

    // then
    assertThat(
            RecordingExporter.timerRecords(TimerIntent.CANCELED)
                .withWorkflowKey(workflowKey)
                .getFirst())
        .isNotNull();

    final Record<TimerRecordValue> slowTimerRecord =
        RecordingExporter.timerRecords(TimerIntent.CREATED)
            .withWorkflowKey(slowerDeployment.getWorkflowKey())
            .getFirst();
    timerRecord = slowTimerRecord.getValue();
    final long writtenTime = slowTimerRecord.getTimestamp();
    assertThat(timerRecord.getDueDate()).isBetween(writtenTime, writtenTime + 4000);

    // when
    engine.increaseTime(Duration.ofSeconds(4));

    // then
    assertThat(
            RecordingExporter.timerRecords(TimerIntent.TRIGGERED)
                .withWorkflowKey(slowerDeployment.getWorkflowKey())
                .exists())
        .isTrue();
  }

  @Test
  public void shouldTriggerDifferentWorkflowsSeparately() {
    // given
    final DeployedWorkflow firstDeployment =
        engine
            .deployment()
            .withXmlResource(THREE_SEC_MODEL)
            .deploy()
            .getValue()
            .getDeployedWorkflows()
            .get(0);

    final DeployedWorkflow secondDeployment =
        engine
            .deployment()
            .withXmlResource(REPEATING_MODEL)
            .deploy()
            .getValue()
            .getDeployedWorkflows()
            .get(0);

    assertThat(
            RecordingExporter.timerRecords(TimerIntent.CREATED)
                .withWorkflowKey(firstDeployment.getWorkflowKey())
                .exists())
        .isTrue();

    assertThat(
            RecordingExporter.timerRecords(TimerIntent.CREATED)
                .withWorkflowKey(secondDeployment.getWorkflowKey())
                .exists())
        .isTrue();

    // when
    engine.increaseTime(Duration.ofSeconds(1));

    // then
    final long firstModelTimestamp =
        RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_ACTIVATING)
            .withElementId("process")
            .getFirst()
            .getTimestamp();

    // when
    engine.increaseTime(Duration.ofSeconds(2));

    // then
    assertThat(
            RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_ACTIVATING)
                .withElementId("process")
                .withWorkflowKey(secondDeployment.getWorkflowKey())
                .limit(2)
                .count())
        .isEqualTo(2);

    final long secondModelTimestamp =
        RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_ACTIVATING)
            .withElementId("process_3")
            .withWorkflowKey(firstDeployment.getWorkflowKey())
            .getFirst()
            .getTimestamp();
    assertThat(secondModelTimestamp).isGreaterThan(firstModelTimestamp);
  }

  @Test
  public void shouldCreateMultipleInstanceAtTheCorrectTimes() {
    // given
    final DeployedWorkflow deployedWorkflow =
        engine
            .deployment()
            .withXmlResource(MULTI_TIMER_START_MODEL)
            .deploy()
            .getValue()
            .getDeployedWorkflows()
            .get(0);
    assertThat(
            RecordingExporter.timerRecords(TimerIntent.CREATED)
                .withWorkflowKey(deployedWorkflow.getWorkflowKey())
                .limit(2)
                .count())
        .isEqualTo(2);

    // when
    engine.increaseTime(Duration.ofSeconds(2));

    // then
    assertThat(
            RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_COMPLETED)
                .withElementId("end_4")
                .withWorkflowKey(deployedWorkflow.getWorkflowKey())
                .exists())
        .isTrue();

    // when
    engine.increaseTime(Duration.ofSeconds(1));
    assertThat(
            RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_COMPLETED)
                .withElementId("end_5")
                .withWorkflowKey(deployedWorkflow.getWorkflowKey())
                .exists())
        .isTrue();
  }

  @Test
  public void shouldTriggerAtSpecifiedTimeDate() {
    // given
    final Instant triggerTime = Instant.now().plusMillis(2000);
    final BpmnModelInstance model =
        Bpmn.createExecutableProcess("process")
            .startEvent("start_2")
            .timerWithDate(triggerTime.toString())
            .endEvent("end_2")
            .done();

    final DeployedWorkflow deployedWorkflow =
        engine
            .deployment()
            .withXmlResource(model)
            .deploy()
            .getValue()
            .getDeployedWorkflows()
            .get(0);

    // when
    engine.increaseTime(Duration.ofSeconds(2));

    // then
    final TimerRecordValue timerRecord =
        RecordingExporter.timerRecords(TimerIntent.TRIGGERED)
            .withWorkflowKey(deployedWorkflow.getWorkflowKey())
            .getFirst()
            .getValue();

    Assertions.assertThat(timerRecord)
        .hasDueDate(triggerTime.toEpochMilli())
        .hasTargetElementId("start_2")
        .hasElementInstanceKey(TimerInstance.NO_ELEMENT_INSTANCE);

    assertThat(
            RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_ACTIVATED)
                .withElementId("end_2")
                .withWorkflowKey(deployedWorkflow.getWorkflowKey())
                .exists())
        .isTrue();
  }

  @Test
  public void shouldTriggerIfTimeDatePassedOnDeployment() {
    // given
    final Instant triggerTime = Instant.now().plusMillis(2000);
    final BpmnModelInstance model =
        Bpmn.createExecutableProcess("process")
            .startEvent("start_2")
            .timerWithDate(triggerTime.toString())
            .endEvent("end_2")
            .done();

    final DeployedWorkflow deployedWorkflow =
        engine
            .deployment()
            .withXmlResource(model)
            .deploy()
            .getValue()
            .getDeployedWorkflows()
            .get(0);

    // when
    engine.increaseTime(Duration.ofMillis(2000L));

    // then
    final TimerRecordValue timerRecord =
        RecordingExporter.timerRecords(TimerIntent.TRIGGERED)
            .withWorkflowKey(deployedWorkflow.getWorkflowKey())
            .getFirst()
            .getValue();

    Assertions.assertThat(timerRecord)
        .hasDueDate(triggerTime.toEpochMilli())
        .hasTargetElementId("start_2")
        .hasElementInstanceKey(TimerInstance.NO_ELEMENT_INSTANCE);
  }

  @Test
  public void shouldTriggerTimerAndMessageStartEvent() {
    // given
    final DeployedWorkflow deployedWorkflow =
        engine
            .deployment()
            .withXmlResource(TIMER_AND_MESSAGE_MODEL)
            .deploy()
            .getValue()
            .getDeployedWorkflows()
            .get(0);
    final long workflowKey = deployedWorkflow.getWorkflowKey();
    assertThat(
            RecordingExporter.timerRecords(TimerIntent.CREATED)
                .withWorkflowKey(workflowKey)
                .exists())
        .isTrue();
    assertThat(
            RecordingExporter.messageStartEventSubscriptionRecords(
                    MessageStartEventSubscriptionIntent.OPENED)
                .withWorkfloKey(workflowKey)
                .exists())
        .isTrue();

    // when
    engine.increaseTime(Duration.ofSeconds(1));
    engine.message().withName("msg1").withCorrelationKey("123").publish();

    // then
    final long timerInstanceKey =
        RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_ACTIVATED)
            .withElementId("timer_end")
            .withWorkflowKey(deployedWorkflow.getWorkflowKey())
            .getFirst()
            .getValue()
            .getWorkflowInstanceKey();

    final long messageInstanceKey =
        RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_ACTIVATED)
            .withElementId("msg_end")
            .getFirst()
            .getValue()
            .getWorkflowInstanceKey();
    assertThat(messageInstanceKey).isNotEqualTo(timerInstanceKey);
  }
}
