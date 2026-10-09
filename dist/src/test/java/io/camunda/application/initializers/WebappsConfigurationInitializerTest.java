/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.initializers;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.application.Profile;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.MapPropertySource;

class WebappsConfigurationInitializerTest {

  private static final String CAMUNDA_WEBAPPS_ENABLED = "camunda.webapps.enabled";
  private static final String STATIC_LOCATIONS = "spring.web.resources.static-locations";
  private static final String DEFAULT_APP = "camunda.webapps.default-app";
  private static final String WEBAPP_LOCATION = "classpath:/META-INF/resources/webapp/";

  @Test
  void shouldNotActivateWebappsWhenNoWebappsProfileIsActive() {
    // given a context with only a non-webapps profile (broker)
    final GenericApplicationContext context = new GenericApplicationContext();
    context.getEnvironment().setActiveProfiles(Profile.BROKER.getId());

    // when the initializer runs
    new WebappsConfigurationInitializer().initialize(context);

    // then webapps is not enabled and the webapp/ static location is not added
    assertThat(context.getEnvironment().getProperty(CAMUNDA_WEBAPPS_ENABLED)).isNull();
    assertThat(context.getEnvironment().getProperty(STATIC_LOCATIONS, String.class))
        .doesNotContain(WEBAPP_LOCATION);
  }

  @Test
  void shouldAddWebappStaticLocationWhenTasklistProfileIsActive() {
    // given a context with the tasklist profile
    final GenericApplicationContext context = new GenericApplicationContext();
    context.getEnvironment().setActiveProfiles(Profile.TASKLIST.getId());

    // when the initializer runs
    new WebappsConfigurationInitializer().initialize(context);

    // then webapps is enabled and the webapp/ static location is registered
    assertThat(context.getEnvironment().getProperty(CAMUNDA_WEBAPPS_ENABLED, Boolean.class))
        .isTrue();
    assertThat(context.getEnvironment().getProperty(STATIC_LOCATIONS, String.class))
        .contains(WEBAPP_LOCATION);
    // and tasklist remains the default app
    assertThat(context.getEnvironment().getProperty(DEFAULT_APP))
        .isEqualTo(Profile.TASKLIST.getId());
  }

  @Test
  void shouldUseOperateAsDefaultAppWhenOperateAndTasklistProfilesAreActive() {
    // given
    final GenericApplicationContext context = new GenericApplicationContext();
    context.getEnvironment().setActiveProfiles(Profile.OPERATE.getId(), Profile.TASKLIST.getId());

    // when
    new WebappsConfigurationInitializer().initialize(context);

    // then
    assertThat(context.getEnvironment().getProperty(DEFAULT_APP))
        .isEqualTo(Profile.OPERATE.getId());
  }

  @Test
  void shouldNotAddWebappStaticLocationWhenTasklistUiIsDisabled() {
    final GenericApplicationContext context = new GenericApplicationContext();
    context.getEnvironment().setActiveProfiles(Profile.TASKLIST.getId());
    context
        .getEnvironment()
        .getPropertySources()
        .addFirst(
            new MapPropertySource("test", Map.of("camunda.webapps.tasklist.ui-enabled", false)));

    new WebappsConfigurationInitializer().initialize(context);

    assertThat(context.getEnvironment().getProperty(STATIC_LOCATIONS, String.class))
        .doesNotContain(WEBAPP_LOCATION);
    assertThat(context.getEnvironment().getProperty(DEFAULT_APP)).isNull();
  }

  @Test
  void shouldNotFallBackToLegacyTasklistSecurityPropertiesInStandaloneMode() {
    final GenericApplicationContext context = new GenericApplicationContext();
    context
        .getEnvironment()
        .setActiveProfiles(Profile.TASKLIST.getId(), Profile.STANDALONE.getId());

    new WebappsConfigurationInitializer().initialize(context);

    assertThat(context.getEnvironment().getProperty("camunda.security.authorizations.enabled"))
        .isNull();
    assertThat(context.getEnvironment().getProperty("camunda.security.multiTenancy.checksEnabled"))
        .isNull();
  }
}
