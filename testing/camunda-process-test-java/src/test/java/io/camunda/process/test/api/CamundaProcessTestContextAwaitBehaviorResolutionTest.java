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
package io.camunda.process.test.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.camunda.client.CamundaClient;
import io.camunda.client.CamundaClientBuilder;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.search.response.Job;
import io.camunda.client.api.search.response.UserTask;
import io.camunda.process.test.impl.assertions.util.InstantProbeAwaitBehavior;
import io.camunda.process.test.impl.client.CamundaClockClient;
import io.camunda.process.test.impl.extension.CamundaProcessTestContextImpl;
import io.camunda.process.test.impl.extension.ConditionalBehaviorEngine;
import io.camunda.process.test.impl.runtime.CamundaProcessTestRuntime;
import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.assertj.core.api.SoftAssertionsProvider.ThrowingRunnable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Verifies that {@link CamundaProcessTestContextImpl} resolves the await behavior dynamically at
 * execution time (via the supplier) rather than using a statically pre-evaluated behavior. This is
 * critical for the conditional behavior engine, where the evaluation scope sets a thread-local
 * override via {@link CamundaAssert#withAwaitBehaviorOverride}.
 */
@ExtendWith(MockitoExtension.class)
class CamundaProcessTestContextAwaitBehaviorResolutionTest {

  private static final long JOB_KEY = 100L;
  private static final String JOB_TYPE = "test-job";
  private static final long USER_TASK_KEY = 200L;
  private static final String USER_TASK_ELEMENT_ID = "task1";

  @Mock private CamundaProcessTestRuntime camundaProcessTestRuntime;
  @Mock private Consumer<AutoCloseable> clientCreationCallback;
  @Mock private CamundaClockClient clockClient;
  @Mock private JsonMapper jsonMapper;
  @Mock private io.camunda.zeebe.client.api.JsonMapper zeebeJsonMapper;

  @Mock private CamundaClientBuilderFactory camundaClientBuilderFactory;
  @Mock private CamundaClientBuilder camundaClientBuilder;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private CamundaClient camundaClient;

  @Mock private Job job;
  @Mock private UserTask userTask;

  private TrackingAwaitBehavior trackingBehavior;
  private ConditionalBehaviorEngine engine;
  private CamundaProcessTestContext context;

  @BeforeEach
  void setup() {
    trackingBehavior = new TrackingAwaitBehavior();
    engine = new ConditionalBehaviorEngine();

    // given - mock client wiring
    when(camundaProcessTestRuntime.getCamundaClientBuilderFactory())
        .thenReturn(camundaClientBuilderFactory);
    when(camundaClientBuilderFactory.get()).thenReturn(camundaClientBuilder);
    when(camundaClientBuilder.build()).thenReturn(camundaClient);

    context =
        new CamundaProcessTestContextImpl(
            camundaProcessTestRuntime,
            clientCreationCallback,
            clockClient,
            CamundaAssert::getAwaitBehavior,
            jsonMapper,
            zeebeJsonMapper,
            engine);

    engine.start(
        () -> {},
        evaluation -> CamundaAssert.withAwaitBehaviorOverride(trackingBehavior, evaluation),
        Duration.ofMillis(50));
  }

  @AfterEach
  void tearDown() {
    engine.stop();
  }

  @Test
  void shouldUseOverriddenAwaitBehaviorWhenCompletingJobInsideConditionalEngine() {
    // given
    when(camundaClient.newJobSearchRequest().filter(any(Consumer.class)).send().join().items())
        .thenReturn(Collections.singletonList(job));
    when(job.getJobKey()).thenReturn(JOB_KEY);
    when(job.getType()).thenReturn(JOB_TYPE);

    // when - complete job inside conditional engine
    engine.when(() -> {}).then(() -> context.completeJob(JOB_TYPE));

    // then - the tracking behavior was used, proving the supplier resolved the override
    await().untilAsserted(() -> assertThat(trackingBehavior.wasUsed()).isTrue());
  }

  @Test
  void shouldUseOverriddenAwaitBehaviorWhenCompletingUserTaskInsideConditionalEngine() {
    // given
    when(camundaClient.newUserTaskSearchRequest().filter(any(Consumer.class)).send().join().items())
        .thenReturn(Collections.singletonList(userTask));
    when(userTask.getUserTaskKey()).thenReturn(USER_TASK_KEY);
    when(userTask.getElementId()).thenReturn(USER_TASK_ELEMENT_ID);

    // when - complete user task inside conditional engine
    engine.when(() -> {}).then(() -> context.completeUserTask(USER_TASK_ELEMENT_ID));

    // then - the tracking behavior was used, proving the supplier resolved the override
    await().untilAsserted(() -> assertThat(trackingBehavior.wasUsed()).isTrue());
  }

  private static final class TrackingAwaitBehavior implements CamundaAssertAwaitBehavior {

    private final AtomicBoolean used = new AtomicBoolean(false);

    private final CamundaAssertAwaitBehavior delegate = new InstantProbeAwaitBehavior();

    boolean wasUsed() {
      return used.get();
    }

    @Override
    public void untilAsserted(final ThrowingRunnable assertion) throws AssertionError {
      delegate.untilAsserted(assertion);
      used.set(true);
    }

    @Override
    public Duration getAssertionInterval() {
      return Duration.ZERO;
    }

    @Override
    public void setAssertionInterval(final Duration assertionInterval) {
      // no-op
    }

    @Override
    public Duration getAssertionTimeout() {
      return Duration.ZERO;
    }

    @Override
    public void setAssertionTimeout(final Duration assertionTimeout) {
      // no-op
    }

    @Override
    public CamundaAssertAwaitBehavior withAssertionTimeout(final Duration assertionTimeout) {
      return this;
    }
  }
}
