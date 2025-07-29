/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.tenant;

import static io.camunda.zeebe.gateway.rest.config.ApiFiltersConfiguration.TENANTS_API_DISABLED_ERROR_MESSAGE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.service.GroupServices;
import io.camunda.service.MappingRuleServices;
import io.camunda.service.RoleServices;
import io.camunda.service.TenantServices;
import io.camunda.service.TenantServices.TenantMemberRequest;
import io.camunda.service.TenantServices.TenantRequest;
import io.camunda.service.UserServices;
import io.camunda.service.exception.ErrorMapper;
import io.camunda.zeebe.broker.client.api.dto.BrokerRejection;
import io.camunda.zeebe.gateway.protocol.rest.TenantCreateRequest;
import io.camunda.zeebe.gateway.protocol.rest.TenantUpdateRequest;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import io.camunda.zeebe.gateway.rest.config.ApiFiltersConfiguration;
import io.camunda.zeebe.gateway.rest.validator.IdentifierPatterns;
import io.camunda.zeebe.protocol.impl.record.value.tenant.TenantRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.TenantIntent;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec;

public class TenantControllerTest {

  private static final String TENANT_BASE_URL = "/v2/tenants";

  @Nested
  @WebMvcTest(TenantController.class)
  public class TenantsApiEnabledTest extends RestControllerTest {
    @MockitoBean private TenantServices tenantServices;
    @MockitoBean private UserServices userServices;
    @MockitoBean private MappingRuleServices mappingRuleServices;
    @MockitoBean private GroupServices groupServices;
    @MockitoBean private RoleServices roleServices;
    @MockitoBean private CamundaAuthenticationProvider authenticationProvider;

    @BeforeEach
    void setup() {
      when(authenticationProvider.getCamundaAuthentication())
          .thenReturn(AUTHENTICATION_WITH_DEFAULT_TENANT);
      when(tenantServices.withAuthentication(any(CamundaAuthentication.class)))
          .thenReturn(tenantServices);
    }

    @ParameterizedTest
    @ValueSource(strings = {"foo", "Foo", "foo123", "foo_", "foo.", "foo@"})
    void createTenantShouldReturnAccepted(final String id) {
      // given
      final var tenantName = "Test Tenant";
      final var tenantDescription = "Test description";
      when(tenantServices.createTenant(new TenantRequest(null, id, tenantName, tenantDescription)))
          .thenReturn(
              CompletableFuture.completedFuture(
                  new TenantRecord()
                      .setName(tenantName)
                      .setDescription(tenantDescription)
                      .setTenantId(id)));

      // when
      webClient
          .post()
          .uri(TENANT_BASE_URL)
          .accept(MediaType.APPLICATION_JSON)
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(
              new TenantCreateRequest()
                  .name(tenantName)
                  .description(tenantDescription)
                  .tenantId(id))
          .exchange()
          .expectStatus()
          .isCreated();

      // then
      verify(tenantServices, times(1))
          .createTenant(new TenantRequest(null, id, tenantName, tenantDescription));
    }

    @Test
    void createTenantShouldReturnAllDetails() {
      // given
      final var tenantName = "Test Tenant";
      final var tenantId = "tenantId";
      final var tenantDescription = "Test description";
      when(tenantServices.createTenant(
              new TenantRequest(null, tenantId, tenantName, tenantDescription)))
          .thenReturn(
              CompletableFuture.completedFuture(
                  new TenantRecord()
                      .setName(tenantName)
                      .setDescription(tenantDescription)
                      .setTenantId(tenantId)));

      // when
      webClient
          .post()
          .uri(TENANT_BASE_URL)
          .accept(MediaType.APPLICATION_JSON)
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(
              new TenantCreateRequest()
                  .name(tenantName)
                  .tenantId(tenantId)
                  .description(tenantDescription))
          .exchange()
          .expectStatus()
          .isCreated()
          .expectBody()
          .json(
              """
            {
              "tenantId": "%s",
              "name": "%s",
              "description": "%s"
            }
            """
                  .formatted(tenantId, tenantName, tenantDescription),
              JsonCompareMode.STRICT);

      // then
      verify(tenantServices, times(1))
          .createTenant(new TenantRequest(null, tenantId, tenantName, tenantDescription));
    }

