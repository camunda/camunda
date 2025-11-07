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

import io.camunda.client.jobhandling.CamundaClientExecutorService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.binder.jvm.ExecutorServiceMetrics;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class MeteredCamundaClientExecutorService extends CamundaClientExecutorService {

  public MeteredCamundaClientExecutorService(
      final ScheduledExecutorService scheduledExecutorService,
      final boolean ownedByCamundaClient,
      final MeterRegistry meterRegistry) {
    super(
        createMeteredScheduledExecutorService(scheduledExecutorService, meterRegistry),
        ownedByCamundaClient);
  }

  public MeteredCamundaClientExecutorService(
      final ScheduledExecutorService scheduledExecutorService,
      final boolean scheduledExecutorOwnedByCamundaClient,
      final ExecutorService executorService,
      final boolean jobHandlingExecutorOwnedByCamundaClient,
      final MeterRegistry meterRegistry) {
    super(
        createMeteredScheduledExecutorService(scheduledExecutorService, meterRegistry),
        scheduledExecutorOwnedByCamundaClient,
        createMeteredExecutorService(executorService, meterRegistry),
        jobHandlingExecutorOwnedByCamundaClient);
  }

  private static ScheduledExecutorService createMeteredScheduledExecutorService(
      final ScheduledExecutorService scheduledExecutorService, final MeterRegistry meterRegistry) {
    return ExecutorServiceMetrics.monitor(
        meterRegistry,
        scheduledExecutorService,
        "camundaClientExecutor",
        Collections.singleton(Tag.of("name", "zeebe_client_thread_pool")));
  }

  private static ExecutorService createMeteredExecutorService(
      final ExecutorService executorService, final MeterRegistry meterRegistry) {
    return ExecutorServiceMetrics.monitor(
        meterRegistry,
        executorService,
        "camundaClientExecutor",
        Collections.singleton(Tag.of("name", "zeebe_client_job_handling_pool")));
  }

  public static MeteredCamundaClientExecutorService createDefault(
      final int poolSize, final MeterRegistry meterRegistry) {

    final ScheduledExecutorService scheduledExecutorService =
        Executors.newScheduledThreadPool(poolSize);
    return new MeteredCamundaClientExecutorService(scheduledExecutorService, true, meterRegistry);
  }
}
