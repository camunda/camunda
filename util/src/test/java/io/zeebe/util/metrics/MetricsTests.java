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
package io.zeebe.util.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.util.buffer.BufferUtil;
import java.util.HashMap;
import java.util.Map;
import org.agrona.ExpandableArrayBuffer;
import org.junit.Test;

public class MetricsTests {
  @Test
  public void emptyState() {
    final MetricsManager metricsManager = new MetricsManager();

    final String dump = dumpAsString(metricsManager, 100);
    assertThat(dump).isEqualTo("");
  }

  @Test
  public void shouldCreateMetric() {
    final MetricsManager metricsManager = new MetricsManager();

    metricsManager.newMetric("metric1").description("example metric description").create();

    final String dump = dumpAsString(metricsManager, 100);
    assertThat(dump)
        .isEqualTo(
            metricComment("example metric description", "counter", "zb_metric1")
                + "zb_metric1{} 0 100\n");
  }

  @Test
  public void shouldCloseMetric() {
    final MetricsManager metricsManager = new MetricsManager();

    final Metric metric = metricsManager.newMetric("metric1").create();

    String dump = dumpAsString(metricsManager, 100);
    assertThat(dump).isEqualTo(metricComment() + "zb_metric1{} 0 100\n");

    metric.close();
    dump = dumpAsString(metricsManager, 100);
    assertThat(dump).isEqualTo("");
  }

  @Test
  public void shouldCreateMultipleMetrics() {
    final MetricsManager metricsManager = new MetricsManager();

    metricsManager.newMetric("metric1").create();

    metricsManager.newMetric("metric2").create();

    final String dump = dumpAsString(metricsManager, 100);
    assertThat(dump)
        .isEqualTo(
            metricComment("zb_metric1")
                + "zb_metric1{} 0 100\n"
                + metricComment("zb_metric2")
                + "zb_metric2{} 0 100\n");
  }

  @Test
  public void shouldIncrementMetric() {
    final MetricsManager metricsManager = new MetricsManager();

    final Metric metric = metricsManager.newMetric("metric1").create();

    metric.incrementOrdered();

    final String dump = dumpAsString(metricsManager, 100);
    assertThat(dump).isEqualTo(metricComment() + "zb_metric1{} 1 100\n");
  }

  @Test
  public void shouldSetMetricValue() {
    final MetricsManager metricsManager = new MetricsManager();

    final Metric metric = metricsManager.newMetric("metric1").create();

    metric.getAndAddOrdered(215);

    final String dump = dumpAsString(metricsManager, 100);
    assertThat(dump).isEqualTo(metricComment() + "zb_metric1{} 215 100\n");
  }

  @Test
  public void shouldAddLabel() {
    final MetricsManager metricsManager = new MetricsManager();

    final Metric metric = metricsManager.newMetric("metric1").label("label1", "value1").create();

    metric.incrementOrdered();

    final String dump = dumpAsString(metricsManager, 100);
    assertThat(dump).isEqualTo(metricComment() + "zb_metric1{label1=\"value1\"} 1 100\n");
  }

  @Test
  public void shouldAddLabels() {
    final MetricsManager metricsManager = new MetricsManager();

    final Metric metric =
        metricsManager
            .newMetric("metric1")
            .label("label1", "value1")
            .label("label2", "value2")
            .create();

    metric.incrementOrdered();

    final String dump = dumpAsString(metricsManager, 100);
    assertThat(dump)
        .isEqualTo(metricComment() + "zb_metric1{label1=\"value1\",label2=\"value2\"} 1 100\n");
  }

  @Test
  public void shouldAddGlobalLabelsAndPrefix() {
    final Map<String, String> labels = new HashMap<>();
    labels.put("broker", "node1");
    final MetricsManager metricsManager = new MetricsManager("bz_", labels);

    metricsManager.newMetric("metric1").label("label1", "value1").create();

    final String dump = dumpAsString(metricsManager, 100);
    assertThat(dump)
        .isEqualTo(
            metricComment("bz_metric1") + "bz_metric1{broker=\"node1\",label1=\"value1\"} 0 100\n");
  }

  private static String dumpAsString(MetricsManager metricsManager, long now) {
    final ExpandableArrayBuffer buffer = new ExpandableArrayBuffer();
    final int length = metricsManager.dump(buffer, 0, now);

    return BufferUtil.bufferAsString(buffer, 0, length);
  }

  private String metricComment() {
    return metricComment("zb_metric1");
  }

  private String metricComment(String name) {
    return metricComment("counter", name);
  }

  private String metricComment(String type, String name) {
    return metricComment(null, type, name);
  }

  private String metricComment(String description, String type, String name) {
    final StringBuilder builder = new StringBuilder();
    if (description != null) {
      builder.append("# HELP ").append(name).append(" ").append(description).append("\n");
    }
    builder.append("# TYPE ").append(name).append(" ").append(type).append("\n");
    return builder.toString();
  }
}