    @Test
    void createTenantWithEmptyTenantIdShouldFail() {
      // given
      final var tenantName = "Tenant Name";

      // when
      webClient
          .post()
          .uri(TENANT_BASE_URL)
          .accept(MediaType.APPLICATION_JSON)
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(new TenantCreateRequest().name(tenantName))
          .exchange()
          .expectStatus()
          .isBadRequest()
          .expectBody()
          .json(
              """
            {
              "type": "about:blank",
              "status": 400,
              "title": "INVALID_ARGUMENT",
              "detail": "No tenantId provided.",
              "instance": "%s"
            }"""
                  .formatted(TENANT_BASE_URL),
              JsonCompareMode.STRICT);

      // then
      verifyNoInteractions(tenantServices);
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
          "foo~", "foo!", "foo#", "foo$", "foo%", "foo^", "foo&", "foo*", "foo(", "foo)", "foo=",
          "foo+", "foo{", "foo[", "foo}", "foo]", "foo|", "foo\\", "foo:", "foo;", "foo\"", "foo'",
          "foo<", "foo>", "foo,", "foo?", "foo/", "foo ", "foo\t", "foo\n", "foo\r"
        })
    void shouldRejectTenantCreationWithIllegalCharactersInId(final String id) {
      // given
      final var tenantName = "Tenant Name";
      final var request = new TenantCreateRequest().tenantId(id).name(tenantName);

      // when
      webClient
          .post()
          .uri(TENANT_BASE_URL)
          .accept(MediaType.APPLICATION_JSON)
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(request)
          .exchange()
          .expectStatus()
          .isBadRequest()
          .expectBody()
          .json(
              """
            {
              "type": "about:blank",
              "status": 400,
              "title": "INVALID_ARGUMENT",
              "detail": "The provided tenantId contains illegal characters. It must match the pattern '%s'.",
              "instance": "%s"
            }"""
                  .formatted(IdentifierPatterns.ID_PATTERN, TENANT_BASE_URL),
              JsonCompareMode.STRICT);

      // then
      verifyNoInteractions(tenantServices);
    }

    @Test
    void shouldRejectTenantWithTooLongId() {
      // given
      final var id = "x".repeat(257);
      final var request = new TenantCreateRequest().tenantId(id).name("Tenant name");

      // when
      webClient
          .post()
          .uri(TENANT_BASE_URL)
          .accept(MediaType.APPLICATION_JSON)
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(request)
          .exchange()
          .expectStatus()
          .isBadRequest()
          .expectBody()
          .json(
              """
            {
              "type": "about:blank",
              "status": 400,
              "title": "INVALID_ARGUMENT",
              "detail": "The provided tenantId exceeds the limit of 256 characters.",
              "instance": "%s"
            }"""
                  .formatted(TENANT_BASE_URL),
              JsonCompareMode.STRICT);

      // then
      verifyNoInteractions(tenantServices);
    }

    @Test
    void updateTenantShouldReturnUpdatedResponse() {
      // given
      final var tenantName = "Updated Tenant Name";
      final var tenantId = "tenant-test-id";
      final var tenantDescription = "Updated description";
      when(tenantServices.updateTenant(
              new TenantRequest(null, tenantId, tenantName, tenantDescription)))
          .thenReturn(
              CompletableFuture.completedFuture(
                  new TenantRecord()
                      .setName(tenantName)
                      .setDescription(tenantDescription)
                      .setTenantId(tenantId)));

      // when
      webClient
          .put()
          .uri("%s/%s".formatted(TENANT_BASE_URL, tenantId))
          .accept(MediaType.APPLICATION_JSON)
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(new TenantUpdateRequest().name(tenantName).description(tenantDescription))
          .exchange()
          .expectStatus()
          .isOk()
          .expectBody()
          .json(
              """
            {
              "tenantId": "%s",
              "name": "%s",
              "description": "%s"
            }
            """
                  .formatted(tenantId, tenantName, tenantDescription),
              JsonCompareMode.STRICT);

      // then
      verify(tenantServices, times(1))
          .updateTenant(new TenantRequest(null, tenantId, tenantName, tenantDescription));
    }

