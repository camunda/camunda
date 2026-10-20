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
import io.camunda.zeebe.config.LoadTesterProperties;
import io.camunda.zeebe.config.WorkerProperties;
import io.camunda.zeebe.metrics.ConnectionMonitor;
import io.camunda.zeebe.util.PayloadReader;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class WorkerTest {

  private static final String CORRELATION_KEY_VAR = "correlationKey-var";
  private static final String CORRELATION_KEY_VALUE = "abc";
  private static final String MESSAGE_NAME = "messageName";
  private static final Duration COMPLETION_DELAY = Duration.ofMillis(250);
  private static final String JOB_TYPE = "test-job";
  private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");

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

  private static long timeHandleJob(
      final Worker worker, final JobClient jobClient, final ActivatedJob job) {
    final long start = System.currentTimeMillis();
    worker.handleJob(jobClient, job);
    return System.currentTimeMillis() - start;
  }

  @Test
  void shouldNeitherCompleteNorDelayJobWhenCompleteJobsIsDisabled() {
    // given
    final var jobClient = mock(JobClient.class);
    final var job = mockJob();
    final var workerProps = new WorkerProperties();
    workerProps.setCompleteJobs(false);
    workerProps.setCompletionDelay(COMPLETION_DELAY);
    final var worker = newWorker(mock(CamundaClient.class), workerProps);

    // when
    final long elapsed = timeHandleJob(worker, jobClient, job);

    // then
    verify(jobClient, never()).newCompleteCommand(anyLong());
    assertThat(elapsed)
        .describedAs("the handler returns at once, so the job worker can pick up the next job")
        .isLessThan(COMPLETION_DELAY.toMillis());
  }

  @Test
  void shouldRecordDelayWhenJobIsActivatedAgainAfterItsDeadline() {
    // given
    final var registry = new SimpleMeterRegistry();
    final var clock = new MutableClock();
    final var worker = newWorker(notCompletingProperties(), registry, clock);
    final var jobClient = mock(JobClient.class);
    final var job = mockJob();
    when(job.getDeadline()).thenReturn(START.plusSeconds(30).toEpochMilli());
    worker.handleJob(jobClient, job);

    // when — the job is activated again 2s after the deadline of the first activation
    clock.advance(Duration.ofSeconds(32));
    when(job.getDeadline()).thenReturn(START.plusSeconds(62).toEpochMilli());
    worker.handleJob(jobClient, job);

    // then
    final var timer = registry.get("job.reactivation.delay").tag("job_type", JOB_TYPE).timer();
    assertThat(timer.count()).isEqualTo(1);
    assertThat(timer.totalTime(TimeUnit.MILLISECONDS)).isEqualTo(2000);
  }

  @Test
  void shouldRecordZeroDelayWhenJobIsActivatedAgainBeforeItsDeadline() {
    // given
    final var registry = new SimpleMeterRegistry();
    final var clock = new MutableClock();
    final var worker = newWorker(notCompletingProperties(), registry, clock);
    final var jobClient = mock(JobClient.class);
    final var job = mockJob();
    when(job.getDeadline()).thenReturn(START.plusSeconds(30).toEpochMilli());
    worker.handleJob(jobClient, job);

    // when
    clock.advance(Duration.ofSeconds(29));
    worker.handleJob(jobClient, job);

    // then
    final var timer = registry.get("job.reactivation.delay").timer();
    assertThat(timer.count()).isEqualTo(1);
    assertThat(timer.totalTime(TimeUnit.MILLISECONDS)).isZero();
  }

  @Test
  void shouldNotRecordDelayOnFirstActivation() {
    // given
    final var registry = new SimpleMeterRegistry();
    final var worker = newWorker(notCompletingProperties(), registry, new MutableClock());
    final var job = mockJob();
    when(job.getDeadline()).thenReturn(START.plusSeconds(30).toEpochMilli());

    // when
    worker.handleJob(mock(JobClient.class), job);

    // then
    assertThat(registry.get("job.reactivation.delay").timer().count()).isZero();
  }

  private static WorkerProperties notCompletingProperties() {
    final var props = new WorkerProperties();
    props.setCompleteJobs(false);
    return props;
  }

  private static final class MutableClock extends Clock {
    private Instant now = START;

    void advance(final Duration duration) {
      now = now.plus(duration);
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(final ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
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
    return new Worker(
        client, properties, payloadReader, connectionMonitor, new SimpleMeterRegistry(), JOB_TYPE);
  }

  private static Worker newWorker(
      final WorkerProperties workerProps, final MeterRegistry registry, final Clock clock) {
    final var properties = new LoadTesterProperties();
    properties.setWorker(workerProps);
    final var payloadReader = mock(PayloadReader.class);
    when(payloadReader.readPayload(anyString())).thenReturn("{}");
    return new Worker(
        mock(CamundaClient.class),
        properties,
        payloadReader,
        mock(ConnectionMonitor.class),
        registry,
        JOB_TYPE,
        clock);
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
