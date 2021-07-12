/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.util.configuration;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

import java.util.Arrays;

import static org.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.CLOUD_PROFILE;

public class CamundaCloudCondition implements Condition {
  @Override
  public boolean matches(final ConditionContext context, final AnnotatedTypeMetadata metadata) {
    return Arrays.asList(context.getEnvironment().getActiveProfiles()).contains(CLOUD_PROFILE);
  }
}
