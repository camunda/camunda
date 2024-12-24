/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.spring.client.annotation.processor;

import io.camunda.client.CamundaClient;
import io.camunda.spring.client.bean.ClassInfo;
import io.camunda.spring.client.configuration.AnnotationProcessorConfiguration;
import java.util.List;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.Ordered;

/**
 * Always created by {@link AnnotationProcessorConfiguration}
 *
 * <p>Keeps a list of all annotations and reads them after all Spring beans are initialized
 */
public class CamundaAnnotationProcessorRegistry implements BeanPostProcessor, Ordered {

  private final List<AbstractCamundaAnnotationProcessor> processors;

  public CamundaAnnotationProcessorRegistry(
      final List<AbstractCamundaAnnotationProcessor> processors) {
    this.processors = processors;
  }

  @Override
  public Object postProcessAfterInitialization(final Object bean, final String beanName)
      throws BeansException {
    final ClassInfo beanInfo = ClassInfo.builder().bean(bean).beanName(beanName).build();

    for (final AbstractCamundaAnnotationProcessor zeebePostProcessor : processors) {
      if (zeebePostProcessor.isApplicableFor(beanInfo)) {
        zeebePostProcessor.configureFor(beanInfo);
      }
    }

    return bean;
  }

  public void startAll(final CamundaClient client) {
    processors.forEach(zeebePostProcessor -> zeebePostProcessor.start(client));
  }

  public void stopAll(final CamundaClient client) {
    processors.forEach(zeebePostProcessor -> zeebePostProcessor.stop(client));
  }

  @Override
  public int getOrder() {
    return LOWEST_PRECEDENCE;
  }
}
