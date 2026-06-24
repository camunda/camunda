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

import io.camunda.client.annotation.AnnotationUtil;
import io.camunda.client.annotation.value.JobWorkerValue;
import io.camunda.client.bean.BeanInfo;
import io.camunda.client.bean.MethodInfo;
import io.camunda.client.spring.test.util.JobWorkerPermutationsGenerator;
import io.camunda.client.spring.test.util.JobWorkerPermutationsGenerator.TestDimension;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.stream.Collectors;

public class JobWorkerPermutationsUtil {

  public static JobWorkerValue jobWorkerValue(
      final Class<?> permutationsClass, final TestDimension testDimension) {
    return AnnotationUtil.getJobWorkerValue(
            JobWorkerPermutationsUtil.getMethodInfo(permutationsClass, testDimension))
        .get();
  }

  public static MethodInfo getMethodInfo(final Class<?> permutationsClass, final TestDimension td) {
    final Method method = JobWorkerPermutationsUtil.getMethod(permutationsClass, td);
    try {
      return MethodInfo.builder()
          .beanInfo(
              BeanInfo.builder()
                  .beanSupplier(
                      () -> {
                        try {
                          return permutationsClass.getConstructors()[0].newInstance();
                        } catch (final InstantiationException
                            | IllegalAccessException
                            | InvocationTargetException e) {
                          throw new RuntimeException(
                              "Error while supplying bean of type " + permutationsClass, e);
                        }
                      })
                  .beanName("testBean")
                  .build())
          .method(method)
          .build();
    } catch (final Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static Method getMethod(final Class<?> permutationsClass, final TestDimension td) {
    final String methodName = JobWorkerPermutationsGenerator.generateMethodName(td);
    final Class<?>[] parameterTypes = JobWorkerPermutationsGenerator.generateParameterTypes(td);
    try {
      return permutationsClass.getDeclaredMethod(methodName, parameterTypes);
    } catch (final NoSuchMethodException e) {
      throw new RuntimeException(
          String.format(
              "Did not find method with name '%s' and parameter types [%s]",
              methodName,
              Arrays.stream(parameterTypes).map(Class::getName).collect(Collectors.joining(", "))),
          e);
    }
  }
}
