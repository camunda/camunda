/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.spring.client.jobhandling.parameter;

import static io.camunda.spring.client.annotation.AnnotationUtil.getVariableValue;
import static io.camunda.spring.client.annotation.AnnotationUtil.isCustomHeaders;
import static io.camunda.spring.client.annotation.AnnotationUtil.isVariable;
import static io.camunda.spring.client.annotation.AnnotationUtil.isVariablesAsType;

import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import io.camunda.spring.client.bean.ParameterInfo;

public class DefaultParameterResolverStrategy implements ParameterResolverStrategy {
  protected final JsonMapper jsonMapper;

  public DefaultParameterResolverStrategy(final JsonMapper jsonMapper) {
    this.jsonMapper = jsonMapper;
  }

  @Override
  public ParameterResolver createResolver(final ParameterInfo parameterInfo) {
    final Class<?> parameterType = parameterInfo.getParameterInfo().getType();
    if (JobClient.class.isAssignableFrom(parameterType)) {
      return new JobClientParameterResolver();
    } else if (ActivatedJob.class.isAssignableFrom(parameterType)) {
      return new ActivatedJobParameterResolver();
    } else if (isVariable(parameterInfo)) {
      // get() can be used savely here as isVariable() verifies that an annotation is present
      final String variableName = getVariableValue(parameterInfo).get().name();
      return new VariableResolver(variableName, parameterType, jsonMapper);
    } else if (isVariablesAsType(parameterInfo)) {
      return new VariablesAsTypeResolver(parameterType);
    } else if (isCustomHeaders(parameterInfo)) {
      return new CustomHeadersResolver();
    }
    throw new IllegalStateException(
        "Could not create parameter resolver for parameter " + parameterInfo);
  }
}
