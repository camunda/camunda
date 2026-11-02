/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.message;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import io.camunda.zeebe.util.TagUtil;
import java.time.Duration;
import java.util.UUID;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

/**
 * Verifies the run-scoped message-start uniqueness lock (ADR 0014, D7 parallel-CF prototype). The
 * "one active instance per correlation key" lock is partitioned by run scope: two runs that publish
 * the same non-empty correlation key each start their own instance, while within one run a second
 * start is still deduplicated (buffered) until the first instance ends, then picked up.
 */
public final class MessageStartEventRunScopeUniquenessTest {

  private static final String CORRELATION_KEY = "order-1";
  private static final String RUN_A = "runA";
  private static final String RUN_B = "runB";
  private static final String JOB_TYPE = "task-type";

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  // Unique ids per test keep the shared engine's record stream and message correlation isolated.
  private final String processId = "message-start-process-" + UUID.randomUUID();
  private final String messageName = "start-message-" + UUID.randomUUID();

  @Test
  public void shouldStartBothRunsSharingACorrelationKey() {
    // given
    deploy();

    // when two different runs publish the same non-empty correlation key
    publishStart(RUN_A);
    publishStart(RUN_B);

    // then each run starts its own instance (the headline: no cross-run collision)
    final var started =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withBpmnProcessId(processId)
            .withElementType(BpmnElementType.PROCESS)
            .limit(2)
            .toList();

    assertThat(started).hasSize(2);
    assertThat(started)
        .anySatisfy(r -> assertThat(r.getValue().getTags()).contains(TagUtil.runScopeTag(RUN_A)))
        .anySatisfy(r -> assertThat(r.getValue().getTags()).contains(TagUtil.runScopeTag(RUN_B)));
  }

  @Test
  public void shouldBufferThenPickUpSecondStartWithinSameRun() {
    // given
    deploy();

    // when the same run publishes the same correlation key twice while the first is still active
    publishStart(RUN_A);
    final var firstInstance =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withBpmnProcessId(processId)
            .withElementType(BpmnElementType.PROCESS)
            .getFirst();
    publishStart(RUN_A);

    // when the first instance ends, releasing the run's lock
    ENGINE.job().ofInstance(firstInstance.getKey()).withType(JOB_TYPE).complete();
    final var firstCompleted =
        RecordingExporter.jobRecords(JobIntent.COMPLETED)
            .withType(JOB_TYPE)
            .withProcessInstanceKey(firstInstance.getKey())
            .getFirst();

    // then exactly two instances started in run A, and the second only after the first ended —
    // proving the second was deduplicated and buffered rather than started concurrently
    final var started =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withBpmnProcessId(processId)
            .withElementType(BpmnElementType.PROCESS)
            .limit(2)
            .toList();

    assertThat(started).hasSize(2);
    assertThat(started)
        .allSatisfy(r -> assertThat(r.getValue().getTags()).contains(TagUtil.runScopeTag(RUN_A)));
    assertThat(started.get(1).getPosition())
        .describedAs("second instance starts only after the first instance's lock is released")
        .isGreaterThan(firstCompleted.getPosition());
  }

  private void deploy() {
    // Waits at a service task, so a started instance stays active and keeps holding its start lock.
    final BpmnModelInstance model =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .message(m -> m.name(messageName))
            .serviceTask("task", t -> t.zeebeJobType(JOB_TYPE))
            .endEvent()
            .done();
    ENGINE.deployment().withXmlResource(model).deploy();
  }

  private void publishStart(final String runScope) {
    ENGINE
        .message()
        .withName(messageName)
        .withCorrelationKey(CORRELATION_KEY)
        .withRunScope(runScope)
        .withTimeToLive(Duration.ofMinutes(5))
        .publish();
  }
}
