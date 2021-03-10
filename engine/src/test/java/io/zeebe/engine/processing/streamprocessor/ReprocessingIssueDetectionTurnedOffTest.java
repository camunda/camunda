/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processing.streamprocessor;

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.engine.util.EngineRule;
import io.zeebe.engine.util.RecordToWrite;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.intent.JobIntent;
import io.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.zeebe.protocol.record.value.BpmnElementType;
import io.zeebe.protocol.record.value.JobRecordValue;
import io.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.zeebe.test.util.record.RecordingExporter;
import io.zeebe.test.util.record.RecordingExporterTestWatcher;
import io.zeebe.util.health.HealthStatus;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

@Ignore(
    "The reprocessing issue detection will be deleted soon (6280) - ignore the tests to make the migration easier")
public final class ReprocessingIssueDetectionTurnedOffTest {

  @Rule public final EngineRule engine = EngineRule.singlePartition();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  private long processInstanceKey;
  private Record<JobRecordValue> jobCreated;
  private Record<ProcessInstanceRecordValue> serviceTaskActivated;
  private Record<ProcessInstanceRecordValue> processActivated;

  @Before
  public void setup() {
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("process")
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType("test"))
                .done())
        .deploy();

    processInstanceKey = engine.processInstance().ofBpmnProcessId("process").create();

    processActivated =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withElementType(BpmnElementType.PROCESS)
            .getFirst();

    jobCreated = RecordingExporter.jobRecords(JobIntent.CREATED).getFirst();

    serviceTaskActivated =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withElementType(BpmnElementType.SERVICE_TASK)
            .getFirst();

    engine.stop();
  }

  @Test
  public void shouldNotDetectDifferentKey() {
    // given
    engine.writeRecords(
        RecordToWrite.command()
            .job(JobIntent.COMPLETE, jobCreated.getValue())
            .key(jobCreated.getKey()),
        RecordToWrite.event()
            .job(JobIntent.COMPLETED, jobCreated.getValue())
            .key(jobCreated.getKey())
            .causedBy(0),
        // expected the key to be serviceTaskActivated.getKey()
        RecordToWrite.event()
            .processInstance(
                ProcessInstanceIntent.ELEMENT_COMPLETING, serviceTaskActivated.getValue())
            .key(123L)
            .causedBy(1));

    // when
    engine.start();

    // then
    engine.awaitReprocessingCompleted();

    final var streamProcessor = engine.getStreamProcessor(1);
    assertThat(streamProcessor.isFailed()).isFalse();
    assertThat(streamProcessor.getHealthStatus()).isEqualTo(HealthStatus.HEALTHY);
  }

  @Test
  public void shouldNotDetectDifferentIntent() {
    // given
    engine.writeRecords(
        RecordToWrite.command()
            .job(JobIntent.COMPLETE, jobCreated.getValue())
            .key(jobCreated.getKey()),
        RecordToWrite.event()
            .job(JobIntent.COMPLETED, jobCreated.getValue())
            .key(jobCreated.getKey())
            .causedBy(0),
        // expected the intent to be ELEMENT_COMPLETING
        RecordToWrite.event()
            .processInstance(
                ProcessInstanceIntent.ELEMENT_TERMINATING, serviceTaskActivated.getValue())
            .key(serviceTaskActivated.getKey())
            .causedBy(1));

    // when
    engine.start();

    // then
    engine.awaitReprocessingCompleted();

    final var streamProcessor = engine.getStreamProcessor(1);
    assertThat(streamProcessor.isFailed()).isFalse();
    assertThat(streamProcessor.getHealthStatus()).isEqualTo(HealthStatus.HEALTHY);
  }

  @Test
  public void shouldNotDetectMissingRecordOnLogStream() {
    // given
    engine.writeRecords(
        RecordToWrite.command()
            .processInstance(ProcessInstanceIntent.CANCEL, processActivated.getValue())
            .key(processInstanceKey),
        RecordToWrite.event()
            .processInstance(ProcessInstanceIntent.ELEMENT_TERMINATING, processActivated.getValue())
            .key(processInstanceKey)
            .causedBy(0),
        // expected the follow-up event with intent ELEMENT_TERMINATING for the service task
        RecordToWrite.command()
            .job(JobIntent.COMPLETE, jobCreated.getValue())
            .key(jobCreated.getKey()),
        RecordToWrite.event()
            .job(JobIntent.COMPLETED, jobCreated.getValue())
            .key(jobCreated.getKey())
            .causedBy(2));

    // when
    engine.start();

    // then
    engine.awaitReprocessingCompleted();

    final var streamProcessor = engine.getStreamProcessor(1);
    assertThat(streamProcessor.isFailed()).isFalse();
    assertThat(streamProcessor.getHealthStatus()).isEqualTo(HealthStatus.HEALTHY);
  }
}
