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
import io.camunda.client.annotation.customizer.JobWorkerValueCustomizer;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.worker.BackoffSupplier;
import io.camunda.client.jobhandling.CamundaClientExecutorService;
import io.camunda.client.jobhandling.CommandExceptionHandlingStrategy;
import io.camunda.client.jobhandling.DefaultCommandExceptionHandlingStrategy;
import io.camunda.client.jobhandling.DefaultJobExceptionHandlingStrategy;
import io.camunda.client.jobhandling.JobExceptionHandlingStrategy;
import io.camunda.client.jobhandling.JobWorkerManager;
import io.camunda.client.jobhandling.parameter.DefaultParameterResolverStrategy;
import io.camunda.client.jobhandling.parameter.ParameterResolverStrategy;
import io.camunda.client.jobhandling.result.DefaultDocumentResultProcessorFailureHandlingStrategy;
import io.camunda.client.jobhandling.result.DefaultResultProcessorStrategy;
import io.camunda.client.jobhandling.result.DocumentResultProcessorFailureHandlingStrategy;
import io.camunda.client.jobhandling.result.ResultProcessorStrategy;
import io.camunda.client.metrics.MetricsRecorder;
import io.camunda.client.spring.configuration.condition.ConditionalOnCamundaClientEnabled;
import io.camunda.client.spring.properties.CamundaClientProperties;
import io.camunda.client.spring.properties.PropertyBasedJobWorkerValueCustomizer;
import io.camunda.zeebe.client.ZeebeClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@ConditionalOnCamundaClientEnabled
@Import({
  AnnotationProcessorConfiguration.class,
  JsonMapperConfiguration.class,
  CamundaBeanPostProcessorConfiguration.class
})
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
        camundaClientProperties.getExecutionThreads());
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
      final JsonMapper jsonMapper,
      @Autowired(required = false) final ZeebeClient zeebeClient,
      final CamundaClient camundaClient) {
    return new DefaultParameterResolverStrategy(jsonMapper, zeebeClient, camundaClient);
  }

  @Bean
  @ConditionalOnMissingBean
  public DocumentResultProcessorFailureHandlingStrategy
      documentResultProcessorFailureHandlingStrategy() {
    return new DefaultDocumentResultProcessorFailureHandlingStrategy();
  }

  @Bean
  @ConditionalOnMissingBean
  public ResultProcessorStrategy resultProcessorStrategy(
      final CamundaClient camundaClient,
      final DocumentResultProcessorFailureHandlingStrategy
          documentResultProcessorFailureHandlingStrategy) {
    return new DefaultResultProcessorStrategy(
        camundaClient, documentResultProcessorFailureHandlingStrategy);
  }

  @Bean
  @ConditionalOnMissingBean
  public JobExceptionHandlingStrategy jobExceptionHandlingStrategy(
      final CommandExceptionHandlingStrategy commandExceptionHandlingStrategy,
      final MetricsRecorder metricsRecorder) {
    return new DefaultJobExceptionHandlingStrategy(
        commandExceptionHandlingStrategy, metricsRecorder);
  }

  @Bean
  public JobWorkerManager jobWorkerManager(
      final CommandExceptionHandlingStrategy commandExceptionHandlingStrategy,
      final MetricsRecorder metricsRecorder,
      final ParameterResolverStrategy parameterResolverStrategy,
      final ResultProcessorStrategy resultProcessorStrategy,
      final BackoffSupplier backoffSupplier,
      final JobExceptionHandlingStrategy jobExceptionHandlingStrategy) {
    return new JobWorkerManager(
        commandExceptionHandlingStrategy,
        metricsRecorder,
        parameterResolverStrategy,
        resultProcessorStrategy,
        backoffSupplier,
        jobExceptionHandlingStrategy);
  }

  @Bean
  @ConditionalOnMissingBean
  public BackoffSupplier backoffSupplier() {
    return BackoffSupplier.newBackoffBuilder().build();
  }

  @Bean("propertyBasedJobWorkerValueCustomizer")
  @ConditionalOnMissingBean(name = "propertyBasedJobWorkerValueCustomizer")
  public JobWorkerValueCustomizer propertyBasedJobWorkerValueCustomizer() {
    return new PropertyBasedJobWorkerValueCustomizer(camundaClientProperties);
  }
}
