/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.util.configuration.condition;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

import java.util.Arrays;
import java.util.List;

import static org.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.CLOUD_PROFILE;

public class CCSaaSCondition implements Condition {
  @Override
  public boolean matches(final ConditionContext context, final AnnotatedTypeMetadata metadata) {
    final List<String> activeProfiles = Arrays.asList(context.getEnvironment().getActiveProfiles());
    return activeProfiles.size() == 1 && activeProfiles.contains(CLOUD_PROFILE);
  }
}
