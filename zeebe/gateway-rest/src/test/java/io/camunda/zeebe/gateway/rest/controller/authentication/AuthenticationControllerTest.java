/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.authentication;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.authentication.entity.CamundaUserDTO;
import io.camunda.authentication.service.CamundaUserService;
import io.camunda.search.entities.TenantEntity;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;

@WebMvcTest(AuthenticationController.class)
@ActiveProfiles("consolidated-auth")
public class AuthenticationControllerTest extends RestControllerTest {

  @MockitoBean private CamundaUserService camundaUserService;

  @Test
  void getAuthorizationShouldReturnOk() {
    // given
    final CamundaUserDTO camundaUserDTO =
        new CamundaUserDTO(
            "camunda user",
            "camundaUSer",
            "camunda.user@email.com",
            List.of("test application"),
            List.of(
                new TenantEntity(100L, "testTenantId", "testTenantNem", "testTenantDescription")),
            List.of("test group"),
            List.of("test role"),
            null,
            Map.of(),
            true);

    when(camundaUserService.getCurrentUser()).thenReturn(camundaUserDTO);

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
                  "authorizedApplications": ["test application"],
                  "tenants": [{"tenantId":"testTenantId","name":"testTenantNem","description":"testTenantDescription"}],
                  "groups": ["test group"],
                  "roles": ["test role"],
                  "c8Links": {},
                  "canLogout": true
                }""",
            JsonCompareMode.STRICT);

    // then
    verify(camundaUserService, times(1)).getCurrentUser();
  }
}
