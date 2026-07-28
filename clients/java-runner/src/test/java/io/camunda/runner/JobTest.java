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
package io.camunda.runner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.command.CompleteJobCommandStep1;
import io.camunda.client.api.command.FailJobCommandStep1;
import io.camunda.client.api.command.FailJobCommandStep1.FailJobCommandStep2;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.response.CompleteJobResponse;
import io.camunda.client.api.response.FailJobResponse;
import io.camunda.client.api.worker.JobClient;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for the {@link Job} interface contract backed by the default implementation.
 *
 * <p>The implementation is expected to provide a package-accessible (or public) concrete class
 * (suggested: {@code io.camunda.runner.internal.DefaultJob}) that wraps an {@link ActivatedJob} and
 * a {@link JobClient}. These tests drive that contract via the {@link Job} interface.
 *
 * <p>Factory method assumed: {@code Job.of(JobClient, ActivatedJob)} — a static factory on the
 * {@code Job} interface itself (or {@code Jobs.of(...)}) that returns the default implementation.
 * Implementer must expose this factory so tests can construct instances without referencing the
 * internal class directly.
 */
class JobTest {

  private JobClient jobClient;
  private ActivatedJob activatedJob;
  private CompleteJobCommandStep1 completeCommand;
  private FailJobCommandStep1 failCommand;
  private FailJobCommandStep2 failCommandStep2;
  private CamundaFuture<CompleteJobResponse> completeFuture;
  private CamundaFuture<FailJobResponse> failFuture;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    jobClient = mock(JobClient.class);
    activatedJob = mock(ActivatedJob.class);
    completeCommand = mock(CompleteJobCommandStep1.class);
    failCommand = mock(FailJobCommandStep1.class);
    failCommandStep2 = mock(FailJobCommandStep2.class);
    completeFuture = mock(CamundaFuture.class);
    failFuture = mock(CamundaFuture.class);

    // stub the command chain so calls don't NPE; send().join() returns null successfully.
    when(jobClient.newCompleteCommand(activatedJob)).thenReturn(completeCommand);
    when(completeCommand.variables((Map<String, Object>) null)).thenReturn(completeCommand);
    when(completeCommand.variables(Map.of("k", "v"))).thenReturn(completeCommand);
    when(completeCommand.variable("k", "v")).thenReturn(completeCommand);
    when(completeCommand.send()).thenReturn(completeFuture);

    when(jobClient.newFailCommand(activatedJob)).thenReturn(failCommand);
    when(failCommand.retries(org.mockito.ArgumentMatchers.anyInt())).thenReturn(failCommandStep2);
    when(failCommandStep2.errorMessage(org.mockito.ArgumentMatchers.anyString()))
        .thenReturn(failCommandStep2);
    when(failCommandStep2.send()).thenReturn(failFuture);
  }

  // -------------------------------------------------------------------------
  // Getter delegation to ActivatedJob
  // -------------------------------------------------------------------------

  @Test
  void shouldExposeActivatedJobGetters() {
    // given
    when(activatedJob.getKey()).thenReturn(42L);
    when(activatedJob.getType()).thenReturn("validate");
    when(activatedJob.getProcessInstanceKey()).thenReturn(100L);
    when(activatedJob.getElementId()).thenReturn("validate");
    when(activatedJob.getRetries()).thenReturn(3);
    when(activatedJob.getVariablesAsMap()).thenReturn(Map.of("orderId", "X1"));
    when(activatedJob.getVariables()).thenReturn("{\"orderId\":\"X1\"}");

    final Job job = Job.of(jobClient, activatedJob);

    // when / then
    assertThat(job.getKey()).isEqualTo(42L);
    assertThat(job.getType()).isEqualTo("validate");
    assertThat(job.getProcessInstanceKey()).isEqualTo(100L);
    assertThat(job.getElementId()).isEqualTo("validate");
    assertThat(job.getRetries()).isEqualTo(3);
    assertThat(job.variables()).containsEntry("orderId", "X1");
    assertThat(job.variablesAsJson()).isEqualTo("{\"orderId\":\"X1\"}");
  }

  @Test
  void shouldDelegateVariableTypedLookupToActivatedJob() {
    // given
    when(activatedJob.getVariable("orderId")).thenReturn("X1");

    final Job job = Job.of(jobClient, activatedJob);

    // when / then
    // variable(name, type) must return the value cast/converted to the requested type
    assertThat(job.variable("orderId", String.class)).isEqualTo("X1");
  }

  // -------------------------------------------------------------------------
  // complete()
  // -------------------------------------------------------------------------

  @Test
  void shouldCompleteJobViaClient() {
    // given
    final Job job = Job.of(jobClient, activatedJob);

    // when
    job.complete();

    // then — newCompleteCommand was invoked and send() was called
    verify(jobClient).newCompleteCommand(activatedJob);
    verify(completeCommand).send();
  }

  @Test
  void shouldCompleteJobWithVariablesMap() {
    // given
    final Job job = Job.of(jobClient, activatedJob);
    final Map<String, Object> vars = Map.of("k", "v");

    // when
    job.complete(vars);

    // then
    verify(jobClient).newCompleteCommand(activatedJob);
    verify(completeCommand).variables(vars);
    verify(completeCommand).send();
  }

  @Test
  void shouldCompleteJobWithSingleKeyValue() {
    // given
    final Job job = Job.of(jobClient, activatedJob);

    // when — sugar overload
    job.complete("k", "v");

    // then — must delegate to variables(Map) or variable(key, value) on the command
    verify(jobClient).newCompleteCommand(activatedJob);
    verify(completeCommand).send();
  }

  // -------------------------------------------------------------------------
  // fail()
  // -------------------------------------------------------------------------

  @Test
  void shouldFailJobWithReason() {
    // given
    when(activatedJob.getRetries()).thenReturn(3);
    final Job job = Job.of(jobClient, activatedJob);

    // when
    job.fail("boom");

    // then — retries = getRetries() - 1 = 2
    verify(jobClient).newFailCommand(activatedJob);
    verify(failCommand).retries(2);
    verify(failCommandStep2).errorMessage("boom");
    verify(failCommandStep2).send();
  }

  @Test
  void shouldFailJobWithExplicitRetries() {
    // given
    when(activatedJob.getRetries()).thenReturn(3);
    final Job job = Job.of(jobClient, activatedJob);

    // when
    job.fail("boom", 0);

    // then — explicit retries override the default decrement
    verify(jobClient).newFailCommand(activatedJob);
    verify(failCommand).retries(0);
    verify(failCommandStep2).errorMessage("boom");
    verify(failCommandStep2).send();
  }

  // -------------------------------------------------------------------------
  // Double-resolve guards
  // -------------------------------------------------------------------------

  @Test
  void shouldThrowOnDoubleResolveCompleteThenFail() {
    // given
    when(activatedJob.getRetries()).thenReturn(3);
    final Job job = Job.of(jobClient, activatedJob);
    job.complete();

    // when / then — second resolution must throw
    assertThatThrownBy(() -> job.fail("oops")).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void shouldThrowOnDoubleResolveFailThenComplete() {
    // given
    when(activatedJob.getRetries()).thenReturn(3);
    final Job job = Job.of(jobClient, activatedJob);
    job.fail("first");

    // when / then
    assertThatThrownBy(() -> job.complete()).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void shouldThrowOnDoubleResolveCompleteTwice() {
    // given
    final Job job = Job.of(jobClient, activatedJob);
    job.complete();

    // when / then
    assertThatThrownBy(() -> job.complete()).isInstanceOf(IllegalStateException.class);
  }
}
