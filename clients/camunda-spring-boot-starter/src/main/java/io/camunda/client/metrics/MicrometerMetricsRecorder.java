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

import io.camunda.client.metrics.JobHandlerMetrics.Action;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MicrometerMetricsRecorder implements MetricsRecorder {
  private static final Logger LOGGER = LoggerFactory.getLogger(MicrometerMetricsRecorder.class);
  private final MeterRegistry meterRegistry;
  private final Map<String, Counter> counters = new ConcurrentHashMap<>();

  public MicrometerMetricsRecorder(final MeterRegistry meterRegistry) {
    LOGGER.info("Enabling Micrometer based metrics for camunda-client");
    this.meterRegistry = meterRegistry;
  }

  private static String getKey(final String name, final Tags tags) {
    return name
        + "######"
        + String.join("#", tags.stream().map(e -> e.getKey() + "=" + e.getValue()).toList());
  }

  @Override
  public void increaseActivated(final CounterMetricsContext context) {
    increaseCounter(context, JobHandlerMetrics.Action.ACTIVATED);
  }

  @Override
  public void increaseCompleted(final CounterMetricsContext context) {
    increaseCounter(context, JobHandlerMetrics.Action.COMPLETED);
  }

  @Override
  public void increaseFailed(final CounterMetricsContext context) {
    increaseCounter(context, JobHandlerMetrics.Action.FAILED);
  }

  @Override
  public void increaseBpmnError(final CounterMetricsContext context) {
    increaseCounter(context, JobHandlerMetrics.Action.BPMN_ERROR);
  }

  @Override
  public <T> T executeWithTimer(
      final TimerMetricsContext context, final Callable<T> methodToExecute) throws Exception {
    final Timer timer = meterRegistry.timer(context.name(), fromEntries(context.tags()));
    return timer.recordCallable(methodToExecute);
  }

  protected void increaseCounter(final CounterMetricsContext context, final Action action) {
    final Tags tags = fromEntries(context.tags(), action.asString());
    final String key = getKey(context.name(), tags);
    final Counter counter =
        counters.computeIfAbsent(
            key,
            k ->
                meterRegistry.counter(
                    context.name(), fromEntries(context.tags(), action.asString())));
    counter.increment(context.count());
  }

  private static Tags fromEntries(final Map<String, String> entries) {
    return Tags.of(entries.entrySet().stream().map(e -> Tag.of(e.getKey(), e.getValue())).toList());
  }

  private static Tags fromEntries(final Map<String, String> entries, final String action) {
    return Tags.concat(fromEntries(entries), JobHandlerMetrics.Tag.ACTION.asString(), action);
  }
}
