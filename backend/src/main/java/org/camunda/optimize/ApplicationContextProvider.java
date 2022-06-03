/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize;

import lombok.Getter;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/**
 * The class is used to get the needed beans in classes that are not managed by spring
 */
@Component
public class ApplicationContextProvider implements ApplicationContextAware {
  @Getter
  private static ApplicationContext applicationContext;

  public static <T> T getBean(Class<T> beanClass) {
    return applicationContext.getBean(beanClass);
  }

  @Override
  public void setApplicationContext(final ApplicationContext applicationContext) throws BeansException {
    ApplicationContextProvider.applicationContext = applicationContext;
  }
}
