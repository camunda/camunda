/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
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
