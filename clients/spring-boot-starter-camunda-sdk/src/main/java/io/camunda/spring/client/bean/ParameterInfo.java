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

import java.lang.reflect.Parameter;

public class ParameterInfo implements BeanInfo {
  private final MethodInfo methodInfo;
  private final String parameterName;
  private final Parameter parameterInfo;

  public ParameterInfo(final MethodInfo methodInfo, final Parameter param, final String paramName) {
    this.methodInfo = methodInfo;
    if (paramName == null) {
      parameterName = param.getName();
    } else {
      parameterName = paramName;
    }
    parameterInfo = param;
  }

  public Parameter getParameterInfo() {
    return parameterInfo;
  }

  public String getParameterName() {
    return parameterName;
  }

  public MethodInfo getMethodInfo() {
    return methodInfo;
  }

  @Override
  public String toString() {
    return "ParameterInfo{"
        + "parameterName="
        + parameterName
        + ", parameterInfo="
        + parameterInfo
        + '}';
  }

  @Override
  public Object getBean() {
    return methodInfo.getBean();
  }

  @Override
  public String getBeanName() {
    return methodInfo.getBeanName();
  }
}
