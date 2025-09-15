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

import io.camunda.client.bean.BeanInfo;
import io.camunda.client.bean.MethodInfo;
import io.camunda.client.bean.ParameterInfo;
import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.StandardReflectionParameterNameDiscoverer;

public class SpringMethodInfo implements MethodInfo {

  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private static final StandardReflectionParameterNameDiscoverer PARAMETER_NAME_DISCOVERER =
      new StandardReflectionParameterNameDiscoverer();

  private final BeanInfo beanInfo;
  private final Method method;

  public SpringMethodInfo(final BeanInfo beanInfo, final Method method) {
    this.beanInfo = beanInfo;
    this.method = method;
  }

  @Override
  public BeanInfo getBeanInfo() {
    return beanInfo;
  }

  @Override
  public Method getMethod() {
    return method;
  }

  @Override
  public List<ParameterInfo> getParameters() {
    return getParametersFiltered(parameter -> true);
  }

  @Override
  public <T extends Annotation> Optional<T> getAnnotation(final Class<T> type) {
    return Optional.ofNullable(findAnnotation(method, type));
  }

  @Override
  public List<ParameterInfo> getParametersFilteredByAnnotation(
      final Class<? extends Annotation> type) {
    return getParametersFiltered(parameter -> parameter.isAnnotationPresent(type));
  }

  @Override
  public Object invoke(final Object... args) throws Exception {
    try {
      return method.invoke(beanInfo.getBeanSupplier().get(), args);
    } catch (final InvocationTargetException e) {
      final Throwable targetException = e.getTargetException();
      if (targetException instanceof Exception) {
        throw (Exception) targetException;
      } else {
        throw new RuntimeException("Failed to invoke method: " + method.getName(), targetException);
      }
    } catch (final IllegalAccessException e) {
      throw new RuntimeException("Failed to invoke method: " + method.getName(), e);
    }
  }

  private List<ParameterInfo> getParametersFiltered(final Predicate<Parameter> filter) {
    final Parameter[] parameters = method.getParameters();
    final String[] parameterNames = getParameterNames();

    final ArrayList<ParameterInfo> result = new ArrayList<>();
    for (int i = 0; i < parameters.length; i++) {
      final Parameter parameter = parameters[i];
      if (filter.test(parameter)) {
        final String parameterName = parameterNames[i];
        final ParameterInfo parameterInfo =
            ParameterInfo.builder()
                .parameterName(parameterName)
                .parameter(parameter)
                .methodInfo(this)
                .build();
        result.add(parameterInfo);
      }
    }
    return result;
  }

  private String[] getParameterNames() {
    String[] parameterNames = PARAMETER_NAME_DISCOVERER.getParameterNames(method);
    if (parameterNames == null) {
      LOG.warn(
          "Parameter names of method "
              + method.getName()
              + " could not be discovered. Please set compiler flag -parameters if you rely on parameter names (e.g. for variable names to fetch from Zeebe)");
      // use default names to avoid null pointer exceptions
      final Parameter[] parameters = method.getParameters();
      parameterNames = new String[parameters.length];
      for (int i = 0; i < parameters.length; i++) {
        parameterNames[i] = "arg" + i;
      }
    }
    return parameterNames;
  }
}
