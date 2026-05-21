/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapp.controllers;

import static io.camunda.webapps.util.HttpUtils.REQUESTED_URL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.security.api.model.config.SaasConfiguration;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.zeebe.gateway.rest.config.WebappConfiguration;
import io.camunda.zeebe.gateway.rest.config.WebappConfiguration.Cloud;
import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.ExtendedModelMap;

@ExtendWith(MockitoExtension.class)
class WebappIndexControllerTest {

  @Mock private ServletContext servletContext;
  @Mock private HttpServletRequest request;

  @Test
  void shouldReturnWebappIndexView() {
    // given
    when(servletContext.getContextPath()).thenReturn("/camunda");
    final WebappConfiguration config = new WebappConfiguration();
    final WebappIndexController controller =
        new WebappIndexController(servletContext, config, null);
    final ExtendedModelMap model = new ExtendedModelMap();

    // when
    final String viewName = controller.webapp(model);

    // then
    assertThat(viewName).isEqualTo("webapp/index");
    assertThat(model.getAttribute("baseName")).isEqualTo("/camunda/webapp/");
    assertThat(model.getAttribute("contextPath")).isEqualTo("/camunda");
    assertThat(model.getAttribute("isEnterprise")).isEqualTo(false);
    assertThat(model.getAttribute("mixpanelToken")).isEqualTo("");
    assertThat(model.getAttribute("mixpanelApiHost")).isEqualTo("");
    assertThat(model.getAttribute("organizationId")).isEqualTo("");
    assertThat(model.getAttribute("clusterId")).isEqualTo("");
  }

  @Test
  void shouldForwardToWebappWhenLoginNotDelegated() {
    // given
    final WebappConfiguration config = new WebappConfiguration();
    final WebappIndexController controller =
        new WebappIndexController(servletContext, config, null);

    // when
    final String result = controller.forwardToWebapp(request);

    // then no auth check is performed; result is unconditional forward to /webapp
    assertThat(result).isEqualTo("forward:/webapp");
  }

  @Test
  void shouldForwardToWebappWhenLoginDelegatedAndUserAuthenticated() {
    // given
    final WebappConfiguration config = new WebappConfiguration();
    config.setLoginDelegated(true);
    final WebappIndexController controller =
        new WebappIndexController(servletContext, config, null);
    final Authentication authentication = mock(Authentication.class);
    when(authentication.isAuthenticated()).thenReturn(true);

    try (final MockedStatic<SecurityContextHolder> mocked =
        mockStatic(SecurityContextHolder.class)) {
      final SecurityContext securityContext = mock(SecurityContext.class);
      when(securityContext.getAuthentication()).thenReturn(authentication);
      mocked.when(SecurityContextHolder::getContext).thenReturn(securityContext);

      // when
      final String result = controller.forwardToWebapp(request);

      // then
      assertThat(result).isEqualTo("forward:/webapp");
    }
  }

  @Test
  void shouldForwardToLoginAndStashUrlWhenLoginDelegatedAndUserUnauthenticated() {
    // given
    final WebappConfiguration config = new WebappConfiguration();
    config.setLoginDelegated(true);
    final WebappIndexController controller =
        new WebappIndexController(servletContext, config, null);
    final HttpSession session = mock(HttpSession.class);
    final AnonymousAuthenticationToken anonymous = mock(AnonymousAuthenticationToken.class);

    when(request.getRequestURI()).thenReturn("/webapp/foo/bar");
    when(request.getContextPath()).thenReturn("");
    when(request.getSession(true)).thenReturn(session);

    try (final MockedStatic<SecurityContextHolder> mocked =
        mockStatic(SecurityContextHolder.class)) {
      final SecurityContext securityContext = mock(SecurityContext.class);
      when(securityContext.getAuthentication()).thenReturn(anonymous);
      mocked.when(SecurityContextHolder::getContext).thenReturn(securityContext);

      // when
      final String result = controller.forwardToWebapp(request);

      // then the URL is stashed in the session and the request is forwarded to /login
      assertThat(result).isEqualTo("forward:/login");
      verify(session).setAttribute(REQUESTED_URL, "/webapp/foo/bar");
    }
  }

  @Test
  void shouldExposeEnterpriseFlagWhenEnabled() {
    // given
    when(servletContext.getContextPath()).thenReturn("");
    final WebappConfiguration config = new WebappConfiguration();
    config.setEnterprise(true);
    final WebappIndexController controller =
        new WebappIndexController(servletContext, config, null);
    final ExtendedModelMap model = new ExtendedModelMap();

    // when
    controller.webapp(model);

    // then
    assertThat(model.getAttribute("isEnterprise")).isEqualTo(true);
    assertThat(model.getAttribute("baseName")).isEqualTo("/webapp/");
    assertThat(model.getAttribute("contextPath")).isEqualTo("");
  }

  @Test
  void shouldExposeMixpanelConfig() {
    // given
    when(servletContext.getContextPath()).thenReturn("");
    final Cloud cloud = new Cloud();
    cloud.setMixpanelToken("test-token");
    cloud.setMixpanelApiHost("https://api-eu.mixpanel.com");
    final WebappConfiguration config = new WebappConfiguration();
    config.setCloud(cloud);
    final WebappIndexController controller =
        new WebappIndexController(servletContext, config, null);
    final ExtendedModelMap model = new ExtendedModelMap();

    // when
    controller.webapp(model);

    // then
    assertThat(model.getAttribute("mixpanelToken")).isEqualTo("test-token");
    assertThat(model.getAttribute("mixpanelApiHost")).isEqualTo("https://api-eu.mixpanel.com");
  }

  @Test
  void shouldUseDefaultConfigWhenWebappConfigurationIsNull() {
    // given
    when(servletContext.getContextPath()).thenReturn("");
    final WebappIndexController controller = new WebappIndexController(servletContext, null, null);
    final ExtendedModelMap model = new ExtendedModelMap();

    // when
    final String viewName = controller.webapp(model);

    // then — falls back to default WebappConfiguration values
    assertThat(viewName).isEqualTo("webapp/index");
    assertThat(model.getAttribute("isEnterprise")).isEqualTo(false);
    assertThat(model.getAttribute("mixpanelToken")).isEqualTo("");
    assertThat(model.getAttribute("mixpanelApiHost")).isEqualTo("");
    assertThat(model.getAttribute("baseName")).isEqualTo("/webapp/");
    assertThat(model.getAttribute("contextPath")).isEqualTo("");
  }

  @Test
  void shouldExposeCloudIdentifiers() {
    // given
    when(servletContext.getContextPath()).thenReturn("");
    final WebappConfiguration config = new WebappConfiguration();
    final SecurityConfiguration securityConfig = new SecurityConfiguration();
    final SaasConfiguration saasConfig = new SaasConfiguration();
    saasConfig.setOrganizationId("org-123");
    saasConfig.setClusterId("cluster-456");
    securityConfig.setSaas(saasConfig);
    final WebappIndexController controller =
        new WebappIndexController(servletContext, config, securityConfig);
    final ExtendedModelMap model = new ExtendedModelMap();

    // when
    controller.webapp(model);

    // then
    assertThat(model.getAttribute("organizationId")).isEqualTo("org-123");
    assertThat(model.getAttribute("clusterId")).isEqualTo("cluster-456");
  }
}
