/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.qa.util;

import java.lang.reflect.Field;
import org.junit.Rule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.support.AbstractTestExecutionListener;


public class DependencyInjectionTestExecutionListener extends AbstractTestExecutionListener {

  private static final Logger logger = LoggerFactory.getLogger(DependencyInjectionTestExecutionListener.class);

  @Override
  public void prepareTestInstance(final TestContext testContext) throws Exception {
    if (logger.isDebugEnabled()) {
      logger.debug("Performing dependency injection for test context [" + testContext + "].");
    }
    injectDependenciesInRules(testContext);
  }

  private void injectDependenciesInRules(final TestContext testContext) throws Exception {
    Object bean = testContext.getTestInstance();
    AutowireCapableBeanFactory beanFactory = testContext.getApplicationContext().getAutowireCapableBeanFactory();
    Class<?> aClass = bean.getClass();
    do {
      for(Field field: aClass.getDeclaredFields()) {
        autowireBeansInRules(bean, beanFactory, field);
      }
      aClass = aClass.getSuperclass();
    } while (aClass != null);
  }

  private void autowireBeansInRules(Object bean, AutowireCapableBeanFactory beanFactory, Field field) {
    if (field.isAnnotationPresent(Rule.class)) {
      try {
        field.setAccessible(true);
        beanFactory.autowireBeanProperties(field.get(bean), AutowireCapableBeanFactory.AUTOWIRE_NO, false);
      } catch (IllegalAccessException e) {
        logger.debug("Unable to inject beans into rule field: " + field.getName());
      }
    }
  }

}
