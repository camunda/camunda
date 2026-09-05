/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.secretreference;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.SecretStoreRegistries;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.SecretReferenceIntent;
import io.camunda.zeebe.protocol.record.value.SecretReferenceRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.time.Duration;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;

/**
 * Companion to {@link SecretResolutionWarmupTest}: the resolution interval here is deliberately an
 * hour, so the reference the first job's warmup requests is still pending by the time the second
 * job is created - this is what makes the dedup assertion deterministic instead of racing the
 * scheduler's own cycle.
 */
public final class SecretResolutionWarmupDedupTest {

  private static final String PROCESS_ID = "process";
  private static final String TASK_ID = "task";
  private static final String JOB_TYPE = "task-type";
  private static final String SECRET_VALUE = "resolved-secret";

  @Rule
  public final EngineRule engine =
      EngineRule.singlePartition()
          .withSecretStoreRegistry(
              SecretStoreRegistries.resolvingFromStore(Map.of("token", SECRET_VALUE)))
          .withEngineConfig(config -> config.setSecretResolutionInterval(Duration.ofHours(1)));

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldNotRequestResolutionTwiceForAReferenceAlreadyPending() {
    // given
    deploy();

    // when - two jobs referencing the same non-cached secret are created back to back
    final long firstInstanceKey = engine.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    awaitJob(firstInstanceKey);
    final long secondInstanceKey = engine.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    awaitJob(secondInstanceKey);

    // then - only the first job's warmup requested the resolution; the second found it pending
    // already. Both jobs' CREATED events and their own warmup follow-up (if any) commit and export
    // in the same batch as that job's creation command, so waiting for the second job's CREATED
    // record above is what makes a snapshot read here safe: a second request, if the dedup had not
    // fired, would already be visible by now rather than merely not-yet-exported
    assertThat(RecordingExporter.getRecords())
        .filteredOn(
            record ->
                record.getIntent() == SecretReferenceIntent.RESOLUTION_REQUESTED
                    && ((SecretReferenceRecordValue) record.getValue())
                        .getSecretReference()
                        .equals("token"))
        .hasSize(1);
  }

  /** Returns the key of the job the given instance waits on, once that job exists. */
  private long awaitJob(final long processInstanceKey) {
    return RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .getFirst()
        .getKey();
  }

  private void deploy() {
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .serviceTask(
                TASK_ID,
                t ->
                    t.zeebeJobType(JOB_TYPE)
                        .zeebeInputExpression(
                            "\"Bearer \" + camunda.secrets.token", "authorization"))
            .endEvent()
            .done();
    engine.deployment().withXmlResource(process).deploy();
  }
}
