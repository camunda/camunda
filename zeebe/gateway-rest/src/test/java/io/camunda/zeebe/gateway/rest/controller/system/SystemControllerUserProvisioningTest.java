/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.system;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.camunda.security.api.context.CamundaAuthenticationProvider;
import io.camunda.security.spring.CamundaSecurityLibraryProperties;
import io.camunda.service.registry.ServiceRegistry;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import io.camunda.zeebe.gateway.rest.config.GatewayRestConfiguration;
import io.camunda.zeebe.gateway.rest.config.PhysicalTenantRestConfigProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;

@WebMvcTest(SystemController.class)
@Import(SystemControllerTestConfiguration.class)
@TestPropertySource(properties = "camunda.scim.enabled=true")
class SystemControllerUserProvisioningTest extends RestControllerTest {

  @MockitoBean CamundaAuthenticationProvider authenticationProvider;
  @MockitoBean CamundaSecurityLibraryProperties cslProperties;
  @MockitoBean ServiceRegistry serviceRegistry;
  @MockitoBean PhysicalTenantRestConfigProvider tenantRestConfigProvider;

  @BeforeEach
  void setup() {
    when(tenantRestConfigProvider.forPhysicalTenant(any()))
        .thenReturn(new GatewayRestConfiguration());
  }

  @Test
  void shouldReturnUserProvisioningEnabledWhenScimIsEnabled() {
    // when/then
    webClient
        .get()
        .uri("/v2/system/configuration")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .json(
            """
            { "authentication": { "isUserProvisioningEnabled": true } }
            """,
            JsonCompareMode.LENIENT);
  }
}
