/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.camunda.identity.webapp.controllers.IdentityIndexController;
import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IdentityIndexControllerTest {

  @Mock private ServletContext servletContext;
  @Mock private WebappsRequestForwardManager webappsRequestForwardManager;
  @Mock private HttpServletRequest request;

  private IdentityIndexController controller;

  @BeforeEach
  void setUp() {
    controller = new IdentityIndexController();
  }

  @Test
  void shouldRedirectIdentityRootToAdmin() {
    // Given
    when(request.getContextPath()).thenReturn("");
    when(request.getRequestURI()).thenReturn("/identity");
    when(request.getQueryString()).thenReturn(null);

    // When
    final var result = controller.redirectIdentityRoot(request);

    // Then
    assertThat(result).isEqualTo("redirect:/admin");
  }

  @Test
  void shouldRedirectIdentityRootWithTrailingSlashToAdmin() {
    // Given
    when(request.getContextPath()).thenReturn("");
    when(request.getRequestURI()).thenReturn("/identity/");
    when(request.getQueryString()).thenReturn(null);

    // When
    final var result = controller.redirectIdentityRoot(request);

    // Then
    assertThat(result).isEqualTo("redirect:/admin/");
  }

  @Test
  void shouldRedirectIdentityRoutesToAdmin() {
    // Given
    when(request.getContextPath()).thenReturn("");
    when(request.getRequestURI()).thenReturn("/identity/users");
    when(request.getQueryString()).thenReturn(null);

    // When
    final var result = controller.redirectIdentityRoutes(request);

    // Then
    assertThat(result).isEqualTo("redirect:/admin/users");
  }

  @Test
  void shouldRedirectIdentityRoutesWithQueryParams() {
    // Given
    when(request.getContextPath()).thenReturn("");
    when(request.getRequestURI()).thenReturn("/identity/users");
    when(request.getQueryString()).thenReturn("filter=active");

    // When
    final var result = controller.redirectIdentityRoutes(request);

    // Then
    assertThat(result).isEqualTo("redirect:/admin/users?filter=active");
  }

  @Test
  void shouldRedirectIdentityNestedRoutesToAdmin() {
    // Given
    when(request.getContextPath()).thenReturn("");
    when(request.getRequestURI()).thenReturn("/identity/users/123");
    when(request.getQueryString()).thenReturn(null);

    // When
    final var result = controller.redirectIdentityRoutes(request);

    // Then
    assertThat(result).isEqualTo("redirect:/admin/users/123");
  }

  @Test
  void shouldRedirectWithContextPath() {
    // Given
    when(request.getContextPath()).thenReturn("/camunda");
    when(request.getRequestURI()).thenReturn("/camunda/identity/users");
    when(request.getQueryString()).thenReturn(null);

    // When
    final var result = controller.redirectIdentityRoutes(request);

    // Then
    assertThat(result).isEqualTo("redirect:/admin/users");
  }
}
