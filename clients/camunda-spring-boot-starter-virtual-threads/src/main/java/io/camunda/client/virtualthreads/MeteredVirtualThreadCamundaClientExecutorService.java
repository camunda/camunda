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
package io.camunda.client.virtualthreads;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.binder.jvm.ExecutorServiceMetrics;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

/**
 * A {@link VirtualThreadCamundaClientExecutorService} that also records executor metrics in a
 * {@link MeterRegistry}.
 *
 * <p>This class references Micrometer types directly, so it is only instantiated by the
 * virtual-threads module's metered configuration, which is guarded on Micrometer and Actuator being
 * present. It keeps Micrometer off the un-metered {@link VirtualThreadCamundaClientExecutorService}
 * path.
 */
public class MeteredVirtualThreadCamundaClientExecutorService
    extends VirtualThreadCamundaClientExecutorService {

  public MeteredVirtualThreadCamundaClientExecutorService(
      final ScheduledExecutorService scheduledExecutor,
      final ExecutorService jobHandlingExecutor,
      final MeterRegistry meterRegistry) {
    super(
        monitor(scheduledExecutor, meterRegistry),
        true,
        monitor(jobHandlingExecutor, meterRegistry),
        true);
  }

  private static ScheduledExecutorService monitor(
      final ScheduledExecutorService executor, final MeterRegistry meterRegistry) {
    return ExecutorServiceMetrics.monitor(
        meterRegistry,
        executor,
        "camundaClientExecutor",
        Collections.singleton(Tag.of("name", "zeebe_client_thread_pool")));
  }

  private static ExecutorService monitor(
      final ExecutorService executor, final MeterRegistry meterRegistry) {
    return ExecutorServiceMetrics.monitor(
        meterRegistry,
        executor,
        "camundaClientExecutor",
        Collections.singleton(Tag.of("name", "zeebe_client_job_handling_pool")));
  }
}
