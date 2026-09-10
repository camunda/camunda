/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.command.CompleteJobCommandStep1;
import io.camunda.client.api.command.PublishMessageCommandStep1;
import io.camunda.client.api.command.PublishMessageCommandStep1.PublishMessageCommandStep2;
import io.camunda.client.api.command.PublishMessageCommandStep1.PublishMessageCommandStep3;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.response.PublishMessageResponse;
import io.camunda.client.api.worker.JobClient;
import io.camunda.client.spring.properties.CamundaClientProperties;
import io.camunda.zeebe.config.LoadTesterProperties;
import io.camunda.zeebe.config.WorkerProperties;
import io.camunda.zeebe.metrics.ConnectionMonitor;
import io.camunda.zeebe.util.PayloadReader;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;

class WorkerTest {

  private static final String CORRELATION_KEY_VAR = "correlationKey-var";
  private static final String CORRELATION_KEY_VALUE = "abc";
  private static final String MESSAGE_NAME = "messageName";
  private static final Duration COMPLETION_DELAY = Duration.ofMillis(250);
  private static final Duration JOB_TIMEOUT = Duration.ofSeconds(30);

  private final MeterRegistry registry = new SimpleMeterRegistry();

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
  void shouldRecordHandleDurationOnBothPublishOutcomes() throws Exception {
    // given — one worker whose publish succeeds and one whose publish fails
    final var successClient = mock(CamundaClient.class);
    mockSuccessfulPublish(successClient);
    final var failingClient = mock(CamundaClient.class);
    mockFailingPublish(failingClient);
    final var jobClient = mock(JobClient.class);
    mockCompleteJob(jobClient);

    // when — a job is handled on the success path and one on the early-return failure path
    newWorker(successClient, sendMessageProperties()).handleJob(jobClient, mockJob());
    newWorker(failingClient, sendMessageProperties()).handleJob(mock(JobClient.class), mockJob());

    // then — both paths are timed, each taking at least the completion delay
    final var timer = registry.get("worker.handle.duration").timer();
    assertThat(timer.count()).isEqualTo(2);
    assertThat(timer.totalTime(TimeUnit.MILLISECONDS))
        .describedAs("both the success and the early-return path record the handling duration")
        .isGreaterThanOrEqualTo(2 * COMPLETION_DELAY.toMillis());
  }

  @Test
  void shouldRecordPublishDurationTaggedWithOutcome() throws Exception {
    // given — three workers whose publish succeeds, times out and errors respectively
    final var successClient = mock(CamundaClient.class);
    mockSuccessfulPublish(successClient);
    final var timingOutClient = mock(CamundaClient.class);
    mockPublishFailingWith(timingOutClient, new TimeoutException("simulated timeout"));
    final var erroringClient = mock(CamundaClient.class);
    mockPublishFailingWith(
        erroringClient, new ExecutionException("simulated failure", new RuntimeException()));
    final var jobClient = mock(JobClient.class);
    mockCompleteJob(jobClient);

    // when
    newWorker(successClient, sendMessageProperties()).handleJob(jobClient, mockJob());
    newWorker(timingOutClient, sendMessageProperties()).handleJob(jobClient, mockJob());
    newWorker(erroringClient, sendMessageProperties()).handleJob(jobClient, mockJob());

    // then — each publish is timed under its own outcome
    assertThat(registry.get("worker.publish.duration").tag("outcome", "success").timer().count())
        .isEqualTo(1);
    assertThat(registry.get("worker.publish.duration").tag("outcome", "timeout").timer().count())
        .isEqualTo(1);
    assertThat(registry.get("worker.publish.duration").tag("outcome", "error").timer().count())
        .isEqualTo(1);
  }

  @Test
  void shouldReportCompletionQueueDepth() throws Exception {
    // given
    final var client = mock(CamundaClient.class);
    mockSuccessfulPublish(client);
    final var jobClient = mock(JobClient.class);
    mockCompleteJob(jobClient);
    final var worker = newWorker(client, sendMessageProperties());
    final var gauge = registry.get("worker.completion.queue.depth").gauge();

    // when — a job is completed but its response is not checked yet
    assertThat(gauge.value()).isZero();
    worker.handleJob(jobClient, mockJob());

    // then — the untracked completion future shows up as queue depth
    assertThat(gauge.value()).isEqualTo(1);
  }

