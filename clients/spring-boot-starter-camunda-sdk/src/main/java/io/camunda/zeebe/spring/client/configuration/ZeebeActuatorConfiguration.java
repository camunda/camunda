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
package io.camunda.zeebe.spring.client.configuration;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.spring.client.actuator.MicrometerMetricsRecorder;
import io.camunda.zeebe.spring.client.actuator.ZeebeClientHealthIndicator;
import io.camunda.zeebe.spring.client.metrics.MetricsRecorder;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.actuate.autoconfigure.endpoint.EndpointAutoConfiguration;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;

@AutoConfigureBefore(MetricsDefaultConfiguration.class)
@ConditionalOnClass({
  EndpointAutoConfiguration.class,
  MeterRegistry.class
}) // only if actuator is on classpath
public class ZeebeActuatorConfiguration {
  @Bean
  @ConditionalOnMissingBean
  public MetricsRecorder micrometerMetricsRecorder(
      final @Autowired @Lazy MeterRegistry meterRegistry) {
    return new MicrometerMetricsRecorder(meterRegistry);
  }

  /**
   * Workaround to fix premature initialization of MeterRegistry that seems to happen here, see
   * https://github.com/camunda-community-hub/spring-zeebe/issues/296
   */
  @Bean
  InitializingBean forceMeterRegistryPostProcessor(
      final @Autowired(required = false) @Qualifier("meterRegistryPostProcessor") BeanPostProcessor
              meterRegistryPostProcessor,
      final @Autowired(required = false) MeterRegistry registry) {
    if (registry == null || meterRegistryPostProcessor == null) {
      return () -> {};
    } else {
      return () -> meterRegistryPostProcessor.postProcessAfterInitialization(registry, "");
    }
  }

  @Bean
  @ConditionalOnProperty(
      prefix = "management.health.zeebe",
      name = "enabled",
      matchIfMissing = true)
  @ConditionalOnClass(HealthIndicator.class)
  @ConditionalOnMissingBean(name = "zeebeClientHealthIndicator")
  public ZeebeClientHealthIndicator zeebeClientHealthIndicator(final ZeebeClient client) {
    return new ZeebeClientHealthIndicator(client);
  }
}
