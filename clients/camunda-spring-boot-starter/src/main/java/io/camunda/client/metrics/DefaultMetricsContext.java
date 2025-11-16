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

import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;

public class DefaultMetricsContext implements MetricsContext {
  private final String name;
  private final List<Entry<String, String>> tags;

  public DefaultMetricsContext(final String name, final List<Entry<String, String>> tags) {
    this.name = name;
    this.tags = new ArrayList<>(tags);
  }

  //  public DefaultMetricsContext(final ActivatedJob job) {
  //      this(List.of("camunda.job.invocations"), List.of(Map.entry("type",job.getType())));
  //    }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public List<Entry<String, String>> getTags() {
    return tags;
  }

  public static class DefaultCounterMetricsContext extends DefaultMetricsContext
      implements CounterMetricsContext {
    private final int count;
    private final List<String> aliases;

    public DefaultCounterMetricsContext(
        final String name,
        final List<String> aliases,
        final List<Entry<String, String>> tags,
        final int count) {
      super(name, tags);
      this.count = count;
      this.aliases = aliases;
    }

    @Override
    public List<String> getAliases() {
      return aliases;
    }

    @Override
    public int getCount() {
      return count;
    }
  }

  public static class DefaultTimerMetricsContext extends DefaultMetricsContext
      implements TimerMetricsContext {

    public DefaultTimerMetricsContext(final String name, final List<Entry<String, String>> tags) {
      super(name, tags);
    }
  }
}
