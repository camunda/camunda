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
package io.atomix.raft.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/** Coverage for the coordinated-leadership-transfer (rebalancing) metrics. */
final class RebalanceMetricsTest {

  private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
  private final RebalanceMetrics metrics = new RebalanceMetrics("group-1", registry);

  @Test
  void shouldExposePausedGaugeReflectingTransferState() {
    // given
    assertThat(pausedGauge()).as("gauge starts cleared").isZero();

    // when
    metrics.setPartitionPaused(true);

    // then
    assertThat(pausedGauge()).isEqualTo(1.0);

    // when
    metrics.setPartitionPaused(false);

    // then
    assertThat(pausedGauge()).isZero();
  }

  @Test
  void shouldRecordPauseDuration() {
    // when
    metrics.observePauseDuration(Duration.ofMillis(250));

    // then
    final var timer = registry.find("zeebe.cluster.rebalance.partition.pause.duration").timer();
    assertThat(timer).isNotNull();
    assertThat(timer.count()).isEqualTo(1);
    assertThat(timer.totalTime(TimeUnit.MILLISECONDS)).isGreaterThanOrEqualTo(250);
  }

  private double pausedGauge() {
    return registry.get("zeebe.cluster.rebalance.partition.paused").gauge().value();
  }
}
