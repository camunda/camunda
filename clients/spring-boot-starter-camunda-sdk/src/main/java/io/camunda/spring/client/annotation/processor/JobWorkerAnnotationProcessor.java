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
package io.camunda.spring.client.annotation.processor;

import static io.camunda.spring.client.annotation.AnnotationUtil.getJobWorkerValue;
import static io.camunda.spring.client.annotation.AnnotationUtil.isJobWorker;
import static org.springframework.util.ReflectionUtils.doWithMethods;

import io.camunda.client.CamundaClient;
import io.camunda.spring.client.annotation.value.JobWorkerValue;
import io.camunda.spring.client.bean.ClassInfo;
import io.camunda.spring.client.configuration.AnnotationProcessorConfiguration;
import io.camunda.spring.client.jobhandling.JobWorkerManager;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.ReflectionUtils;

/**
 * Always created by {@link AnnotationProcessorConfiguration}
 *
 * <p>Triggered by {@link AbstractCamundaAnnotationProcessor#onStart(CamundaClient)} to add Handler
 * subscriptions for {@link io.camunda.spring.client.annotation.JobWorker} method-annotations.
 *
 * <p>Triggered by {@link AbstractCamundaAnnotationProcessor#onStop(CamundaClient)} to remove all
 * Handler subscriptions.
 */
public class JobWorkerAnnotationProcessor extends AbstractCamundaAnnotationProcessor {

  private static final Logger LOGGER = LoggerFactory.getLogger(JobWorkerAnnotationProcessor.class);

  private final JobWorkerManager jobWorkerManager;

  private final List<JobWorkerValue> jobWorkerValues = new ArrayList<>();

  public JobWorkerAnnotationProcessor(final JobWorkerManager jobWorkerFactory) {
    jobWorkerManager = jobWorkerFactory;
  }

  @Override
  public boolean isApplicableFor(final ClassInfo beanInfo) {
    return isJobWorker(beanInfo);
  }

  @Override
  public void configureFor(final ClassInfo beanInfo) {
    final List<JobWorkerValue> newJobWorkerValues = new ArrayList<>();

    doWithMethods(
        beanInfo.getTargetClass(),
        method ->
            getJobWorkerValue(beanInfo.toMethodInfo(method)).ifPresent(newJobWorkerValues::add),
        ReflectionUtils.USER_DECLARED_METHODS);

    LOGGER.info(
        "Configuring {} Job worker(s) of bean '{}': {}",
        newJobWorkerValues.size(),
        beanInfo.getBeanName(),
        newJobWorkerValues);
    jobWorkerValues.addAll(newJobWorkerValues);
  }

  @Override
  public void start(final CamundaClient client) {
    jobWorkerValues.forEach(
        jobWorkerValue -> {
          jobWorkerManager.createJobWorker(client, jobWorkerValue, this);
        });
  }

  @Override
  public void stop(final CamundaClient camundaClient) {
    jobWorkerManager.closeAllJobWorkers(this);
  }
}
