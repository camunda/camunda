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
package io.camunda.runner.internal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
import io.camunda.runner.Job;
import java.util.Collections;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link JobHandlerAdapter} — the internal adapter that wraps user lambdas ({@link
 * Function}&lt;{@link Job}, Map&gt; or {@link Consumer}&lt;{@link Job}&gt;) into an SDK {@link
 * io.camunda.client.api.worker.JobHandler}.
 *
 * <p>Resolution rules under test:
 *
 * <ul>
 *   <li>Function returning non-null Map → complete(map)
 *   <li>Function returning null or empty Map → complete() with no variables
 *   <li>Consumer with no explicit resolve → auto-complete()
 *   <li>Consumer calling complete/fail explicitly → that result wins; no auto-complete
 *   <li>Explicit resolve suppresses fall-through auto-complete
 *   <li>Uncaught exception → rethrown (SDK auto-fail path)
 * </ul>
 *
 * <p>The {@link JobHandlerAdapter} is instantiated directly via its constructors:
 *
 * <pre>
 *   new JobHandlerAdapter(Function&lt;Job, Map&lt;String, Object&gt;&gt; fn)
 *   new JobHandlerAdapter(Consumer&lt;Job&gt; consumer)
 * </pre>
 *
 * The adapter implements {@link io.camunda.client.api.worker.JobHandler} and its {@code
 * handle(JobClient, ActivatedJob)} method is what we invoke in each test.
 */
class LambdaResolutionTest {

  private JobClient jobClient;
  private ActivatedJob activatedJob;
  private CompleteJobCommandStep1 completeCommand;
  private FailJobCommandStep1 failCommand;
  private FailJobCommandStep2 failCommandStep2;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    jobClient = mock(JobClient.class);
    activatedJob = mock(ActivatedJob.class);
    completeCommand = mock(CompleteJobCommandStep1.class);
    failCommand = mock(FailJobCommandStep1.class);
    failCommandStep2 = mock(FailJobCommandStep2.class);
    final CamundaFuture<CompleteJobResponse> completeFuture = mock(CamundaFuture.class);
    final CamundaFuture<FailJobResponse> failFuture = mock(CamundaFuture.class);

    when(activatedJob.getRetries()).thenReturn(3);

    // stub complete chain — send().join() returns null successfully
    when(jobClient.newCompleteCommand(activatedJob)).thenReturn(completeCommand);
    when(completeCommand.variables(any(Map.class))).thenReturn(completeCommand);
    when(completeCommand.send()).thenReturn(completeFuture);

