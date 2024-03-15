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
import org.springframework.beans.factory.BeanNameAware;

public abstract class AbstractZeebeAnnotationProcessor implements BeanNameAware {

  private String beanName;

  public String getBeanName() {
    return beanName;
  }

  @Override
  public void setBeanName(final String beanName) {
    this.beanName = beanName;
  }

  public abstract boolean isApplicableFor(ClassInfo beanInfo);

  public abstract void configureFor(final ClassInfo beanInfo);

  public abstract void start(ZeebeClient zeebeClient);

  public abstract void stop(ZeebeClient zeebeClient);
}
