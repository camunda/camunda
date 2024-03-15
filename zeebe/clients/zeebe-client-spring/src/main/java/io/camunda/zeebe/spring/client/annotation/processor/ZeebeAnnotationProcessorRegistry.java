/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.spring.client.annotation.processor;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.spring.client.bean.ClassInfo;
import io.camunda.zeebe.spring.client.configuration.AnnotationProcessorConfiguration;
import java.util.List;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.Ordered;

/**
 * Always created by {@link AnnotationProcessorConfiguration}
 *
 * <p>Keeps a list of all annotations and reads them after all Spring beans are initialized
 */
public class ZeebeAnnotationProcessorRegistry implements BeanPostProcessor, Ordered {

  private final List<AbstractZeebeAnnotationProcessor> processors;

  public ZeebeAnnotationProcessorRegistry(final List<AbstractZeebeAnnotationProcessor> processors) {
    this.processors = processors;
  }

  @Override
  public Object postProcessAfterInitialization(final Object bean, final String beanName)
      throws BeansException {
    final ClassInfo beanInfo = ClassInfo.builder().bean(bean).beanName(beanName).build();

    for (final AbstractZeebeAnnotationProcessor zeebePostProcessor : processors) {
      if (zeebePostProcessor.isApplicableFor(beanInfo)) {
        zeebePostProcessor.configureFor(beanInfo);
      }
    }

    return bean;
  }

  public void startAll(final ZeebeClient zeebeClient) {
    processors.forEach(zeebePostProcessor -> zeebePostProcessor.start(zeebeClient));
  }

  public void stopAll(final ZeebeClient zeebeClient) {
    processors.forEach(zeebePostProcessor -> zeebePostProcessor.stop(zeebeClient));
  }

  @Override
  public int getOrder() {
    return LOWEST_PRECEDENCE;
  }
}
