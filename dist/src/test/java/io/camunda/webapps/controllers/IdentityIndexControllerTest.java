/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import io.camunda.identity.webapp.controllers.IdentityIndexController;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.security.entity.AuthenticationMethod;
import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.ui.ExtendedModelMap;

class IdentityIndexControllerTest {

  private static final HttpServletResponse RESPONSE = mock(HttpServletResponse.class);
  private SecurityConfiguration securityConfiguration;
  private IdentityIndexController controller;

  @BeforeEach
  void setUp() {
    securityConfiguration = new SecurityConfiguration();
    final var context = mock(ServletContext.class);
    controller = new IdentityIndexController(context, null, securityConfiguration);
    when(context.getContextPath()).thenReturn("");
  }

  @ParameterizedTest
  @MethodSource("provideSecurityConfig")
  void viteIsOidcShouldBeTrueIfAuthenticationMethodIsOidc(
      final Consumer<SecurityConfiguration> consumer, final String entry, final String value)
      throws IOException {
    // given
    consumer.accept(securityConfiguration);
    final var model = new ExtendedModelMap();

    // when
    controller.identity(model, RESPONSE);

    // then
    assertThat(model.getAttribute("clientConfig"))
        .isInstanceOf(Map.class)
        .extracting(m -> (Map<String, String>) m)
        .satisfies(m -> assertThat(m.get(entry)).isEqualTo(value));
  }

  private static Stream<Arguments> provideSecurityConfig() {
    return Stream.of(
        Arguments.of(
            (Consumer<SecurityConfiguration>)
                sc -> sc.getAuthentication().setMethod(AuthenticationMethod.OIDC),
            "VITE_IS_OIDC",
            "true"),
        Arguments.of(
            (Consumer<SecurityConfiguration>)
                sc -> sc.getAuthentication().setMethod(AuthenticationMethod.BASIC),
            "VITE_IS_OIDC",
            "false"),
        Arguments.of(
            (Consumer<SecurityConfiguration>) sc -> sc.getAuthentication().setOidc(null),
            "VITE_INTERNAL_GROUPS_ENABLED",
            "true"),
        Arguments.of(
            (Consumer<SecurityConfiguration>)
                sc -> sc.getAuthentication().getOidc().setGroupsClaim(null),
            "VITE_INTERNAL_GROUPS_ENABLED",
            "true"),
        Arguments.of(
            (Consumer<SecurityConfiguration>)
                sc -> sc.getAuthentication().getOidc().setGroupsClaim("claim"),
            "VITE_INTERNAL_GROUPS_ENABLED",
            "false"),
        Arguments.of(
            (Consumer<SecurityConfiguration>) sc -> sc.getMultiTenancy().setApiEnabled(true),
            "VITE_TENANTS_API_ENABLED",
            "true"),
        Arguments.of(
            (Consumer<SecurityConfiguration>) sc -> sc.getMultiTenancy().setApiEnabled(false),
            "VITE_TENANTS_API_ENABLED",
            "false"));
  }
}
