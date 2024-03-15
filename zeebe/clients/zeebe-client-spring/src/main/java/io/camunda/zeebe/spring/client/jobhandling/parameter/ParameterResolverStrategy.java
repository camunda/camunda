package io.camunda.zeebe.spring.client.jobhandling.parameter;

import io.camunda.zeebe.spring.client.bean.ParameterInfo;

public interface ParameterResolverStrategy {
  ParameterResolver createResolver(ParameterInfo parameterInfo);
}
