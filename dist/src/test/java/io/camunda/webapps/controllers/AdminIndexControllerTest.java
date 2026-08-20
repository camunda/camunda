/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.camunda.identity.webapp.controllers.AdminIndexController;
import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ExtendedModelMap;

@ExtendWith(MockitoExtension.class)
public class AdminIndexControllerTest {
  @Mock private ServletContext servletContext;
  @Mock private WebappsRequestForwardManager webappsRequestForwardManager;
  @Mock private HttpServletRequest request;

  private AdminIndexController controller;

  @BeforeEach
  void setUp() {
    controller = new AdminIndexController(servletContext, webappsRequestForwardManager);
  }

  @Test
  void shouldReturnAdminIndexView() throws IOException {
    // Given
    when(servletContext.getContextPath()).thenReturn("/camunda");
    final var model = new ExtendedModelMap();

    // When
    final var viewName = controller.admin(model);

    // Then
    assertThat(viewName).isEqualTo("admin/index");
    assertThat(model.getAttribute("contextPath")).isEqualTo("/camunda/admin/");
  }

  @Test
  void shouldForwardToAdminForRootPath() {
    // Given
    when(webappsRequestForwardManager.forward(any(HttpServletRequest.class), eq("admin")))
        .thenReturn("forward:/admin/app");

    // When
    final String result = controller.forwardToAdmin(request);

    // Then
    assertThat(result).isEqualTo("forward:/admin/app");
  }
}
