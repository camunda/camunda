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

/**
 * Default implementation for MetricsRecorder simply ignoring the counts. Typically, you will
 * replace this by a proper Micrometer implementation as you can find in the starter module
 * (activated if Actuator is on the classpath)
 */
public class DefaultNoopMetricsRecorder implements MetricsRecorder {

  @Override
  public void increase(
      final String metricName, final String action, final String type, final int count) {
    // ignore
  }

  @Override
  public void executeWithTimer(
      final String metricName, final String jobType, final Runnable methodToExecute) {
    methodToExecute.run();
  }
}
