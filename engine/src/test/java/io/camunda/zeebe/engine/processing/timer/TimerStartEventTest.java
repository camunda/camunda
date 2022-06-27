/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.timer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.state.instance.TimerInstance;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.ProcessBuilder;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.TimerIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.TimerRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.stream.Collectors;
import org.junit.Rule;
import org.junit.Test;

public final class TimerStartEventTest {

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

  private static final BpmnModelInstance MULTIPLE_START_EVENTS_MODEL =
      createTimerAndMessageStartEventsModel();

  private static final BpmnModelInstance MULTI_TIMER_START_MODEL = createMultipleTimerStartModel();

  private static final BpmnModelInstance FEEL_DATE_TIME_EXPRESSION_MODEL =
      Bpmn.createExecutableProcess("process_5")
          .startEvent("start_5")
          .timerWithDateExpression("date and time(date(\"3978-11-25\"),time(\"T00:00:00@UTC\"))")
          .endEvent("end_5")
          .done();

  private static final BpmnModelInstance FEEL_CYCLE_EXPRESSION_MODEL =
      Bpmn.createExecutableProcess("process_5")
          .startEvent("start_6")
          .timerWithCycleExpression("cycle(duration(\"PT1S\"))")
          .endEvent("end_6")
          .done();

  @Rule public final EngineRule engine = EngineRule.singlePartition();

  private static BpmnModelInstance createTimerAndMessageStartEventsModel() {
    final ProcessBuilder builder = Bpmn.createExecutableProcess("process");
    builder.startEvent("none_start").endEvent("none_end");
    builder.startEvent("timer_start").timerWithCycle("R1/PT1S").endEvent("timer_end");
    return builder.startEvent("msg_start").message("msg1").endEvent("msg_end").done();
  }

  private static BpmnModelInstance createMultipleTimerStartModel() {
    final ProcessBuilder builder = Bpmn.createExecutableProcess("process_4");
    builder.startEvent("start_4").timerWithCycle("R/PT2S").endEvent("end_4");
    return builder.startEvent("start_4_2").timerWithCycle("R/PT3S").endEvent("end_4_2").done();
  }

  @Test
  public void shouldCreateTimer() {
    // when
    final var deployedProcess =
        engine
            .deployment()
            .withXmlResource(SIMPLE_MODEL)
            .deploy()
            .getValue()
            .getProcessesMetadata()
            .get(0);

    // then
    final TimerRecordValue timerRecord =
        RecordingExporter.timerRecords(TimerIntent.CREATED)
            .withProcessDefinitionKey(deployedProcess.getProcessDefinitionKey())
            .getFirst()
            .getValue();

    Assertions.assertThat(timerRecord)
        .hasProcessInstanceKey(TimerInstance.NO_ELEMENT_INSTANCE)
        .hasTargetElementId("start_1")
        .hasElementInstanceKey(TimerInstance.NO_ELEMENT_INSTANCE);

    final long now = engine.getClock().getCurrentTimeInMillis();
    assertThat(timerRecord.getDueDate()).isBetween(now, now + 1000L);
  }

  @Test
  public void shouldCreateTimerFromFeelExpression() {
    // when
    final var deployedProcess =
        engine
            .deployment()
            .withXmlResource(FEEL_DATE_TIME_EXPRESSION_MODEL)
            .deploy()
            .getValue()
            .getProcessesMetadata()
            .get(0);

    // then
    final TimerRecordValue timerRecord =
        RecordingExporter.timerRecords(TimerIntent.CREATED)
            .withProcessDefinitionKey(deployedProcess.getProcessDefinitionKey())
            .getFirst()
            .getValue();

    Assertions.assertThat(timerRecord)
        .hasProcessInstanceKey(TimerInstance.NO_ELEMENT_INSTANCE)
        .hasTargetElementId("start_5")
        .hasElementInstanceKey(TimerInstance.NO_ELEMENT_INSTANCE);

    final long expected =
        ZonedDateTime.of(LocalDate.of(3978, 11, 25), LocalTime.of(0, 0, 0), ZoneId.of("UTC"))
            .toInstant()
            .toEpochMilli();
    assertThat(timerRecord.getDueDate()).isEqualTo(expected);
  }

