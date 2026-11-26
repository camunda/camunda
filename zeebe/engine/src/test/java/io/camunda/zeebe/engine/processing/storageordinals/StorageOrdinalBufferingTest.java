/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.storageordinals;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.BufferedCommandIntent;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.TimerIntent;
import io.camunda.zeebe.protocol.record.value.BufferedCommandRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.time.Duration;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;

/**
 * Pins the storage ordinal of BUFFERED records for commands written by the engine itself (scheduler
 * or processor). Such a command value must already carry the ordinal, and the BUFFERED record
 * copies it from the value.
 */
public final class StorageOrdinalBufferingTest {

  private static final int FIXED_ORDINAL = 1234;

  @Rule
  public final EngineRule engine =
      EngineRule.singlePartition()
          .maxCommandsInBatch(1)
          .withEngineConfig(
              c -> c.setArchiverlessEnabled(true).setFixedStorageOrdinals(List.of(FIXED_ORDINAL)));

  @Rule public final RecordingExporterTestWatcher watcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldAssignConfiguredOrdinalToBufferedTimerTriggerWrittenByScheduler() {
    // given
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("ordinal-buffer-timer")
                .startEvent()
                .intermediateCatchEvent("timer-catch", c -> c.timerWithDuration("PT1H"))
                .endEvent()
                .done())
        .deploy();
    final long processInstanceKey =
        engine.processInstance().ofBpmnProcessId("ordinal-buffer-timer").create();
    RecordingExporter.timerRecords(TimerIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .getFirst();
    engine.processInstance().withInstanceKey(processInstanceKey).suspend();

    // when
    engine.increaseTime(Duration.ofHours(2));

    // then
    final var trigger =
        RecordingExporter.timerRecords(TimerIntent.TRIGGER)
            .onlyCommands()
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    assertThat(trigger.getValue().getStorageOrdinal()).isEqualTo(FIXED_ORDINAL);
    assertThat(bufferedCommand(processInstanceKey, ValueType.TIMER, TimerIntent.TRIGGER))
        .extracting(BufferedCommandRecordValue::getStorageOrdinal)
        .isEqualTo(FIXED_ORDINAL);
  }

  @Test
  public void shouldAssignConfiguredOrdinalToBufferedJobRecurAfterBackoff() {
    // given
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("ordinal-buffer-backoff")
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType("ordinal-backoff-task"))
                .endEvent()
                .done())
        .deploy();
    final long processInstanceKey =
        engine.processInstance().ofBpmnProcessId("ordinal-buffer-backoff").create();
    engine.jobs().withType("ordinal-backoff-task").activate();
    final long jobKey =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst()
            .getKey();
    engine.job().withKey(jobKey).withRetries(1).withBackOff(Duration.ofMinutes(1)).fail();
    RecordingExporter.jobRecords(JobIntent.FAILED).withRecordKey(jobKey).await();
    engine.processInstance().withInstanceKey(processInstanceKey).suspend();

    // when
    engine.increaseTime(Duration.ofMinutes(2));

    // then
    assertThat(bufferedCommand(processInstanceKey, ValueType.JOB, JobIntent.RECUR_AFTER_BACKOFF))
        .extracting(BufferedCommandRecordValue::getStorageOrdinal)
        .isEqualTo(FIXED_ORDINAL);

    // when - the instance resumes and the buffered RECUR_AFTER_BACKOFF drains
    engine.processInstance().withInstanceKey(processInstanceKey).resume();

    // then
    final var recurred =
        RecordingExporter.jobRecords(JobIntent.RECURRED_AFTER_BACKOFF)
            .withRecordKey(jobKey)
            .getFirst();
    assertThat(recurred.getValue().getStorageOrdinal()).isEqualTo(FIXED_ORDINAL);
  }

  private static BufferedCommandRecordValue bufferedCommand(
      final long processInstanceKey, final ValueType valueType, final Intent intent) {
    return (BufferedCommandRecordValue)
        RecordingExporter.records()
            .withValueType(ValueType.BUFFERED_COMMAND)
            .withIntent(BufferedCommandIntent.BUFFERED)
            .filter(
                r -> {
                  final var value = (BufferedCommandRecordValue) r.getValue();
                  return value.getProcessInstanceKey() == processInstanceKey
                      && value.getValueType() == valueType
                      && value.getIntent() == intent;
                })
            .getFirst()
            .getValue();
  }
}
