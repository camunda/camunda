/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processing.streamprocessor;

import static io.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_ACTIVATED;

import io.zeebe.engine.util.EngineRule;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.record.value.BpmnElementType;
import io.zeebe.test.util.record.RecordingExporter;
import io.zeebe.test.util.stream.StreamWrapperException;
import java.time.Duration;
import java.util.stream.IntStream;
import org.awaitility.Awaitility;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class EngineReprocessingTest {

  private static final String PROCESS_ID = "process";
  private static final BpmnModelInstance SIMPLE_FLOW =
      Bpmn.createExecutableProcess(PROCESS_ID).startEvent().endEvent().done();
  private static final int PARTITION_ID = 1;
  private static final long MAX_TEST_WAIT_TIME = Duration.ofSeconds(1).toMillis();

  @Rule public EngineRule engineRule = EngineRule.singlePartition();

  @Before
  public void init() {
    RecordingExporter.setMaximumWaitTime(MAX_TEST_WAIT_TIME);
    engineRule.deployment().withXmlResource(SIMPLE_FLOW).deploy();
    final var instanceCount = 10;
    IntStream.range(0, instanceCount)
        .forEach(i -> engineRule.processInstance().ofBpmnProcessId(PROCESS_ID).create());

    Awaitility.await()
        .until(
            () ->
                RecordingExporter.processInstanceRecords()
                    .withElementType(BpmnElementType.PROCESS)
                    .withIntent(ELEMENT_ACTIVATED)
                    .limit(instanceCount)
                    .count(),
            (count) -> count == instanceCount);

    engineRule.stop();
  }

  @After
  public void tearDown() {
    RecordingExporter.setMaximumWaitTime(RecordingExporter.DEFAULT_MAX_WAIT_TIME);
  }

  @Test
  public void shouldReprocess() {
    // given - reprocess
    final int lastSize = RecordingExporter.getRecords().size();
    // we need to reset the record exporter
    RecordingExporter.reset();
    engineRule.start();

    // when - then
    Awaitility.await("Await reprocessing of " + lastSize)
        .until(() -> RecordingExporter.getRecords().size(), (size) -> size >= lastSize);
  }

  @Test
  public void shouldContinueProcessingAfterReprocessing() {
    // given - reprocess
    final int lastSize = RecordingExporter.getRecords().size();
    // we need to reset the record exporter
    RecordingExporter.reset();
    engineRule.start();

    Awaitility.await("Await reprocessing of " + lastSize)
        .until(() -> RecordingExporter.getRecords().size(), (size) -> size >= lastSize);

    // when - then
    engineRule.processInstance().ofBpmnProcessId(PROCESS_ID).create();
  }

  @Test
  public void shouldNotContinueProcessingWhenPausedDuringReprocessing() {
    // given - reprocess
    final int lastSize = RecordingExporter.getRecords().size();
    // we need to reset the record exporter
    RecordingExporter.reset();
    engineRule.start();
    engineRule.pauseProcessing(PARTITION_ID);

    // when
    Awaitility.await("Await reprocessing of " + lastSize)
        .until(() -> RecordingExporter.getRecords().size(), (size) -> size >= lastSize);

    // then
    Assert.assertThrows(
        StreamWrapperException.class,
        () -> engineRule.processInstance().ofBpmnProcessId(PROCESS_ID).create());
  }

  @Test
  public void shouldContinueAfterReprocessWhenProcessingWasResumed() {
    // given
    final int lastSize = RecordingExporter.getRecords().size();
    // we need to reset the record exporter
    RecordingExporter.reset();
    engineRule.start();
    engineRule.pauseProcessing(PARTITION_ID);
    engineRule.resumeProcessing(PARTITION_ID);

    Awaitility.await("Await reprocessing of " + lastSize)
        .until(() -> RecordingExporter.getRecords().size(), (size) -> size >= lastSize);

    // when
    engineRule.processInstance().ofBpmnProcessId(PROCESS_ID).create();
  }
}
