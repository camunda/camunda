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

public abstract class AbstractMetricsRecorder implements MetricsRecorder {
  public static final String ACTION_ACTIVATED = "activated";
  public static final String ACTION_COMPLETED = "completed";
  public static final String ACTION_FAILED = "failed";
  public static final String ACTION_BPMN_ERROR = "bpmn-error";

  @Override
  public void increaseActivated(final CounterMetricsContext context) {
    increase(context, ACTION_ACTIVATED);
  }

  @Override
  public void increaseCompleted(final CounterMetricsContext context) {
    increase(context, ACTION_COMPLETED);
  }

  @Override
  public void increaseFailed(final CounterMetricsContext context) {
    increase(context, ACTION_FAILED);
  }

  @Override
  public void increaseBpmnError(final CounterMetricsContext context) {
    increase(context, ACTION_BPMN_ERROR);
  }

  protected abstract void increase(final CounterMetricsContext context, String action);
}
