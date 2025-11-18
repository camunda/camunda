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

import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.metrics.DefaultMetricsContext.DefaultCounterMetricsContext;
import io.camunda.client.metrics.DefaultMetricsContext.DefaultTimerMetricsContext;
import io.camunda.client.metrics.JobHandlerMetrics.Name;
import io.camunda.client.metrics.JobHandlerMetrics.Tag;
import io.camunda.client.metrics.MetricsContext.CounterMetricsContext;
import io.camunda.client.metrics.MetricsContext.TimerMetricsContext;
import java.util.Map;

public interface JobHandlerInvokingBeansMetricsContext {

  static CounterMetricsContext counter(final ActivatedJob activatedJob) {
    return new DefaultCounterMetricsContext(
        Name.INVOCATION, Map.ofEntries(Map.entry(Tag.TYPE, activatedJob.getType())), 1);
  }

  static TimerMetricsContext timer(final ActivatedJob activatedJob) {
    return new DefaultTimerMetricsContext(
        Name.EXECUTION_TIME, Map.ofEntries(Map.entry(Tag.TYPE, activatedJob.getType())));
  }
}
