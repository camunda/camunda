/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.secretreference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import io.camunda.secretstore.SecretStoreRegistry;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.SecretStoreRegistries;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.impl.record.value.job.JobBatchRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.SecretReferenceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.JobBatchRecordValue;
import io.camunda.zeebe.protocol.record.value.ResolutionState;
import io.camunda.zeebe.protocol.record.value.SecretReferenceRecordValue;
import io.camunda.zeebe.stream.api.CommandResponseWriter;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.time.Duration;
import java.util.Map;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.awaitility.Awaitility;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.stubbing.Answer;

/**
 * Covers the whole loop a secret that is not cached yet goes through: the job is parked, the
 * background resolution reads the store, and the job is reactivated with the value injected.
 *
 * <p>The registry here is deliberately a real one with a cold cache rather than {@link
 * SecretStoreRegistries#resolveAll(String)}, whose cache answers every name so nothing ever parks
 * and the scheduler is never involved. That gap let a reference addressing no configured store ship
 * unnoticed, failing every background resolution (#59432).
 */
public final class SecretResolutionLifecycleTest {

  private static final String PROCESS_ID = "process";
  private static final String TASK_ID = "task";
  private static final String JOB_TYPE = "task-type";
  private static final String SECRET_VALUE = "resolved-secret";

  @Rule
  public final EngineRule engine =
      EngineRule.singlePartition()
          .withSecretStoreRegistry(
              SecretStoreRegistries.resolvingFromStore(Map.of("token", SECRET_VALUE)))
          // the default interval is 5s, which would make every assertion below wait on it
          .withEngineConfig(config -> config.setSecretResolutionInterval(Duration.ofMillis(100)));

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  private CommandResponseWriter mockResponseWriter;
  private volatile JobBatchRecord activationResponse;

  @Before
  public void setUp() {
    mockResponseWriter = engine.getCommandResponseWriter();
    interceptResponseWriter();
  }

  @Test
  public void shouldResolveParkedSecretInBackgroundAndReactivateTheJob() {
    // given - a job whose secret the store holds but the cache does not
    deploy();
    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final long jobKey = awaitJob(processInstanceKey);

    // when - the first activation parks the job and requests the resolution
    final Record<JobBatchRecordValue> parked =
        engine.jobs().withType(JOB_TYPE).withRequestStreamId(1).withRequestId(1L).activate();
    assertThat(parked.getValue().getJobs()).isEmpty();

    // then - the request addresses the default store, which is the one configured. The first
    // RESOLUTION_REQUESTED for this reference is the creation-time warmup, which carries no job
    // key (see SecretResolutionWarmupTest) - this is the poll-driven one, which parks this job
    final Record<SecretReferenceRecordValue> requested =
        RecordingExporter.secretReferenceRecords(SecretReferenceIntent.RESOLUTION_REQUESTED)
            .withSecretReference("token")
            .filter(record -> record.getValue().getJobKeys().contains(jobKey))
            .getFirst();
    assertThat(requested.getValue().getStoreId()).isEqualTo(SecretStoreRegistry.DEFAULT_STORE_ID);
    assertThat(requested.getValue().getJobKeys()).containsExactly(jobKey);

    // and - the background resolution succeeds against that store
    final Record<SecretReferenceRecordValue> resolved =
        RecordingExporter.secretReferenceRecords(SecretReferenceIntent.RESOLUTION_COMPLETED)
            .withSecretReference("token")
            .getFirst();
    assertThat(resolved.getValue().getResolutionState()).isEqualTo(ResolutionState.SUCCESS);

    // and - the job is reactivated and the next activation injects the value into the response only
    final Record<SecretReferenceRecordValue> reactivated =
        RecordingExporter.secretReferenceRecords(SecretReferenceIntent.BATCH_JOBS_REACTIVATED)
            .withSecretReference("token")
            .getFirst();
    assertThat(reactivated.getValue().getJobKeys()).containsExactly(jobKey);
    final Record<JobBatchRecordValue> activated =
        engine.jobs().withType(JOB_TYPE).withRequestStreamId(2).withRequestId(2L).activate();
    assertThat(activated.getValue().getJobKeys()).containsExactly(jobKey);
    // the parked activation above already wrote its own (empty) response, so the wait must be for
    // the response carrying the job rather than for any response at all, and must hold on to the
    // record it saw: a response written later would otherwise replace it before it is asserted on
    final JobBatchRecord response =
        Awaitility.await("until the activation response carrying the job is written")
            .atMost(Duration.ofSeconds(5))
            .until(
                () -> activationResponse,
                written -> written != null && !written.getJobs().isEmpty());
    assertThat(response.getJobs().get(0).getVariables())
        .containsEntry("authorization", "Bearer " + SECRET_VALUE);

    // and - the job completes and the instance reaches its end, so the whole loop is over and
    // nothing further will be processed: only then does the absence of an incident below mean the
    // run never raised one, rather than that none had been raised by the time the records were read
    engine.job().withKey(jobKey).complete();
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementType(BpmnElementType.PROCESS)
        .getFirst();

    // and - no incident was raised, and no exported record leaks the value
    assertThat(RecordingExporter.getRecords())
        .filteredOn(record -> record.getIntent() == IncidentIntent.CREATED)
        .isEmpty();
    assertThat(RecordingExporter.getRecords())
        .noneMatch(record -> record.toString().contains(SECRET_VALUE));
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

  private void interceptResponseWriter() {
    doAnswer(
            (Answer<CommandResponseWriter>)
                invocation -> {
                  final var arguments = invocation.getArguments();
                  if (arguments != null
                      && arguments.length == 1
                      && arguments[0] instanceof final JobBatchRecord jobBatchRecord) {
                    // copy the record: engine record objects are reused across commands, so the
                    // captured reference could otherwise be overwritten by a later command
                    final var copy = new JobBatchRecord();
                    final MutableDirectBuffer buffer =
                        new UnsafeBuffer(new byte[jobBatchRecord.getLength()]);
                    jobBatchRecord.write(buffer, 0);
                    copy.wrap(buffer);
                    activationResponse = copy;
                  }
                  return mockResponseWriter;
                })
        .when(mockResponseWriter)
        .valueWriter(any());
  }
}
