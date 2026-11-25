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
import static org.awaitility.Awaitility.await;

import io.camunda.zeebe.client.api.command.ClientHttpException;
import io.camunda.zeebe.metrics.WorkerMetricsDoc;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ResponseCheckerTest {

  private final MeterRegistry registry = new SimpleMeterRegistry();
  private final BlockingQueue<Future<?>> futures = new ArrayBlockingQueue<>(10);
  private ResponseChecker checker;

  @BeforeEach
  void setUp() {
    checker =
        new ResponseChecker(futures, new WorkerResultMetrics(registry, "benchmark-task", futures));
    checker.start();
  }

  @AfterEach
  void tearDown() {
    checker.close();
  }

  @Test
  void shouldCountSuccessfulCompletion() {
    // when
    futures.add(CompletableFuture.completedFuture(null));

    // then
    await().untilAsserted(() -> assertThat(completions("success", null)).isEqualTo(1.0));
  }

  @Test
  void shouldCountRestFailureByHttpStatus() {
    // when
    futures.add(
        CompletableFuture.failedFuture(new ClientHttpException(503, "Service Unavailable")));

    // then
    await().untilAsserted(() -> assertThat(completions("failure", "http_503")).isEqualTo(1.0));
    assertThat(completions("success", null)).isZero();
  }

  private double completions(final String outcome, final String error) {
    final var counter =
        registry
            .find(WorkerMetricsDoc.JOB_COMPLETIONS.getName())
            .tag("outcome", outcome)
            .tags(error == null ? Tags.empty() : Tags.of("error", error))
            .counter();
    return counter == null ? 0.0 : counter.count();
  }
}
