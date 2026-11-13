/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.authentication;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.camunda.search.query.AuthorizationQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.api.context.CamundaAuthenticationProvider;
import io.camunda.security.core.port.in.CamundaUserPort;
import io.camunda.security.spring.CamundaSecurityLibraryProperties;
import io.camunda.security.spring.spi.WebAppAuthorizationEnabledPort;
import io.camunda.service.AuthorizationServices;
import io.camunda.service.registry.ServiceRegistry;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;

/**
 * With a {@link WebAppAuthorizationEnabledPort}, the own authorizations search reports the flag of
 * the request's physical tenant, not the root one.
 */
@WebMvcTest(AuthenticationController.class)
@ActiveProfiles("consolidated-auth")
@EnableConfigurationProperties(CamundaSecurityLibraryProperties.class)
class AuthenticationControllerTenantAuthorizationsTest extends RestControllerTest {

  private static final String ME_AUTHORIZATIONS_URL = "/v2/authentication/me/authorizations/search";

  @MockitoBean private CamundaUserPort camundaUserPort;
  @MockitoBean private ServiceRegistry serviceRegistry;
  @MockitoBean private CamundaAuthenticationProvider authenticationProvider;
  @MockitoBean private AuthorizationServices authorizationServices;
  @MockitoBean private WebAppAuthorizationEnabledPort authorizationEnabled;
  @Autowired private CamundaSecurityLibraryProperties securityProperties;

  @BeforeEach
  void setUp() {
    when(serviceRegistry.authorizationServices(any())).thenReturn(authorizationServices);
    when(authenticationProvider.getCamundaAuthentication())
        .thenReturn(AUTHENTICATION_WITH_DEFAULT_TENANT);
    when(authorizationServices.searchOwnAuthorizations(any(AuthorizationQuery.class), any()))
        .thenReturn(SearchQueryResult.empty());
  }

  @ParameterizedTest(name = "tenant flag = {0}")
  @ValueSource(booleans = {true, false})
  void shouldReportTheFlagOfTheRequestTenant(final boolean tenantFlag) {
    // given — the root flag is the opposite of the tenant flag
    securityProperties.getAuthorizations().setEnabled(!tenantFlag);
    when(authorizationEnabled.isAuthorizationEnabled(any())).thenReturn(tenantFlag);

    // when / then
    webClient
        .post()
        .uri(ME_AUTHORIZATIONS_URL)
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .json(
            """
                {
                  "authorizationsEnabled": %s,
                  "items": []
                }"""
                .formatted(tenantFlag),
            JsonCompareMode.LENIENT);
  }
}