    @Test
    void updateTenantWithoutDescriptionShouldFail() {
      // given
      final var tenantId = 100L;
      final var tenantName = "tenant name";
      final var uri = "%s/%s".formatted(TENANT_BASE_URL, tenantId);

      // when / then
      webClient
          .put()
          .uri(uri)
          .accept(MediaType.APPLICATION_JSON)
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(new TenantUpdateRequest().name(tenantName))
          .exchange()
          .expectStatus()
          .isBadRequest()
          .expectBody()
          .json(
              """
            {
              "type": "about:blank",
              "status": 400,
              "title": "INVALID_ARGUMENT",
              "detail": "No description provided.",
              "instance": "%s"
            }"""
                  .formatted(uri),
              JsonCompareMode.STRICT);

      verifyNoInteractions(tenantServices);
    }

    @Test
    void updateTenantWithoutNameShouldFail() {
      // given
      final var tenantId = 100L;
      final var tenantDescription = "Tenant description";
      final var uri = "%s/%s".formatted(TENANT_BASE_URL, tenantId);

      // when / then
      webClient
          .put()
          .uri(uri)
          .accept(MediaType.APPLICATION_JSON)
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(new TenantUpdateRequest().description(tenantDescription))
          .exchange()
          .expectStatus()
          .isBadRequest()
          .expectBody()
          .json(
              """
            {
              "type": "about:blank",
              "status": 400,
              "title": "INVALID_ARGUMENT",
              "detail": "No name provided.",
              "instance": "%s"
            }"""
                  .formatted(uri),
              JsonCompareMode.STRICT);

      verifyNoInteractions(tenantServices);
    }

    @Test
    void updateNonExistingTenantShouldReturnError() {
      // given
      final var tenantId = "tenant-id";
      final var tenantName = "My tenant";
      final var tenantDescription = "My tenant description";
      final var path = "%s/%s".formatted(TENANT_BASE_URL, tenantId);
      when(tenantServices.updateTenant(
              new TenantRequest(null, tenantId, tenantName, tenantDescription)))
          .thenReturn(
              CompletableFuture.failedFuture(
                  ErrorMapper.mapBrokerRejection(
                      new BrokerRejection(
                          TenantIntent.UPDATE, -1L, RejectionType.NOT_FOUND, "Tenant not found"))));

      // when / then
      webClient
          .put()
          .uri(path)
          .accept(MediaType.APPLICATION_JSON)
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(new TenantUpdateRequest().name(tenantName).description(tenantDescription))
          .exchange()
          .expectStatus()
          .isNotFound();

      verify(tenantServices, times(1))
          .updateTenant(new TenantRequest(null, tenantId, tenantName, tenantDescription));
    }

    @Test
    void deleteTenantShouldReturnNoContent() {
      // given
      final String tenantId = "tenant-to-delete-id";

      final var tenantRecord = new TenantRecord().setTenantId(tenantId);

      when(tenantServices.deleteTenant(tenantId))
          .thenReturn(CompletableFuture.completedFuture(tenantRecord));

      // when
      webClient
          .delete()
          .uri(TENANT_BASE_URL + "/{tenantId}", tenantId)
          .accept(MediaType.APPLICATION_JSON)
          .exchange()
          .expectStatus()
          .isNoContent();

      // then
      verify(tenantServices, times(1)).deleteTenant(tenantId);
    }

    @ParameterizedTest
    @MethodSource("provideAddMemberByIdTestCases")
    void testAddMemberToTenantById(final EntityType entityType, final String entityPath) {
      // given
      final var tenantId = "some-tenant-id";
      final var entityId = "some-entity-id";
      final var request = new TenantMemberRequest(tenantId, entityId, entityType);

      when(tenantServices.addMember(request)).thenReturn(CompletableFuture.completedFuture(null));

      // when
      webClient
          .put()
          .uri("%s/%s/%s/%s".formatted(TENANT_BASE_URL, tenantId, entityPath, entityId))
          .accept(MediaType.APPLICATION_JSON)
          .exchange()
          .expectStatus()
          .isNoContent();

      // then
      verify(tenantServices, times(1)).addMember(request);
    }

