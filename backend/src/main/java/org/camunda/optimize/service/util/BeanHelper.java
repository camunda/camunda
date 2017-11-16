package org.camunda.optimize.service.util;

/**
 * @author Askar Akhmerov
 */
public class BeanHelper {

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
