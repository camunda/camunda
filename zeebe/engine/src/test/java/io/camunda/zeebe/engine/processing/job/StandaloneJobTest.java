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
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.RecordingJobStreamer;
import io.camunda.zeebe.engine.util.RecordingJobStreamer.RecordingJobStream;
import io.camunda.zeebe.msgpack.value.StringValue;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.impl.stream.job.JobActivationPropertiesImpl;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.AsyncRequestIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.value.JobKind;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.stream.api.CommandResponseWriter;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.awaitility.Awaitility;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;

/**
 * Verifies the standalone-job POC: a job created directly by a client (no owning process instance),
 * activated/completed like any other job, with the result correlated back to the awaiting creator —
 * mirroring {@code CreateProcessInstanceWithResult} but for a job.
 */
public final class StandaloneJobTest {

  private static final RecordingJobStreamer JOB_STREAMER = new RecordingJobStreamer();

  @ClassRule
  public static final EngineRule ENGINE =
      EngineRule.singlePartition().withJobStreamer(JOB_STREAMER);

  private static JobRecord responseValue;
  private static CommandResponseWriter mockCommandResponseWriter;

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @BeforeClass
  public static void init() {
    mockCommandResponseWriter = ENGINE.getCommandResponseWriter();
    interceptResponseWriter(mockCommandResponseWriter);
  }

  @Before
  public void reset() {
    Mockito.clearInvocations(mockCommandResponseWriter);
    JOB_STREAMER.clearStreams();
    responseValue = null;
  }

  @Test
  public void shouldCreateStandaloneJob() {
    // when
    final var created =
        ENGINE.job().withType(Strings.newRandomValidBpmnId()).withRetries(3).create();

    // then
    assertThat(created.getIntent()).isEqualTo(JobIntent.CREATED);
    assertThat(created.getKey()).isPositive();
    final var job = created.getValue();
    assertThat(job.getJobKind()).isEqualTo(JobKind.STANDALONE);
    assertThat(job.getProcessInstanceKey()).isEqualTo(-1L);
    assertThat(job.getProcessDefinitionKey()).isEqualTo(-1L);
    assertThat(job.getElementInstanceKey()).isEqualTo(-1L);
    assertThat(job.getBpmnProcessId()).isEmpty();
    assertThat(job.getRetries()).isEqualTo(3);
  }

  @Test
  public void shouldRejectStandaloneJobWithoutType() {
    // when (no type provided)
    final var rejection = ENGINE.job().withRetries(3).expectRejection().create();

    // then
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_ARGUMENT);
  }

  @Test
  public void shouldDeliverResultToCreatorOnComplete() {
    // given a caller that created a standalone job and awaits its result
    final long jobKey =
        ENGINE
            .job()
            .withType(Strings.newRandomValidBpmnId())
            .withRetries(3)
            .withVariable("user", "alice")
            .createWithResult(7, 42L)
            .getKey();

    // when the worker completes the job with a result
    ENGINE.job().withKey(jobKey).withVariable("valid", true).complete();

    // then the result is sent back synchronously to the original creator's request
    verify(mockCommandResponseWriter, timeout(1000).times(1)).intent(JobIntent.COMPLETED);
    verify(mockCommandResponseWriter, timeout(1000).times(1)).tryWriteResponse(7, 42L);
    assertThat(responseValue).isNotNull();
    assertThat(responseValue.getVariables()).containsEntry("valid", true);

    // and the awaited request is cleaned up so it does not leak
    RecordingExporter.records().withIntent(AsyncRequestIntent.PROCESSED).getFirst();
  }

  @Test
  public void shouldCompleteFireAndForgetStandaloneJobWithoutAwaitedRequest() {
    // given a standalone job created without awaiting a result
    final long jobKey =
        ENGINE.job().withType(Strings.newRandomValidBpmnId()).withRetries(3).create().getKey();

    // when
    ENGINE.job().withKey(jobKey).complete();

    // then it completes without attempting to resolve any awaited result
    final boolean resolveEmitted =
        RecordingExporter.records()
            .limit(
                r ->
                    r.getValueType() == ValueType.JOB
                        && r.getKey() == jobKey
                        && r.getIntent() == JobIntent.COMPLETED)
            .anyMatch(r -> r.getIntent() == JobIntent.RESOLVE_AWAIT_RESULT);
    assertThat(resolveEmitted).isFalse();
  }

  @Test
  public void shouldNotRaiseIncidentAndCleanUpAwaitOnTerminalFailure() {
    // given a standalone job whose creator awaits the result
    final long jobKey =
        ENGINE
            .job()
            .withType(Strings.newRandomValidBpmnId())
            .withRetries(3)
            .createWithResult(8, 43L)
            .getKey();

    // when the worker fails it terminally (no retries left)
    ENGINE.job().withKey(jobKey).withRetries(0).fail();

    // then the awaited request is cleaned up...
    RecordingExporter.records().withIntent(AsyncRequestIntent.PROCESSED).getFirst();

    // ...and no incident is raised (standalone jobs have no process instance to attach one to)
    final boolean incidentRaised =
        RecordingExporter.records()
            .limit(r -> r.getIntent() == AsyncRequestIntent.PROCESSED)
            .anyMatch(r -> r.getValueType() == ValueType.INCIDENT);
    assertThat(incidentRaised).isFalse();
  }

  @Test
  public void shouldPushStandaloneJobToWorkerStreamWithVariables() {
    // given a worker streaming jobs of a given type
    final String jobType = Strings.newRandomValidBpmnId();
    final Map<String, Object> variables = Map.of("config", "value");
    final var properties =
        new JobActivationPropertiesImpl()
            .setWorker(BufferUtil.wrapString("worker"), 0, "worker".length())
            .setTimeout(30_000L)
            .setTenantIds(List.of(TenantOwned.DEFAULT_TENANT_IDENTIFIER))
            .setFetchVariables(Set.of(new StringValue("config")));
    final RecordingJobStream jobStream =
        JOB_STREAMER.addJobStream(BufferUtil.wrapString(jobType), properties);

    // when a standalone job of that type is created
    final long jobKey =
        ENGINE.job().withType(jobType).withRetries(3).withVariables(variables).create().getKey();

    // then it is pushed to the worker's stream, with its input variables, as a STANDALONE job
    // (this only succeeds because activation authorization is relaxed for standalone jobs)
    Awaitility.await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(() -> assertThat(jobStream.getActivatedJobs()).hasSize(1));
    final var pushed = jobStream.getActivatedJobs().getFirst();
    assertThat(pushed.jobKey()).isEqualTo(jobKey);
    assertThat(pushed.jobRecord().getJobKind()).isEqualTo(JobKind.STANDALONE);
    assertThat(pushed.jobRecord().getVariables()).containsEntry("config", "value");
  }

  private static void interceptResponseWriter(final CommandResponseWriter responseWriter) {
    doAnswer(
            (Answer<CommandResponseWriter>)
                invocation -> {
                  final Object[] arguments = invocation.getArguments();
                  if (arguments != null
                      && arguments.length == 1
                      && arguments[0] instanceof final JobRecord jobRecord) {
                    responseValue = jobRecord;
                  }
                  return responseWriter;
                })
        .when(responseWriter)
        .valueWriter(any());
  }
}
