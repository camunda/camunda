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
package io.camunda.spring.client.configuration;

import io.camunda.client.CamundaClient;
import io.camunda.spring.client.actuator.CamundaClientHealthIndicator;
import io.camunda.spring.client.actuator.JobWorkerController;
import io.camunda.spring.client.actuator.MicrometerMetricsRecorder;
import io.camunda.spring.client.configuration.condition.ConditionalOnCamundaClientEnabled;
import io.camunda.spring.client.jobhandling.JobWorkerManager;
import io.camunda.spring.client.metrics.MetricsRecorder;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.actuate.autoconfigure.endpoint.EndpointAutoConfiguration;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;

@AutoConfigureBefore(MetricsDefaultConfiguration.class)
@ConditionalOnCamundaClientEnabled
@ConditionalOnClass({
  EndpointAutoConfiguration.class,
  MeterRegistry.class
}) // only if actuator is on classpath
public class CamundaActuatorConfiguration {

  @Bean
  public JobWorkerController jobWorkerController(final JobWorkerManager jobWorkerManager) {
    return new JobWorkerController(jobWorkerManager);
  }

  @Bean
  @ConditionalOnMissingBean
  public MetricsRecorder micrometerMetricsRecorder(@Lazy final MeterRegistry meterRegistry) {
    return new MicrometerMetricsRecorder(meterRegistry);
  }

  @Bean
  @ConditionalOnProperty(
      prefix = "management.health.camunda",
      name = "enabled",
      matchIfMissing = true)
  @ConditionalOnClass(HealthIndicator.class)
  @ConditionalOnMissingBean(name = "camundaClientHealthIndicator")
  public CamundaClientHealthIndicator camundaClientHealthIndicator(final CamundaClient client) {
    return new CamundaClientHealthIndicator(client);
  }
}
