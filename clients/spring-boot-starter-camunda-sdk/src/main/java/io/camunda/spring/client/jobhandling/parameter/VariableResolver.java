/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.spring.client.jobhandling.parameter;

import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;

public class VariableResolver implements ParameterResolver {
  private final String variableName;
  private final Class<?> variableType;
  private final JsonMapper jsonMapper;

  public VariableResolver(
      final String variableName, final Class<?> variableType, final JsonMapper jsonMapper) {
    this.variableName = variableName;
    this.variableType = variableType;
    this.jsonMapper = jsonMapper;
  }

  @Override
  public Object resolve(final JobClient jobClient, final ActivatedJob job) {
    final Object variableValue = getVariable(job);
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
    return job.getVariable(variableName);
  }

  protected Object mapZeebeVariable(final Object variableValue) {
    if (variableValue != null && !variableType.isInstance(variableValue)) {
      return jsonMapper.fromJson(jsonMapper.toJson(variableValue), variableType);
    } else {
      return variableType.cast(variableValue);
    }
  }
}