    // stub fail chain
    when(jobClient.newFailCommand(activatedJob)).thenReturn(failCommand);
    when(failCommand.retries(anyInt())).thenReturn(failCommandStep2);
    when(failCommandStep2.errorMessage(anyString())).thenReturn(failCommandStep2);
    when(failCommandStep2.send()).thenReturn(failFuture);
  }

  // -------------------------------------------------------------------------
  // Function overload — null return
  // -------------------------------------------------------------------------

  @Test
  void shouldAutoCompleteOnFunctionReturnNull() throws Exception {
    // given — function that returns null
    final Function<Job, Map<String, Object>> fn = job -> null;
    final JobHandlerAdapter adapter = new JobHandlerAdapter(fn);

    // when
    adapter.handle(jobClient, activatedJob);

    // then — complete() with no variables
    verify(jobClient).newCompleteCommand(activatedJob);
    verify(completeCommand, never()).variables(any(Map.class));
    verify(completeCommand).send();
  }

  // -------------------------------------------------------------------------
  // Function overload — empty map return
  // -------------------------------------------------------------------------

  @Test
  void shouldAutoCompleteOnFunctionReturnEmptyMap() throws Exception {
    // given — function that returns an empty map
    final Function<Job, Map<String, Object>> fn = job -> Collections.emptyMap();
    final JobHandlerAdapter adapter = new JobHandlerAdapter(fn);

    // when
    adapter.handle(jobClient, activatedJob);

    // then — complete() with no variables (empty map treated same as null)
    verify(jobClient).newCompleteCommand(activatedJob);
    verify(completeCommand, never()).variables(any(Map.class));
    verify(completeCommand).send();
  }

  // -------------------------------------------------------------------------
  // Function overload — non-empty map return
  // -------------------------------------------------------------------------

  @Test
  void shouldCompleteWithVariablesOnFunctionReturnMap() throws Exception {
    // given — function that returns variables
    final Map<String, Object> vars = Map.of("a", 1);
    final Function<Job, Map<String, Object>> fn = job -> vars;
    final JobHandlerAdapter adapter = new JobHandlerAdapter(fn);

    // when
    adapter.handle(jobClient, activatedJob);

    // then — complete(map) called with the returned variables
    verify(jobClient).newCompleteCommand(activatedJob);
    verify(completeCommand).variables(vars);
    verify(completeCommand).send();
  }

  // -------------------------------------------------------------------------
  // Consumer overload — no explicit resolve → auto-complete
  // -------------------------------------------------------------------------

  @Test
  void shouldAutoCompleteOnConsumerFallthrough() throws Exception {
    // given — consumer that does NOT call complete or fail
    final Consumer<Job> consumer =
        job -> {
          /* no-op */
        };
    final JobHandlerAdapter adapter = new JobHandlerAdapter(consumer);

    // when
    adapter.handle(jobClient, activatedJob);

    // then — adapter must auto-complete after consumer returns
    verify(jobClient).newCompleteCommand(activatedJob);
    verify(completeCommand).send();
  }

  // -------------------------------------------------------------------------
  // Consumer overload — explicit complete → no auto-complete
  // -------------------------------------------------------------------------

  @Test
  void shouldHonourExplicitCompleteInConsumer() throws Exception {
    // given — consumer that calls job.complete(vars) explicitly
    final Map<String, Object> vars = Map.of("k", "v");
    final Consumer<Job> consumer = job -> job.complete(vars);
    final JobHandlerAdapter adapter = new JobHandlerAdapter(consumer);

    // when
    adapter.handle(jobClient, activatedJob);

    // then — complete(vars) reached the client exactly once; no double complete
    verify(jobClient).newCompleteCommand(activatedJob);
    verify(completeCommand).variables(vars);
    verify(completeCommand).send();
  }

  // -------------------------------------------------------------------------
  // Consumer overload — explicit fail → no auto-complete
  // -------------------------------------------------------------------------

  @Test
  void shouldHonourExplicitFailInConsumer() throws Exception {
    // given — consumer that calls job.fail explicitly
    final Consumer<Job> consumer = job -> job.fail("boom");
    final JobHandlerAdapter adapter = new JobHandlerAdapter(consumer);

    // when
    adapter.handle(jobClient, activatedJob);

    // then — fail path reached; complete must NOT be called
    verify(jobClient).newFailCommand(activatedJob);
    verify(failCommandStep2).errorMessage("boom");
    verify(failCommandStep2).send();
    verify(jobClient, never()).newCompleteCommand(any());
  }

  // -------------------------------------------------------------------------
  // Explicit resolve suppresses fall-through
  // -------------------------------------------------------------------------

  @Test
  void shouldNotAutoCompleteAfterExplicitResolve() throws Exception {
    // given — consumer resolves explicitly; adapter must not resolve again
    final Consumer<Job> consumer = job -> job.complete(Map.of("done", true));
    final JobHandlerAdapter adapter = new JobHandlerAdapter(consumer);

    // when
    adapter.handle(jobClient, activatedJob);

    // then — newCompleteCommand invoked exactly once (the explicit one, not the auto one)
    verify(jobClient).newCompleteCommand(activatedJob);

    // complete.send() called once
    verify(completeCommand).send();
  }

  // -------------------------------------------------------------------------
  // Uncaught exception — adapter rethrows; SDK handles auto-fail
  // -------------------------------------------------------------------------

  @Test
  void shouldPropagateUncaughtExceptionFromFunction() {
    // given — function that throws a runtime exception
    final Function<Job, Map<String, Object>> fn =
        job -> {
          throw new IllegalStateException("worker exploded");
        };
    final JobHandlerAdapter adapter = new JobHandlerAdapter(fn);

    // when / then — adapter must rethrow so the SDK's auto-fail path applies
    assertThatThrownBy(() -> adapter.handle(jobClient, activatedJob))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("worker exploded");

    // and the adapter must NOT have called complete or fail itself
    verify(jobClient, never()).newCompleteCommand(any());
    verify(jobClient, never()).newFailCommand(any());
  }

  @Test
  void shouldPropagateUncaughtExceptionFromConsumer() {
    // given — consumer that throws
    final Consumer<Job> consumer =
        job -> {
          throw new RuntimeException("consumer exploded");
        };
    final JobHandlerAdapter adapter = new JobHandlerAdapter(consumer);

    // when / then
    assertThatThrownBy(() -> adapter.handle(jobClient, activatedJob))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("consumer exploded");

    verify(jobClient, never()).newCompleteCommand(any());
    verify(jobClient, never()).newFailCommand(any());
  }
}
