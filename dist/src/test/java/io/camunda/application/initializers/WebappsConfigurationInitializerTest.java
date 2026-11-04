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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.MapPropertySource;

class WebappsConfigurationInitializerTest {

  private static final String OPERATE_TASKLIST_URL = "camunda.operate.tasklist-url";

  @ParameterizedTest
  @CsvSource({
    "/ctx, /ctx/tasklist",
    "/ctx/, /ctx/tasklist",
    "' /ctx/ ', /ctx/tasklist",
    "/, /tasklist",
    "'', /tasklist"
  })
  void shouldDefaultOperateTasklistUrlRelativeToContextPath(
      final String contextPath, final String expectedTasklistUrl) {
    // given
    final GenericApplicationContext context = new GenericApplicationContext();
    context.getEnvironment().setActiveProfiles(Profile.OPERATE.getId(), Profile.TASKLIST.getId());
    context
        .getEnvironment()
        .getPropertySources()
        .addFirst(
            new MapPropertySource("test", Map.of("server.servlet.context-path", contextPath)));

    // when
    new WebappsConfigurationInitializer().initialize(context);

    // then
    assertThat(context.getEnvironment().getProperty(OPERATE_TASKLIST_URL))
        .isEqualTo(expectedTasklistUrl);
  }

  @Test
  void shouldDefaultOperateTasklistUrlWithoutContextPath() {
    // given
    final GenericApplicationContext context = new GenericApplicationContext();
    context.getEnvironment().setActiveProfiles(Profile.OPERATE.getId(), Profile.TASKLIST.getId());

    // when
    new WebappsConfigurationInitializer().initialize(context);

    // then
    assertThat(context.getEnvironment().getProperty(OPERATE_TASKLIST_URL)).isEqualTo("/tasklist");
  }

  @Test
  void shouldResolvePlaceholderInContextPath() {
    // given
    final GenericApplicationContext context = new GenericApplicationContext();
    context.getEnvironment().setActiveProfiles(Profile.OPERATE.getId(), Profile.TASKLIST.getId());
    context
        .getEnvironment()
        .getPropertySources()
        .addFirst(
            new MapPropertySource(
                "test",
                Map.of(
                    "server.servlet.context-path",
                    "${test.context-path}",
                    "test.context-path",
                    "/ctx")));

    // when
    new WebappsConfigurationInitializer().initialize(context);

    // then
    assertThat(context.getEnvironment().getProperty(OPERATE_TASKLIST_URL))
        .isEqualTo("/ctx/tasklist");
  }

  @ParameterizedTest
  @CsvSource({"'', https://tasklist.test", "/, /custom/tasklist"})
  void shouldKeepExplicitOperateTasklistUrl(final String contextPath, final String explicitUrl) {
    // given
    final GenericApplicationContext context = new GenericApplicationContext();
    context.getEnvironment().setActiveProfiles(Profile.OPERATE.getId(), Profile.TASKLIST.getId());
    context
        .getEnvironment()
        .getPropertySources()
        .addFirst(
            new MapPropertySource(
                "test",
                Map.of(
                    "server.servlet.context-path",
                    contextPath,
                    OPERATE_TASKLIST_URL,
                    explicitUrl)));

    // when
    new WebappsConfigurationInitializer().initialize(context);

    // then
    assertThat(context.getEnvironment().getProperty(OPERATE_TASKLIST_URL)).isEqualTo(explicitUrl);
  }

  @Test
  void shouldNotDefaultOperateTasklistUrlWithoutTasklist() {
    // given
    final GenericApplicationContext context = new GenericApplicationContext();
    context.getEnvironment().setActiveProfiles(Profile.OPERATE.getId());

    // when
    new WebappsConfigurationInitializer().initialize(context);

    // then
    assertThat(context.getEnvironment().getProperty(OPERATE_TASKLIST_URL)).isNull();
  }
}
