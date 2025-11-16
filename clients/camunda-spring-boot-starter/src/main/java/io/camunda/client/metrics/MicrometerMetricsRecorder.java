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

import io.camunda.client.metrics.MetricsContext.CounterMetricsContext;
import io.camunda.client.metrics.MetricsContext.TimerMetricsContext;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MicrometerMetricsRecorder extends AbstractMetricsRecorder {

  private static final Logger LOGGER = LoggerFactory.getLogger(MicrometerMetricsRecorder.class);

  private final MeterRegistry meterRegistry;
  private final Map<String, Counter> counters = new ConcurrentHashMap<>();

  public MicrometerMetricsRecorder(final MeterRegistry meterRegistry) {
    LOGGER.info("Enabling Micrometer based metrics for camunda-client");
    this.meterRegistry = meterRegistry;
  }

  protected Counter newCounter(final String metricName, final Tags tags) {
    return meterRegistry.counter(metricName, tags);
  }

  @Override
  protected void increase(final CounterMetricsContext context, final String action) {
    final String key = MetricsContext.getKey(context.getName(), action, context.getTags());
    final Counter counter =
        counters.computeIfAbsent(
            key, k -> newCounter(context.getName(), fromEntries(context.getTags(), action)));
    counter.increment(context.getCount());
  }

  private static Tags fromEntries(final List<Entry<String, String>> entries) {
    return Tags.of(entries.stream().map(e -> Tag.of(e.getKey(), e.getValue())).toList());
  }

  private static Tags fromEntries(final List<Entry<String, String>> entries, final String action) {
    return Tags.concat(
        entries.stream().map(e -> Tag.of(e.getKey(), e.getValue())).toList(), "action", action);
  }

  @Override
  public void executeWithTimer(final TimerMetricsContext context, final Runnable methodToExecute) {
    final Timer timer = meterRegistry.timer(context.getName(), fromEntries(context.getTags()));
    timer.record(methodToExecute);
  }
}
