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

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ServiceLoader;
import java.util.function.Supplier;
import org.slf4j.LoggerFactory;

public interface InfoFactory {
  static InfoFactory instance() {
    final ServiceLoader<InfoFactory> serviceLoader = ServiceLoader.load(InfoFactory.class);
    final long count = serviceLoader.stream().count();
    if (count == 0) {
      throw new IllegalStateException("No Builders found for InfoFactory");
    } else if (count == 1) {
      return serviceLoader.iterator().next();
    } else {
      final InfoFactory factory = serviceLoader.iterator().next();
      LoggerFactory.getLogger(InfoFactory.class)
          .warn(
              "Found more than one Builder for InfoFactory, returning instance of type {}",
              factory.getClass());
      return factory;
    }
  }

  BeanInfo beanInfo(String beanName, Supplier<Object> beanSupplier, Class<?> targetClass);

  MethodInfo methodInfo(BeanInfo beanInfo, Method method);

  ParameterInfo parameterInfo(Parameter parameter, String parameterName, MethodInfo methodInfo);
}
