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

import java.util.Collections;
import java.util.Map;

public class DefaultMetricsContext implements MetricsContext {
  private final String name;
  private final Map<String, String> tags;

  public DefaultMetricsContext(final String name, final Map<String, String> tags) {
    this.name = name;
    this.tags = Collections.unmodifiableMap(tags);
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public Map<String, String> getTags() {
    return tags;
  }

  public static class DefaultCounterMetricsContext extends DefaultMetricsContext
      implements CounterMetricsContext {
    private final int count;

    public DefaultCounterMetricsContext(
        final String name, final Map<String, String> tags, final int count) {
      super(name, tags);
      this.count = count;
    }

    @Override
    public int getCount() {
      return count;
    }
  }

  public static class DefaultTimerMetricsContext extends DefaultMetricsContext
      implements TimerMetricsContext {

    public DefaultTimerMetricsContext(final String name, final Map<String, String> tags) {
      super(name, tags);
    }
  }
}
