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
import java.lang.invoke.MethodHandles;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.StandardReflectionParameterNameDiscoverer;

public class MethodInfo implements BeanInfo {

  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private static final StandardReflectionParameterNameDiscoverer PARAMETER_NAME_DISCOVERER =
      new StandardReflectionParameterNameDiscoverer();

  protected ClassInfo classInfo;
  protected Method method;

  protected MethodInfo(final ClassInfo classInfo, final Method method) {
    this.classInfo = classInfo;
    this.method = method;
  }

  protected MethodInfo(final MethodInfo original) {
    classInfo = original.classInfo;
    method = original.method;
  }

  @Override
  public Object getBean() {
    return classInfo.getBean();
  }

  @Override
  public String getBeanName() {
    return classInfo.getBeanName();
  }

  @Override
  public String toString() {
    return "MethodInfo{" + "classInfo=" + classInfo + ", method=" + method + '}';
  }

  public String getMethodName() {
    return method.getName();
  }

  public Object invoke(final Object... args) throws Exception {
    try {
      return method.invoke(getBean(), args);
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

  public <T extends Annotation> Optional<T> getAnnotation(final Class<T> type) {
    return Optional.ofNullable(findAnnotation(method, type));
  }

  public List<ParameterInfo> getParameters() {
    final Parameter[] parameters = method.getParameters();
    final String[] parameterNames = getParameterNames();

    final ArrayList<ParameterInfo> result = new ArrayList<>();
    for (int i = 0; i < parameters.length; i++) {
      result.add(new ParameterInfo(this, parameters[i], parameterNames[i]));
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

  public List<ParameterInfo> getParametersFilteredByAnnotation(
      final Class<? extends Annotation> type) {
    final Parameter[] parameters = method.getParameters();
    final String[] parameterNames = getParameterNames();

    final ArrayList<ParameterInfo> result = new ArrayList<>();
    for (int i = 0; i < parameters.length; i++) {
      if (parameters[i].isAnnotationPresent(type)) {
        result.add(new ParameterInfo(this, parameters[i], parameterNames[i]));
      }
    }
    return result;
  }

  public Class<?> getReturnType() {
    return method.getReturnType();
  }

  public static MethodInfoBuilder builder() {
    return new MethodInfoBuilder();
  }

  public static final class MethodInfoBuilder {

    private ClassInfo classInfo;
    private Method method;

    private MethodInfoBuilder() {}

    public MethodInfoBuilder classInfo(final ClassInfo classInfo) {
      this.classInfo = classInfo;
      return this;
    }

    public MethodInfoBuilder method(final Method method) {
      this.method = method;
      return this;
    }

    public MethodInfo build() {
      return new MethodInfo(classInfo, method);
    }
  }
}
