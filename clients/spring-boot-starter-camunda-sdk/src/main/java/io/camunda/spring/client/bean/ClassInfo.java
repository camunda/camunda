/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.spring.client.bean;

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

  @Override
  public String toString() {
    return "ClassInfo{" + "beanName=" + beanName + '}';
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
