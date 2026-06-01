/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.identity;

import java.util.List;
import java.util.Map;
import org.apache.commons.logging.Log;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.logging.DeferredLogFactory;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Bridges the legacy persistent-web-session enable-properties onto the canonical CSL property
 * {@code camunda.security.session.persistent.enabled}, so existing OC deployments keep working
 * after the session wiring moved into CSL.
 *
 * <p>If the canonical property is not already set and any legacy key is present, the canonical
 * property is set to {@code true} when <em>any</em> legacy key is {@code true} (matching the
 * previous {@code ConditionalOnPersistentWebSessionEnabled} semantics), and a deprecation warning
 * is logged.
 */
public class PersistentWebSessionPropertiesPostProcessor implements EnvironmentPostProcessor {

  static final String CANONICAL_PROPERTY = "camunda.security.session.persistent.enabled";
  static final List<String> LEGACY_PROPERTIES =
      List.of(
          "camunda.persistent.sessions.enabled",
          "camunda.tasklist.persistent.sessions.enabled",
          "camunda.tasklist.persistentSessionsEnabled",
          "camunda.operate.persistent.sessions.enabled",
          "camunda.operate.persistentSessionsEnabled");

  private final Log log;

  public PersistentWebSessionPropertiesPostProcessor(final DeferredLogFactory deferredLogFactory) {
    log = deferredLogFactory.getLog(getClass());
  }

  @Override
  public void postProcessEnvironment(
      final ConfigurableEnvironment environment, final SpringApplication application) {
    if (environment.containsProperty(CANONICAL_PROPERTY)) {
      return;
    }
    final var presentLegacyKeys =
        LEGACY_PROPERTIES.stream().filter(environment::containsProperty).toList();
    if (presentLegacyKeys.isEmpty()) {
      return;
    }
    final boolean enabled =
        presentLegacyKeys.stream().map(environment::getProperty).anyMatch(Boolean::parseBoolean);
    log.warn(
        String.format(
            "Legacy persistent web session %s set; mapping to '%s=%s'. Please migrate to '%s'.",
            presentLegacyKeys, CANONICAL_PROPERTY, enabled, CANONICAL_PROPERTY));
    environment
        .getPropertySources()
        .addFirst(
            new MapPropertySource(
                "persistent-web-session-legacy-mapping",
                Map.of(CANONICAL_PROPERTY, String.valueOf(enabled))));
  }
}
