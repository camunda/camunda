/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.SecretStoreRegistries;
import io.camunda.zeebe.protocol.impl.record.value.job.JobBatchRecord;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.value.ClusterVariableKind;
import io.camunda.zeebe.protocol.record.value.JobKind;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.stream.api.CommandResponseWriter;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import io.camunda.zeebe.util.buffer.BufferWriter;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public final class StandaloneJobTest {

  /** Every secret resolves, so activation hands out jobs that reference one. */
  @ClassRule
  public static final EngineRule ENGINE =
      EngineRule.singlePartition()
          .withSecretStoreRegistry(SecretStoreRegistries.resolveAll("resolved"));

  private static final int REQUEST_STREAM_ID = 1;
  private static final AtomicLong REQUEST_IDS = new AtomicLong();
  private static final Map<Long, Response> RESPONSES = new ConcurrentHashMap<>();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  private final String jobType = "standalone-" + UUID.randomUUID();
  private final long requestId = REQUEST_IDS.incrementAndGet();

  @BeforeClass
  public static void captureResponses() {
    final CommandResponseWriter writer = ENGINE.getCommandResponseWriter();
    final var intent = new AtomicReference<Intent>();
    final var value = new AtomicReference<UnsafeBuffer>();
    doAnswer(
            invocation -> {
              intent.set(invocation.getArgument(0));
              return writer;
            })
        .when(writer)
        .intent(any());
    doAnswer(
            invocation -> {
              value.set(copyOf(invocation.getArgument(0)));
              return writer;
            })
        .when(writer)
        .valueWriter(any());
    doAnswer(
            invocation -> {
              final long id = invocation.getArgument(1);
              RESPONSES.put(id, new Response(intent.get(), value.get()));
              return null;
            })
        .when(writer)
        .tryWriteResponse(anyInt(), anyLong());
  }

  @Test
  public void shouldHandOutStandaloneJobWithEvaluatedInput() {
    // given
    ENGINE
        .standaloneJob()
        .withType(jobType)
        .withInputExpression("={channel: \"general\", limit: 1 + 2}")
        .withCustomHeaders(Map.of("camunda.query", "validateCredentials"))
        .withRequest(REQUEST_STREAM_ID, requestId)
        .create();

    // when
    final JobRecordValue job = activateJob();

    // then
    assertThat(job.getJobKind()).isEqualTo(JobKind.STANDALONE);
    assertThat(job.getProcessInstanceKey()).isEqualTo(-1L);
    assertThat(job.getBpmnProcessId()).isEmpty();
    assertThat(job.getVariables()).containsOnlyKeys("channel", "limit");
    assertThat(job.getVariables()).containsEntry("channel", "general");
    assertThat(((Number) job.getVariables().get("limit")).intValue()).isEqualTo(3);
    assertThat(job.getCustomHeaders()).containsEntry("camunda.query", "validateCredentials");
    assertThat(RESPONSES).doesNotContainKey(requestId);
  }

  @Test
  public void shouldAnswerCreatorWhenWorkerCompletesJob() {
    // given
    final long jobKey = createJob();
    activateJob();

    // when
    ENGINE.job().withKey(jobKey).withVariables(Map.of("valid", true)).complete();

    // then
    final Response response = awaitResponse();
    assertThat(response.intent()).isEqualTo(JobIntent.COMPLETED);
    assertThat(response.job().getVariables()).containsExactly(Map.entry("valid", true));
    assertThat(RecordingExporter.jobRecords(JobIntent.ANSWER).withRecordKey(jobKey).exists())
        .isTrue();
  }

  @Test
  public void shouldAnswerCreatorWhenWorkerThrowsError() {
    // given
    final long jobKey = createJob();
    activateJob();

    // when
    ENGINE
        .job()
        .withKey(jobKey)
        .withErrorCode("INVALID_CREDENTIALS")
        .withErrorMessage("the token was revoked")
        .throwError();

    // then
    final Response response = awaitResponse();
    assertThat(response.intent()).isEqualTo(JobIntent.ERROR_THROWN);
    assertThat(response.job().getErrorCode()).isEqualTo("INVALID_CREDENTIALS");
    assertThat(response.job().getErrorMessage()).isEqualTo("the token was revoked");
    assertJobIsGone(jobKey);
  }

  @Test
  public void shouldAnswerCreatorWithoutIncidentWhenWorkerFailsWithoutRetriesLeft() {
    // given
    final long jobKey = createJob();
    activateJob();

    // when
    ENGINE.job().withKey(jobKey).withRetries(0).withErrorMessage("connection refused").fail();

    // then
    final Response response = awaitResponse();
    assertThat(response.intent()).isEqualTo(JobIntent.FAILED);
    assertThat(response.job().getErrorMessage()).isEqualTo("connection refused");
    assertThat(
            RecordingExporter.records()
                .limit(r -> r.getIntent() == JobIntent.ANSWER && r.getKey() == jobKey)
                .filter(r -> r.getValueType() == ValueType.INCIDENT))
        .isEmpty();
    assertJobIsGone(jobKey);
  }

  @Test
  public void shouldHandOutJobAgainWhenWorkerFailsWithRetriesLeft() {
    // given
    final long jobKey =
        ENGINE
            .standaloneJob()
            .withType(jobType)
            .withRetries(2)
            .withRequest(REQUEST_STREAM_ID, requestId)
            .create()
            .getKey();
    activateJob();

    // when
    ENGINE.job().withKey(jobKey).withRetries(1).withErrorMessage("try again").fail();

    // then
    final var reactivated = ENGINE.jobs().withType(jobType).activate().getValue();
    assertThat(reactivated.getJobKeys()).containsExactly(jobKey);
    assertThat(RESPONSES).doesNotContainKey(requestId);
  }

  @Test
  public void shouldExpireJobThatNoWorkerActivates() {
    // given
    final long jobKey =
        ENGINE
            .standaloneJob()
            .withType(jobType)
            .withTimeToAnswer(Duration.ofSeconds(1))
            .withRequest(REQUEST_STREAM_ID, requestId)
            .create()
            .getKey();

    // when
    ENGINE.increaseTime(Duration.ofSeconds(2));

    // then
    assertThat(RecordingExporter.jobRecords(JobIntent.EXPIRED).withRecordKey(jobKey).exists())
        .isTrue();
    final Response response = awaitResponse();
    assertThat(response.intent()).isEqualTo(JobIntent.EXPIRED);
    assertThat(response.job().getWorker()).describedAs("no worker activated the job").isEmpty();
    assertThat(ENGINE.jobs().withType(jobType).activate().getValue().getJobKeys()).isEmpty();
  }

  @Test
  public void shouldExpireActivatedJobAndRejectTheLateAnswer() {
    // given
    final long jobKey =
        ENGINE
            .standaloneJob()
            .withType(jobType)
            .withTimeToAnswer(Duration.ofSeconds(1))
            .withRequest(REQUEST_STREAM_ID, requestId)
            .create()
            .getKey();
    ENGINE.jobs().withType(jobType).byWorker("slow-worker").activate();

    // when
    ENGINE.increaseTime(Duration.ofSeconds(2));
    final Response response = awaitResponse();
    final var lateCompletion = ENGINE.job().withKey(jobKey).expectRejection().complete();

    // then
    assertThat(response.intent()).isEqualTo(JobIntent.EXPIRED);
    assertThat(response.job().getWorker()).isEqualTo("slow-worker");
    Assertions.assertThat(lateCompletion).hasRejectionType(RejectionType.NOT_FOUND);
  }

  @Test
  public void shouldInjectSecretsOfConnectionClusterVariable() {
    // given
    final String connection = "connection_" + UUID.randomUUID().toString().replace("-", "");
    ENGINE
        .clusterVariables()
        .withName(connection)
        .setGlobalScope()
        .withKind(ClusterVariableKind.SECRET_REFERENCE)
        .withValue(Map.of("token", "camunda.secrets.slack_token"))
        .create();

    // when
    final var created =
        ENGINE
            .standaloneJob()
            .withType(jobType)
            .withInputExpression("={authentication: camunda.vars.cluster." + connection + "}")
            .withRequest(REQUEST_STREAM_ID, requestId)
            .create();
    final long activationRequestId = REQUEST_IDS.incrementAndGet();
    ENGINE
        .jobs()
        .withType(jobType)
        .withRequestStreamId(REQUEST_STREAM_ID)
        .withRequestId(activationRequestId)
        .activate();

    // then
    assertThat(created.getValue().getVariables())
        .describedAs("the log keeps the placeholder, not the secret")
        .containsEntry("authentication", Map.of("token", "camunda.secrets.slack_token"));
    assertThat(created.getValue().getSecretReferences())
        .extracting(JobRecordValue.JobSecretReferenceValue::getPath)
        .containsExactly("/authentication/token");
    assertThat(awaitResponse(activationRequestId).batch().getJobs().getFirst().getVariables())
        .describedAs("only the worker gets the secret")
        .containsEntry("authentication", Map.of("token", "resolved"));
  }

  @Test
  public void shouldRejectInputExpressionThatIsNotAContext() {
    // when
    final var rejection =
        ENGINE
            .standaloneJob()
            .withType(jobType)
            .withInputExpression("=\"not a context\"")
            .withRequest(REQUEST_STREAM_ID, requestId)
            .createExpectingRejection();

    // then
    Assertions.assertThat(rejection)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT)
        .hasRejectionReason(
            "Expected to create a standalone job with a valid input expression, but it must"
                + " evaluate to a context, but it evaluated to 'STRING'");
  }

  @Test
  public void shouldRejectStandaloneJobWithoutType() {
    // when
    final var rejection =
        ENGINE.standaloneJob().withRequest(REQUEST_STREAM_ID, requestId).createExpectingRejection();

    // then
    Assertions.assertThat(rejection).hasRejectionType(RejectionType.INVALID_ARGUMENT);
  }

  private long createJob() {
    return ENGINE
        .standaloneJob()
        .withType(jobType)
        .withRequest(REQUEST_STREAM_ID, requestId)
        .create()
        .getKey();
  }

  private JobRecordValue activateJob() {
    final var batch = ENGINE.jobs().withType(jobType).activate().getValue();
    assertThat(batch.getJobs()).hasSize(1);
    return batch.getJobs().getFirst();
  }

  private Response awaitResponse() {
    return awaitResponse(requestId);
  }

  private static Response awaitResponse(final long id) {
    return await()
        .atMost(Duration.ofSeconds(5))
        .until(() -> Optional.ofNullable(RESPONSES.get(id)), Optional::isPresent)
        .orElseThrow();
  }

  private void assertJobIsGone(final long jobKey) {
    final var rejection = ENGINE.job().withKey(jobKey).expectRejection().complete();
    Assertions.assertThat(rejection).hasRejectionType(RejectionType.NOT_FOUND);
  }

  private static UnsafeBuffer copyOf(final BufferWriter value) {
    final var buffer = new UnsafeBuffer(new byte[value.getLength()]);
    value.write(buffer, 0);
    return buffer;
  }

  private record Response(Intent intent, UnsafeBuffer value) {

    JobRecord job() {
      final var job = new JobRecord();
      job.wrap(value);
      return job;
    }

    JobBatchRecord batch() {
      final var batch = new JobBatchRecord();
      batch.wrap(value);
      return batch;
    }
  }
}
