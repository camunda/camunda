/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.processing.job.SecretResolver.SecretReference;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.ServiceTaskBuilder;
import io.camunda.zeebe.protocol.impl.record.value.job.JobBatchRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.SecretReferenceIntent;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import io.camunda.zeebe.protocol.record.value.JobBatchRecordValue;
import io.camunda.zeebe.protocol.record.value.SecretReferenceRecordValue;
import io.camunda.zeebe.stream.api.CommandResponseWriter;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.awaitility.Awaitility;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.stubbing.Answer;

public final class JobSecretActivationInjectionTest {

  private static final String PROCESS_ID = "process";
  private static final String TASK_ID = "task";
  private static final String JOB_TYPE = "task-type";

  private final Map<String, String> cachedSecrets = new HashMap<>();
  private boolean failResolution;

  @Rule
  public final EngineRule engine =
      EngineRule.singlePartition().withSecretResolver(this::resolveFromCachedSecrets);

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
  public void shouldInjectCachedSecretIntoResponseOnly() {
    // given
    cachedSecrets.put("token", "resolved-secret");
    deploy(t -> t.zeebeInputExpression("\"Bearer \" + camunda.secrets.token", "authorization"));
    createInstanceAndAwaitJob();

    // when
    final Record<JobBatchRecordValue> activated =
        engine.jobs().withType(JOB_TYPE).withRequestStreamId(1).withRequestId(1L).activate();

    // then - the persisted ACTIVATED event keeps the unresolved placeholder (no secret in the log)
    assertThat(activated.getValue().getJobs().get(0).getVariables())
        .containsEntry("authorization", "Bearer camunda.secrets.token");

    // and - the worker response carries the resolved secret value
    Awaitility.await("until the activation response is written")
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(() -> assertThat(activationResponse).isNotNull());
    assertThat(activationResponse.getJobs().get(0).getVariables())
        .containsEntry("authorization", "Bearer resolved-secret");

    // and - no exported record (state, log) leaks the resolved secret value
    assertThat(RecordingExporter.getRecords())
        .noneMatch(record -> record.toString().contains("resolved-secret"));
  }

  @Test
  public void shouldNotActivateJobAndRequestResolutionWhenSecretIsNotCached() {
    // given - the secret has no cached value (empty resolver)
    deploy(t -> t.zeebeInputExpression("\"Bearer \" + camunda.secrets.token", "authorization"));
    final long processInstanceKey = createInstanceAndAwaitJob();
    final long jobKey =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst()
            .getKey();

    // when
    final Record<JobBatchRecordValue> activated =
        engine.jobs().withType(JOB_TYPE).withRequestStreamId(1).withRequestId(1L).activate();

    // then - the job is not handed out, neither in the event nor in the response
    assertThat(activated.getValue().getJobs()).isEmpty();
    assertThat(activated.getValue().getJobKeys()).isEmpty();
    Awaitility.await("until the activation response is written")
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(() -> assertThat(activationResponse).isNotNull());
    assertThat(activationResponse.getJobs()).isEmpty();

    // and - a RESOLUTION_REQUESTED event is written for the missing secret, carrying the job key
    final Record<SecretReferenceRecordValue> requested =
        RecordingExporter.secretReferenceRecords(SecretReferenceIntent.RESOLUTION_REQUESTED)
            .getFirst();
    assertThat(requested.getValue().getStoreId()).isEmpty();
    assertThat(requested.getValue().getSecretReference()).isEqualTo("token");
    assertThat(requested.getValue().getJobKeys()).containsExactly(jobKey);
    // and - it is written after the JobBatch ACTIVATED event
    assertThat(requested.getPosition()).isGreaterThan(activated.getPosition());

    // and - the job is now waiting for resolution and no longer activatable: even once the secret
    // value is cached, a later poll does not hand it out (the job is reactivated only after the
    // resolution flow completes, which is out of this component's scope)
    cachedSecrets.put("token", "resolved-secret");
    final Record<JobBatchRecordValue> secondAttempt =
        engine.jobs().withType(JOB_TYPE).withRequestStreamId(2).withRequestId(2L).activate();
    assertThat(secondAttempt.getValue().getJobs()).isEmpty();
  }

  @Test
  public void shouldNotActivateJobWhenResolverThrows() {
    // given
    failResolution = true;
    deploy(t -> t.zeebeInputExpression("\"Bearer \" + camunda.secrets.token", "authorization"));
    createInstanceAndAwaitJob();

    // when
    final Record<JobBatchRecordValue> activated =
        engine.jobs().withType(JOB_TYPE).withRequestStreamId(1).withRequestId(1L).activate();

    // then - the job is not handed out
    assertThat(activated.getValue().getJobs()).isEmpty();
    Awaitility.await("until the activation response is written")
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(() -> assertThat(activationResponse).isNotNull());
    assertThat(activationResponse.getJobs()).isEmpty();

    // and - the resolver error stays out of every exported record
    assertThat(RecordingExporter.getRecords())
        .noneMatch(record -> record.toString().contains("resolver exploded"));
  }

