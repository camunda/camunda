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
package io.camunda.zeebe.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.metrics.WorkerMetricsDoc.WorkerMetricKeyNames;
import io.micrometer.core.instrument.Meter.Type;
import org.junit.jupiter.api.Test;

class WorkerMetricsDocTest {

  @Test
  void shouldDefineMessagePublicationsCounter() {
    // given
    final var metric = WorkerMetricsDoc.MESSAGE_PUBLICATIONS;

    // when / then
    assertThat(metric.getName()).isEqualTo("worker.message.publications");
    assertThat(metric.getType()).isEqualTo(Type.COUNTER);
    assertThat(metric.getDescription())
        .isEqualTo(
            "Total number of message publications by the worker, by outcome and error type.");
    assertThat(metric.getKeyNames())
        .containsExactly(
            WorkerMetricKeyNames.JOB_TYPE,
            ResultMetricKeyNames.OUTCOME,
            ResultMetricKeyNames.ERROR);
  }

  @Test
  void shouldDefineJobCompletionsCounter() {
    // given
    final var metric = WorkerMetricsDoc.JOB_COMPLETIONS;

    // when / then
    assertThat(metric.getName()).isEqualTo("worker.job.completions");
    assertThat(metric.getType()).isEqualTo(Type.COUNTER);
    assertThat(metric.getDescription())
        .isEqualTo("Total number of answered job completion requests by outcome and error type.");
    assertThat(metric.getKeyNames())
        .containsExactly(
            WorkerMetricKeyNames.JOB_TYPE,
            ResultMetricKeyNames.OUTCOME,
            ResultMetricKeyNames.ERROR);
  }

  @Test
  void shouldDefineJobCompletionTrackingDroppedCounter() {
    // given
    final var metric = WorkerMetricsDoc.JOB_COMPLETION_TRACKING_DROPPED;

    // when / then
    assertThat(metric.getName()).isEqualTo("worker.job.completion.tracking.dropped");
    assertThat(metric.getType()).isEqualTo(Type.COUNTER);
    assertThat(metric.getDescription())
        .isEqualTo(
            "Total number of job completions dropped from result tracking because the queue was full.");
    assertThat(metric.getKeyNames()).containsExactly(WorkerMetricKeyNames.JOB_TYPE);
  }

  @Test
  void shouldDefineJobCompletionsPendingGauge() {
    // given
    final var metric = WorkerMetricsDoc.JOB_COMPLETIONS_PENDING;

    // when / then
    assertThat(metric.getName()).isEqualTo("worker.job.completions.pending");
    assertThat(metric.getType()).isEqualTo(Type.GAUGE);
    assertThat(metric.getDescription())
        .isEqualTo("Number of job completion requests queued and not yet awaited.");
    assertThat(metric.getKeyNames()).containsExactly(WorkerMetricKeyNames.JOB_TYPE);
  }

  @Test
  void shouldDefineClientInfoGauge() {
    // given
    final var metric = WorkerMetricsDoc.CLIENT_INFO;

    // when / then
    assertThat(metric.getName()).isEqualTo("client.info");
    assertThat(metric.getType()).isEqualTo(Type.GAUGE);
    assertThat(metric.getDescription()).isEqualTo("The information about the client.");
    assertThat(metric.getKeyNames())
        .containsExactly(
            WorkerMetricKeyNames.NAME,
            WorkerMetricKeyNames.JOB_TYPE,
            WorkerMetricKeyNames.COMPLETION_DELAY,
            WorkerMetricKeyNames.SEND_MESSAGE);
  }
}
