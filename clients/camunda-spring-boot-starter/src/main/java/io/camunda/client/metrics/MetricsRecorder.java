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

import java.util.Map;
import java.util.concurrent.Callable;

public interface MetricsRecorder {

  void increaseActivated(CounterMetricsContext context);

  void increaseCompleted(CounterMetricsContext context);

  void increaseFailed(CounterMetricsContext context);

  void increaseBpmnError(CounterMetricsContext context);

  /**
   * Execute the given runnable and measure the execution time
   *
   * <p>Note: the provided runnable is executed synchronously
   *
   * @param context - the context the metric uses
   * @param methodToExecute - the method to execute
   */
  <T> T executeWithTimer(TimerMetricsContext context, Callable<T> methodToExecute) throws Exception;

  record CounterMetricsContext(String name, Map<String, String> tags, int count) {}

  record TimerMetricsContext(String name, Map<String, String> tags) {}
}
