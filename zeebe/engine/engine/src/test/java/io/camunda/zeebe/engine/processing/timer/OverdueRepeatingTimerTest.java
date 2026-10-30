/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.timer;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.TimerIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.TimerRecordValue;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.time.Duration;
import org.junit.Rule;
import org.junit.Test;

/**
 * Covers repeating timers ({@code R.../PT..}) that become overdue by more than one interval -- e.g.
 * because the broker was down for a while -- and verifies they fire exactly once for the whole gap
 * instead of firing again immediately after the first catch-up trigger.
 *
 * <p>{@code TimerTriggerProcessor#refreshTimer} must not reschedule such a timer onto a due date
 * that already lies in the past: doing so lets the due-date clamp in {@code Interval#toEpochMilli}
 * collapse the reschedule onto "now", firing the timer again immediately.
 */
public final class OverdueRepeatingTimerTest {

  @Rule public final EngineRule engine = EngineRule.singlePartition();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldFireCyclingStartEventExactlyOnceWhenOverdueForSeveralIntervals() {
    // given
    final String processId = Strings.newRandomValidBpmnId();
    final long processDefinitionKey =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent("start")
                    .timerWithCycle("R/PT2S")
                    .endEvent()
                    .done())
            .deploy()
            .getValue()
            .getProcessesMetadata()
            .get(0)
            .getProcessDefinitionKey();
    RecordingExporter.timerRecords(TimerIntent.CREATED)
        .withProcessDefinitionKey(processDefinitionKey)
        .getFirst();

    // when - the start event's timer becomes overdue by ~4 intervals in a single jump
    engine.increaseTime(Duration.ofSeconds(10));

    // then
    final Record<TimerRecordValue> triggered =
        RecordingExporter.timerRecords(TimerIntent.TRIGGERED)
            .withProcessDefinitionKey(processDefinitionKey)
            .getFirst();
    final Record<TimerRecordValue> rescheduled =
        RecordingExporter.timerRecords(TimerIntent.CREATED)
            .withProcessDefinitionKey(processDefinitionKey)
            .limit(2)
            .getLast();