    @ParameterizedTest
    @MethodSource("provideAddMemberByIdTestCases")
    void testAddMemberToTenantWithInvalidTenantId(
        final EntityType entityType, final String entityPath) {
      // given
      final var tenantId = "invalidTenantId!";
      final var entityId = "some-entity-id";
      final var request = new TenantMemberRequest(tenantId, entityId, entityType);

      when(tenantServices.addMember(request)).thenReturn(CompletableFuture.completedFuture(null));

      // when
      final var uri = "%s/%s/%s/%s".formatted(TENANT_BASE_URL, tenantId, entityPath, entityId);
      webClient
          .put()
          .uri(uri)
          .accept(MediaType.APPLICATION_JSON)
          .exchange()
          .expectStatus()
          .isBadRequest()
          .expectBody()
          .json(
              """
            {
              "type": "about:blank",
              "status": 400,
              "title": "INVALID_ARGUMENT",
              "detail": "The provided tenantId contains illegal characters. It must match the pattern '%s'.",
              "instance": "%s"
            }"""
                  .formatted(IdentifierPatterns.ID_PATTERN, uri),
              JsonCompareMode.STRICT);

      // then
      verifyNoInteractions(tenantServices);
    }

    @ParameterizedTest
    @MethodSource("provideAddMemberByIdTestCases")
    void testAddMemberToTenantWithInvalidEntityId(
        final EntityType entityType, final String entityPath, final String entityIdName) {
      // given
      final var tenantId = "some-tenant-id";
      final var entityId = "invalidEntityId!";
      final var request = new TenantMemberRequest(tenantId, entityId, entityType);

      when(tenantServices.addMember(request)).thenReturn(CompletableFuture.completedFuture(null));

      // when
      final var uri = "%s/%s/%s/%s".formatted(TENANT_BASE_URL, tenantId, entityPath, entityId);
      webClient
          .put()
          .uri(uri)
          .accept(MediaType.APPLICATION_JSON)
          .exchange()
          .expectStatus()
          .isBadRequest()
          .expectBody()
          .json(
              """
            {
              "type": "about:blank",
              "status": 400,
              "title": "INVALID_ARGUMENT",
              "detail": "The provided %s contains illegal characters. It must match the pattern '%s'.",
              "instance": "%s"
            }"""
                  .formatted(entityIdName, IdentifierPatterns.ID_PATTERN, uri),
              JsonCompareMode.STRICT);

      // then
      verifyNoInteractions(tenantServices);
    }

    @ParameterizedTest
    @MethodSource("provideRemoveMemberByIdTestCases")
    void testRemoveMemberByIdFromTenant(final EntityType entityType, final String entityPath) {
      // given
      final var tenantId = "some-tenant-id";
      final var entityId = "entity-id";
      final var tenantMemberRequest = new TenantMemberRequest(tenantId, entityId, entityType);

      when(tenantServices.removeMember(tenantMemberRequest))
          .thenReturn(CompletableFuture.completedFuture(null));

      // when
      webClient
          .delete()
          .uri("%s/%s/%s/%s".formatted(TENANT_BASE_URL, tenantId, entityPath, entityId))
          .accept(MediaType.APPLICATION_JSON)
          .exchange()
          .expectStatus()
          .isNoContent();

      // then
      verify(tenantServices, times(1)).removeMember(tenantMemberRequest);
    }

    @ParameterizedTest
    @MethodSource("provideAddMemberByIdTestCases")
    void testRemoveMemberToTenantWithInvalidTenantId(
        final EntityType entityType, final String entityPath) {
      // given
      final var tenantId = "invalidTenantId!";
      final var entityId = "some-entity-id";
      final var request = new TenantMemberRequest(tenantId, entityId, entityType);

      when(tenantServices.removeMember(request))
          .thenReturn(CompletableFuture.completedFuture(null));

      // when
      final var uri = "%s/%s/%s/%s".formatted(TENANT_BASE_URL, tenantId, entityPath, entityId);
      webClient
          .put()
          .uri(uri)
          .accept(MediaType.APPLICATION_JSON)
          .exchange()
          .expectStatus()
          .isBadRequest()
          .expectBody()
          .json(
              """
            {
              "type": "about:blank",
              "status": 400,
              "title": "INVALID_ARGUMENT",
              "detail": "The provided tenantId contains illegal characters. It must match the pattern '%s'.",
              "instance": "%s"
            }"""
                  .formatted(IdentifierPatterns.ID_PATTERN, uri),
              JsonCompareMode.STRICT);

      // then
      verifyNoInteractions(tenantServices);
    }

