/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.gateway.protocol.model.OwnerTypeEnum;
import io.camunda.gateway.protocol.model.ResourceTypeEnum;
import io.camunda.search.entities.AuthorizationEntity;
import io.camunda.search.entities.TenantEntity;
import io.camunda.search.filter.AuthorizationFilter;
import io.camunda.search.query.AuthorizationQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.TenantQuery;
import io.camunda.security.api.context.CamundaAuthenticationProvider;
import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.api.model.authz.AuthorizationResourceMatcher;
import io.camunda.security.api.model.authz.PermissionType;
import io.camunda.security.api.model.user.CamundaUserDTO;
import io.camunda.security.core.port.in.CamundaUserPort;
import io.camunda.security.spring.CamundaSecurityLibraryProperties;
import io.camunda.service.AuthorizationServices;
import io.camunda.service.TenantServices;
import io.camunda.service.registry.ServiceRegistry;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;

@WebMvcTest(AuthenticationController.class)
@ActiveProfiles("consolidated-auth")
@EnableConfigurationProperties(CamundaSecurityLibraryProperties.class)
public class AuthenticationControllerTest extends RestControllerTest {

  private static final String ME_AUTHORIZATIONS_URL = "/v2/authentication/me/authorizations/search";

  @MockitoBean private CamundaUserPort camundaUserPort;
  @MockitoBean private ServiceRegistry serviceRegistry;
  @MockitoBean private CamundaAuthenticationProvider authenticationProvider;
  @MockitoBean private AuthorizationServices authorizationServices;
  @Autowired private CamundaSecurityLibraryProperties securityProperties;

