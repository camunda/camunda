/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.authentication;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.search.entities.TenantEntity;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.TenantQuery;
import io.camunda.security.api.model.user.CamundaUserDTO;
import io.camunda.security.core.port.in.CamundaUserPort;
import io.camunda.service.TenantServices;
import io.camunda.service.registry.ServiceRegistry;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;

@WebMvcTest(AuthenticationController.class)
@ActiveProfiles("consolidated-auth")
public class AuthenticationControllerTest extends RestControllerTest {

  @MockitoBean private CamundaUserPort camundaUserPort;
  @MockitoBean private ServiceRegistry serviceRegistry;

  @Test
  void getAuthorizationShouldReturnOk() {
    // given
    final CamundaUserDTO camundaUserDTO =
        new CamundaUserDTO(
            "camunda user",
            "camundaUSer",
            "camunda.user@email.com",
            List.of("test application"),
            List.of("testTenantId"),
            List.of("test group"),
            List.of("test role"),
            null,
            Map.of(),
            true);

    final TenantServices tenantServices = Mockito.mock(TenantServices.class);
    when(serviceRegistry.tenantServices(any())).thenReturn(tenantServices);
    when(camundaUserPort.getCurrentUser()).thenReturn(camundaUserDTO);
    when(tenantServices.search(any(TenantQuery.class), any()))
        .thenReturn(
            SearchQueryResult.of(
                new TenantEntity(100L, "testTenantId", "testTenantNem", "testTenantDescription")));

    // when
    webClient
        .get()
        .uri("%s".formatted("/v2/authentication/me"))
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .json(
            """
                {
                  "displayName": "camunda user",
                  "username": "camundaUSer",
                  "email": "camunda.user@email.com",
                  "authorizedComponents": ["test application"],
                  "tenants": [{"tenantId":"testTenantId","name":"testTenantNem","description":"testTenantDescription"}],
                  "groups": ["test group"],
                  "roles": ["test role"],
                  "salesPlanType": null,
                  "c8Links": {},
                  "canLogout": true
                }""",
            JsonCompareMode.STRICT);

    // then
    verify(camundaUserPort, times(1)).getCurrentUser();
  }
}
