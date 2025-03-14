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

import static io.camunda.spring.client.configuration.CamundaClientConfigurationImpl.DEFAULT;
import static java.util.Optional.ofNullable;

import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.worker.BackoffSupplier;
import io.camunda.client.impl.worker.ExponentialBackoffBuilderImpl;
import io.camunda.spring.client.annotation.customizer.JobWorkerValueCustomizer;
import io.camunda.spring.client.jobhandling.CamundaClientExecutorService;
import io.camunda.spring.client.jobhandling.CommandExceptionHandlingStrategy;
import io.camunda.spring.client.jobhandling.DefaultCommandExceptionHandlingStrategy;
import io.camunda.spring.client.jobhandling.JobWorkerManager;
import io.camunda.spring.client.jobhandling.parameter.DefaultParameterResolverStrategy;
import io.camunda.spring.client.jobhandling.parameter.ParameterResolverStrategy;
import io.camunda.spring.client.jobhandling.result.DefaultResultProcessorStrategy;
import io.camunda.spring.client.jobhandling.result.ResultProcessorStrategy;
import io.camunda.spring.client.metrics.MetricsRecorder;
import io.camunda.spring.client.properties.CamundaClientProperties;
import io.camunda.spring.client.properties.PropertyBasedJobWorkerValueCustomizer;
import io.camunda.zeebe.client.ZeebeClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@ConditionalOnProperty(
    prefix = "camunda.client",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
@Import({AnnotationProcessorConfiguration.class, JsonMapperConfiguration.class})
@EnableConfigurationProperties({CamundaClientProperties.class})
public class CamundaClientAllAutoConfiguration {

  private final CamundaClientProperties camundaClientProperties;

  public CamundaClientAllAutoConfiguration(final CamundaClientProperties camundaClientProperties) {
    this.camundaClientProperties = camundaClientProperties;
  }

  @Bean
  @ConditionalOnMissingBean
  public CamundaClientExecutorService camundaClientExecutorService() {
    return CamundaClientExecutorService.createDefault(
        ofNullable(camundaClientProperties.getExecutionThreads())
            .orElse(DEFAULT.getNumJobWorkerExecutionThreads()));
  }

  @Bean
  @ConditionalOnMissingBean
  public CommandExceptionHandlingStrategy commandExceptionHandlingStrategy(
      final CamundaClientExecutorService scheduledExecutorService) {
    return new DefaultCommandExceptionHandlingStrategy(
        backoffSupplier(), scheduledExecutorService.get());
  }

  @Bean
  @ConditionalOnMissingBean
  public ParameterResolverStrategy parameterResolverStrategy(
      final JsonMapper jsonMapper, @Autowired(required = false) final ZeebeClient zeebeClient) {
    return new DefaultParameterResolverStrategy(jsonMapper, zeebeClient);
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
      final ResultProcessorStrategy resultProcessorStrategy,
      final BackoffSupplier backoffSupplier) {
    return new JobWorkerManager(
        commandExceptionHandlingStrategy,
        metricsRecorder,
        parameterResolverStrategy,
        resultProcessorStrategy,
        backoffSupplier);
  }

  @Bean
  @ConditionalOnMissingBean
  public BackoffSupplier backoffSupplier() {
    return new ExponentialBackoffBuilderImpl()
        .maxDelay(1000L)
        .minDelay(50L)
        .backoffFactor(1.5)
        .jitterFactor(0.2)
        .build();
  }

  @Bean("propertyBasedJobWorkerValueCustomizer")
  @ConditionalOnMissingBean(name = "propertyBasedJobWorkerValueCustomizer")
  public JobWorkerValueCustomizer propertyBasedJobWorkerValueCustomizer() {
    return new PropertyBasedJobWorkerValueCustomizer(camundaClientProperties);
  }
}
