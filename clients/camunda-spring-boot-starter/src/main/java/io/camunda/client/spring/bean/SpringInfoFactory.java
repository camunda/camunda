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

import io.camunda.client.bean.BeanInfo;
import io.camunda.client.bean.InfoFactory;
import io.camunda.client.bean.MethodInfo;
import io.camunda.client.bean.ParameterInfo;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.function.Supplier;

public class SpringInfoFactory implements InfoFactory {

  @Override
  public BeanInfo beanInfo(
      final String beanName, final Supplier<Object> beanSupplier, final Class<?> targetClass) {
    return new SpringBeanInfo(beanSupplier, beanName, targetClass);
  }

  @Override
  public MethodInfo methodInfo(final BeanInfo beanInfo, final Method method) {
    return new SpringMethodInfo(beanInfo, method);
  }

  @Override
  public ParameterInfo parameterInfo(
      final Parameter parameter, final String parameterName, final MethodInfo methodInfo) {
    return new SpringParameterInfo(methodInfo, parameterName, parameter);
  }
}
