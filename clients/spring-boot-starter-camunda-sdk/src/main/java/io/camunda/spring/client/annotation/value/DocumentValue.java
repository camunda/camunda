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
package io.camunda.spring.client.annotation.value;

import io.camunda.spring.client.bean.ParameterInfo;

public class DocumentValue implements CamundaAnnotationValue<ParameterInfo> {
  private final String name;
  private final ParameterInfo parameterInfo;
  private final boolean optional;
  private final ParameterType parameterType;

  public DocumentValue(
      final String name,
      final ParameterInfo parameterInfo,
      final boolean optional,
      final ParameterType parameterType) {
    this.name = name;
    this.parameterInfo = parameterInfo;
    this.optional = optional;
    this.parameterType = parameterType;
  }

  public String getName() {
    return name;
  }

  @Override
  public ParameterInfo getBeanInfo() {
    return parameterInfo;
  }

  public boolean isOptional() {
    return optional;
  }

  public ParameterType getParameterType() {
    return parameterType;
  }

  public enum ParameterType {
    SINGLE,
    LIST,
    CONTEXT
  }
}
