/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.spring.client.jobhandling.parameter;

import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;

public class CustomHeadersResolver implements ParameterResolver {
  @Override
  public Object resolve(final JobClient jobClient, final ActivatedJob job) {
    return job.getCustomHeaders();
  }
}
