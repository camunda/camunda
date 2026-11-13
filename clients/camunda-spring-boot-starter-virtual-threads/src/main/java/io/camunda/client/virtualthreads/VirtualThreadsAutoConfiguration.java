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
import io.camunda.client.spring.configuration.CamundaAutoConfiguration;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
 *
 * <p>The configuration is active by default when this module is on the classpath. Set {@code
 * camunda.client.threads.virtual.enabled=false} to disable it and fall back to the default
 * platform-thread executor (sized by {@code camunda.client.execution-threads}).
 */
@AutoConfiguration
@AutoConfigureBefore(CamundaAutoConfiguration.class)
@ConditionalOnProperty(
    name = "camunda.client.threads.virtual.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class VirtualThreadsAutoConfiguration {

  /**
   * Fallback bean used when no {@link io.micrometer.core.instrument.MeterRegistry} bean is
   * available — either because Micrometer is absent from the classpath, or because it is present
   * but no registry bean has been configured.
   *
   * <p>The bean is a {@link VirtualThreadCamundaClientExecutorService}: that type carries the
   * {@link io.camunda.client.jobhandling.VirtualThreadExecutorService} capability, which is how the
   * multi-client factory recognises that job handling runs on virtual threads and replicates a
   * virtual-thread executor per named client.
   *
   * @return the configured executor service without metrics
   */
  @Bean
  @ConditionalOnMissingBean(type = "io.micrometer.core.instrument.MeterRegistry")
  public CamundaClientExecutorService camundaClientExecutorServiceWithoutMetrics() {
    return VirtualThreadCamundaClientExecutorService.create();
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
  @ConditionalOnBean(type = "io.micrometer.core.instrument.MeterRegistry")
  static class MeteredConfiguration {

    /**
     * Creates a {@link CamundaClientExecutorService} that uses virtual threads for job execution
     * with metrics support when Micrometer is available.
     *
     * <p>The bean is a {@link MeteredVirtualThreadCamundaClientExecutorService}, which carries the
     * {@link io.camunda.client.jobhandling.VirtualThreadExecutorService} capability so the
     * multi-client factory recognises it as virtual-threaded.
     *
     * @param meterRegistry the meter registry for metrics
     * @return the configured executor service with virtual threads for job handling and a single
     *     platform thread for scheduling
     */
    @Bean
    CamundaClientExecutorService camundaClientExecutorService(
        @Lazy final MeterRegistry meterRegistry) {
      final VirtualThreadCamundaClientExecutorService delegate =
          VirtualThreadCamundaClientExecutorService.create();
      return new MeteredVirtualThreadCamundaClientExecutorService(
          delegate.getScheduledExecutor(), delegate.getJobHandlingExecutor(), meterRegistry);
    }
  }
}
