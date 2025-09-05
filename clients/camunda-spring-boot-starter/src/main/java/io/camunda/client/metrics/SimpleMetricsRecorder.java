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

import java.util.HashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Super simple class to record metrics in memory. Typically used for test cases */
public class SimpleMetricsRecorder implements MetricsRecorder {

  public HashMap<String, AtomicLong> counters = new HashMap<>();

  public HashMap<String, Long> timers = new HashMap<>();

  @Override
  public void increase(
      final String metricName, final String action, final String type, final int count) {
    final String key = key(metricName, action, type);
    if (!counters.containsKey(key)) {
      counters.put(key, new AtomicLong(count));
    } else {
      counters.get(key).addAndGet(count);
    }
  }

  @Override
  public void executeWithTimer(
      final String metricName, final String jobType, final Runnable methodToExecute) {
    final long startTime = System.currentTimeMillis();
    methodToExecute.run();
    timers.put(metricName + "#" + jobType, System.currentTimeMillis() - startTime);
  }

  private String key(final String metricName, final String action, final String type) {
    final String key = metricName + "#" + action + "#" + type;
    return key;
  }

  public long getCount(final String metricName, final String action, final String type) {
    if (!counters.containsKey(key(metricName, action, type))) {
      return 0;
    }
    return counters.get(key(metricName, action, type)).get();
  }
}
