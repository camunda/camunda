package io.camunda.zeebe.spring.client.bean;

import java.lang.reflect.InvocationTargetException;
import org.apache.commons.beanutils.BeanUtilsBean;

public class CopyNotNullBeanUtilsBean extends BeanUtilsBean {

  @Override
  public void copyProperty(Object bean, String name, Object value)
      throws IllegalAccessException, InvocationTargetException {
    if (value != null) {
      super.copyProperty(bean, name, value);
    }
  }
}
