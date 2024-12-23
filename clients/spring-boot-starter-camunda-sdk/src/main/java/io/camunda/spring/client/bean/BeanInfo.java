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

import static org.springframework.util.ReflectionUtils.getAllDeclaredMethods;

import java.lang.annotation.Annotation;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.springframework.aop.support.AopUtils;

public interface BeanInfo {

  static Supplier<IllegalStateException> noAnnotationFound(final Class<? extends Annotation> type) {
    return () -> new IllegalStateException("no annotation found - " + type);
  }

  Object getBean();

  String getBeanName();

  default Class<?> getTargetClass() {
    return AopUtils.getTargetClass(getBean());
  }

  default boolean hasClassAnnotation(final Class<? extends Annotation> type) {
    return getTargetClass().isAnnotationPresent(type);
  }

  default boolean hasMethodAnnotation(final Class<? extends Annotation> type) {
    return Stream.of(getAllDeclaredMethods(getTargetClass()))
        .anyMatch(m -> m.isAnnotationPresent(type));
  }
}
