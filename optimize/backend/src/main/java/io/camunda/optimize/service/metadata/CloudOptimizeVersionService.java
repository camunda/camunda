/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.metadata;

import io.camunda.optimize.service.util.configuration.condition.CamundaCloudCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(CamundaCloudCondition.class)
public class CloudOptimizeVersionService extends OptimizeVersionService {

  private static final String C8_VERSION = "8.6.0-alpha4";

  public CloudOptimizeVersionService() {
    super(
        Version.RAW_VERSION.endsWith("-SNAPSHOT") ? C8_VERSION + "-SNAPSHOT" : C8_VERSION,
        C8_VERSION,
        Version.VERSION);
  }
}
