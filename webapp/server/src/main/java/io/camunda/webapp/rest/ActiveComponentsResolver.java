/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapp.rest;

import io.camunda.spring.utils.ConditionalOnWebappUiEnabled;
import java.util.List;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Resolves the set of webapp components whose UI is currently enabled in this deployment.
 *
 * <p>A component is included when none of its three gating properties are explicitly set to {@code
 * false}. All three default to {@code true}, mirroring the property set checked by {@link
 * io.camunda.spring.utils.ConditionalOnWebappUiEnabled.OnWebappUiEnabledCondition} (which itself
 * extends {@link io.camunda.spring.utils.ConditionalOnWebappEnabled.OnWebappEnabledCondition} with
 * the additional {@code ui-enabled} key).
 *
 * <p>The returned list is always sorted alphabetically so JSON snapshots are deterministic.
 */
@Component
@ConditionalOnWebappUiEnabled("tmp-webapp")
public class ActiveComponentsResolver {

  static final List<String> KNOWN_COMPONENTS = List.of("admin", "operate", "tasklist");

  private final Environment environment;

  public ActiveComponentsResolver(final Environment environment) {
    this.environment = environment;
  }

  /**
   * Returns the sorted list of enabled component names from {@link #KNOWN_COMPONENTS}.
   *
   * <p>A component is enabled when all three of the following are {@code true} (or absent,
   * defaulting to {@code true}):
   *
   * <ul>
   *   <li>{@code camunda.{name}.webapp-enabled} — legacy property
   *   <li>{@code camunda.webapps.{name}.enabled} — unified backend-enabled property
   *   <li>{@code camunda.webapps.{name}.ui-enabled} — unified UI-enabled property
   * </ul>
   *
   * <p>The {@code ui-enabled} check is required to keep this resolver's semantics aligned with
   * {@link ConditionalOnWebappUiEnabled}: a deployment that disables a component's UI but leaves
   * its backend enabled must not be reported here as having an active UI component.
   */
  public List<String> resolve() {
    return KNOWN_COMPONENTS.stream().filter(this::isEnabled).toList();
  }

  private boolean isEnabled(final String name) {
    final boolean legacyEnabled =
        environment.getProperty("camunda." + name + ".webapp-enabled", Boolean.class, true);
    final boolean unifiedEnabled =
        environment.getProperty("camunda.webapps." + name + ".enabled", Boolean.class, true);
    final boolean uiEnabled =
        environment.getProperty("camunda.webapps." + name + ".ui-enabled", Boolean.class, true);
    return legacyEnabled && unifiedEnabled && uiEnabled;
  }
}
