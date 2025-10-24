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
package io.camunda.client.spring.testsupport;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.bean.BeanInfo;
import io.camunda.client.bean.ParameterInfo;
import java.beans.Introspector;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

public final class BeanInfoUtil {
  private BeanInfoUtil() {}

  public static BeanInfo beanInfo(final Object bean) {
    return BeanInfo.builder()
        .bean(bean)
        .beanName(Introspector.decapitalize(bean.getClass().getSimpleName()))
        .build();
  }

  public static BeanInfo beanInfo(final Class<?> beanClass) {
    return BeanInfo.builder()
        .beanSupplier(
            () -> {
              try {
                return beanClass.getConstructors()[0].newInstance();
              } catch (final InstantiationException
                  | IllegalAccessException
                  | InvocationTargetException e) {
                throw new RuntimeException("Error while constructing bean of type " + beanClass, e);
              }
            })
        .beanName(Introspector.decapitalize(beanClass.getSimpleName()))
        .build();
  }

  public static ParameterInfo parameterInfo(final Object bean, final String methodName) {
    final List<ParameterInfo> parameterInfos = parameterInfos(bean, methodName);
    assertThat(parameterInfos).hasSize(1);
    return parameterInfos.get(0);
  }

  public static List<ParameterInfo> parameterInfos(final Object bean, final String methodName) {
    return beanInfo(bean).toMethodInfo(method(bean, methodName)).getParameters();
  }

  public static Method method(final Object bean, final String name) {
    final List<Method> methods =
        Arrays.stream(bean.getClass().getMethods()).filter(m -> m.getName().equals(name)).toList();
    assertThat(methods).hasSize(1);
    return methods.get(0);
  }
}
