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
import io.camunda.zeebe.spring.client.configuration.condition.ConditionalOnCamundaClientEnabled;
import io.camunda.zeebe.spring.client.metrics.MetricsRecorder;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;

/**
 * Spring Boot 4 compatible actuator configuration. Uses the new package location for
 * HealthIndicator in Spring Boot 4.x.
 */
@AutoConfigureBefore(MetricsDefaultConfiguration.class)
@ConditionalOnCamundaClientEnabled
<<<<<<< HEAD:clients/camunda-spring-boot-4-starter/src/main/java/io/camunda/zeebe/spring/client/configuration/ZeebeActuatorConfiguration.java
@ConditionalOnClass({
  EndpointAutoConfiguration.class,
  MeterRegistry.class
}) // only if actuator is on classpath
public class ZeebeActuatorConfiguration {
=======
@ConditionalOnClass(
    name = {
      "org.springframework.boot.actuate.autoconfigure.endpoint.EndpointAutoConfiguration",
      "io.micrometer.core.instrument.MeterRegistry"
    }) // only if actuator is on classpath
public class CamundaActuatorConfiguration {

  @Bean
  public JobWorkerController jobWorkerController(final JobWorkerManager jobWorkerManager) {
    return new JobWorkerController(jobWorkerManager);
  }

>>>>>>> d91a829d (fix: use string-based @ConditionalOnClass for optional dependencies to fix Java 24+ compatibility):clients/camunda-spring-boot-starter/src/main/java/io/camunda/client/spring/configuration/CamundaActuatorConfiguration.java
  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnBean(MeterRegistry.class)
  public MetricsRecorder micrometerMetricsRecorder(@Lazy final MeterRegistry meterRegistry) {
    return new MicrometerMetricsRecorder(meterRegistry);
  }

  @Bean
  @ConditionalOnProperty(
      prefix = "management.health.zeebe",
      name = "enabled",
      matchIfMissing = true)
<<<<<<< HEAD:clients/camunda-spring-boot-4-starter/src/main/java/io/camunda/zeebe/spring/client/configuration/ZeebeActuatorConfiguration.java
  @ConditionalOnClass(HealthIndicator.class)
  @ConditionalOnMissingBean(name = "zeebeClientHealthIndicator")
  public ZeebeClientHealthIndicator zeebeClientHealthIndicator(final ZeebeClient client) {
    return new ZeebeClientHealthIndicator(client);
=======
  @ConditionalOnClass(name = "org.springframework.boot.health.contributor.HealthIndicator")
  @ConditionalOnMissingBean(name = "camundaClientHealthIndicator")
  public CamundaClientHealthIndicator camundaClientHealthIndicator(final HealthCheck healthCheck) {
    return new CamundaClientHealthIndicator(healthCheck);
>>>>>>> d91a829d (fix: use string-based @ConditionalOnClass for optional dependencies to fix Java 24+ compatibility):clients/camunda-spring-boot-starter/src/main/java/io/camunda/client/spring/configuration/CamundaActuatorConfiguration.java
  }
}
