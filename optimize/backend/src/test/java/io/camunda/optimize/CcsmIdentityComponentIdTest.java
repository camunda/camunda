/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.identity.autoconfigure.IdentityProperties;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.PropertiesPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.core.io.ClassPathResource;

/**
 * Optimize tells Management Identity which component it is, so that Identity returns only the
 * permissions Optimize's own resource server defines rather than every permission on the audience
 * it asked with. Identity treats a missing component as a caller that named none and silently
 * returns everything, so a default that fails to reach the SDK leaves the permission leak open with
 * nothing to show for it.
 *
 * <p>The default therefore cannot live in {@code application-ccsm.yaml}. {@code
 * optimize-startup.sh} puts {@code ${BASEDIR}/config} ahead of the jar on the classpath, and the
 * Helm chart mounts its own {@code application-ccsm.yaml} there, which would hide the packaged one
 * outright. These tests layer that chart file over the packaged defaults to show the value survives
 * it.
 */
class CcsmIdentityComponentIdTest {

  /** The key of Optimize's entry under Identity's {@code component-presets}. */
  private static final String PRESET_KEY = "optimize";

  /**
   * What the chart writes to {@code /optimize/config/application-ccsm.yaml} with auth enabled: the
   * same prefix, and deliberately no {@code componentId}.
   */
  private static final Map<String, Object> CHART_CCSM_FILE =
      Map.of(
          "camunda.identity.clientId", "optimize",
          "camunda.identity.audience", "optimize-api",
          "camunda.identity.issuer", "http://keycloak/realms/camunda",
          "camunda.identity.issuerBackendUrl", "http://keycloak/realms/camunda");

  private static IdentityProperties identityPropertiesWith(final Map<String, Object> overrides)
      throws IOException {
    final List<PropertySource<?>> packaged =
        new PropertiesPropertySourceLoader()
            .load("application", new ClassPathResource("application.properties"));

    final StandardEnvironment environment = new StandardEnvironment();
    packaged.forEach(source -> environment.getPropertySources().addLast(source));

    // Ahead of the packaged defaults, the way the mounted file is ahead of the jar.
    environment.getPropertySources().addFirst(new MapPropertySource("chart", CHART_CCSM_FILE));
    if (!overrides.isEmpty()) {
      // A system-environment source, not a plain map: the SCREAMING_SNAKE_CASE spelling an
      // operator uses is only relaxed onto a property name by this source type.
      environment
          .getPropertySources()
          .addFirst(
              new SystemEnvironmentPropertySource(
                  StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, overrides));
    }
    ConfigurationPropertySources.attach(environment);

    return Binder.get(environment).bind("camunda.identity", IdentityProperties.class).get();
  }

  @Test
  void survivesTheConfigFileTheChartMountsOverTheProfile() throws IOException {
    assertThat(identityPropertiesWith(Map.of()).componentId()).isEqualTo(PRESET_KEY);
  }

  @Test
  void letsADeploymentOverrideIt() throws IOException {
    // The relaxed-binding spelling, which is what an operator setting this reaches for.
    final IdentityProperties identity =
        identityPropertiesWith(Map.of("CAMUNDA_IDENTITY_COMPONENTID", "elsewhere"));

    assertThat(identity.componentId()).isEqualTo("elsewhere");
  }
}
