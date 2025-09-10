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
import io.camunda.client.bean.ParameterInfoBuilder;
import java.lang.reflect.Parameter;

public class ParameterInfoImpl implements ParameterInfo, ParameterInfoBuilder {
  private MethodInfo methodInfo;
  private String parameterName;
  private Parameter parameter;

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

  @Override
  public ParameterInfoBuilder parameter(final Parameter parameter) {
    this.parameter = parameter;
    return this;
  }

  @Override
  public ParameterInfoBuilder parameterName(final String parameterName) {
    this.parameterName = parameterName;
    return this;
  }

  @Override
  public ParameterInfoBuilder methodInfo(final MethodInfo methodInfo) {
    this.methodInfo = methodInfo;
    return this;
  }

  @Override
  public ParameterInfo build() {
    assert methodInfo != null : "methodInfo is null";
    assert parameter != null : "parameter is null";
    if (parameterName == null) {
      parameterName = parameter.getName();
    }
    return this;
  }
}
