/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processing.streamprocessor;

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.engine.processing.streamprocessor.StreamProcessor.Phase;
import io.zeebe.engine.state.ZbColumnFamilies;
import io.zeebe.engine.util.EngineRule;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.intent.JobIntent;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import io.zeebe.protocol.record.value.BpmnElementType;
import io.zeebe.test.util.record.RecordingExporter;
import io.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Map;
import org.assertj.core.api.SoftAssertions;
import org.awaitility.Awaitility;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public final class ReplayStateTest {

  @Rule public final EngineRule engine = EngineRule.singlePartition();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  private long workflowInstanceKey;
  private Map<ZbColumnFamilies, Map<Object, Object>> processingState;

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

    workflowInstanceKey = engine.workflowInstance().ofBpmnProcessId("process").create();

    RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_ACTIVATED)
        .withElementType(BpmnElementType.SERVICE_TASK)
        .await();

    final var jobCreated = RecordingExporter.jobRecords(JobIntent.CREATED).getFirst();

    Awaitility.await("await until the last record is processed")
        .untilAsserted(
            () -> {
              final var processedPosition =
                  engine.getStreamProcessor(1).getLastProcessedPositionAsync().join();
              assertThat(processedPosition).isEqualTo(jobCreated.getPosition());
            });

    processingState = engine.collectState();
    engine.stop();
  }

  @Test
  public void shouldContinueAfterRestart() {
    // given
    engine.start();

    // when
    engine
        .jobs()
        .withType("test")
        .activate()
        .getValue()
        .getJobKeys()
        .forEach(jobKey -> engine.job().withKey(jobKey).complete());

    // then
    assertThat(
            RecordingExporter.workflowInstanceRecords()
                .withWorkflowInstanceKey(workflowInstanceKey)
                .filterRootScope()
                .limitToWorkflowInstanceCompleted())
        .extracting(Record::getIntent)
        .contains(WorkflowInstanceIntent.ELEMENT_COMPLETED);
  }

  @Test
  public void shouldRestoreState() {
    // when
    engine.start();

    Awaitility.await()
        .untilAsserted(
            () -> {
              assertThat(engine.getStreamProcessor(1).getCurrentPhase().join())
                  .isEqualTo(Phase.PROCESSING);
            });

    // then
    final var replayState = engine.collectState();

    final var softly = new SoftAssertions();

    processingState.forEach(
        (column, processingEntries) -> {
          final var replayEntries = replayState.get(column);

          softly
              .assertThat(replayEntries)
              .describedAs("The state column '%s' has different entries after replay", column)
              .containsExactlyInAnyOrderEntriesOf(processingEntries);
        });

    softly.assertAll();
  }
}
