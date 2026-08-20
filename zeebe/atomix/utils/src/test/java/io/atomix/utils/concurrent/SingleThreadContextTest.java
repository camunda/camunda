/*
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
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
package io.atomix.utils.concurrent;

import static io.atomix.utils.concurrent.Threads.namedThreads;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.util.CheckedRunnable;
import io.camunda.zeebe.util.ExponentialBackoffRetryDelay;
import io.camunda.zeebe.util.RetryDelayStrategy;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SingleThreadContextTest {

  private final Logger log = LoggerFactory.getLogger("thread");
  private Consumer<Throwable> exceptionHandler;

  @Test
  public void shouldInvokeHandlerOnException() throws InterruptedException {
    // given
    try (final var threadContext =
        new SingleThreadContext(namedThreads("test", log), e -> exceptionHandler.accept(e))) {
      final CountDownLatch latch = new CountDownLatch(1);
      exceptionHandler = e -> latch.countDown();

      // when
      threadContext.execute(
          () -> {
            throw new RuntimeException();
          });

      assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();

      // then
      assertThat(0).isEqualTo(latch.getCount());
    }
  }

  @Nested
  public class Schedule {
    private static final int NUM_RETRIES = 10;
    private static final Duration MAX_DELAY = Duration.ofMillis(10);
    private CheckedRunnable taskToRetry;
    private RetryDelayStrategy delayStrategy;
    private AtomicInteger retryCount;
    private CountDownLatch latch;
    private SingleThreadContext threadContext;

    @BeforeEach
    public void setup() {
      threadContext =
          new SingleThreadContext(
              namedThreads("test", log), e -> exceptionHandler.accept(e)); // given
      delayStrategy = new ExponentialBackoffRetryDelay(MAX_DELAY, Duration.ofMillis(1));
      retryCount = new AtomicInteger(0);
      latch = new CountDownLatch(1);
      taskToRetry =
          () -> {
            // retry until NUM_RETRIES is reached, then wait for the latch
            if (retryCount.incrementAndGet() < NUM_RETRIES) {
              throw new IllegalArgumentException("Expected");
            } else {
              try {
                latch.await();
              } catch (final InterruptedException e) {
                throw new RuntimeException(e);
              }
            }
          };
    }

    @AfterEach
    public void tearDown() {
      threadContext.close();
    }

    @Test
    public void shouldRetryCallableUntilSuccessful() {
      // when
      final var result =
          threadContext.retryUntilSuccessful(taskToRetry, delayStrategy, (ignored) -> true);

      // then
      assertThat(result.isDone()).isFalse();
      Awaitility.await("Await 10 retries").until(() -> retryCount.get() >= NUM_RETRIES);

      // when
      latch.countDown();

      // then
      Awaitility.await("the futures succeeds").until(result::isDone);
    }

    @Test
    public void shouldNotRetryWhenPredicateIsFalse() {
      // when
      final var result =
          threadContext.retryUntilSuccessful(taskToRetry, delayStrategy, (ignored) -> false);

      // then
      Awaitility.await("Await 1 retry ").until(() -> retryCount.get() == 1);
      assertThat(result.isCompletedExceptionally()).isTrue();
    }

    @Test
    public void shouldNotContinueIfFutureIsCancelled() throws InterruptedException {
      // given
      final var result =
          threadContext.retryUntilSuccessful(
              () -> {
                throw new RuntimeException("expected");
              },
              delayStrategy,
              (ignored) -> true);

      // when
      result.cancel(false);

      // submit a task after the cancellation with MAX_DELAY so that it will be executed after
      // the first task checks for cancellation
      final var scheduled = threadContext.schedule(MAX_DELAY, () -> {});
      Awaitility.await("task is completed").until(scheduled::isDone);

      // then - no tasks are scheduled
      final var scheduledTasks = threadContext.executor.shutdownNow();
      assertThat(scheduledTasks.isEmpty()).isTrue();
      // executor terminates gracefully if no task is scheduled
      assertThat(threadContext.executor.awaitTermination(1, TimeUnit.SECONDS)).isTrue();
    }
  }
}
