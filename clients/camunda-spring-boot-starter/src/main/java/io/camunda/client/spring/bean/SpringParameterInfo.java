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

import io.camunda.client.bean.MethodInfo;
import io.camunda.client.bean.ParameterInfo;
import java.lang.reflect.Parameter;

public class SpringParameterInfo implements ParameterInfo {
  private final MethodInfo methodInfo;
  private final String parameterName;
  private final Parameter parameter;

  public SpringParameterInfo(
      final MethodInfo methodInfo, final String parameterName, final Parameter parameter) {
    this.methodInfo = methodInfo;
    this.parameterName = parameterName;
    this.parameter = parameter;
  }

  @Override
  public Parameter getParameter() {
    return parameter;
  }

  @Override
  public String getParameterName() {
    return parameterName;
  }

  @Override
  public MethodInfo getMethodInfo() {
    return methodInfo;
  }
}
