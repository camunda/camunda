/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.controllers;

import static com.google.common.net.HttpHeaders.CONTENT_SECURITY_POLICY;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import io.camunda.identity.webapp.controllers.IdentityIndexController;
import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletPath;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
    controllers = IdentityIndexController.class,
    excludeAutoConfiguration = {
      org.springframework.boot.autoconfigure.thymeleaf.ThymeleafAutoConfiguration.class
    })
@Import(IdentityIndexControllerTest.TestConfig.class)
class IdentityIndexControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private ServletContext servletContext;

  @MockitoBean private WebappsRequestForwardManager webappsRequestForwardManager;

  @Test
  void shouldReturnIdentityIndexView() throws Exception {
    // Given
    when(servletContext.getContextPath()).thenReturn("/camunda");

    // When & Then
    mockMvc
        .perform(get("/identity"))
        .andExpect(status().isOk())
        .andExpect(view().name("identity/index"))
        .andExpect(model().attribute("contextPath", "/camunda/identity/"))
        .andExpect(header().doesNotExist(CONTENT_SECURITY_POLICY));
  }

  @Test
  void shouldForwardToIdentityForRootPath() throws Exception {
    // Given
    when(webappsRequestForwardManager.forward(any(HttpServletRequest.class), eq("identity")))
        .thenReturn("forward:/identity/app");

    // When & Then
    mockMvc
        .perform(get("/identity/"))
        .andExpect(status().isOk())
        .andExpect(header().doesNotExist(CONTENT_SECURITY_POLICY));
  }

  @SpringBootApplication
  static class TestApplication {
    @Bean
    public IdentityIndexController identityIndexController(
        final ServletContext servletContext,
        final WebappsRequestForwardManager webappsRequestForwardManager) {
      return new IdentityIndexController(servletContext, webappsRequestForwardManager);
    }
  }

  @TestConfiguration
  static class TestConfig {
    @Bean
    public DispatcherServletPath dispatcherServletPath() {
      return () -> "";
    }
  }
}
