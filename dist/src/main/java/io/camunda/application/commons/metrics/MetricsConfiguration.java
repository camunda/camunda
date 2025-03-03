/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.metrics;

import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

@Component
public class MetricsConfiguration implements BeanPostProcessor {

  /** Configure PrometheusMeterRegistry if it's configured */
  @Override
  public Object postProcessAfterInitialization(final Object bean, final String beanName)
      throws BeansException {
    if (bean instanceof PrometheusMeterRegistry) {
      // throw an exception if the user is trying to register invalid metrics, such as
      // the same metric but with different number of labels.
      ((PrometheusMeterRegistry) bean).throwExceptionOnRegistrationFailure();
    }
    return bean;
  }
}
