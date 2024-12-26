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
import org.springframework.beans.factory.BeanNameAware;

public abstract class AbstractCamundaAnnotationProcessor implements BeanNameAware {

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

  public abstract void start(CamundaClient client);

  public abstract void stop(CamundaClient client);
}
