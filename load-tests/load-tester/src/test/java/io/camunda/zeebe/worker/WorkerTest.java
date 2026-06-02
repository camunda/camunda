/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.zeebe.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.ZeebeFuture;
import io.camunda.zeebe.client.api.command.CompleteJobCommandStep1;
import io.camunda.zeebe.client.api.command.PublishMessageCommandStep1;
import io.camunda.zeebe.client.api.command.PublishMessageCommandStep1.PublishMessageCommandStep2;
import io.camunda.zeebe.client.api.command.PublishMessageCommandStep1.PublishMessageCommandStep3;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.response.PublishMessageResponse;
import io.camunda.zeebe.client.api.worker.JobClient;
import io.camunda.zeebe.config.LoadTesterProperties;
import io.camunda.zeebe.config.WorkerProperties;
import io.camunda.zeebe.metrics.ConnectionMonitor;
import io.camunda.zeebe.util.PayloadReader;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class WorkerTest {

  private static final String CORRELATION_KEY_VAR = "correlationKey-var";
  private static final String CORRELATION_KEY_VALUE = "abc";
  private static final String MESSAGE_NAME = "messageName";
  private static final Duration COMPLETION_DELAY = Duration.ofMillis(250);

  @Test
  void shouldApplyCompletionDelayWhenPublishMessageFails() throws Exception {
    // given — worker configured to send a message before completing, with a publish that fails
    final var jobClient = mock(JobClient.class);
    final var job = mockJob();
    final var client = mock(ZeebeClient.class);
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
    final var client = mock(ZeebeClient.class);
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

  private static Worker newWorker(final ZeebeClient client, final WorkerProperties workerProps) {
    final var properties = new LoadTesterProperties();
    properties.setWorker(workerProps);
    final var payloadReader = mock(PayloadReader.class);
    when(payloadReader.readPayload(anyString())).thenReturn("{}");
    final var connectionMonitor = mock(ConnectionMonitor.class);
    return new Worker(client, properties, payloadReader, connectionMonitor);
  }

  @SuppressWarnings("unchecked")
  private static void mockFailingPublish(final ZeebeClient client) throws Exception {
    final var step1 = mock(PublishMessageCommandStep1.class);
    final var step2 = mock(PublishMessageCommandStep2.class);
    final var step3 = mock(PublishMessageCommandStep3.class);
    final ZeebeFuture<PublishMessageResponse> future = mock(ZeebeFuture.class);
    when(client.newPublishMessageCommand()).thenReturn(step1);
    when(step1.messageName(MESSAGE_NAME)).thenReturn(step2);
    when(step2.correlationKey(CORRELATION_KEY_VALUE)).thenReturn(step3);
    when(step3.send()).thenReturn(future);
    when(future.get(anyLong(), org.mockito.ArgumentMatchers.any(TimeUnit.class)))
        .thenThrow(new ExecutionException("simulated publish failure", new RuntimeException()));
  }

  @SuppressWarnings("unchecked")
  private static void mockSuccessfulPublish(final ZeebeClient client) throws Exception {
    final var step1 = mock(PublishMessageCommandStep1.class);
    final var step2 = mock(PublishMessageCommandStep2.class);
    final var step3 = mock(PublishMessageCommandStep3.class);
    final ZeebeFuture<PublishMessageResponse> future = mock(ZeebeFuture.class);
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
    final ZeebeFuture<Object> future = mock(ZeebeFuture.class);
    when(jobClient.newCompleteCommand(anyLong())).thenReturn(completeStep);
    when(completeStep.variables(anyString())).thenReturn(completeStep);
    when(completeStep.send()).thenReturn((ZeebeFuture) future);
    return completeStep;
  }
}
