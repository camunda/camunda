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

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class MeteredCamundaClientExecutorServiceTest {

  private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

  @Test
  void shouldRegisterDistinctMetricsForScheduledAndJobHandlingExecutors() {
    final ScheduledThreadPoolExecutor scheduledExecutor = new ScheduledThreadPoolExecutor(1);
    final ThreadPoolExecutor jobHandlingExecutor =
        new ThreadPoolExecutor(2, 2, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());

    try {
      new MeteredCamundaClientExecutorService(
          scheduledExecutor, false, jobHandlingExecutor, false, registry);

      assertExecutorMetricsRegistered("zeebe_client_thread_pool");
      assertExecutorMetricsRegistered("zeebe_client_job_handling_pool");
    } finally {
      scheduledExecutor.shutdownNow();
      jobHandlingExecutor.shutdownNow();
      registry.close();
    }
  }

  private void assertExecutorMetricsRegistered(final String executorName) {
    for (final String metricName :
        new String[] {"executor.active", "executor.pool.size", "executor.queued"}) {
      assertThat(
              registry
                  .find(metricName)
                  .tag("name", "camundaClientExecutor")
                  .tag("executor", executorName)
                  .gauge())
          .as("Expected %s metric for executor %s", metricName, executorName)
          .isNotNull();
    }
  }
}
