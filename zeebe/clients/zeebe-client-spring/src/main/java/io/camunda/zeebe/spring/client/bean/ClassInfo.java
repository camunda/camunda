/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.spring.client.bean;

import static org.springframework.core.annotation.AnnotationUtils.findAnnotation;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Optional;

public final class ClassInfo implements BeanInfo {

  private final Object bean;
  private final String beanName;

  private ClassInfo(final Object bean, final String beanName) {
    this.bean = bean;
    this.beanName = beanName;
  }

  @Override
  public Object getBean() {
    return bean;
  }

  @Override
  public String getBeanName() {
    return beanName;
  }

  public MethodInfo toMethodInfo(final Method method) {
    return MethodInfo.builder().classInfo(this).method(method).build();
  }

  public <T extends Annotation> Optional<T> getAnnotation(final Class<T> type) {
    return Optional.ofNullable(findAnnotation(getTargetClass(), type));
  }

  public static final ClassInfoBuilder builder() {
    return new ClassInfoBuilder();
  }

  public static final class ClassInfoBuilder {

    private Object bean;
    private String beanName;

    public ClassInfoBuilder() {}

    public ClassInfoBuilder bean(final Object bean) {
      this.bean = bean;
      return this;
    }

    public ClassInfoBuilder beanName(final String beanName) {
      this.beanName = beanName;
      return this;
    }

    public ClassInfo build() {
      return new ClassInfo(bean, beanName);
    }
  }
}
