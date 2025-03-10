/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package org.camunda.community.migration.adapter;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.worker.JobWorker;
import io.camunda.client.api.worker.JobWorkerBuilderStep1.JobWorkerBuilderStep3;
import io.camunda.zeebe.spring.client.annotation.processor.AbstractZeebeAnnotationProcessor;
import io.camunda.zeebe.spring.client.bean.ClassInfo;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import org.camunda.bpm.client.spring.impl.client.ClientConfiguration;
import org.camunda.bpm.client.spring.impl.subscription.SpringTopicSubscriptionImpl;
import org.camunda.community.migration.adapter.worker.ExternalTaskHandlerWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExternalTaskWorkerRegistration extends AbstractZeebeAnnotationProcessor {
  private static final Logger LOG = LoggerFactory.getLogger(ExternalTaskWorkerRegistration.class);
  private final ClientConfiguration clientConfiguration;
  private final Map<String, SpringTopicSubscriptionImpl> springTopicSubscriptions = new HashMap<>();
  private final List<JobWorker> openedWorkers = new ArrayList<>();

  public ExternalTaskWorkerRegistration(ClientConfiguration clientConfiguration) {
    this.clientConfiguration = clientConfiguration;
  }

  private Long calculateLockDuration(SpringTopicSubscriptionImpl subscription) {
    Long lockDuration = clientConfiguration.getLockDuration();
    if (subscription.getLockDuration() != null && subscription.getLockDuration() < 0L) {
      lockDuration = subscription.getLockDuration();
    }
    return lockDuration;
  }

  private <T> void setIfPresent(T value, Consumer<T> setter) {
    if (value != null) {
      setter.accept(value);
    }
  }

  @Override
  public boolean isApplicableFor(ClassInfo beanInfo) {
    return SpringTopicSubscriptionImpl.class.isAssignableFrom(beanInfo.getBean().getClass());
  }

  @Override
  public void configureFor(ClassInfo beanInfo) {
    LOG.info("Registering Camunda worker(s) of bean: {}", beanInfo.getBean());
    springTopicSubscriptions.put(
        beanInfo.getBeanName(), (SpringTopicSubscriptionImpl) beanInfo.getBean());
  }

  @Override
  public void start(CamundaClient camundaClient) {
    springTopicSubscriptions.forEach(
        (beanName, bean) -> {
          final JobWorkerBuilderStep3 builder =
              camundaClient
                  .newWorker()
                  .jobType(bean.getTopicName())
                  .handler(
                      new ExternalTaskHandlerWrapper(
                          bean.getExternalTaskHandler(), Optional.empty()))
                  .name(beanName);
          setIfPresent(calculateLockDuration(bean), builder::timeout);
          setIfPresent(clientConfiguration.getMaxTasks(), builder::maxJobsActive);
          setIfPresent(
              clientConfiguration.getAsyncResponseTimeout(),
              timeout -> builder.pollInterval(Duration.ofMillis(timeout)));
          setIfPresent(bean.getVariableNames(), builder::fetchVariables);
          setIfPresent(
              clientConfiguration.getAsyncResponseTimeout(),
              timeout -> builder.requestTimeout(Duration.ofMillis(timeout)));
          openedWorkers.add(builder.open());
        });
  }

  @Override
  public void stop(CamundaClient camundaClient) {
    openedWorkers.forEach(JobWorker::close);
  }
}
