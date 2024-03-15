package io.camunda.zeebe.spring.client.jobhandling.parameter;

import io.camunda.zeebe.client.api.JsonMapper;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.worker.JobClient;

public class VariableResolver implements ParameterResolver {
  private final String variableName;
  private final Class<?> variableType;
  private final JsonMapper jsonMapper;

  public VariableResolver(String variableName, Class<?> variableType, JsonMapper jsonMapper) {
    this.variableName = variableName;
    this.variableType = variableType;
    this.jsonMapper = jsonMapper;
  }

  @Override
  public Object resolve(JobClient jobClient, ActivatedJob job) {
    Object variableValue = getVariable(job);
    try {
      return mapZeebeVariable(variableValue);
    } catch (ClassCastException | IllegalArgumentException ex) {
      throw new RuntimeException(
          "Cannot assign process variable '"
              + variableName
              + "' to parameter when executing job '"
              + job.getType()
              + "', invalid type found: "
              + ex.getMessage());
    }
  }

  protected Object getVariable(ActivatedJob job) {
    return job.getVariable(variableName);
  }

  protected Object mapZeebeVariable(Object variableValue) {
    if (variableValue != null && !variableType.isInstance(variableValue)) {
      return jsonMapper.fromJson(jsonMapper.toJson(variableValue), variableType);
    } else {
      return variableType.cast(variableValue);
    }
  }
}