  @Test
  public void shouldNotReCreateTimerOnDuplicateDeployment() {
    // when
    final var firstVersion =
        Bpmn.createExecutableProcess("process")
            .startEvent("start_5")
            .timerWithDateExpression("now() + duration(\"PT15S\")")
            .endEvent("end_5")
            .done();

    final var secondVersion =
        Bpmn.createExecutableProcess("process")
            .startEvent("start_6")
            .timerWithDateExpression("now() + duration(\"PT15S\")")
            .endEvent("end_5")
            .done();

    final var deployedProcess =
        engine
            .deployment()
            .withXmlResource(firstVersion)
            .deploy()
            .getValue()
            .getProcessesMetadata()
            .get(0);

    final TimerRecordValue timerRecord =
        RecordingExporter.timerRecords(TimerIntent.CREATED)
            .withProcessDefinitionKey(deployedProcess.getProcessDefinitionKey())
            .getFirst()
            .getValue();

    // when
    engine.deployment().withXmlResource(firstVersion).deploy();

    // then
    final var secondVersionMetadata =
        engine
            .deployment()
            .withXmlResource(secondVersion)
            .deploy()
            .getValue()
            .getProcessesMetadata()
            .get(0);

    // expect only two timer creations

    final var actualTimerCount =
        RecordingExporter.timerRecords(TimerIntent.CREATED)
            .limit(
                timerRecordValueRecord ->
                    timerRecordValueRecord.getValue().getProcessDefinitionKey()
                        == secondVersionMetadata.getProcessDefinitionKey())
            .count();

    assertThat(actualTimerCount)
        .describedAs("Timer.CREATED count should be %d, but was %d", 2, actualTimerCount)
        .isEqualTo(2);
  }

  @Test
  public void shouldNotReTriggerTimerAfterDuplicateDeployment() {
    // when
    final var firstVersion =
        Bpmn.createExecutableProcess("process")
            .startEvent("start_5")
            .timerWithDateExpression("now() + duration(\"PT15S\")")
            .endEvent("end_5")
            .done();

    final var secondVersion =
        Bpmn.createExecutableProcess("process")
            .startEvent("start_6")
            .timerWithDateExpression("now() + duration(\"PT15S\")")
            .endEvent("end_5")
            .done();

    final var deployedProcess =
        engine
            .deployment()
            .withXmlResource(firstVersion)
            .deploy()
            .getValue()
            .getProcessesMetadata()
            .get(0);

    engine.awaitProcessingOf(
        RecordingExporter.timerRecords(TimerIntent.CREATED)
            .withProcessDefinitionKey(deployedProcess.getProcessDefinitionKey())
            .getFirst());

    // when
    engine.increaseTime(Duration.ofMinutes(1));
    RecordingExporter.timerRecords(TimerIntent.TRIGGER)
        .withProcessDefinitionKey(deployedProcess.getProcessDefinitionKey())
        .await();
    engine.deployment().withXmlResource(firstVersion).deploy();
    engine.increaseTime(Duration.ofMinutes(1));

    // then
    final var secondVersionMetadata =
        engine
            .deployment()
            .withXmlResource(secondVersion)
            .deploy()
            .getValue()
            .getProcessesMetadata()
            .get(0);
    engine.awaitProcessingOf(
        RecordingExporter.timerRecords(TimerIntent.CREATED)
            .withProcessDefinitionKey(secondVersionMetadata.getProcessDefinitionKey())
            .getFirst());
    engine.increaseTime(Duration.ofMinutes(1));
    RecordingExporter.timerRecords(TimerIntent.TRIGGER)
        .withProcessDefinitionKey(secondVersionMetadata.getProcessDefinitionKey())
        .await();

    // expect only two process instances created by a timer
    final var processInstanceRecords =
        RecordingExporter.processInstanceRecords()
            .limit(
                record ->
                    record.getIntent() == ProcessInstanceIntent.ACTIVATE_ELEMENT
                        && record.getValue().getProcessDefinitionKey()
                            == secondVersionMetadata.getProcessDefinitionKey())
            .collect(Collectors.toList());

    final var processInstanceActivateList =
        processInstanceRecords.stream()
            .filter(record -> record.getValue().getBpmnElementType() == BpmnElementType.PROCESS)
            .filter(record -> record.getIntent() == ProcessInstanceIntent.ACTIVATE_ELEMENT)
            .collect(Collectors.toList());

    assertThat(processInstanceActivateList)
        .describedAs(
            "Expect to trigger timer start events only for new deployments, but duplicate deployment causes retriggering of timer start event.")
        .hasSize(2)
        .extracting(record -> record.getValue().getProcessDefinitionKey())
        .containsExactly(
            deployedProcess.getProcessDefinitionKey(),
            secondVersionMetadata.getProcessDefinitionKey());
  }

