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
package io.camunda.client.spring.annotation.processor;

import static io.camunda.client.annotation.AnnotationUtil.getJobWorkerValue;
import static io.camunda.client.annotation.AnnotationUtil.isJobWorker;
import static org.springframework.util.ReflectionUtils.doWithMethods;

import io.camunda.client.CamundaClient;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.bean.BeanInfo;
import io.camunda.client.bean.MethodInfo;
import io.camunda.client.jobhandling.BeanJobHandlerFactory;
import io.camunda.client.jobhandling.CommandExceptionHandlingStrategy;
import io.camunda.client.jobhandling.JobWorkerManager;
import io.camunda.client.jobhandling.ManagedJobWorker;
import io.camunda.client.jobhandling.parameter.ParameterResolverStrategy;
import io.camunda.client.jobhandling.result.ResultProcessorStrategy;
import io.camunda.client.metrics.MetricsRecorder;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.ReflectionUtils;

/**
 * Triggered by {@link AbstractCamundaAnnotationProcessor#onStart(CamundaClient)} to add Handler
 * subscriptions for {@link JobWorker} method-annotations.
 *
 * <p>Triggered by {@link AbstractCamundaAnnotationProcessor#onStop(CamundaClient)} to remove all
 * Handler subscriptions.
 */
public class JobWorkerAnnotationProcessor extends AbstractCamundaAnnotationProcessor {

  private static final Logger LOGGER = LoggerFactory.getLogger(JobWorkerAnnotationProcessor.class);

  private final JobWorkerManager jobWorkerManager;
  private final CommandExceptionHandlingStrategy commandExceptionHandlingStrategy;
  private final MetricsRecorder metricsRecorder;
  private final ParameterResolverStrategy parameterResolverStrategy;
  private final ResultProcessorStrategy resultProcessorStrategy;
  private final List<ManagedJobWorker> managedJobWorkers = new ArrayList<>();

  public JobWorkerAnnotationProcessor(
      final JobWorkerManager jobWorkerManager,
      final CommandExceptionHandlingStrategy commandExceptionHandlingStrategy,
      final MetricsRecorder metricsRecorder,
      final ParameterResolverStrategy parameterResolverStrategy,
      final ResultProcessorStrategy resultProcessorStrategy) {
    this.jobWorkerManager = jobWorkerManager;
    this.commandExceptionHandlingStrategy = commandExceptionHandlingStrategy;
    this.metricsRecorder = metricsRecorder;
    this.parameterResolverStrategy = parameterResolverStrategy;
    this.resultProcessorStrategy = resultProcessorStrategy;
  }

  @Override
  public boolean isApplicableFor(final BeanInfo beanInfo) {
    return isJobWorker(beanInfo);
  }

  @Override
  public void configureFor(final BeanInfo beanInfo) {
    final List<ManagedJobWorker> newManagedJobWorkers = new ArrayList<>();

    doWithMethods(
        beanInfo.getTargetClass(),
        method -> {
          final MethodInfo methodInfo = beanInfo.toMethodInfo(method);
          getJobWorkerValue(methodInfo)
              .map(
                  jobWorkerValue ->
                      new ManagedJobWorker(
                          jobWorkerValue,
                          new BeanJobHandlerFactory(
                              methodInfo,
                              commandExceptionHandlingStrategy,
                              parameterResolverStrategy,
                              resultProcessorStrategy,
                              metricsRecorder)))
              .ifPresent(newManagedJobWorkers::add);
        },
        ReflectionUtils.USER_DECLARED_METHODS);

    LOGGER.debug(
        "Configuring {} Job worker(s) of bean '{}': {}",
        newManagedJobWorkers.size(),
        beanInfo.getBeanName(),
        newManagedJobWorkers);
    managedJobWorkers.addAll(newManagedJobWorkers);
  }

  @Override
  public void start(final CamundaClient client) {
    managedJobWorkers.forEach(
        managedJobWorker -> {
          jobWorkerManager.createJobWorker(client, managedJobWorker, this);
        });
  }

  @Override
  public void stop(final CamundaClient camundaClient) {
    jobWorkerManager.closeAllJobWorkers(this);
    managedJobWorkers.clear();
  }
}