  @BeforeEach
  void setUpAuthorizationServices() {
    when(serviceRegistry.authorizationServices(any())).thenReturn(authorizationServices);
    securityProperties.getAuthorizations().setEnabled(true);
  }

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
                new TenantEntity(100L, "testTenantId", "testTenantName", "testTenantDescription")));

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
                  "tenants": [{"tenantId":"testTenantId","name":"testTenantName","description":"testTenantDescription"}],
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

  @Test
  void searchOwnAuthorizationsShouldReturnRecordsGrantedViaGroupAndRole() {
    // given — the service is the source of truth for owner-id scoping; the controller must simply
    // pass every record it returns through to the response, regardless of ownerType
    when(authenticationProvider.getCamundaAuthentication())
        .thenReturn(AUTHENTICATION_WITH_DEFAULT_TENANT);
    final var directAuthorization =
        new AuthorizationEntity(
            1L,
            "foo",
            OwnerTypeEnum.USER.getValue(),
            ResourceTypeEnum.PROCESS_DEFINITION.getValue(),
            AuthorizationResourceMatcher.ID.value(),
            "processId",
            "",
            Set.of(PermissionType.READ_PROCESS_DEFINITION));
    final var groupAuthorization =
        new AuthorizationEntity(
            2L,
            "groupId",
            OwnerTypeEnum.GROUP.getValue(),
            ResourceTypeEnum.RESOURCE.getValue(),
            AuthorizationResourceMatcher.ANY.value(),
            "*",
            null,
            Set.of(PermissionType.CREATE));
    final var roleAuthorization =
        new AuthorizationEntity(
            3L,
            "roleId",
            OwnerTypeEnum.ROLE.getValue(),
            ResourceTypeEnum.USER_TASK.getValue(),
            AuthorizationResourceMatcher.PROPERTY.value(),
            "",
            "assignee",
            Set.of(PermissionType.CLAIM));
    final var ownAuthorizations =
        List.of(directAuthorization, groupAuthorization, roleAuthorization);
    when(authorizationServices.searchOwnAuthorizations(any(AuthorizationQuery.class), any()))
        .thenReturn(
            SearchQueryResult.<AuthorizationEntity>of(b -> b.total(3).items(ownAuthorizations)));

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
                  "authorizationsEnabled": true,
                  "items": [
                    {
                      "authorizationKey": "1",
                      "ownerId": "foo",
                      "ownerType": "USER",
                      "resourceType": "PROCESS_DEFINITION",
                      "resourceId": "processId",
                      "resourcePropertyName": null,
                      "permissionTypes": ["READ_PROCESS_DEFINITION"]
                    },
                    {
                      "authorizationKey": "2",
                      "ownerId": "groupId",
                      "ownerType": "GROUP",
                      "resourceType": "RESOURCE",
                      "resourceId": "*",
                      "resourcePropertyName": null,
                      "permissionTypes": ["CREATE"]
                    },
                    {
                      "authorizationKey": "3",
                      "ownerId": "roleId",
                      "ownerType": "ROLE",
                      "resourceType": "USER_TASK",
                      "resourceId": null,
                      "resourcePropertyName": "assignee",
                      "permissionTypes": ["CLAIM"]
                    }
                  ]
                }""",
            JsonCompareMode.LENIENT);

    verify(authorizationServices)
        .searchOwnAuthorizations(
            any(AuthorizationQuery.class), eq(AUTHENTICATION_WITH_DEFAULT_TENANT));
  }

  @Test
  void searchOwnAuthorizationsShouldIndicateWhenAuthorizationsAreDisabled() {
    // given
    securityProperties.getAuthorizations().setEnabled(false);
    when(authenticationProvider.getCamundaAuthentication())
        .thenReturn(AUTHENTICATION_WITH_DEFAULT_TENANT);
    when(authorizationServices.searchOwnAuthorizations(any(AuthorizationQuery.class), any()))
        .thenReturn(SearchQueryResult.empty());

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
                  "authorizationsEnabled": false,
                  "items": []
                }""",
            JsonCompareMode.LENIENT);
  }

  @Test
  void searchOwnAuthorizationsShouldForwardResourceTypeFilterToTheService() {
    // given
    when(authenticationProvider.getCamundaAuthentication())
        .thenReturn(AUTHENTICATION_WITH_DEFAULT_TENANT);
    when(authorizationServices.searchOwnAuthorizations(any(AuthorizationQuery.class), any()))
        .thenReturn(SearchQueryResult.empty());

    // when
    webClient
        .post()
        .uri(ME_AUTHORIZATIONS_URL)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            """
                {
                    "filter": {
                        "resourceType": "PROCESS_DEFINITION"
                    }
                }""")
        .exchange()
        .expectStatus()
        .isOk();

    // then — the controller forwards the caller's resourceType filter as-is; scoping the query to
    // the caller's own owner ids is the service's responsibility (see AuthorizationServicesTest)
    final var captor = ArgumentCaptor.forClass(AuthorizationQuery.class);
    verify(authorizationServices)
        .searchOwnAuthorizations(captor.capture(), eq(AUTHENTICATION_WITH_DEFAULT_TENANT));
    final AuthorizationFilter filter = captor.getValue().filter();
    assertThat(filter.resourceType()).isEqualTo(ResourceTypeEnum.PROCESS_DEFINITION.getValue());
  }

  @Test
  void searchOwnAuthorizationsShouldReturnUnauthorizedForAnonymousAuthentication() {
    // given
    when(authenticationProvider.getCamundaAuthentication())
        .thenReturn(CamundaAuthentication.anonymous());

    // when / then
    webClient
        .post()
        .uri(ME_AUTHORIZATIONS_URL)
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isUnauthorized();

    verify(authorizationServices, never()).searchOwnAuthorizations(any(), any());
  }

  @Test
  void searchOwnAuthorizationsShouldReturnUnauthorizedWhenNoAuthenticationIsResolved() {
    // given
    when(authenticationProvider.getCamundaAuthentication()).thenReturn(null);

    // when / then
    webClient
        .post()
        .uri(ME_AUTHORIZATIONS_URL)
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isUnauthorized();

    verify(authorizationServices, never()).searchOwnAuthorizations(any(), any());
  }
}