  @Test
  public void shouldCreateRepeatingTimerFromFeelExpression() {
    // when
    final var deployedProcess =
        engine
            .deployment()
            .withXmlResource(FEEL_CYCLE_EXPRESSION_MODEL)
            .deploy()
            .getValue()
            .getProcessesMetadata()
            .get(0);

    // then
    final TimerRecordValue timerRecord =
        RecordingExporter.timerRecords(TimerIntent.CREATED)
            .withProcessDefinitionKey(deployedProcess.getProcessDefinitionKey())
            .getFirst()
            .getValue();

    Assertions.assertThat(timerRecord)
        .hasProcessInstanceKey(TimerInstance.NO_ELEMENT_INSTANCE)
        .hasTargetElementId("start_6")
        .hasElementInstanceKey(TimerInstance.NO_ELEMENT_INSTANCE);

    final long now = engine.getClock().getCurrentTimeInMillis();
    assertThat(timerRecord.getDueDate()).isBetween(now, now + 10000L);
  }

  @Test
  public void shouldTriggerAndCreateProcessInstance() {
    // given
    final var deployedProcess =
        engine
            .deployment()
            .withXmlResource(SIMPLE_MODEL)
            .deploy()
            .getValue()
            .getProcessesMetadata()
            .get(0);

    final long processDefinitionKey = deployedProcess.getProcessDefinitionKey();
    assertThat(
            RecordingExporter.timerRecords(TimerIntent.CREATED)
                .withProcessDefinitionKey(processDefinitionKey)
                .exists())
        .isTrue();

    // when
    engine.increaseTime(Duration.ofSeconds(2));

    // then
    final ProcessInstanceRecordValue startEventActivating =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withElementType(BpmnElementType.START_EVENT)
            .withProcessDefinitionKey(processDefinitionKey)
            .getFirst()
            .getValue();

    Assertions.assertThat(startEventActivating)
        .hasElementId("start_1")
        .hasBpmnProcessId("process")
        .hasVersion(deployedProcess.getVersion())
        .hasProcessDefinitionKey(processDefinitionKey);

    final long triggerRecordPosition =
        RecordingExporter.timerRecords(TimerIntent.TRIGGER)
            .withProcessDefinitionKey(processDefinitionKey)
            .getFirst()
            .getPosition();

    assertThat(
            RecordingExporter.timerRecords()
                .withProcessDefinitionKey(processDefinitionKey)
                .skipUntil(r -> r.getPosition() >= triggerRecordPosition)
                .limit(2))
        .extracting(Record::getIntent)
        .containsExactly(TimerIntent.TRIGGER, TimerIntent.TRIGGERED);

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessDefinitionKey(processDefinitionKey)
                .skipUntil(r -> r.getPosition() >= triggerRecordPosition)
                .limit(4))
        .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
        .containsExactly(
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ACTIVATE_ELEMENT),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.START_EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATING));
  }

  @Test
  public void shouldCreateMultipleProcessInstancesWithRepeatingTimer() {
    // given
    final var deployedProcess =
        engine
            .deployment()
            .withXmlResource(THREE_SEC_MODEL)
            .deploy()
            .getValue()
            .getProcessesMetadata()
            .get(0);
    final long processDefinitionKey = deployedProcess.getProcessDefinitionKey();

    // when
    assertThat(
            RecordingExporter.timerRecords(TimerIntent.CREATED)
                .withProcessDefinitionKey(processDefinitionKey)
                .exists())
        .isTrue();
    engine.increaseTime(Duration.ofSeconds(3));

    // then
    assertThat(
            RecordingExporter.timerRecords(TimerIntent.TRIGGERED)
                .withProcessDefinitionKey(processDefinitionKey)
                .exists())
        .isTrue();
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
                .withElementId("process_3")
                .withProcessDefinitionKey(processDefinitionKey)
                .exists())
        .isTrue();
    assertThat(
            RecordingExporter.timerRecords(TimerIntent.CREATED)
                .withProcessDefinitionKey(processDefinitionKey)
                .limit(2)
                .count())
        .isEqualTo(2);

    // when
    engine.increaseTime(Duration.ofSeconds(3));

    // then
    assertThat(
            RecordingExporter.timerRecords(TimerIntent.TRIGGERED)
                .withProcessDefinitionKey(processDefinitionKey)
                .limit(2)
                .count())
        .isEqualTo(2);
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
                .withElementId("process_3")
                .withProcessDefinitionKey(processDefinitionKey)
                .limit(2)
                .count())
        .isEqualTo(2);
  }

  @Test
  public void shouldCompleteProcess() {
    // given
    final var deployedProcess =
        engine
            .deployment()
            .withXmlResource(SIMPLE_MODEL)
            .deploy()
            .getValue()
            .getProcessesMetadata()
            .get(0);
    assertThat(RecordingExporter.timerRecords(TimerIntent.CREATED).exists()).isTrue();

    // when
    engine.increaseTime(Duration.ofSeconds(1));

    // then
    final ProcessInstanceRecordValue instanceCompleted =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
            .withProcessDefinitionKey(deployedProcess.getProcessDefinitionKey())
            .withElementId("process")
            .getFirst()
            .getValue();

    Assertions.assertThat(instanceCompleted)
        .hasBpmnProcessId("process")
        .hasVersion(1)
        .hasProcessDefinitionKey(deployedProcess.getProcessDefinitionKey());
  }

  @Test
  public void shouldUpdateProcess() {
    // when
    final var deployedProcess =
        engine
            .deployment()
            .withXmlResource(SIMPLE_MODEL)
            .deploy()
            .getValue()
            .getProcessesMetadata()
            .get(0);
    assertThat(
            RecordingExporter.timerRecords(TimerIntent.CREATED)
                .withProcessDefinitionKey(deployedProcess.getProcessDefinitionKey())
                .exists())
        .isTrue();
    engine.increaseTime(Duration.ofSeconds(1));

    // then
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withElementId("end_1")
                .withBpmnProcessId("process")
                .withProcessDefinitionKey(deployedProcess.getProcessDefinitionKey())
                .withVersion(1)
                .exists())
        .isTrue();

    // when
    final var repeatingProcess =
        engine
            .deployment()
            .withXmlResource(REPEATING_MODEL)
            .deploy()
            .getValue()
            .getProcessesMetadata()
            .get(0);
    assertThat(
            RecordingExporter.timerRecords(TimerIntent.CREATED)
                .withProcessDefinitionKey(repeatingProcess.getProcessDefinitionKey())
                .exists())
        .isTrue();
    engine.increaseTime(Duration.ofSeconds(2));

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withElementId("end_2")
                .withBpmnProcessId("process")
                .withProcessDefinitionKey(repeatingProcess.getProcessDefinitionKey())
                .withVersion(2)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldReplaceTimerStartWithNoneStart() {
    // when
    final var repeatingProcess =
        engine
            .deployment()
            .withXmlResource(REPEATING_MODEL)
            .deploy()
            .getValue()
            .getProcessesMetadata()
            .get(0);
    final long repeatingProcessDefinitionKey = repeatingProcess.getProcessDefinitionKey();
    assertThat(
            RecordingExporter.timerRecords(TimerIntent.CREATED)
                .withProcessDefinitionKey(repeatingProcessDefinitionKey)
                .exists())
        .isTrue();
    engine.increaseTime(Duration.ofSeconds(1));

    // then
    assertThat(
            RecordingExporter.timerRecords(TimerIntent.TRIGGERED)
                .withProcessDefinitionKey(repeatingProcessDefinitionKey)
                .exists())
        .isTrue();

    // when
    final BpmnModelInstance nonTimerModel =
        Bpmn.createExecutableProcess("process").startEvent("start_4").endEvent("end_4").done();
    final var notTimerDeployment =
        engine
            .deployment()
            .withXmlResource(nonTimerModel)
            .deploy()
            .getValue()
            .getProcessesMetadata()
            .get(0);
    engine.increaseTime(Duration.ofSeconds(2));

    // then
    assertThat(
            RecordingExporter.timerRecords(TimerIntent.CANCELED)
                .withProcessDefinitionKey(repeatingProcessDefinitionKey)
                .exists())
        .isTrue();
    assertThat(
            RecordingExporter.timerRecords(TimerIntent.TRIGGERED)
                .withProcessDefinitionKey(repeatingProcessDefinitionKey)
                .exists())
        .isTrue();

    final long processInstanceKey = engine.processInstance().ofBpmnProcessId("process").create();

    final ProcessInstanceRecordValue lastRecord =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withElementId("end_4")
            .withProcessDefinitionKey(notTimerDeployment.getProcessDefinitionKey())
            .getFirst()
            .getValue();

    Assertions.assertThat(lastRecord)
        .hasVersion(2)
        .hasBpmnProcessId("process")
        .hasProcessInstanceKey(processInstanceKey);
  }

  @Test
  public void shouldUpdateTimerPeriod() {
    // given
    final var deployedProcess =
        engine
            .deployment()
            .withXmlResource(THREE_SEC_MODEL)
            .deploy()
            .getValue()
            .getProcessesMetadata()
            .get(0);

    final long processDefinitionKey = deployedProcess.getProcessDefinitionKey();
    assertThat(
            RecordingExporter.timerRecords(TimerIntent.CREATED)
                .withProcessDefinitionKey(processDefinitionKey)
                .exists())
        .isTrue();

    // when
    final long now = engine.getClock().getCurrentTimeInMillis();
    engine.increaseTime(Duration.ofSeconds(3));

    // then
    TimerRecordValue timerRecord =
        RecordingExporter.timerRecords(TimerIntent.TRIGGERED)
            .withProcessDefinitionKey(processDefinitionKey)
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
    final var slowerDeployment =
        engine
            .deployment()
            .withXmlResource(slowerModel)
            .deploy()
            .getValue()
            .getProcessesMetadata()
            .get(0);

    // then
    assertThat(
            RecordingExporter.timerRecords(TimerIntent.CANCELED)
                .withProcessDefinitionKey(processDefinitionKey)
                .getFirst())
        .isNotNull();

    final Record<TimerRecordValue> slowTimerRecord =
        RecordingExporter.timerRecords(TimerIntent.CREATED)
            .withProcessDefinitionKey(slowerDeployment.getProcessDefinitionKey())
            .getFirst();
    timerRecord = slowTimerRecord.getValue();
    final long writtenTime = slowTimerRecord.getTimestamp();
    assertThat(timerRecord.getDueDate()).isBetween(writtenTime, writtenTime + 4000);

    // when
    engine.increaseTime(Duration.ofSeconds(4));

    // then
    assertThat(
            RecordingExporter.timerRecords(TimerIntent.TRIGGERED)
                .withProcessDefinitionKey(slowerDeployment.getProcessDefinitionKey())
                .exists())
        .isTrue();
  }

  @Test
  public void shouldTriggerDifferentProcessesSeparately() {
    // given
    final var firstDeployment =
        engine
            .deployment()
            .withXmlResource(THREE_SEC_MODEL)
            .deploy()
            .getValue()
            .getProcessesMetadata()
            .get(0);

    final var secondDeployment =
        engine
            .deployment()
            .withXmlResource(REPEATING_MODEL)
            .deploy()
            .getValue()
            .getProcessesMetadata()
            .get(0);

    assertThat(
            RecordingExporter.timerRecords(TimerIntent.CREATED)
                .withProcessDefinitionKey(firstDeployment.getProcessDefinitionKey())
                .exists())
        .isTrue();

    assertThat(
            RecordingExporter.timerRecords(TimerIntent.CREATED)
                .withProcessDefinitionKey(secondDeployment.getProcessDefinitionKey())
                .exists())
        .isTrue();

    // when
    engine.increaseTime(Duration.ofSeconds(1));

    // then
    final long firstModelTimestamp =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withElementId("process")
            .getFirst()
            .getTimestamp();

    // when
    engine.increaseTime(Duration.ofSeconds(2));

    // then
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
                .withElementId("process")
                .withProcessDefinitionKey(secondDeployment.getProcessDefinitionKey())
                .limit(2)
                .count())
        .isEqualTo(2);

    final long secondModelTimestamp =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withElementId("process_3")
            .withProcessDefinitionKey(firstDeployment.getProcessDefinitionKey())
            .getFirst()
            .getTimestamp();
    assertThat(secondModelTimestamp).isGreaterThan(firstModelTimestamp);
  }

  @Test
  public void shouldCreateMultipleInstanceAtTheCorrectTimes() {
    // given
    final var deployedProcess =
        engine
            .deployment()
            .withXmlResource(MULTI_TIMER_START_MODEL)
            .deploy()
            .getValue()
            .getProcessesMetadata()
            .get(0);
    assertThat(
            RecordingExporter.timerRecords(TimerIntent.CREATED)
                .withProcessDefinitionKey(deployedProcess.getProcessDefinitionKey())
                .limit(2)
                .count())
        .isEqualTo(2);

    // when
    engine.increaseTime(Duration.ofSeconds(2));

    // then
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withElementId("end_4")
                .withProcessDefinitionKey(deployedProcess.getProcessDefinitionKey())
                .exists())
        .isTrue();

    // when
    engine.increaseTime(Duration.ofSeconds(1));
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withElementId("end_4_2")
                .withProcessDefinitionKey(deployedProcess.getProcessDefinitionKey())
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

    final var deployedProcess =
        engine
            .deployment()
            .withXmlResource(model)
            .deploy()
            .getValue()
            .getProcessesMetadata()
            .get(0);

    // when
    engine.increaseTime(Duration.ofSeconds(2));

    // then
    final TimerRecordValue timerRecord =
        RecordingExporter.timerRecords(TimerIntent.TRIGGERED)
            .withProcessDefinitionKey(deployedProcess.getProcessDefinitionKey())
            .getFirst()
            .getValue();

    Assertions.assertThat(timerRecord)
        .hasDueDate(triggerTime.toEpochMilli())
        .hasTargetElementId("start_2")
        .hasElementInstanceKey(TimerInstance.NO_ELEMENT_INSTANCE);

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withElementId("end_2")
                .withProcessDefinitionKey(deployedProcess.getProcessDefinitionKey())
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

    final var deployedProcess =
        engine
            .deployment()
            .withXmlResource(model)
            .deploy()
            .getValue()
            .getProcessesMetadata()
            .get(0);

    // when
    engine.increaseTime(Duration.ofMillis(2000L));

    // then
    final TimerRecordValue timerRecord =
        RecordingExporter.timerRecords(TimerIntent.TRIGGERED)
            .withProcessDefinitionKey(deployedProcess.getProcessDefinitionKey())
            .getFirst()
            .getValue();

    Assertions.assertThat(timerRecord)
        .hasDueDate(triggerTime.toEpochMilli())
        .hasTargetElementId("start_2")
        .hasElementInstanceKey(TimerInstance.NO_ELEMENT_INSTANCE);
  }

  @Test
  public void shouldTriggerOnlyTimerStartEvent() {
    // given
    final var deployedProcess =
        engine
            .deployment()
            .withXmlResource(MULTIPLE_START_EVENTS_MODEL)
            .deploy()
            .getValue()
            .getProcessesMetadata()
            .get(0);
    final long processDefinitionKey = deployedProcess.getProcessDefinitionKey();

    RecordingExporter.timerRecords(TimerIntent.CREATED)
        .withProcessDefinitionKey(processDefinitionKey)
        .await();

    // when
    engine.increaseTime(Duration.ofSeconds(1));

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessDefinitionKey(processDefinitionKey)
                .limitToProcessInstanceCompleted()
                .withElementType(BpmnElementType.START_EVENT))
        .extracting(r -> r.getValue().getElementId())
        .containsOnly("timer_start");
  }

  @Test
  public void shouldWriteTriggeredEventWithProcessInstanceKey() {
    // given
    final var deployedProcess =
        engine
            .deployment()
            .withXmlResource(SIMPLE_MODEL)
            .deploy()
            .getValue()
            .getProcessesMetadata()
            .get(0);

    final long processDefinitionKey = deployedProcess.getProcessDefinitionKey();

    assertThat(
            RecordingExporter.timerRecords(TimerIntent.CREATED)
                .withProcessDefinitionKey(processDefinitionKey)
                .exists())
        .isTrue();

    // when
    engine.increaseTime(Duration.ofSeconds(2));

    // then
    final var processInstanceKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withElementType(BpmnElementType.PROCESS)
            .withProcessDefinitionKey(processDefinitionKey)
            .getFirst()
            .getKey();

    final var timerTriggered =
        RecordingExporter.timerRecords(TimerIntent.TRIGGERED)
            .withProcessDefinitionKey(processDefinitionKey)
            .getFirst();

    Assertions.assertThat(timerTriggered.getValue())
        .hasProcessDefinitionKey(processDefinitionKey)
        .hasProcessInstanceKey(processInstanceKey)
        .hasElementInstanceKey(-1L)
        .hasTargetElementId("start_1");
  }

  @Test
  public void shouldNotTriggerBeforeTheStartTime() {
    // given
    final ZonedDateTime start =
        ZonedDateTime.ofInstant(
            Instant.ofEpochMilli(engine.getClock().getCurrentTimeInMillis()),
            ZoneId.systemDefault());

    final long firstDueDate = start.plusSeconds(10).toInstant().toEpochMilli();
    final long secondDueDate = start.plusSeconds(40).toInstant().toEpochMilli();
    final BpmnModelInstance firstModel =
        Bpmn.createExecutableProcess("process_1")
            .startEvent("start_1")
            .timerWithCycle(String.format("R1/%s/PT10S", start.plusSeconds(10)))
            .endEvent("end_1")
            .done();

    final BpmnModelInstance secondModel =
        Bpmn.createExecutableProcess("process_2")
            .startEvent("start_2")
            .timerWithCycle(String.format("R1/%s/PT10S", start.plusSeconds(40)))
            .endEvent("end_2")
            .done();

    final var firstDeployment =
        engine
            .deployment()
            .withXmlResource(firstModel)
            .deploy()
            .getValue()
            .getProcessesMetadata()
            .get(0);

    final var secondDeployment =
        engine
            .deployment()
            .withXmlResource(secondModel)
            .deploy()
            .getValue()
            .getProcessesMetadata()
            .get(0);

    // when
    engine.increaseTime(Duration.ofSeconds(20));

    // then
    assertThat(
            RecordingExporter.timerRecords(TimerIntent.TRIGGERED)
                .withProcessDefinitionKey(firstDeployment.getProcessDefinitionKey())
                .exists())
        .isTrue();

    final TimerRecordValue timerRecord =
        RecordingExporter.timerRecords(TimerIntent.TRIGGERED)
            .withProcessDefinitionKey(firstDeployment.getProcessDefinitionKey())
            .getFirst()
            .getValue();

    Assertions.assertThat(timerRecord)
        .hasDueDate(firstDueDate)
        .hasTargetElementId("start_1")
        .hasElementInstanceKey(TimerInstance.NO_ELEMENT_INSTANCE);

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withElementId("end_1")
                .withProcessDefinitionKey(firstDeployment.getProcessDefinitionKey())
                .exists())
        .isTrue();

    assertThat(RecordingExporter.timerRecords(TimerIntent.CREATED).limit(2))
        .extracting(Record::getValue)
        .extracting(TimerRecordValue::getProcessDefinitionKey, TimerRecordValue::getDueDate)
        .contains(
            tuple(firstDeployment.getProcessDefinitionKey(), firstDueDate),
            tuple(secondDeployment.getProcessDefinitionKey(), secondDueDate));

    // when
    engine.increaseTime(Duration.ofSeconds(20));

    // then
    assertThat(
            RecordingExporter.timerRecords(TimerIntent.TRIGGERED)
                .withProcessDefinitionKey(secondDeployment.getProcessDefinitionKey())
                .exists())
        .isTrue();

    final TimerRecordValue secondTimerRecord =
        RecordingExporter.timerRecords(TimerIntent.TRIGGERED)
            .withProcessDefinitionKey(secondDeployment.getProcessDefinitionKey())
            .getFirst()
            .getValue();

    Assertions.assertThat(secondTimerRecord)
        .hasDueDate(secondDueDate)
        .hasTargetElementId("start_2")
        .hasElementInstanceKey(TimerInstance.NO_ELEMENT_INSTANCE);

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withElementId("process_2")
                .withProcessDefinitionKey(secondDeployment.getProcessDefinitionKey())
                .exists())
        .isTrue();
  }

  @Test
  public void shouldTriggerOnlyTwice() {
    // given
    final ZonedDateTime start =
        ZonedDateTime.ofInstant(
                Instant.ofEpochMilli(engine.getClock().getCurrentTimeInMillis()),
                ZoneId.systemDefault())
            .plusSeconds(10);

    final BpmnModelInstance firstModel =
        Bpmn.createExecutableProcess("process_1")
            .startEvent("start_1")
            .timerWithCycle(String.format("R2/%s/PT10S", start))
            .endEvent("end_1")
            .done();

    final BpmnModelInstance secondModel =
        Bpmn.createExecutableProcess("process_2")
            .startEvent("start_2")
            .timerWithCycle(String.format("R3/%s/PT10S", start))
            .endEvent("end_2")
            .done();

    final var firstDeployment =
        engine
            .deployment()
            .withXmlResource(firstModel)
            .deploy()
            .getValue()
            .getProcessesMetadata()
            .get(0);

    final var secondDeployment =
        engine
            .deployment()
            .withXmlResource(secondModel)
            .deploy()
            .getValue()
            .getProcessesMetadata()
            .get(0);
    final long firstDeploymentProcessDefinitionKey = firstDeployment.getProcessDefinitionKey();
    final long secondDeploymentProcessDefinitionKey = secondDeployment.getProcessDefinitionKey();

    // when
    engine.increaseTime(Duration.ofSeconds(25));

    // then
    assertThat(
            RecordingExporter.timerRecords(TimerIntent.TRIGGERED)
                .withProcessDefinitionKey(firstDeploymentProcessDefinitionKey)
                .exists())
        .isTrue();

    assertThat(
            RecordingExporter.timerRecords(TimerIntent.TRIGGERED)
                .withProcessDefinitionKey(secondDeploymentProcessDefinitionKey)
                .exists())
        .isTrue();

    // when
    engine.increaseTime(Duration.ofSeconds(5));

    // then
    assertThat(RecordingExporter.timerRecords(TimerIntent.TRIGGERED).limit(4))
        .extracting(record -> record.getValue().getProcessDefinitionKey())
        .containsExactly(
            firstDeploymentProcessDefinitionKey,
            secondDeploymentProcessDefinitionKey,
            firstDeploymentProcessDefinitionKey,
            secondDeploymentProcessDefinitionKey);

    // when
    engine.increaseTime(Duration.ofSeconds(10));

    // then
    assertThat(RecordingExporter.timerRecords(TimerIntent.TRIGGERED).limit(5))
        .extracting(record -> record.getValue().getProcessDefinitionKey())
        .containsExactly(
            firstDeploymentProcessDefinitionKey,
            secondDeploymentProcessDefinitionKey,
            firstDeploymentProcessDefinitionKey,
            secondDeploymentProcessDefinitionKey,
            secondDeploymentProcessDefinitionKey);
  }

  @Test
  public void shouldAvoidTimeShifting() {
    // given
    // Set the start time to 10 seconds later
    final ZonedDateTime start =
        ZonedDateTime.ofInstant(
                Instant.ofEpochMilli(engine.getClock().getCurrentTimeInMillis()),
                ZoneId.systemDefault())
            .plusSeconds(10);

    final long dueDate = start.toInstant().toEpochMilli();
    final long lastDueDate = dueDate + 10_000L;
    final BpmnModelInstance model =
        Bpmn.createExecutableProcess("process")
            .startEvent("start")
            .timerWithCycle(String.format("R2/%s/PT10S", start))
            .endEvent("end")
            .done();
    final var deployedProcess =
        engine
            .deployment()
            .withXmlResource(model)
            .deploy()
            .getValue()
            .getProcessesMetadata()
            .get(0);
    final long processDefinitionKey = deployedProcess.getProcessDefinitionKey();

    // when
    engine.increaseTime(Duration.ofSeconds(15));

    // then
    final TimerRecordValue timerRecord =
        RecordingExporter.timerRecords(TimerIntent.TRIGGERED)
            .withProcessDefinitionKey(deployedProcess.getProcessDefinitionKey())
            .getFirst()
            .getValue();

    Assertions.assertThat(timerRecord)
        .hasDueDate(dueDate)
        .hasTargetElementId("start")
        .hasElementInstanceKey(TimerInstance.NO_ELEMENT_INSTANCE);

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withElementId("end")
                .withProcessDefinitionKey(deployedProcess.getProcessDefinitionKey())
                .exists())
        .isTrue();

    // when
    engine.increaseTime(Duration.ofSeconds(5));

    // then
    final TimerRecordValue lastTimerRecord =
        RecordingExporter.timerRecords(TimerIntent.TRIGGERED)
            .withProcessDefinitionKey(deployedProcess.getProcessDefinitionKey())
            .skip(1)
            .getFirst()
            .getValue();

    Assertions.assertThat(lastTimerRecord)
        .hasDueDate(lastDueDate)
        .hasTargetElementId("start")
        .hasElementInstanceKey(TimerInstance.NO_ELEMENT_INSTANCE);

    assertThat(
            RecordingExporter.timerRecords(TimerIntent.TRIGGERED)
                .withProcessDefinitionKey(processDefinitionKey)
                .limit(2)
                .count())
        .isEqualTo(2);
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
                .withElementId("process")
                .withProcessDefinitionKey(processDefinitionKey)
                .limit(2)
                .count())
        .isEqualTo(2);
  }
}
