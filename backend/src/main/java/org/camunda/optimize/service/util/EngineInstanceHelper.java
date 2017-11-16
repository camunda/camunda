package org.camunda.optimize.service.util;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @author Askar Akhmerov
 */
@Component
public class EngineInstanceHelper {

  @Autowired
  private BeanFactory beanFactory;

  public <R, C extends Class<R>> R getInstance(C requiredType, String engineAlias) {
    return requiredType.cast(beanFactory.getBean(BeanHelper.getBeanName(requiredType), engineAlias));
  }
}
