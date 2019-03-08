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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import org.agrona.MutableDirectBuffer;

public class MetricsManager {
  private final List<Metric> metrics = new ArrayList<>();

  private final String prefix;
  private final Map<String, String> globalLabels;
  private final Consumer<Metric> onClose = this::free;
  private final ReentrantLock lock = new ReentrantLock();

  public MetricsManager() {
    this("zb_", new HashMap<>());
  }

  public MetricsManager(String prefix, Map<String, String> globalLabels) {
    this.prefix = prefix;
    this.globalLabels = globalLabels;
  }

  public Metric allocate(String name, String type, String description, Map<String, String> labels) {
    lock.lock();
    try {
      labels.putAll(globalLabels);
      final Metric metric = new Metric(prefix + name, type, description, labels, onClose);
      metrics.add(metric);
      return metric;
    } finally {
      lock.unlock();
    }
  }

  public MetricBuilder newMetric(String name) {
    return new MetricBuilder(name);
  }

  public int dump(MutableDirectBuffer buffer, int offset, long now) {
    lock.lock();
    try {
      for (int i = 0; i < metrics.size(); i++) {
        offset = metrics.get(i).dump(buffer, offset, now);
      }

      return offset;
    } finally {
      lock.unlock();
    }
  }

  public void free(Metric metric) {
    lock.lock();
    try {
      metrics.remove(metric);
    } finally {
      lock.unlock();
    }
  }

  public class MetricBuilder {

    private final String name;
    private String type;
    private String description;
    private final Map<String, String> labels = new HashMap<>();

    public MetricBuilder(String name) {
      this.name = name;
      this.type = "counter";
    }

    public MetricBuilder type(String type) {
      this.type = type;
      return this;
    }

    public MetricBuilder label(String name, String value) {
      labels.put(name, value);
      return this;
    }

    public MetricBuilder description(String description) {
      this.description = description;
      return this;
    }

    public Metric create() {
      return MetricsManager.this.allocate(name, type, description, labels);
    }
  }
}
