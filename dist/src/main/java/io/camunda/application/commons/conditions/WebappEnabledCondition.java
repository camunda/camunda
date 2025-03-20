/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.conditions;

import java.util.List;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

public class WebappEnabledCondition implements Condition {

  @Override
  public boolean matches(final ConditionContext context, final AnnotatedTypeMetadata metadata) {
    final var apps = List.of("operate", "tasklist");
    for (final var app : apps) {
      if (context.getEnvironment().matchesProfiles(app)) {
        return webappEnabled(app, context.getEnvironment());
      }
    }
    return context.getEnvironment().matchesProfiles("identity");
  }

  private boolean webappEnabled(final String app, final Environment environment) {
    // Defaults to true if missing, since it's the default in OperateProperties & TasklistProperties
    return environment.getProperty(
        String.format("camunda.%s.webappEnabled", app), Boolean.class, Boolean.TRUE);
  }
}
