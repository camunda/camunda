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
package io.camunda.client.bean;

import io.camunda.client.spring.bean.BeanInfoImpl;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.function.Supplier;

public interface BeanInfo {
  String getBeanName();

  default boolean hasClassAnnotation(final Class<? extends Annotation> type) {
    return getTargetClass().isAnnotationPresent(type);
  }

  boolean hasMethodAnnotation(final Class<? extends Annotation> type);

  <T extends Annotation> Optional<T> getAnnotation(final Class<T> type);

  Supplier<Object> getBeanSupplier();

  Class<?> getTargetClass();

  default MethodInfo toMethodInfo(final Method method) {
    return MethodInfo.builder().beanInfo(this).method(method).build();
  }

  static Builder builder() {
    return new BeanInfoImpl();
  }

  interface Builder {
    Builder beanName(String beanName);

    Builder beanSupplier(Supplier<Object> beanSupplier);

    default Builder bean(final Object bean) {
      return beanSupplier(() -> bean);
    }

    Builder targetClass(Class<?> targetClass);

    BeanInfo build();
  }
}
