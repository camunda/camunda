/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.command.CompleteAdHocSubProcessResultStep1.CompleteAdHocSubProcessResultStep2;
import io.camunda.client.api.command.CompleteJobCommandStep1;
import io.camunda.client.api.command.CompleteJobCommandStep1.CompleteJobCommandJobResultStep;
import io.camunda.client.api.command.CompleteJobResult;
import io.camunda.client.api.command.PublishMessageCommandStep1;
import io.camunda.client.api.command.PublishMessageCommandStep1.PublishMessageCommandStep2;
import io.camunda.client.api.command.PublishMessageCommandStep1.PublishMessageCommandStep3;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.response.PublishMessageResponse;
import io.camunda.client.api.worker.JobClient;
import io.camunda.zeebe.config.LoadTesterProperties;
import io.camunda.zeebe.config.WorkerProperties;
import io.camunda.zeebe.metrics.ConnectionMonitor;
import io.camunda.zeebe.util.PayloadReader;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class WorkerTest {

  private static final String CORRELATION_KEY_VAR = "correlationKey-var";
  private static final String CORRELATION_KEY_VALUE = "abc";
  private static final String MESSAGE_NAME = "messageName";
  private static final Duration COMPLETION_DELAY = Duration.ofMillis(250);
  private static final String AD_HOC_SUB_PROCESS_JOB_TYPE = "agent-visibility-orchestrator";

  @Test
  void shouldApplyCompletionDelayWhenPublishMessageFails() throws Exception {
    // given — worker configured to send a message before completing, with a publish that fails
    final var jobClient = mock(JobClient.class);
    final var job = mockJob();
    final var client = mock(CamundaClient.class);
    mockFailingPublish(client);
    final var worker = newWorker(client, sendMessageProperties());

    // when
    final long elapsed = timeHandleJob(worker, jobClient, job);

    // then — the configured completion delay still elapses on the failure path
    assertThat(elapsed)
        .describedAs("handleJob should honour the completion delay even when message publish fails")
        .isGreaterThanOrEqualTo(COMPLETION_DELAY.toMillis());
    // and — the job is neither completed nor explicitly failed; it is left to time out
    verify(jobClient, never()).newCompleteCommand(anyLong());
    verify(jobClient, never()).newCompleteCommand(job);
    verify(jobClient, never()).newFailCommand(anyLong());
    verify(jobClient, never()).newFailCommand(job);
  }

  @Test
  void shouldApplyCompletionDelayOnSuccessfulPublishAndComplete() throws Exception {
    // given — worker configured to send a message before completing, with a publish that succeeds
    final var jobClient = mock(JobClient.class);
    final var job = mockJob();
    final var client = mock(CamundaClient.class);
    mockSuccessfulPublish(client);
    final var completeStep = mockCompleteJob(jobClient);
    final var worker = newWorker(client, sendMessageProperties());

    // when
    final long elapsed = timeHandleJob(worker, jobClient, job);

    // then — the delay is honoured and a complete command is dispatched
    assertThat(elapsed)
        .describedAs("handleJob should honour the completion delay on the success path")
        .isGreaterThanOrEqualTo(COMPLETION_DELAY.toMillis());
    verify(jobClient).newCompleteCommand(job.getKey());
    verify(completeStep).send();
  }

  @Test
  void shouldFollowFixedRoundScheduleForAdHocSubProcessOrchestration() {
    // given — a single worker instance, so its internal round counter persists across
    // successive "activations" of the same process instance's orchestrator job
    final var jobClient = mock(JobClient.class);
    final var worker = newWorker(mock(CamundaClient.class), zeroDelayProperties());
    final long processInstanceKey = 777L;

    // round 1 — one tool activated, completion condition not yet fulfilled
    var round = driveAdHocSubProcessRound(worker, jobClient, processInstanceKey);
    assertThat(round.activatedElements()).containsExactly("tool-lookup-account");
    assertThat(round.completionConditionFulfilled()).isFalse();

    // round 2 — two tools activated within the same job result (parallel tool calls)
    round = driveAdHocSubProcessRound(worker, jobClient, processInstanceKey);
    assertThat(round.activatedElements())
        .containsExactly("tool-calculate-score", "tool-send-notification");
    assertThat(round.completionConditionFulfilled()).isFalse();

    // round 3 — one tool activated again, revisiting round 1's tool
    round = driveAdHocSubProcessRound(worker, jobClient, processInstanceKey);
    assertThat(round.activatedElements()).containsExactly("tool-lookup-account");
    assertThat(round.completionConditionFulfilled()).isFalse();

    // round 4 — no tools activated; the completion condition is fulfilled instead
    round = driveAdHocSubProcessRound(worker, jobClient, processInstanceKey);
    assertThat(round.activatedElements()).isEmpty();
    assertThat(round.completionConditionFulfilled()).isTrue();

    // and — the round-counter entry for this process instance was evicted once round 4
    // completed: a further call restarts the schedule from round 1 rather than continuing
    // past the end of it
    round = driveAdHocSubProcessRound(worker, jobClient, processInstanceKey);
    assertThat(round.activatedElements()).containsExactly("tool-lookup-account");
  }

  @Test
  void shouldFallThroughToPlainCompletionForOtherJobTypes() {
    // given — a job of a type that is not the ad-hoc-sub-process orchestrator (e.g. one of the
    // scenario's tool roles, or any other scenario's worker role)
    final var jobClient = mock(JobClient.class);
    final var job = mock(ActivatedJob.class);
    when(job.getType()).thenReturn("tool-lookup-account");
    when(job.getKey()).thenReturn(99L);
    final var completeStep = mockCompleteJob(jobClient);
    final var worker = newWorker(mock(CamundaClient.class), zeroDelayProperties());

    // when
    worker.handleJob(jobClient, job);

    // then — the existing plain-completion path is used, never the ad-hoc-sub-process one
    verify(jobClient).newCompleteCommand(job.getKey());
    verify(jobClient, never()).newCompleteCommand(job);
    verify(completeStep).send();
  }

  /** One round's outcome: which tool elements were activated, and the final completion flag. */
  private record AdHocSubProcessRoundResult(
      List<String> activatedElements, boolean completionConditionFulfilled) {}

  /**
   * Drives a single {@link Worker#handleJob} invocation for an ad-hoc-sub-process orchestrator job
   * on the given process instance, capturing the {@code JobResult} function passed to {@link
   * CompleteJobCommandStep1#withResult} and invoking it exactly as the real command builder would,
   * so the resulting {@code activateElement}/{@code completionConditionFulfilled} calls can be
   * asserted on.
   */
  @SuppressWarnings("unchecked")
  private static AdHocSubProcessRoundResult driveAdHocSubProcessRound(
      final Worker worker, final JobClient jobClient, final long processInstanceKey) {
    final var job = mock(ActivatedJob.class);
    when(job.getType()).thenReturn(AD_HOC_SUB_PROCESS_JOB_TYPE);
    when(job.getProcessInstanceKey()).thenReturn(processInstanceKey);

    final var completeStep = mock(CompleteJobCommandStep1.class);
    final CamundaFuture<Object> future = mock(CamundaFuture.class);
    when(jobClient.newCompleteCommand(job)).thenReturn(completeStep);
    when(completeStep.send()).thenReturn((CamundaFuture) future);

    final ArgumentCaptor<Function<CompleteJobCommandJobResultStep, CompleteJobResult>>
        resultFunctionCaptor = ArgumentCaptor.forClass(Function.class);
    when(completeStep.withResult(resultFunctionCaptor.capture())).thenReturn(completeStep);

    worker.handleJob(jobClient, job);

    final var jobResultStep = mock(CompleteJobCommandJobResultStep.class);
    final var adHocResult = mock(CompleteAdHocSubProcessResultStep2.class);
    when(jobResultStep.forAdHocSubProcess()).thenReturn(adHocResult);
    when(adHocResult.activateElement(anyString())).thenReturn(adHocResult);
    when(adHocResult.completionConditionFulfilled(anyBoolean())).thenReturn(adHocResult);

    resultFunctionCaptor.getValue().apply(jobResultStep);

    final var activatedElements = ArgumentCaptor.forClass(String.class);
    verify(adHocResult, atLeast(0)).activateElement(activatedElements.capture());
    final var completionConditionFulfilled = ArgumentCaptor.forClass(Boolean.class);
    verify(adHocResult).completionConditionFulfilled(completionConditionFulfilled.capture());

    return new AdHocSubProcessRoundResult(
        activatedElements.getAllValues(), completionConditionFulfilled.getValue());
  }

  private static WorkerProperties zeroDelayProperties() {
    final var props = new WorkerProperties();
    props.setCompletionDelay(Duration.ZERO);
    return props;
  }

  private static long timeHandleJob(
      final Worker worker, final JobClient jobClient, final ActivatedJob job) {
    final long start = System.currentTimeMillis();
    worker.handleJob(jobClient, job);
    return System.currentTimeMillis() - start;
  }

  private static ActivatedJob mockJob() {
    final var job = mock(ActivatedJob.class);
    when(job.getKey()).thenReturn(42L);
    when(job.getVariable(CORRELATION_KEY_VAR)).thenReturn(CORRELATION_KEY_VALUE);
    return job;
  }

  private static WorkerProperties sendMessageProperties() {
    final var props = new WorkerProperties();
    props.setSendMessage(true);
    props.setMessageName(MESSAGE_NAME);
    props.setCorrelationKeyVariableName(CORRELATION_KEY_VAR);
    props.setCompletionDelay(COMPLETION_DELAY);
    return props;
  }

  private static Worker newWorker(final CamundaClient client, final WorkerProperties workerProps) {
    final var properties = new LoadTesterProperties();
    properties.setWorker(workerProps);
    final var payloadReader = mock(PayloadReader.class);
    when(payloadReader.readPayload(anyString())).thenReturn("{}");
    final var connectionMonitor = mock(ConnectionMonitor.class);
    return new Worker(client, properties, payloadReader, connectionMonitor);
  }

  @SuppressWarnings("unchecked")
  private static void mockFailingPublish(final CamundaClient client) throws Exception {
    final var step1 = mock(PublishMessageCommandStep1.class);
    final var step2 = mock(PublishMessageCommandStep2.class);
    final var step3 = mock(PublishMessageCommandStep3.class);
    final CamundaFuture<PublishMessageResponse> future = mock(CamundaFuture.class);
    when(client.newPublishMessageCommand()).thenReturn(step1);
    when(step1.messageName(MESSAGE_NAME)).thenReturn(step2);
    when(step2.correlationKey(CORRELATION_KEY_VALUE)).thenReturn(step3);
    when(step3.send()).thenReturn(future);
    when(future.get(anyLong(), org.mockito.ArgumentMatchers.any(TimeUnit.class)))
        .thenThrow(new ExecutionException("simulated publish failure", new RuntimeException()));
  }

  @SuppressWarnings("unchecked")
  private static void mockSuccessfulPublish(final CamundaClient client) throws Exception {
    final var step1 = mock(PublishMessageCommandStep1.class);
    final var step2 = mock(PublishMessageCommandStep2.class);
    final var step3 = mock(PublishMessageCommandStep3.class);
    final CamundaFuture<PublishMessageResponse> future = mock(CamundaFuture.class);
    when(client.newPublishMessageCommand()).thenReturn(step1);
    when(step1.messageName(MESSAGE_NAME)).thenReturn(step2);
    when(step2.correlationKey(CORRELATION_KEY_VALUE)).thenReturn(step3);
    when(step3.send()).thenReturn(future);
    when(future.get(anyLong(), org.mockito.ArgumentMatchers.any(TimeUnit.class)))
        .thenReturn(mock(PublishMessageResponse.class));
  }

  @SuppressWarnings("unchecked")
  private static CompleteJobCommandStep1 mockCompleteJob(final JobClient jobClient) {
    final var completeStep = mock(CompleteJobCommandStep1.class);
    final CamundaFuture<Object> future = mock(CamundaFuture.class);
    when(jobClient.newCompleteCommand(anyLong())).thenReturn(completeStep);
    when(completeStep.variables(anyString())).thenReturn(completeStep);
    when(completeStep.send()).thenReturn((CamundaFuture) future);
    return completeStep;
  }
}
