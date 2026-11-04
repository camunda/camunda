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
import io.camunda.security.api.model.config.AuthenticationConfiguration;
import io.camunda.security.api.model.config.AuthenticationMethod;
import io.camunda.security.spring.CamundaSecurityLibraryProperties;
import io.camunda.service.registry.ServiceRegistry;
import io.camunda.spring.utils.PhysicalTenantContext;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import io.camunda.zeebe.gateway.rest.config.GatewayRestConfiguration;
import io.camunda.zeebe.gateway.rest.config.PhysicalTenantRestConfigProvider;
import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;

/**
 * {@code isCamundaGroupsEnabled} comes from the configuration of the request's physical tenant. The
 * cluster (the {@code default} tenant) has no groups claim, and {@code tenanta} sets one.
 */
@WebMvcTest(SystemController.class)
@Import({
  SystemControllerTestConfiguration.class,
  SystemControllerPhysicalTenantGroupsTest.PhysicalTenantHeaderFilter.class
})
@TestPropertySource(
    properties = {
      "camunda.security.authentication.method=oidc",
      "camunda.physical-tenants.tenanta.security.authentication.oidc.groups-claim=groups"
    })
class SystemControllerPhysicalTenantGroupsTest extends RestControllerTest {

  private static final String PHYSICAL_TENANT_HEADER = "X-Test-Physical-Tenant";

  @MockitoBean CamundaAuthenticationProvider authenticationProvider;
  @MockitoBean CamundaSecurityLibraryProperties cslProperties;
  @MockitoBean ServiceRegistry serviceRegistry;
  @MockitoBean PhysicalTenantRestConfigProvider tenantRestConfigProvider;

  @BeforeEach
  void setUp() {
    when(tenantRestConfigProvider.forPhysicalTenant(any()))
        .thenReturn(new GatewayRestConfiguration());
    final var clusterConfig = new AuthenticationConfiguration();
    clusterConfig.setMethod(AuthenticationMethod.OIDC);
    when(cslProperties.getAuthentication()).thenReturn(clusterConfig);
  }

  @ParameterizedTest(name = "{0} -> {1}")
  @CsvSource({"default, true", "tenanta, false"})
  void shouldReturnTheGroupsModeOfTheRequestTenant(
      final String physicalTenantId, final boolean expected) {
    webClient
        .get()
        .uri(SystemControllerTest.SYSTEM_CONFIGURATION_URL)
        .header(PHYSICAL_TENANT_HEADER, physicalTenantId)
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .json(
            """
            {"authentication": {"isCamundaGroupsEnabled": %s}}"""
                .formatted(expected),
            JsonCompareMode.LENIENT);
  }

  /** Stands in for the physical tenant filter, which stamps the tenant on the request. */
  @TestConfiguration
  static class PhysicalTenantHeaderFilter {
    @Bean
    Filter physicalTenantHeaderFilter() {
      return (request, response, chain) -> {
        final String tenantId = ((HttpServletRequest) request).getHeader(PHYSICAL_TENANT_HEADER);
        if (tenantId != null) {
          request.setAttribute(
              PhysicalTenantContext.REQUEST_ATTRIBUTE_PHYSICAL_TENANT_ID, tenantId);
        }
        chain.doFilter(request, response);
      };
    }
  }
}