    @ParameterizedTest
    @MethodSource("provideAddMemberByIdTestCases")
    void testRemoveMemberToTenantWithInvalidEntityId(
        final EntityType entityType, final String entityPath, final String entityIdName) {
      // given
      final var tenantId = "some-tenant-id";
      final var entityId = "invalidEntityId!";
      final var request = new TenantMemberRequest(tenantId, entityId, entityType);

      when(tenantServices.removeMember(request))
          .thenReturn(CompletableFuture.completedFuture(null));

      // when
      final var uri = "%s/%s/%s/%s".formatted(TENANT_BASE_URL, tenantId, entityPath, entityId);
      webClient
          .put()
          .uri(uri)
          .accept(MediaType.APPLICATION_JSON)
          .exchange()
          .expectStatus()
          .isBadRequest()
          .expectBody()
          .json(
              """
            {
              "type": "about:blank",
              "status": 400,
              "title": "INVALID_ARGUMENT",
              "detail": "The provided %s contains illegal characters. It must match the pattern '%s'.",
              "instance": "%s"
            }"""
                  .formatted(entityIdName, IdentifierPatterns.ID_PATTERN, uri),
              JsonCompareMode.STRICT);

      // then
      verifyNoInteractions(tenantServices);
    }

    private static Stream<Arguments> provideAddMemberByIdTestCases() {
      return Stream.of(
          Arguments.of(EntityType.USER, "users", "username"),
          Arguments.of(EntityType.MAPPING_RULE, "mapping-rules", "mappingRuleId"),
          Arguments.of(EntityType.GROUP, "groups", "groupId"),
          Arguments.of(EntityType.ROLE, "roles", "roleId"),
          Arguments.of(EntityType.CLIENT, "clients", "clientId"));
    }

