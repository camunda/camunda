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
package io.camunda.client.spring.configuration;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.metrics.CamundaClientMetrics;
import io.camunda.client.health.HealthCheck;
import io.camunda.client.jobhandling.JobWorkerManager;
import io.camunda.client.metrics.JobWorkerMetricsFactory;
import io.camunda.client.metrics.MetricsRecorder;
import io.camunda.client.metrics.MicrometerJobWorkerMetricsFactory;
import io.camunda.client.metrics.MicrometerMetricsRecorder;
import io.camunda.client.spring.actuator.CamundaClientHealthIndicator;
import io.camunda.client.spring.actuator.JobWorkerController;
import io.camunda.client.spring.configuration.condition.ConditionalOnCamundaClientEnabled;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.autoconfigure.endpoint.EndpointAutoConfiguration;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

/**
 * Spring Boot 3 compatible actuator configuration. Uses the package location for HealthIndicator in
 * Spring Boot 3.x.
 */
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
  @ConditionalOnMissingBean
  public JobWorkerMetricsFactory jobWorkerMetricsFactory(@Lazy final MeterRegistry meterRegistry) {
    return new MicrometerJobWorkerMetricsFactory(meterRegistry);
  }

  /**
   * Records the client metrics (e.g. of its HTTP connection pool) to the application's meter
   * registry. The job workers of the starter set their own metrics, so the client's fallback job
   * worker metrics only apply to workers the application opens itself.
   *
   * <p>All configured clients share this one registry. Their meters are tagged with their physical
   * tenant, so clients of different tenants don't collide. Two clients of the same physical tenant
   * in one application do collide (see {@link io.camunda.client.api.metrics.CamundaClientMetrics}):
   * this is a known limitation, as there is no hook to give individual clients their own registry.
   *
   * <p>Runs before all other {@link CamundaClientBuilderCustomizer}s, so that an application's
   * customizer can replace the registry for all clients.
   */
  @Bean
  @Order(Ordered.HIGHEST_PRECEDENCE)
  public CamundaClientBuilderCustomizer camundaClientMeterRegistryCustomizer(
      final ObjectProvider<MeterRegistry> meterRegistry) {
    // Resolved per client when it is built: no registry, or several without a primary one, means
    // no client metrics instead of failing the client creation.
    return builder -> {
      final MeterRegistry registry = meterRegistry.getIfUnique();
      if (registry != null) {
        builder.metrics(CamundaClientMetrics.micrometer().withMeterRegistry(registry).build());
      }
    };
  }

  @Bean
  @ConditionalOnMissingBean
  // Register whenever a primary client is resolvable (one client, or several with a designated
  // primary) and health-check the @Primary client. Resolves from the configuration properties, so
  // it is evaluated correctly before the per-client CamundaClient beans are contributed by the
  // bean-definition post-processor (a plain @ConditionalOnSingleCandidate would see zero candidates
  // at that point and never register). With no primary (multiple clients, none designated) there is
  // no unambiguous client to health-check.
  @Conditional(OnResolvablePrimaryClientCondition.class)
  public HealthCheck camundaHealthCheck(final CamundaClient client) {
    return new HealthCheck(client);
  }

  @Bean
  @ConditionalOnProperty(
      prefix = "management.health.camunda",
      name = "enabled",
      matchIfMissing = true)
  @ConditionalOnClass(HealthIndicator.class)
  @Conditional(OnResolvablePrimaryClientCondition.class)
  @ConditionalOnMissingBean(name = "camundaClientHealthIndicator")
  public CamundaClientHealthIndicator camundaClientHealthIndicator(final HealthCheck healthCheck) {
    return new CamundaClientHealthIndicator(healthCheck);
  }
}
