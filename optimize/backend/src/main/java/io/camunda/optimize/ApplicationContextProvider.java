/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/** The class is used to get the needed beans in classes that are not managed by spring */
@Component
public class ApplicationContextProvider implements ApplicationContextAware {

  private static ApplicationContext applicationContext;

  public static <T> T getBean(final Class<T> beanClass) {
    return applicationContext.getBean(beanClass);
  }

  public static ApplicationContext getApplicationContext() {
    return ApplicationContextProvider.applicationContext;
  }

  @Override
  public void setApplicationContext(final ApplicationContext applicationContext)
      throws BeansException {
    ApplicationContextProvider.applicationContext = applicationContext;
  }
}
