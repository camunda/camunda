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
import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;

class WebappsConfigurationInitializerTest {

  private static final String CAMUNDA_WEBAPPS_ENABLED = "camunda.webapps.enabled";
  private static final String STATIC_LOCATIONS = "spring.web.resources.static-locations";
  private static final String DEFAULT_APP = "camunda.webapps.default-app";
  private static final String WEBAPP_LOCATION = "classpath:/META-INF/resources/webapp/";
  private static final String TASKLIST_LOCATION = "classpath:/META-INF/resources/tasklist/";

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
  void shouldAddWebappStaticLocationWhenTmpWebappProfileIsActive() {
    // given a context with only the tmp-webapp profile
    final GenericApplicationContext context = new GenericApplicationContext();
    context.getEnvironment().setActiveProfiles(Profile.TMP_WEBAPP.getId());

    // when the initializer runs
    new WebappsConfigurationInitializer().initialize(context);

    // then webapps is enabled and the webapp/ static location is registered
    assertThat(context.getEnvironment().getProperty(CAMUNDA_WEBAPPS_ENABLED, Boolean.class))
        .isTrue();
    assertThat(context.getEnvironment().getProperty(STATIC_LOCATIONS, String.class))
        .contains(WEBAPP_LOCATION);
    // and tmp-webapp is deliberately NOT set as the default app (no / redirect fallback)
    assertThat(context.getEnvironment().getProperty(DEFAULT_APP)).isNull();
  }

  @Test
  void shouldNotMakeTmpWebappTheDefaultWhenLegacyProfileIsAlsoActive() {
    // given a context with both tasklist and tmp-webapp profiles active
    final GenericApplicationContext context = new GenericApplicationContext();
    context
        .getEnvironment()
        .setActiveProfiles(Profile.TASKLIST.getId(), Profile.TMP_WEBAPP.getId());

    // when the initializer runs
    new WebappsConfigurationInitializer().initialize(context);

    // then both static locations are present
    final String locations = context.getEnvironment().getProperty(STATIC_LOCATIONS, String.class);
    assertThat(locations).contains(TASKLIST_LOCATION).contains(WEBAPP_LOCATION);
    // and tasklist wins the default-app contest, not tmp-webapp
    assertThat(context.getEnvironment().getProperty(DEFAULT_APP))
        .isEqualTo(Profile.TASKLIST.getId());
  }
}
