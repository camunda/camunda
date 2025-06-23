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
package io.camunda.spring.client.jobhandling.parameter;

import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;

public class VariableParameterResolver implements ParameterResolver {
  private final String variableName;
  private final Class<?> variableType;
  private final JsonMapper jsonMapper;
  private final boolean optional;

  public VariableParameterResolver(
      final String variableName,
      final Class<?> variableType,
      final JsonMapper jsonMapper,
      final boolean optional) {
    this.variableName = variableName;
    this.variableType = variableType;
    this.jsonMapper = jsonMapper;
    this.optional = optional;
  }

  @Override
  public Object resolve(final JobClient jobClient, final ActivatedJob job) {
    final Object variableValue = getVariable(job);
    if (variableValue == null) {
      if (optional) {
        return null;
      } else {
        throw new IllegalStateException(
            "Variable " + variableName + " is mandatory, but no value was found");
      }
    }
    try {
      return mapZeebeVariable(variableValue);
    } catch (final ClassCastException | IllegalArgumentException ex) {
      throw new RuntimeException(
          "Cannot assign process variable '"
              + variableName
              + "' to parameter when executing job '"
              + job.getType()
              + "', invalid type found: "
              + ex.getMessage());
    }
  }

  protected Object getVariable(final ActivatedJob job) {
    return job.getVariablesAsMap().get(variableName);
  }

  protected Object mapZeebeVariable(final Object variableValue) {
    if (variableValue != null && !variableType.isInstance(variableValue)) {
      return jsonMapper.fromJson(jsonMapper.toJson(variableValue), variableType);
    } else {
      return variableType.cast(variableValue);
    }
  }
}
