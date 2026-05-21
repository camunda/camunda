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
import io.camunda.zeebe.gateway.rest.config.WebappConfiguration;
import jakarta.servlet.ServletContext;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;

/**
 * Tests that the controller correctly reads enterprise and cloud config from the injected
 * WebappConfiguration bean. Legacy property fallback (camunda.operate.* / camunda.tasklist.*) is
 * now handled by WebappPropertiesOverride in the configuration module; the controller itself simply
 * reads the already-resolved WebappConfiguration.
 */
@WebMvcTest(SystemController.class)
@Import(SystemControllerLegacyPropertyTest.WebappConfigTestConfig.class)
public class SystemControllerLegacyPropertyTest extends RestControllerTest {

  static final String SYSTEM_CONFIGURATION_URL = "/v2/system/configuration";

  @MockitoBean UsageMetricsServices usageMetricsServices;
  @MockitoBean CamundaAuthenticationProvider authenticationProvider;
  @MockitoBean GatewayRestConfiguration gatewayRestConfiguration;
  @MockitoBean SecurityConfiguration securityConfiguration;
  @MockitoBean ServletContext servletContext;

  @Test
  void shouldUseEnterpriseAndCloudValuesFromWebappConfiguration() {
    // given: WebappConfiguration already resolved (translation from legacy keys done by
    // WebappPropertiesOverride at runtime; here we supply the resolved values directly)
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
                "stage": "dev",
                "mixpanelToken": "test-token",
                "mixpanelAPIHost": "test-host"
              }
            }
            """,
            JsonCompareMode.LENIENT);
  }

  @TestConfiguration
  static class WebappConfigTestConfig {

    @Bean
    @Primary
    public WebappConfiguration testWebappConfiguration() {
      final WebappConfiguration config = new WebappConfiguration();
      config.setEnterprise(true);
      final WebappConfiguration.Cloud cloud = config.getCloud();
      cloud.setStage("dev");
      cloud.setMixpanelToken("test-token");
      cloud.setMixpanelApiHost("test-host");
      return config;
    }
  }
}
