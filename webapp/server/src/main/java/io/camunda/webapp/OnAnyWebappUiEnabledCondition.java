/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapp;

import io.camunda.spring.utils.ConditionalOnWebappUiEnabled.OnWebappUiEnabledCondition;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Matches when at least one app served by the unified webapp has its profile active and its UI
 * enabled. Gates what the apps share (assets, favicon, custom CSS), because {@code
 * ConditionalOnWebappUiEnabled} with several names requires all of them to be enabled.
 */
public class OnAnyWebappUiEnabledCondition extends OnWebappUiEnabledCondition {

  private static final List<String> WEBAPPS = List.of("tasklist", "operate");

  public static Set<String> enabledWebapps(final Environment environment) {
    final var condition = new OnAnyWebappUiEnabledCondition();
    return WEBAPPS.stream()
        .filter(
            webapp ->
                environment.acceptsProfiles(Profiles.of(webapp))
                    && condition.getRequiredProperties(webapp).stream()
                        .allMatch(
                            property -> environment.getProperty(property, Boolean.class, true)))
        .collect(Collectors.toUnmodifiableSet());
  }

  @Override
  public boolean matches(final ConditionContext context, final AnnotatedTypeMetadata metadata) {
    return !enabledWebapps(context.getEnvironment()).isEmpty();
  }
}
