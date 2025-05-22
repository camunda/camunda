package io.camunda.zeebe.gateway.rest.util;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

@Component
public class SpringContextHolder implements ApplicationContextAware {

  private static ApplicationContext context;

  /**
   * Returns the Spring managed bean instance of the given class type if it exists. Returns null
   * otherwise.
   *
   * @param beanClass
   * @return
   */
  public static <T extends Object> T getBean(final Class<T> beanClass) {
    return context.getBean(beanClass);
  }

  @Override
  public void setApplicationContext(final ApplicationContext context) throws BeansException {
    // store ApplicationContext reference to access required beans later on
    SpringContextHolder.context = context;
  }
}
