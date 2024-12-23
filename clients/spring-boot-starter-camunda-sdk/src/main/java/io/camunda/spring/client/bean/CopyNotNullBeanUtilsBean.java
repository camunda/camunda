/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.spring.client.bean;

import java.lang.reflect.InvocationTargetException;
import org.apache.commons.beanutils.BeanUtilsBean;

public class CopyNotNullBeanUtilsBean extends BeanUtilsBean {

  @Override
  public void copyProperty(final Object bean, final String name, final Object value)
      throws IllegalAccessException, InvocationTargetException {
    if (value != null) {
      super.copyProperty(bean, name, value);
    }
  }
}
