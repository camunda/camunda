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

import java.util.List;
import java.util.Map.Entry;

public interface MetricsContext {
  static String getKey(
      final String name, final String action, final List<Entry<String, String>> tags) {
    return name
        + "######"
        + action
        + "######"
        + String.join("#", tags.stream().map(e -> e.getKey() + "=" + e.getValue()).toList());
  }

  String getName();

  List<Entry<String, String>> getTags();

  interface CounterMetricsContext extends MetricsContext {
    List<String> getAliases();

    int getCount();
  }

  interface TimerMetricsContext extends MetricsContext {}
}
