/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.usermanagement;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.security.api.context.CamundaAuthenticationProvider;
import io.camunda.security.spring.CamundaSecurityLibraryProperties;
import io.camunda.service.GroupServices;
import io.camunda.service.RoleServices;
import io.camunda.service.registry.ServiceRegistry;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import io.camunda.zeebe.gateway.rest.controller.PhysicalTenantFilter;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * On a SaaS cluster, mapping rules can't be assigned to or unassigned from roles and groups of a
 * physical tenant without an additional IdP: the endpoints answer {@code 403} and reach no service.
 * Here only {@code tenantb} has an additional IdP.
 */
@WebMvcTest({RoleController.class, GroupController.class})
class MappingRuleMembershipForbiddenTest extends RestControllerTest {

  @MockitoBean private ServiceRegistry serviceRegistry;
  @MockitoBean private RoleServices roleServices;
  @MockitoBean private GroupServices groupServices;
  @MockitoBean private CamundaAuthenticationProvider authenticationProvider;

  @ParameterizedTest
  @CsvSource({
    "PUT, /v2/roles/admin/mapping-rules/default-admin",
    "DELETE, /v2/roles/admin/mapping-rules/default-admin",
    "PUT, /v2/groups/some-group/mapping-rules/default-admin",
    "DELETE, /v2/groups/some-group/mapping-rules/default-admin",
    "PUT, /physical-tenants/tenanta/v2/roles/admin/mapping-rules/default-admin",
  })
  void shouldRejectManagingMappingRuleMembership(final String method, final String path) {
    webClient
        .method(HttpMethod.valueOf(method))
        .uri(path)
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isForbidden()
        .expectBody()
        .jsonPath("$.detail")
        .isEqualTo(MappingRuleMembershipPolicy.DETAIL);

    verifyNoInteractions(serviceRegistry, roleServices, groupServices);
  }

  @Test
  void shouldAllowManagingMappingRuleMembershipInTheTenantWithAnAdditionalIdp() {
    // given
    when(serviceRegistry.roleServices("tenantb")).thenReturn(roleServices);
    when(roleServices.addMember(any(), any())).thenReturn(CompletableFuture.completedFuture(null));

    // when
    webClient
        .put()
        .uri("/physical-tenants/tenantb/v2/roles/admin/mapping-rules/default-admin")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isNoContent();

    // then
    verify(roleServices).addMember(any(), any());
  }

  @TestConfiguration
  static class SaasWithAnAdditionalIdpForTenantb {
    // Resolves the physical tenant from the path, as in production.
    @Bean
    PhysicalTenantFilter physicalTenantFilter() {
      return new PhysicalTenantFilter();
    }

    @Bean
    @Primary
    MappingRuleMembershipPolicy tenantMappingRuleMembershipPolicy() {
      final var properties = new CamundaSecurityLibraryProperties();
      properties.getSaas().setOrganizationId("org-1");
      final var environment =
          new MockEnvironment()
              .withProperty("camunda.security.authentication.method", "oidc")
              .withProperty(
                  "camunda.physical-tenants.tenanta.security.authentication.oidc.client-id",
                  "tenanta-client")
              .withProperty(
                  "camunda.physical-tenants.tenantb.security.authentication.providers.oidc.byoidp"
                      + ".client-id",
                  "byoidp-client");
      return MappingRuleMembershipPolicy.of(properties, environment);
    }
  }
}
