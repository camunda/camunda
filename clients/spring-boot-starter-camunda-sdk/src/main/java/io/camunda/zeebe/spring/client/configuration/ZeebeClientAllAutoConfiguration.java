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

import static io.camunda.zeebe.spring.client.configuration.PropertyUtil.getOrLegacyOrDefault;
import static io.camunda.zeebe.spring.client.properties.ZeebeClientConfigurationProperties.DEFAULT;

import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.worker.BackoffSupplier;
import io.camunda.client.impl.worker.ExponentialBackoffBuilderImpl;
import io.camunda.zeebe.spring.client.annotation.customizer.ZeebeWorkerValueCustomizer;
import io.camunda.zeebe.spring.client.jobhandling.CommandExceptionHandlingStrategy;
import io.camunda.zeebe.spring.client.jobhandling.DefaultCommandExceptionHandlingStrategy;
import io.camunda.zeebe.spring.client.jobhandling.JobWorkerManager;
import io.camunda.zeebe.spring.client.jobhandling.ZeebeClientExecutorService;
import io.camunda.zeebe.spring.client.jobhandling.parameter.DefaultParameterResolverStrategy;
import io.camunda.zeebe.spring.client.jobhandling.parameter.ParameterResolverStrategy;
import io.camunda.zeebe.spring.client.jobhandling.result.DefaultResultProcessorStrategy;
import io.camunda.zeebe.spring.client.jobhandling.result.ResultProcessorStrategy;
import io.camunda.zeebe.spring.client.metrics.MetricsRecorder;
import io.camunda.zeebe.spring.client.properties.CamundaClientProperties;
import io.camunda.zeebe.spring.client.properties.PropertyBasedZeebeWorkerValueCustomizer;
import io.camunda.zeebe.spring.client.properties.ZeebeClientConfigurationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@ConditionalOnProperty(
    prefix = "zeebe.client",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
@Import({AnnotationProcessorConfiguration.class, JsonMapperConfiguration.class})
@EnableConfigurationProperties({
  ZeebeClientConfigurationProperties.class,
  CamundaClientProperties.class
})
public class ZeebeClientAllAutoConfiguration {

  private final ZeebeClientConfigurationProperties configurationProperties;
  private final CamundaClientProperties camundaClientProperties;

  public ZeebeClientAllAutoConfiguration(
      final ZeebeClientConfigurationProperties configurationProperties,
      final CamundaClientProperties camundaClientProperties) {
    this.configurationProperties = configurationProperties;
    this.camundaClientProperties = camundaClientProperties;
  }

  @Bean
  @ConditionalOnMissingBean
  public ZeebeClientExecutorService zeebeClientExecutorService() {
    return ZeebeClientExecutorService.createDefault(
        getOrLegacyOrDefault(
            "NumJobWorkerExecutionThreads",
            () -> camundaClientProperties.getZeebe().getExecutionThreads(),
            configurationProperties::getNumJobWorkerExecutionThreads,
            DEFAULT.getNumJobWorkerExecutionThreads(),
            null));
  }

  @Bean
  @ConditionalOnMissingBean
  public CommandExceptionHandlingStrategy commandExceptionHandlingStrategy(
      final ZeebeClientExecutorService scheduledExecutorService) {
    return new DefaultCommandExceptionHandlingStrategy(
        backoffSupplier(), scheduledExecutorService.get());
  }

  @Bean
  @ConditionalOnMissingBean
  public ParameterResolverStrategy parameterResolverStrategy(final JsonMapper jsonMapper) {
    return new DefaultParameterResolverStrategy(jsonMapper);
  }

  @Bean
  @ConditionalOnMissingBean
  public ResultProcessorStrategy resultProcessorStrategy() {
    return new DefaultResultProcessorStrategy();
  }

  @Bean
  public JobWorkerManager jobWorkerManager(
      final CommandExceptionHandlingStrategy commandExceptionHandlingStrategy,
      final MetricsRecorder metricsRecorder,
      final ParameterResolverStrategy parameterResolverStrategy,
      final ResultProcessorStrategy resultProcessorStrategy) {
    return new JobWorkerManager(
        commandExceptionHandlingStrategy,
        metricsRecorder,
        parameterResolverStrategy,
        resultProcessorStrategy);
  }

  @Bean
  public BackoffSupplier backoffSupplier() {
    return new ExponentialBackoffBuilderImpl()
        .maxDelay(1000L)
        .minDelay(50L)
        .backoffFactor(1.5)
        .jitterFactor(0.2)
        .build();
  }

  @Bean("propertyBasedZeebeWorkerValueCustomizer")
  @ConditionalOnMissingBean(name = "propertyBasedZeebeWorkerValueCustomizer")
  public ZeebeWorkerValueCustomizer propertyBasedZeebeWorkerValueCustomizer() {
    return new PropertyBasedZeebeWorkerValueCustomizer(
        configurationProperties, camundaClientProperties);
  }
}
