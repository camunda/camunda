/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.system;

import static org.mockito.Mockito.when;

import io.camunda.security.api.context.CamundaAuthenticationProvider;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.service.UsageMetricsServices;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import io.camunda.zeebe.gateway.rest.config.GatewayRestConfiguration;
import io.camunda.zeebe.gateway.rest.config.GatewayRestConfiguration.JobMetricsConfiguration;
import jakarta.servlet.ServletContext;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;

/**
 * Tests that verify backward-compatible property resolution: the unified key takes precedence over
 * legacy per-app keys, and when the unified key is absent the legacy keys are used as fallbacks.
 */
@WebMvcTest(SystemController.class)
@Import(SystemControllerLegacyPropertyTest.LegacyPropertyTestConfig.class)
public class SystemControllerLegacyPropertyTest extends RestControllerTest {

  static final String SYSTEM_CONFIGURATION_URL = "/v2/system/configuration";

  @MockitoBean UsageMetricsServices usageMetricsServices;
  @MockitoBean CamundaAuthenticationProvider authenticationProvider;
  @MockitoBean GatewayRestConfiguration gatewayRestConfiguration;
  @MockitoBean SecurityConfiguration securityConfiguration;
  @MockitoBean ServletContext servletContext;

  @Test
  void shouldFallbackToOperateEnterpriseWhenUnifiedKeyNotSet() {
    // given: legacy Operate/Tasklist keys set, unified key absent
    when(gatewayRestConfiguration.getJobMetrics()).thenReturn(new JobMetricsConfiguration());
    when(servletContext.getContextPath()).thenReturn("");

    // when/then
    webClient
        .get()
        .uri(SYSTEM_CONFIGURATION_URL)
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .json(
            """
            {
              "deployment": {
                "isEnterprise": true
              },
              "cloud": {
                "stage": "test-stage",
                "mixpanelToken": "test-token",
                "mixpanelAPIHost": "test-host"
              }
            }
            """,
            JsonCompareMode.LENIENT);
  }

  @TestConfiguration
  static class LegacyPropertyTestConfig {

    @Bean
    @Primary
    public Environment testEnvironment() {
      final MockEnvironment env = new MockEnvironment();

      // camunda.webapp.enterprise deliberately absent — triggers fallback to Operate key
      env.setProperty("camunda.operate.enterprise", "true");

      // Unified cloud keys absent — fallback to Operate / Tasklist legacy keys
      env.setProperty("camunda.tasklist.cloud.stage", "test-stage");
      env.setProperty("camunda.operate.cloud.mixpanelToken", "test-token");
      env.setProperty("camunda.operate.cloud.mixpanelAPIHost", "test-host");

      // Required configuration
      env.setProperty("camunda.webapps.login-delegated", "false");
      env.setProperty("spring.servlet.multipart.max-request-size", "4MB");

      // Components all enabled
      env.setProperty("camunda.admin.webapp-enabled", "true");
      env.setProperty("camunda.webapps.admin.enabled", "true");
      env.setProperty("camunda.webapps.admin.ui-enabled", "true");
      env.setProperty("camunda.operate.webapp-enabled", "true");
      env.setProperty("camunda.webapps.operate.enabled", "true");
      env.setProperty("camunda.webapps.operate.ui-enabled", "true");
      env.setProperty("camunda.tasklist.webapp-enabled", "true");
      env.setProperty("camunda.webapps.tasklist.enabled", "true");
      env.setProperty("camunda.webapps.tasklist.ui-enabled", "true");

      return env;
    }
  }
}
