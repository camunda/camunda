package io.camunda.zeebe.spring.client.jobhandling.parameter;

import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.worker.JobClient;

public class VariablesAsTypeResolver implements ParameterResolver {
  private final Class<?> variablesType;

  public VariablesAsTypeResolver(Class<?> variablesType) {
    this.variablesType = variablesType;
  }

  @Override
  public Object resolve(JobClient jobClient, ActivatedJob job) {
    return job.getVariablesAsType(variablesType);
  }
}