  @Test
  void shouldRecordIntakeDelayFromTheLeaseAlreadySpentOnDelivery() {
    // given — a job the broker activated 400ms ago, so 400ms of its lease went on delivery
    final var worker = newWorker(mock(CamundaClient.class), new WorkerProperties());
    final var job = mockJob(Duration.ofMillis(400));

    // when
    final var jobClient = mock(JobClient.class);
    mockCompleteJob(jobClient);
    worker.handleJob(jobClient, job);

    // then
    final var timer = registry.get("worker.intake.delay").timer();
    assertThat(timer.count()).isOne();
    assertThat(timer.totalTime(TimeUnit.MILLISECONDS)).isBetween(350d, 450d);
  }

  @Test
  void shouldSkipIntakeDelayWhenNoJobTimeoutIsConfigured() {
    // given — without a configured timeout the deadline cannot be resolved to an activation instant
    final var worker = newWorker(mock(CamundaClient.class), new WorkerProperties(), null);

    // when
    final var jobClient = mock(JobClient.class);
    mockCompleteJob(jobClient);
    worker.handleJob(jobClient, mockJob());

    // then — no sample rather than a wrong one
    assertThat(registry.find("worker.intake.delay").timer().count()).isZero();
  }

  private static long timeHandleJob(
      final Worker worker, final JobClient jobClient, final ActivatedJob job) {
    final long start = System.currentTimeMillis();
    worker.handleJob(jobClient, job);
    return System.currentTimeMillis() - start;
  }

  private static ActivatedJob mockJob() {
    return mockJob(Duration.ZERO);
  }

  /** A job the broker activated {@code intakeDelay} ago, i.e. with that much of its lease spent. */
  private static ActivatedJob mockJob(final Duration intakeDelay) {
    final var job = mock(ActivatedJob.class);
    when(job.getKey()).thenReturn(42L);
    when(job.getVariable(CORRELATION_KEY_VAR)).thenReturn(CORRELATION_KEY_VALUE);
    when(job.getDeadline())
        .thenReturn(System.currentTimeMillis() + JOB_TIMEOUT.toMillis() - intakeDelay.toMillis());
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

  private Worker newWorker(final CamundaClient client, final WorkerProperties workerProps) {
    return newWorker(client, workerProps, JOB_TIMEOUT);
  }

  private Worker newWorker(
      final CamundaClient client, final WorkerProperties workerProps, final Duration jobTimeout) {
    final var properties = new LoadTesterProperties();
    properties.setWorker(workerProps);
    final var clientProperties = new CamundaClientProperties();
    clientProperties.getWorker().getDefaults().setTimeout(jobTimeout);
    final var payloadReader = mock(PayloadReader.class);
    when(payloadReader.readPayload(anyString())).thenReturn("{}");
    final var connectionMonitor = mock(ConnectionMonitor.class);
    return new Worker(
        client, properties, clientProperties, payloadReader, connectionMonitor, registry);
  }

  private static void mockFailingPublish(final CamundaClient client) throws Exception {
    mockPublishFailingWith(
        client, new ExecutionException("simulated publish failure", new RuntimeException()));
  }

  @SuppressWarnings("unchecked")
  private static void mockPublishFailingWith(final CamundaClient client, final Exception failure)
      throws Exception {
    final var step1 = mock(PublishMessageCommandStep1.class);
    final var step2 = mock(PublishMessageCommandStep2.class);
    final var step3 = mock(PublishMessageCommandStep3.class);
    final CamundaFuture<PublishMessageResponse> future = mock(CamundaFuture.class);
    when(client.newPublishMessageCommand()).thenReturn(step1);
    when(step1.messageName(MESSAGE_NAME)).thenReturn(step2);
    when(step2.correlationKey(CORRELATION_KEY_VALUE)).thenReturn(step3);
    when(step3.send()).thenReturn(future);
    when(future.get(anyLong(), org.mockito.ArgumentMatchers.any(TimeUnit.class)))
        .thenThrow(failure);
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