    private static Stream<Arguments> provideRemoveMemberByIdTestCases() {
      return Stream.of(
          Arguments.of(EntityType.USER, "users", "username"),
          Arguments.of(EntityType.MAPPING_RULE, "mapping-rules", "mappingRuleId"),
          Arguments.of(EntityType.GROUP, "groups", "groupId"),
          Arguments.of(EntityType.ROLE, "roles", "roleId"),
          Arguments.of(EntityType.CLIENT, "clients", "clientId"));
    }
  }

  @Nested
  @WebMvcTest(TenantController.class)
  @Import(ApiFiltersConfiguration.class)
  @TestPropertySource(properties = "camunda.security.multiTenancy.apiEnabled=false")
  public class TenantsApiDisabledTest extends RestControllerTest {
    public static final String FORBIDDEN_MESSAGE =
        """
        {
          "type": "about:blank",
          "status": 403,
          "title": "Access issue",
          "detail": "%%s endpoint is not accessible: %s",
          "instance": "%%s"
        }"""
            .formatted(TENANTS_API_DISABLED_ERROR_MESSAGE);

    @MockitoBean private TenantServices tenantServices;
    @MockitoBean private UserServices userServices;
    @MockitoBean private MappingRuleServices mappingRuleServices;
    @MockitoBean private GroupServices groupServices;
    @MockitoBean private RoleServices roleServices;
    @MockitoBean private CamundaAuthenticationProvider authenticationProvider;

    @ParameterizedTest
    @MethodSource("tenantControllerRequests")
    void shouldReturnForbiddenWhenTenantsApiIsDisabled(
        final String uri, final Function<WebTestClient, ResponseSpec> webClientConsumer) {
      webClientConsumer
          .apply(webClient)
          .expectStatus()
          .isForbidden()
          .expectBody()
          .json(FORBIDDEN_MESSAGE.formatted(uri, uri), JsonCompareMode.STRICT);
    }

    private static Stream<Arguments> tenantControllerRequests() {
      return Stream.of(
          Arguments.of(
              "/v2/tenants",
              (Function<WebTestClient, ResponseSpec>)
                  webClient -> webClient.post().uri("/v2/tenants").exchange()),
          Arguments.of(
              "/v2/tenants/tenantId",
              (Function<WebTestClient, ResponseSpec>)
                  webClient -> webClient.get().uri("/v2/tenants/tenantId").exchange()),
          Arguments.of(
              "/v2/tenants/search",
              (Function<WebTestClient, ResponseSpec>)
                  webClient -> webClient.post().uri("/v2/tenants/search").exchange()),
          Arguments.of(
              "/v2/tenants/tenantId",
              (Function<WebTestClient, ResponseSpec>)
                  webClient -> webClient.put().uri("/v2/tenants/tenantId").exchange()),
          Arguments.of(
              "/v2/tenants/tenantId/users/username",
              (Function<WebTestClient, ResponseSpec>)
                  webClient ->
                      webClient.put().uri("/v2/tenants/tenantId/users/username").exchange()),
          Arguments.of(
              "/v2/tenants/tenantId/users/search",
              (Function<WebTestClient, ResponseSpec>)
                  webClient ->
                      webClient.post().uri("/v2/tenants/tenantId/users/search").exchange()),
          Arguments.of(
              "/v2/tenants/tenantId/clients/clientId",
              (Function<WebTestClient, ResponseSpec>)
                  webClient ->
                      webClient.put().uri("/v2/tenants/tenantId/clients/clientId").exchange()),
          Arguments.of(
              "/v2/tenants/tenantId/mapping-rules/mappingRuleId",
              (Function<WebTestClient, ResponseSpec>)
                  webClient ->
                      webClient
                          .put()
                          .uri("/v2/tenants/tenantId/mapping-rules/mappingRuleId")
                          .exchange()),
          Arguments.of(
              "/v2/tenants/tenantId/groups/groupId",
              (Function<WebTestClient, ResponseSpec>)
                  webClient ->
                      webClient.put().uri("/v2/tenants/tenantId/groups/groupId").exchange()),
          Arguments.of(
              "/v2/tenants/tenantId/roles/roleId",
              (Function<WebTestClient, ResponseSpec>)
                  webClient -> webClient.put().uri("/v2/tenants/tenantId/roles/roleId").exchange()),
          Arguments.of(
              "/v2/tenants/tenantId",
              (Function<WebTestClient, ResponseSpec>)
                  webClient -> webClient.delete().uri("/v2/tenants/tenantId").exchange()),
          Arguments.of(
              "/v2/tenants/tenantId/users/username",
              (Function<WebTestClient, ResponseSpec>)
                  webClient ->
                      webClient.delete().uri("/v2/tenants/tenantId/users/username").exchange()),
          Arguments.of(
              "/v2/tenants/tenantId/clients/clientId",
              (Function<WebTestClient, ResponseSpec>)
                  webClient ->
                      webClient.delete().uri("/v2/tenants/tenantId/clients/clientId").exchange()),
          Arguments.of(
              "/v2/tenants/tenantId/mapping-rules/search",
              (Function<WebTestClient, ResponseSpec>)
                  webClient ->
                      webClient.post().uri("/v2/tenants/tenantId/mapping-rules/search").exchange()),
          Arguments.of(
              "/v2/tenants/tenantId/mapping-rules/mappingRuleId",
              (Function<WebTestClient, ResponseSpec>)
                  webClient ->
                      webClient
                          .delete()
                          .uri("/v2/tenants/tenantId/mapping-rules/mappingRuleId")
                          .exchange()),
          Arguments.of(
              "/v2/tenants/tenantId/groups/groupId",
              (Function<WebTestClient, ResponseSpec>)
                  webClient ->
                      webClient.delete().uri("/v2/tenants/tenantId/groups/groupId").exchange()),
          Arguments.of(
              "/v2/tenants/tenantId/roles/roleId",
              (Function<WebTestClient, ResponseSpec>)
                  webClient ->
                      webClient.delete().uri("/v2/tenants/tenantId/roles/roleId").exchange()),
          Arguments.of(
              "/v2/tenants/tenantId/groups/search",
              (Function<WebTestClient, ResponseSpec>)
                  webClient ->
                      webClient.post().uri("/v2/tenants/tenantId/groups/search").exchange()),
          Arguments.of(
              "/v2/tenants/tenantId/roles/search",
              (Function<WebTestClient, ResponseSpec>)
                  webClient ->
                      webClient.post().uri("/v2/tenants/tenantId/roles/search").exchange()),
          Arguments.of(
              "/v2/tenants/tenantId/clients/search",
              (Function<WebTestClient, ResponseSpec>)
                  webClient ->
                      webClient.post().uri("/v2/tenants/tenantId/clients/search").exchange()));
    }
  }
}
