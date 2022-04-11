/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.util.configuration.condition;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

// This condition is enabled if either Cloud SaaS or CCSM conditions are enabled
public class CamundaCloudCondition implements Condition {
  @Override
  public boolean matches(final ConditionContext context, final AnnotatedTypeMetadata metadata) {
    return new CCSaaSCondition().matches(context, metadata) || new CCSMCondition().matches(context, metadata);
  }
}