    assertThat(
            RecordingExporter.<Boolean>expectNoMatchingRecords(
                records ->
                    RecordingExporter.timerRecords(TimerIntent.TRIGGERED)
                        .withProcessDefinitionKey(processDefinitionKey)
                        .skip(1)
                        .exists()))
        .describedAs("timer must fire exactly once for the whole overdue gap, not twice")
        .isFalse();
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessDefinitionKey(processDefinitionKey)
        .withElementType(BpmnElementType.PROCESS)
        .getFirst();
    assertThat(
            RecordingExporter.<Boolean>expectNoMatchingRecords(
                records ->
                    RecordingExporter.processInstanceRecords(
                            ProcessInstanceIntent.ELEMENT_ACTIVATED)
                        .withProcessDefinitionKey(processDefinitionKey)
                        .withElementType(BpmnElementType.PROCESS)
                        .skip(1)
                        .exists()))
        .describedAs(
            "exactly one process instance must be spawned for the whole overdue gap, not more")
        .isFalse();
    assertThat(rescheduled.getValue().getDueDate() - triggered.getTimestamp())
        .describedAs("reschedule must land in the future, not collapse onto the trigger time")
        .isGreaterThanOrEqualTo(500L);
  }

  @Test
  public void
      shouldConsumeExactlyOneRepetitionWhenFiniteRepetitionsTimerIsOverdueForSeveralIntervals() {
    // given
    final String processId = Strings.newRandomValidBpmnId();
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType(processId))
                .boundaryEvent("timer", b -> b.cancelActivity(false).timerWithCycle("R3/PT2S"))
                .endEvent()
                .moveToActivity("task")
                .endEvent()
                .done())
        .deploy();
    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();
    final Record<TimerRecordValue> firstCreated =
        RecordingExporter.timerRecords(TimerIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    final long elementInstanceKey = firstCreated.getValue().getElementInstanceKey();
    assertThat(firstCreated.getValue().getRepetitions()).isEqualTo(3);

    // when - the timer becomes overdue by ~4 intervals in a single jump
    engine.increaseTime(Duration.ofSeconds(10));

    // then
    final Record<TimerRecordValue> triggered =
        RecordingExporter.timerRecords(TimerIntent.TRIGGERED)
            .withElementInstanceKey(elementInstanceKey)
            .getFirst();
    final Record<TimerRecordValue> rescheduled =
        RecordingExporter.timerRecords(TimerIntent.CREATED)
            .withElementInstanceKey(elementInstanceKey)
            .limit(2)
            .getLast();

    assertTimerTriggeredExactlyOnce(
        elementInstanceKey, "timer must fire exactly once for the whole overdue gap, not twice");
    assertThat(rescheduled.getValue().getRepetitions())
        .describedAs("a multi-interval gap must consume only one repetition, not several")
        .isEqualTo(2);
    assertThat(rescheduled.getValue().getDueDate() - triggered.getTimestamp())
        .describedAs("reschedule must land in the future, not collapse onto the trigger time")
        .isGreaterThanOrEqualTo(500L);
  }

  @Test
  public void shouldFireExactlyOnceAfterRestartWhenOverdueForLongerThanOneInterval() {
    // given
    final String processId = Strings.newRandomValidBpmnId();
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType(processId))
                .boundaryEvent("timer", b -> b.cancelActivity(false).timerWithCycle("R/PT2S"))
                .endEvent()
                .moveToActivity("task")
                .endEvent()
                .done())
        .deploy();
    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();
    final Record<TimerRecordValue> firstCreated =
        RecordingExporter.timerRecords(TimerIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    final long elementInstanceKey = firstCreated.getValue().getElementInstanceKey();
    engine.snapshot();
    engine.stop();

    // when - broker downtime spans ~4 intervals while the engine is stopped; increaseTime()
    // cannot be used here because it awaits the (now closed) stream processor, so the clock is
    // advanced directly instead
    engine.getClock().addTime(Duration.ofSeconds(10));
    RecordingExporter.reset();
    engine.start();

    // then - the recovery re-scan (DueDateTimerCheckScheduler#onRecovered) fires the timer once
    final Record<TimerRecordValue> triggered =
        RecordingExporter.timerRecords(TimerIntent.TRIGGERED)
            .withElementInstanceKey(elementInstanceKey)
            .getFirst();
    // RecordingExporter.reset() cleared the exporter's bookkeeping, so the pre-restart TIMER
    // CREATED record (from process creation) is re-discovered as well; skip it to reach the
    // reschedule created by processing the recovery TRIGGER.
    final Record<TimerRecordValue> rescheduled =
        RecordingExporter.timerRecords(TimerIntent.CREATED)
            .withElementInstanceKey(elementInstanceKey)
            .limit(2)
            .getLast();

    assertTimerTriggeredExactlyOnce(
        elementInstanceKey,
        "timer must fire exactly once after recovering from the overdue gap, not twice");
    assertThat(rescheduled.getValue().getDueDate() - triggered.getTimestamp())
        .describedAs("reschedule must land in the future, not collapse onto the trigger time")
        .isGreaterThanOrEqualTo(500L);
  }

  @Test
  public void shouldFireExactlyOnceAndPushRescheduleWhenNaturalNextDueDateExactlyEqualsNow() {
    // given
    final String processId = Strings.newRandomValidBpmnId();
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType(processId))
                .boundaryEvent("timer", b -> b.cancelActivity(false).timerWithCycle("R/PT1S"))
                .endEvent()
                .moveToActivity("task")
                .endEvent()
                .done())
        .deploy();
    // Pin the clock: otherwise wall-clock time elapsed during this test would drift "now" away
    // from the exact boundary this test targets.
    engine.getClock().pinCurrentTime();
    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();
    final Record<TimerRecordValue> firstCreated =
        RecordingExporter.timerRecords(TimerIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    final long elementInstanceKey = firstCreated.getValue().getElementInstanceKey();
    final long firstDueDate = firstCreated.getValue().getDueDate();

    // when - "push on the edge": advancing by exactly two intervals makes the natural next due
    // date (firstDueDate + one interval) land exactly on "now", the boundary that must still
    // count as overdue rather than resolve onto
    engine.increaseTime(Duration.ofSeconds(2));

    // then
    RecordingExporter.timerRecords(TimerIntent.TRIGGERED)
        .withElementInstanceKey(elementInstanceKey)
        .await();
    final Record<TimerRecordValue> rescheduled =
        RecordingExporter.timerRecords(TimerIntent.CREATED)
            .withElementInstanceKey(elementInstanceKey)
            .limit(2)
            .getLast();

    assertTimerTriggeredExactlyOnce(
        elementInstanceKey,
        "a natural next due date landing exactly on now must still count as overdue and fire"
            + " only once");
    assertThat(rescheduled.getValue().getDueDate())
        .describedAs(
            "push on the edge: an exact match with now must push the reschedule a full interval"
                + " past now, not resolve onto now")
        .isEqualTo(firstDueDate + Duration.ofSeconds(2).toMillis());
  }

  @Test
  public void shouldKeepCadenceUnchangedWhenGapIsShorterThanOneInterval() {
    // given
    final String processId = Strings.newRandomValidBpmnId();
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType(processId))
                .boundaryEvent("timer", b -> b.cancelActivity(false).timerWithCycle("R/PT1S"))
                .endEvent()
                .moveToActivity("task")
                .endEvent()
                .done())
        .deploy();
    // Pin the clock: otherwise wall-clock time elapsed during this test would push the actual
    // delay past the one-interval threshold this test relies on staying under.
    engine.getClock().pinCurrentTime();
    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();
    final Record<TimerRecordValue> firstCreated =
        RecordingExporter.timerRecords(TimerIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    final long elementInstanceKey = firstCreated.getValue().getElementInstanceKey();
    final long firstDueDate = firstCreated.getValue().getDueDate();

    // when - the trigger runs 0.5s late, still short of a full interval: no multi-interval gap
    engine.increaseTime(Duration.ofMillis(1500));

    // then
    RecordingExporter.timerRecords(TimerIntent.TRIGGERED)
        .withElementInstanceKey(elementInstanceKey)
        .await();
    final Record<TimerRecordValue> rescheduled =
        RecordingExporter.timerRecords(TimerIntent.CREATED)
            .withElementInstanceKey(elementInstanceKey)
            .limit(2)
            .getLast();

    assertTimerTriggeredExactlyOnce(
        elementInstanceKey, "normal cadence must fire exactly once, not more");
    assertThat(rescheduled.getValue().getDueDate())
        .describedAs(
            "normal cadence must reschedule exactly one interval past the previous due date")
        .isEqualTo(firstDueDate + Duration.ofSeconds(1).toMillis());
  }

  /**
   * Asserts that exactly one {@code TimerIntent#TRIGGERED} record was written for {@code
   * elementInstanceKey}. Uses {@link RecordingExporter#expectNoMatchingRecords} rather than a plain
   * count check: a duplicate trigger can be written asynchronously after the first one is observed,
   * so this actively waits out a timeout to confirm no second record ever arrives, instead of
   * racily checking whatever has already been exported at the time of the call.
   */
  private static void assertTimerTriggeredExactlyOnce(
      final long elementInstanceKey, final String description) {
    assertThat(
            RecordingExporter.<Boolean>expectNoMatchingRecords(
                records ->
                    RecordingExporter.timerRecords(TimerIntent.TRIGGERED)
                        .withElementInstanceKey(elementInstanceKey)
                        .skip(1)
                        .exists()))
        .describedAs(description)
        .isFalse();
  }
}
