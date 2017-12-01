package org.camunda.optimize.service.util;

import org.camunda.optimize.rest.engine.EngineContext;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @author Askar Akhmerov
 */
@Component
public class BeanHelper {

  @Autowired
  private BeanFactory beanFactory;

  public <R, C extends Class<R>> R getInstance(C requiredType, EngineContext engineContext) {
    return requiredType.cast(beanFactory.getBean(BeanHelper.getBeanName(requiredType), engineContext));
  }

  /**
   * Used to create spring bean id out of class simple name.
   * Basically replaces first letter with it's lower case.
   *
   * @param beanClass
   * @return
   */
  public static String getBeanName(Class beanClass) {
    return beanClass.getSimpleName().substring(0, 1).toLowerCase() + beanClass.getSimpleName().substring(1);
  }
}
