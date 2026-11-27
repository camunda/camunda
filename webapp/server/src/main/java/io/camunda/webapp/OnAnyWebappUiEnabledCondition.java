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
import java.util.Map;
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

  private static final String ADMIN = "admin";

  /** Webapp name to the profiles that activate it. */
  private static final Map<String, List<String>> WEBAPP_PROFILES =
      Map.of("tasklist", List.of("tasklist"), "operate", List.of("operate"), ADMIN, List.of(ADMIN));

  /**
   * Webapp name to the names whose enabled and ui-enabled properties must all hold. Admin's UI
   * needs both the {@code identity} and {@code admin} switches, as in {@code
   * WebappsHelper#isIdentityUiEnabled}.
   */
  private static final Map<String, List<String>> WEBAPP_PROPERTY_NAMES =
      Map.of(
          "tasklist",
          List.of("tasklist"),
          "operate",
          List.of("operate"),
          ADMIN,
          List.of("identity", ADMIN));

  public static Set<String> enabledWebapps(final Environment environment) {
    final var condition = new OnAnyWebappUiEnabledCondition();
    return WEBAPP_PROFILES.entrySet().stream()
        .filter(
            webapp ->
                environment.acceptsProfiles(Profiles.of(webapp.getValue().toArray(String[]::new)))
                    && WEBAPP_PROPERTY_NAMES.get(webapp.getKey()).stream()
                        .flatMap(name -> condition.getRequiredProperties(name).stream())
                        .allMatch(
                            property -> environment.getProperty(property, Boolean.class, true)))
        .map(Map.Entry::getKey)
        .collect(Collectors.toUnmodifiableSet());
  }

  @Override
  public boolean matches(final ConditionContext context, final AnnotatedTypeMetadata metadata) {
    return !enabledWebapps(context.getEnvironment()).isEmpty();
  }
}
