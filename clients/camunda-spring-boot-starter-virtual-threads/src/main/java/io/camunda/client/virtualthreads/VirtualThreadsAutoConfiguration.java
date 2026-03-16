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

import io.camunda.client.jobhandling.CamundaClientExecutorService;
import io.camunda.client.metrics.MeteredCamundaClientExecutorService;
import io.camunda.client.spring.configuration.CamundaAutoConfiguration;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

/**
 * Autoconfiguration for Camunda client with virtual threads support.
 *
 * <p>This configuration provides a {@link CamundaClientExecutorService} that uses Java 21 virtual
 * threads for job execution and a single platform thread for scheduling. This configuration runs
 * before {@link CamundaAutoConfiguration} to override the default executor service.
 *
 * <p>Virtual threads are lightweight threads that allow for highly concurrent job processing
 * without the overhead of traditional platform threads.
 */
@AutoConfiguration
@AutoConfigureBefore(CamundaAutoConfiguration.class)
public class VirtualThreadsAutoConfiguration {

  /**
   * Fallback bean when Micrometer is not on the classpath.
   *
   * @return the configured executor service without metrics
   */
  @Bean
  @ConditionalOnMissingClass("io.micrometer.core.instrument.MeterRegistry")
  public CamundaClientExecutorService camundaClientExecutorServiceWithoutMetrics() {
    final ThreadFactory virtualThreadFactory = createVirtualThreadFactory();
    final ExecutorService jobHandlingExecutor =
        Executors.newThreadPerTaskExecutor(virtualThreadFactory);
    final ScheduledExecutorService scheduledExecutor = Executors.newScheduledThreadPool(1);

    return new CamundaClientExecutorService(scheduledExecutor, true, jobHandlingExecutor, true);
  }

  private static ThreadFactory createVirtualThreadFactory() {
    return Thread.ofVirtual().name("job-worker-virtual-", 0).factory();
  }

  /**
   * Nested configuration that is only loaded when both Micrometer and Spring Boot Actuator are on
   * the classpath. This must be a separate nested class because {@code MeterRegistry} is used as a
   * method parameter type — if it appeared on the enclosing class, {@link
   * Class#getDeclaredMethods()} would throw {@link NoClassDefFoundError} when Micrometer is absent.
   *
   * @see <a href="https://github.com/camunda/camunda/issues/47464">#47464</a>
   */
  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass(
      name = {
        "io.micrometer.core.instrument.MeterRegistry",
        "org.springframework.boot.actuate.autoconfigure.endpoint.EndpointAutoConfiguration"
      })
  static class MeteredConfiguration {

    /**
     * Creates a {@link CamundaClientExecutorService} that uses virtual threads for job execution
     * with metrics support when Micrometer is available.
     *
     * @param meterRegistry the meter registry for metrics
     * @return the configured executor service with virtual threads for job handling and a single
     *     platform thread for scheduling
     */
    @Bean
    CamundaClientExecutorService camundaClientExecutorService(
        @Lazy final MeterRegistry meterRegistry) {
      final ThreadFactory virtualThreadFactory = createVirtualThreadFactory();
      final ExecutorService jobHandlingExecutor =
          Executors.newThreadPerTaskExecutor(virtualThreadFactory);
      final ScheduledExecutorService scheduledExecutor = Executors.newScheduledThreadPool(1);

      return new MeteredCamundaClientExecutorService(
          scheduledExecutor, true, jobHandlingExecutor, true, meterRegistry);
    }
  }
}
