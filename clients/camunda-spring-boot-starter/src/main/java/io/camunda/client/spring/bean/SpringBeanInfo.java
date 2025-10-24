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
package io.camunda.client.spring.bean;

import static org.springframework.core.annotation.AnnotationUtils.findAnnotation;
import static org.springframework.util.ReflectionUtils.getAllDeclaredMethods;

import io.camunda.client.bean.BeanInfo;
import java.lang.annotation.Annotation;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.springframework.aop.support.AopUtils;

public final class SpringBeanInfo implements BeanInfo {
  private final Supplier<Object> beanSupplier;
  private final String beanName;
  private final Class<?> targetClass;

  public SpringBeanInfo(
      final Supplier<Object> beanSupplier, final String beanName, final Class<?> targetClass) {
    this.beanSupplier = beanSupplier;
    this.beanName = beanName;
    this.targetClass =
        targetClass == null ? AopUtils.getTargetClass(beanSupplier.get()) : targetClass;
  }

  @Override
  public String getBeanName() {
    return beanName;
  }

  @Override
  public boolean hasMethodAnnotation(final Class<? extends Annotation> type) {
    return Stream.of(getAllDeclaredMethods(getTargetClass()))
        .anyMatch(m -> m.isAnnotationPresent(type));
  }

  @Override
  public <T extends Annotation> Optional<T> getAnnotation(final Class<T> type) {
    return Optional.ofNullable(findAnnotation(getTargetClass(), type));
  }

  @Override
  public Supplier<Object> getBeanSupplier() {
    return beanSupplier;
  }

  @Override
  public Class<?> getTargetClass() {
    return targetClass;
  }
}
