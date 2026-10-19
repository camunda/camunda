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
package io.camunda.client.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.metrics.MetricsRecorder.CounterMetricsContext;
import io.camunda.client.metrics.MetricsRecorder.TimerMetricsContext;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MicrometerMetricsRecorderTest {

  private static final String COUNTER_NAME = "camunda.job.invocations";
  private static final String TIMER_NAME = "camunda.job.execution-time";

  private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
  private final MicrometerMetricsRecorder recorder = new MicrometerMetricsRecorder(registry);

  @Test
  void shouldCountJobActionsPerTypeAndAction() {
    // given
    final var context = new CounterMetricsContext(COUNTER_NAME, Map.of("type", "job-type"), 2);

    // when
    recorder.increaseActivated(context);
    recorder.increaseActivated(context);
    recorder.increaseCompleted(context);

    // then
    assertThat(
            registry
                .find(COUNTER_NAME)
                .tag("type", "job-type")
                .tag("action", "activated")
                .counter()
                .count())
        .isEqualTo(4);
    assertThat(
            registry
                .find(COUNTER_NAME)
                .tag("type", "job-type")
                .tag("action", "completed")
                .counter()
                .count())
        .isEqualTo(2);
  }

  @Test
  void shouldTimeJobExecution() throws Exception {
    // given
    final var context = new TimerMetricsContext(TIMER_NAME, Map.of("type", "job-type"));

    // when
    final String result = recorder.executeWithTimer(context, () -> "done");

    // then
    assertThat(result).isEqualTo("done");
    assertThat(registry.find(TIMER_NAME).tag("type", "job-type").timer().count()).isEqualTo(1);
  }
}
