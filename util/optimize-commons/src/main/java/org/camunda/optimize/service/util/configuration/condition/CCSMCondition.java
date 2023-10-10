/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.util.configuration.condition;

import org.camunda.optimize.service.util.configuration.ConfigurationServiceConstants;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.CCSM_PROFILE;

public class CCSMCondition implements Condition {
  @Override
  public boolean matches(final ConditionContext context, final AnnotatedTypeMetadata metadata) {
    // Necessary because Arrays.asList(...) returns an immutable list
    final List<String> activeProfiles = new ArrayList<>(Arrays.asList(context.getEnvironment().getActiveProfiles()));
    activeProfiles.removeAll(ConfigurationServiceConstants.optimizeDatabaseProfiles);
    return activeProfiles.size() == 1 && activeProfiles.contains(CCSM_PROFILE);
  }
}
