package io.camunda.zeebe.spring.client.jobhandling.parameter;

import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.worker.JobClient;

public class CustomHeadersResolver implements ParameterResolver {
  @Override
  public Object resolve(JobClient jobClient, ActivatedJob job) {
    return job.getCustomHeaders();
  }
}