  @Test
  public void shouldNotChangeVariablesForJobWithoutSecrets() {
    // given
    cachedSecrets.put("token", "resolved-secret");
    deploy(t -> t.zeebeInputExpression("\"plain-value\"", "authorization"));
    createInstanceAndAwaitJob();

    // when
    final Record<JobBatchRecordValue> activated =
        engine.jobs().withType(JOB_TYPE).withRequestStreamId(1).withRequestId(1L).activate();

    // then - variables are unchanged in both the event and the response
    assertThat(activated.getValue().getJobs().get(0).getVariables())
        .containsEntry("authorization", "plain-value");
    Awaitility.await("until the activation response is written")
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(() -> assertThat(activationResponse).isNotNull());
    assertThat(activationResponse.getJobs().get(0).getVariables())
        .containsEntry("authorization", "plain-value");
  }

  @Test
  public void shouldDropJobExceedingMessageSizeBudgetAndHandItOutOnNextActivation() {
    // given - two jobs referencing the same secret; the injected value fits the batch growth
    // budget once, but not twice
    final var value = "x".repeat(EngineConfiguration.BATCH_SIZE_CALCULATION_BUFFER / 2 + 1000);
    cachedSecrets.put("token", value);
    deploy(t -> t.zeebeInputExpression("\"Bearer \" + camunda.secrets.token", "authorization"));
    createInstanceAndAwaitJob();
    createInstanceAndAwaitJob();

    // when
    final Record<JobBatchRecordValue> activated =
        engine.jobs().withType(JOB_TYPE).withRequestStreamId(1).withRequestId(1L).activate();

    // then - only the first job is activated; the second is dropped and the batch is marked
    // truncated so the client polls again right away
    assertThat(activated.getValue().getJobs()).hasSize(1);
    assertThat(activated.getValue().isTruncated()).isTrue();
    Awaitility.await("until the activation response is written")
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(() -> assertThat(activationResponse).isNotNull());
    assertThat(activationResponse.getJobs()).hasSize(1);
    assertThat(activationResponse.getTruncated()).isTrue();
    assertThat(activationResponse.getJobs().get(0).getVariables())
        .containsEntry("authorization", "Bearer " + value);

    // and - the dropped job stays activatable: the next activation hands it out with the value
    final Record<JobBatchRecordValue> secondAttempt =
        engine.jobs().withType(JOB_TYPE).withRequestStreamId(2).withRequestId(2L).activate();
    assertThat(secondAttempt.getValue().getJobs()).hasSize(1);
    Awaitility.await("until the second activation response is written")
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () ->
                assertThat(activationResponse.getJobs().get(0).getVariables())
                    .containsEntry("authorization", "Bearer " + value));

    // and - no exported record leaks the resolved secret value
    assertThat(RecordingExporter.getRecords())
        .noneMatch(record -> record.toString().contains(value));
  }

  @Test
  public void shouldRaiseIncidentWhenSecretValueCanNeverFitMessageSizeBudget() {
    // given - the injected value alone exceeds the whole batch growth budget, so no activation
    // batch could ever carry this job
    final var value = "x".repeat(EngineConfiguration.BATCH_SIZE_CALCULATION_BUFFER + 1000);
    cachedSecrets.put("token", value);
    deploy(t -> t.zeebeInputExpression("\"Bearer \" + camunda.secrets.token", "authorization"));
    final long processInstanceKey = createInstanceAndAwaitJob();
    final long jobKey =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst()
            .getKey();

    // when
    final Record<JobBatchRecordValue> activated =
        engine.jobs().withType(JOB_TYPE).withRequestStreamId(1).withRequestId(1L).activate();

    // then - the job is not handed out and a message-size incident is raised for it, like for a
    // job that is too large to activate without secrets
    assertThat(activated.getValue().getJobs()).isEmpty();
    Awaitility.await("until the activation response is written")
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(() -> assertThat(activationResponse).isNotNull());
    assertThat(activationResponse.getJobs()).isEmpty();

    final Record<IncidentRecordValue> incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED).withJobKey(jobKey).getFirst();
    assertThat(incident.getValue().getErrorType()).isEqualTo(ErrorType.MESSAGE_SIZE_EXCEEDED);

    // and - the incident disables the job: a later activation does not hand it out either
    final Record<JobBatchRecordValue> secondAttempt =
        engine.jobs().withType(JOB_TYPE).withRequestStreamId(2).withRequestId(2L).activate();
    assertThat(secondAttempt.getValue().getJobs()).isEmpty();

    // and - no exported record (including the incident) leaks the resolved secret value
    assertThat(RecordingExporter.getRecords())
        .noneMatch(record -> record.toString().contains(value));
  }

  private Map<SecretReference, String> resolveFromCachedSecrets(
      final Set<SecretReference> references) {
    if (failResolution) {
      throw new IllegalStateException("resolver exploded");
    }
    final Map<SecretReference, String> values = new HashMap<>();
    for (final SecretReference reference : references) {
      final String value = cachedSecrets.get(reference.secretReference());
      if (value != null) {
        values.put(reference, value);
      }
    }
    return values;
  }

  private long createInstanceAndAwaitJob() {
    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .getFirst();
    return processInstanceKey;
  }

  private void deploy(final Consumer<ServiceTaskBuilder> modifier) {
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .serviceTask(
                TASK_ID,
                t -> {
                  t.zeebeJobType(JOB_TYPE);
                  modifier.accept(t);
                })
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
