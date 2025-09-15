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

import java.lang.reflect.Parameter;

public final class ParameterInfoBuilder {
  private Parameter parameter;
  private String parameterName;
  private MethodInfo methodInfo;

  public ParameterInfoBuilder parameter(final Parameter parameter) {
    this.parameter = parameter;
    return this;
  }

  public ParameterInfoBuilder parameterName(final String parameterName) {
    this.parameterName = parameterName;
    return this;
  }

  public ParameterInfoBuilder methodInfo(final MethodInfo methodInfo) {
    this.methodInfo = methodInfo;
    return this;
  }

  public ParameterInfo build() {
    assert methodInfo != null : "methodInfo is null";
    assert parameter != null : "parameter is null";
    if (parameterName == null) {
      parameterName = parameter.getName();
    }
    return InfoFactory.instance().parameterInfo(parameter, parameterName, methodInfo);
  }
}
