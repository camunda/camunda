/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.usermanagement;

import static org.mockito.Mockito.verifyNoInteractions;

import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.service.GroupServices;
import io.camunda.service.MappingRuleServices;
import io.camunda.service.RoleServices;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * On a SaaS cluster, mapping rules can't be assigned to or unassigned from roles and groups: the
 * endpoints answer {@code 403} and reach no service.
 */
@WebMvcTest({RoleController.class, GroupController.class})
class MappingRuleMembershipForbiddenTest extends RestControllerTest {

  @MockitoBean private RoleServices roleServices;
  @MockitoBean private GroupServices groupServices;
  @MockitoBean private MappingRuleServices mappingRuleServices;
  @MockitoBean private CamundaAuthenticationProvider authenticationProvider;
  @MockitoBean private SecurityConfiguration securityConfiguration;

  @ParameterizedTest
  @CsvSource({
    "PUT, /v2/roles/admin/mapping-rules/default-admin",
    "DELETE, /v2/roles/admin/mapping-rules/default-admin",
    "PUT, /v2/groups/some-group/mapping-rules/default-admin",
    "DELETE, /v2/groups/some-group/mapping-rules/default-admin",
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

    verifyNoInteractions(roleServices, groupServices, mappingRuleServices);
  }

  @TestConfiguration
  static class Saas {
    @Bean
    @Primary
    MappingRuleMembershipPolicy forbiddenMappingRuleMembershipPolicy() {
      return new MappingRuleMembershipPolicy(false);
    }
  }
}
