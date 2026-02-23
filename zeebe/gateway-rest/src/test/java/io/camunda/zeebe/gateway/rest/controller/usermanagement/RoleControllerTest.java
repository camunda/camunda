/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.usermanagement;

import static io.camunda.security.configuration.SecurityConfiguration.DEFAULT_EXTERNAL_ID_REGEX;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.gateway.protocol.model.RoleCreateRequest;
import io.camunda.gateway.protocol.model.RoleUpdateRequest;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.security.validation.IdentifierValidator;
import io.camunda.service.GroupServices;
import io.camunda.service.MappingRuleServices;
import io.camunda.service.RoleServices;
import io.camunda.service.RoleServices.CreateRoleRequest;
import io.camunda.service.RoleServices.RoleMemberRequest;
import io.camunda.service.RoleServices.UpdateRoleRequest;
import io.camunda.service.UserServices;
import io.camunda.service.exception.ErrorMapper;
import io.camunda.zeebe.broker.client.api.dto.BrokerRejection;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import io.camunda.zeebe.protocol.impl.record.value.authorization.RoleRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.RoleIntent;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.test.util.Strings;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;

public class RoleControllerTest {

  static final String ROLE_BASE_URL = "/v2/roles";
  static final Pattern ID_PATTERN = Pattern.compile(SecurityConfiguration.DEFAULT_ID_REGEX);

  @Nested
  @WebMvcTest(RoleController.class)
  class DefaultRoleControllerTest extends RestControllerTest {

    @MockitoBean private RoleServices roleServices;
    @MockitoBean private UserServices userServices;
    @MockitoBean private MappingRuleServices mappingRuleServices;
    @MockitoBean private GroupServices groupServices;
    @MockitoBean private CamundaAuthenticationProvider authenticationProvider;

    @BeforeEach
    void setup() {
      when(authenticationProvider.getCamundaAuthentication())
          .thenReturn(AUTHENTICATION_WITH_DEFAULT_TENANT);
      when(roleServices.withAuthentication(any(CamundaAuthentication.class)))
          .thenReturn(roleServices);
      when(userServices.withAuthentication(any(CamundaAuthentication.class)))
          .thenReturn(userServices);
      when(mappingRuleServices.withAuthentication(any(CamundaAuthentication.class)))
          .thenReturn(mappingRuleServices);
      when(groupServices.withAuthentication(any(CamundaAuthentication.class)))
          .thenReturn(groupServices);
    }

    @ParameterizedTest
    @ValueSource(strings = {"foo", "foo~", "Foo", "foo123", "foo_", "foo.", "foo@"})
    void createRoleShouldReturnCreated(final String roleId) {
      // given
      final var roleName = "Test Role";
      final var description = "A role used for testing";
      final var request = new CreateRoleRequest(roleId, roleName, description);
      when(roleServices.createRole(request))
          .thenReturn(
              CompletableFuture.completedFuture(
                  new RoleRecord()
                      .setRoleId(roleId)
                      .setName(roleName)
                      .setDescription(description)));

      // when
      webClient
          .post()
          .uri(ROLE_BASE_URL)
          .accept(MediaType.APPLICATION_JSON)
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(new RoleCreateRequest().roleId(roleId).name(roleName).description(description))
          .exchange()
          .expectStatus()
          .isCreated();

      // then
      verify(roleServices, times(1)).createRole(request);
    }

    @Test
    void createRoleWithoutIdShouldFail() {
      // given
      final String roleId = null;
      final var roleName = "Role name";

      // when
      webClient
          .post()
          .uri(ROLE_BASE_URL)
          .accept(MediaType.APPLICATION_JSON)
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(new RoleCreateRequest().roleId(roleId).name(roleName))
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
              "detail": "No roleId provided.",
              "instance": "%s"
            }"""
                  .formatted(ROLE_BASE_URL),
              JsonCompareMode.STRICT);

      // then
      verifyNoInteractions(roleServices);
    }

    @Test
    void createRoleWithEmptyIdShouldFail() {
      // given
      final String roleId = "";
      final var roleName = "Role name";

      // when
      webClient
          .post()
          .uri(ROLE_BASE_URL)
          .accept(MediaType.APPLICATION_JSON)
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(new RoleCreateRequest().roleId(roleId).name(roleName))
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
              "detail": "No roleId provided.",
              "instance": "%s"
            }"""
                  .formatted(ROLE_BASE_URL),
              JsonCompareMode.STRICT);

      // then
      verifyNoInteractions(roleServices);
    }

    @Test
    void createRoleWithEmptyNameShouldFail() {
      // given
      final var roleId = "roleId";
      final var roleName = "";

      // when
      webClient
          .post()
          .uri(ROLE_BASE_URL)
          .accept(MediaType.APPLICATION_JSON)
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(new RoleCreateRequest().roleId(roleId).name(roleName))
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
                  .formatted(ROLE_BASE_URL),
              JsonCompareMode.STRICT);

      // then
      verifyNoInteractions(roleServices);
    }

    @Test
    void shouldFailOnCreateRoleWithTooLongGroupId() {
      // given
      final var roleId = "x".repeat(257);

      // when
      webClient
          .post()
          .uri(ROLE_BASE_URL)
          .accept(MediaType.APPLICATION_JSON)
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(new RoleCreateRequest().name("name").roleId(roleId))
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
              "detail": "The provided roleId exceeds the limit of 256 characters.",
              "instance": "%s"
            }"""
                  .formatted(ROLE_BASE_URL),
              JsonCompareMode.STRICT);

      // then
      verifyNoInteractions(roleServices);
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
          "foo!", "foo#", "foo$", "foo%", "foo^", "foo&", "foo*", "foo(", "foo)", "foo=", "foo{",
          "foo[", "foo}", "foo]", "foo|", "foo\\", "foo:", "foo;", "foo\"", "foo'", "foo<", "foo>",
          "foo,", "foo?", "foo/", "foo ", "foo\t", "foo\n", "foo\r"
        })
    void shouldRejectRoleCreationWithIllegalCharactersInId(final String roleId) {
      // when then
      webClient
          .post()
          .uri(ROLE_BASE_URL)
          .accept(MediaType.APPLICATION_JSON)
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(new RoleCreateRequest().name("name").roleId(roleId))
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
                "detail": "The provided roleId contains illegal characters. It must match the pattern '%s'.",
                "instance": "%s"
              }"""
                  .formatted(SecurityConfiguration.DEFAULT_ID_REGEX, ROLE_BASE_URL),
              JsonCompareMode.STRICT);
      verifyNoInteractions(roleServices);
    }

    @Test
    void shouldUpdateRoleAndReturnResponse() {
      // given
      final var roleKey = 100L;
      final var roleId = "roleId";
      final var roleName = "Updated Role Name";
      final var description = "Updated Role Description";
      final var request = new UpdateRoleRequest(roleId, roleName, description);
      when(roleServices.updateRole(request))
          .thenReturn(
              CompletableFuture.completedFuture(
                  new RoleRecord()
                      .setRoleKey(roleKey)
                      .setRoleId(roleId)
                      .setName(roleName)
                      .setDescription(description)));

      // when
      webClient
          .put()
          .uri("%s/%s".formatted(ROLE_BASE_URL, roleId))
          .accept(MediaType.APPLICATION_JSON)
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(new RoleUpdateRequest().name(roleName).description(description))
          .exchange()
          .expectStatus()
          .isOk()
          .expectBody()
          .json(
              """
            {
              "roleId": "%s",
              "name": "%s",
              "description": "%s"
            }
            """
                  .formatted(roleId, roleName, description),
              JsonCompareMode.STRICT);

      // then
      verify(roleServices, times(1)).updateRole(request);
    }

    @Test
    void updateRoleWithEmptyNameShouldFail() {
      // given
      final var roleId = "roleId";
      final var roleName = "";
      final var description = "Updated Role Description";
      final var uri = "%s/%s".formatted(ROLE_BASE_URL, roleId);

      // when / then
      webClient
          .put()
          .uri(uri)
          .accept(MediaType.APPLICATION_JSON)
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(new RoleUpdateRequest().name(roleName).description(description))
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

      verifyNoInteractions(roleServices);
    }

    @Test
    void updateNonExistingRoleShouldReturnError() {
      // given
      final var roleId = "roleId";
      final var roleName = "Updated Role Name";
      final var description = "Updated Role Description";
      final var request = new UpdateRoleRequest(roleId, roleName, description);
      final var path = "%s/%s".formatted(ROLE_BASE_URL, roleId);
      when(roleServices.updateRole(request))
          .thenReturn(
              CompletableFuture.failedFuture(
                  ErrorMapper.mapBrokerRejection(
                      new BrokerRejection(
                          RoleIntent.UPDATE, 1L, RejectionType.NOT_FOUND, "Role not found"))));

      // when / then
      webClient
          .put()
          .uri(path)
          .accept(MediaType.APPLICATION_JSON)
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(new RoleUpdateRequest().name(roleName).description(description))
          .exchange()
          .expectStatus()
          .isNotFound();

      verify(roleServices, times(1)).updateRole(request);
    }

    @Test
    void updateRoleWithoutDescription() {
      // given
      final var roleId = "roleId";
      final var roleName = "roleName";
      final String description = null;
      final var uri = "%s/%s".formatted(ROLE_BASE_URL, roleId);
      final var request = new UpdateRoleRequest(roleId, roleName, description);
      when(roleServices.updateRole(request))
          .thenReturn(
              CompletableFuture.completedFuture(
                  new RoleRecord()
                      .setRoleId(roleId)
                      .setName(roleName)
                      .setDescription(description)));

      // when / then
      webClient
          .put()
          .uri(uri)
          .accept(MediaType.APPLICATION_JSON)
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(new RoleUpdateRequest().name(roleName).description(description))
          .exchange()
          .expectStatus()
          .isOk()
          .expectBody()
          .json(
              """
            {
              "name":"%s",
              "description":"%s",
              "roleId":"%s"
            }
            """
                  .formatted(roleName, "", roleId),
              JsonCompareMode.STRICT);

      verify(roleServices, times(1)).updateRole(request);
    }

    @Test
    void deleteRoleShouldReturnNoContent() {
      // given
      final var roleId = "roleId";

      final var roleRecord = new RoleRecord().setRoleId(roleId);

      when(roleServices.deleteRole(roleId))
          .thenReturn(CompletableFuture.completedFuture(roleRecord));

      // when
      webClient
          .delete()
          .uri("%s/%s".formatted(ROLE_BASE_URL, roleId))
          .accept(MediaType.APPLICATION_JSON)
          .exchange()
          .expectStatus()
          .isNoContent();

      // then
      verify(roleServices, times(1)).deleteRole(roleId);
    }

    @Test
    void shouldAssignUserToRoleAndReturnNoContent() {
      // given
      final var roleId = "roleId";
      final var username = "username";

      final var request = new RoleMemberRequest(roleId, username, EntityType.USER);
      when(roleServices.addMember(request)).thenReturn(CompletableFuture.completedFuture(null));

      // when
      webClient
          .put()
          .uri("%s/%s/users/%s".formatted(ROLE_BASE_URL, roleId, username))
          .accept(MediaType.APPLICATION_JSON)
          .exchange()
          .expectStatus()
          .isNoContent();

      // then
      verify(roleServices, times(1)).addMember(request);
    }

    @Test
    void shouldAssignMappingToRoleAndReturnNoContent() {
      // given
      final var roleId = Strings.newRandomValidIdentityId();
      final var mappingRuleId = Strings.newRandomValidIdentityId();
      final var request = new RoleMemberRequest(roleId, mappingRuleId, EntityType.MAPPING_RULE);
      when(roleServices.addMember(request)).thenReturn(CompletableFuture.completedFuture(null));

      // when
      webClient
          .put()
          .uri("%s/%s/mapping-rules/%s".formatted(ROLE_BASE_URL, roleId, mappingRuleId))
          .accept(MediaType.APPLICATION_JSON)
          .exchange()
          .expectStatus()
          .isNoContent();

      // then
      verify(roleServices, times(1)).addMember(request);
    }

    @Test
    void shouldReturnErrorForAssigningMissingMappingToRole() {
      // given
      final var roleId = Strings.newRandomValidIdentityId();
      final var mappingRuleId = Strings.newRandomValidIdentityId();
      final var path = "%s/%s/mapping-rules/%s".formatted(ROLE_BASE_URL, roleId, mappingRuleId);
      final var request = new RoleMemberRequest(roleId, mappingRuleId, EntityType.MAPPING_RULE);
      when(roleServices.addMember(request))
          .thenReturn(
              CompletableFuture.failedFuture(
                  ErrorMapper.mapBrokerRejection(
                      new BrokerRejection(
                          RoleIntent.ENTITY_ADDED,
                          1L,
                          RejectionType.NOT_FOUND,
                          "Mapping not found"))));

      // when
      webClient
          .put()
          .uri(path)
          .accept(MediaType.APPLICATION_PROBLEM_JSON)
          .exchange()
          .expectStatus()
          .isNotFound();

      // then
      verify(roleServices, times(1)).addMember(request);
    }

    @Test
    void shouldReturnErrorForAssigningMappingToMissingRole() {
      // given
      final var roleId = Strings.newRandomValidIdentityId();
      final var mappingRuleId = Strings.newRandomValidIdentityId();
      final var path = "%s/%s/mapping-rules/%s".formatted(ROLE_BASE_URL, roleId, mappingRuleId);
      final var request = new RoleMemberRequest(roleId, mappingRuleId, EntityType.MAPPING_RULE);
      when(roleServices.addMember(request))
          .thenReturn(
              CompletableFuture.failedFuture(
                  ErrorMapper.mapBrokerRejection(
                      new BrokerRejection(
                          RoleIntent.ENTITY_ADDED,
                          1L,
                          RejectionType.NOT_FOUND,
                          "Role not found"))));

      // when
      webClient
          .put()
          .uri(path)
          .accept(MediaType.APPLICATION_PROBLEM_JSON)
          .exchange()
          .expectStatus()
          .isNotFound();

      // then
      verify(roleServices, times(1)).addMember(request);
    }

    @Test
    void shouldReturnErrorForProvidingInvalidMappingIdWhenAssigningToRole() {
      // given
      final var roleId = Strings.newRandomValidIdentityId();
      final var mappingRuleId = "mappingRuleId!";
      final var path = "%s/%s/mapping-rules/%s".formatted(ROLE_BASE_URL, roleId, mappingRuleId);

      // when
      webClient
          .put()
          .uri(path)
          .accept(MediaType.APPLICATION_JSON)
          .contentType(MediaType.APPLICATION_JSON)
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
                "detail": "The provided mappingRuleId contains illegal characters. It must match the pattern '%s'.",
                "instance": "%s"
              }"""
                  .formatted(SecurityConfiguration.DEFAULT_ID_REGEX, path),
              JsonCompareMode.STRICT);
      verifyNoInteractions(roleServices);
    }

    @Test
    void shouldReturnErrorForProvidingInvalidRoleIdWhenAssigningMappingToRole() {
      // given
      final String roleId = "roleId!";
      final String mappingRuleId = Strings.newRandomValidIdentityId();
      final var path = "%s/%s/mapping-rules/%s".formatted(ROLE_BASE_URL, roleId, mappingRuleId);

      // when
      webClient
          .put()
          .uri(path)
          .accept(MediaType.APPLICATION_JSON)
          .contentType(MediaType.APPLICATION_JSON)
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
                "detail": "The provided roleId contains illegal characters. It must match the pattern '%s'.",
                "instance": "%s"
              }"""
                  .formatted(SecurityConfiguration.DEFAULT_ID_REGEX, path),
              JsonCompareMode.STRICT);
      verifyNoInteractions(roleServices);
    }

    @Test
    void shouldUnassignMappingFromRoleAndReturnNoContent() {
      // given
      final var roleId = Strings.newRandomValidIdentityId();
      final var mappingRuleId = Strings.newRandomValidIdentityId();

      final var request = new RoleMemberRequest(roleId, mappingRuleId, EntityType.MAPPING_RULE);
      when(roleServices.removeMember(request)).thenReturn(CompletableFuture.completedFuture(null));

      // when
      webClient
          .delete()
          .uri("%s/%s/mapping-rules/%s".formatted(ROLE_BASE_URL, roleId, mappingRuleId))
          .accept(MediaType.APPLICATION_JSON)
          .exchange()
          .expectStatus()
          .isNoContent();

      // then
      verify(roleServices, times(1)).removeMember(request);
    }

    @Test
    void shouldReturnErrorForUnassigningMissingMappingFromRole() {
      // given
      final var roleId = Strings.newRandomValidIdentityId();
      final var mappingRuleId = Strings.newRandomValidIdentityId();
      final var path = "%s/%s/mapping-rules/%s".formatted(ROLE_BASE_URL, roleId, mappingRuleId);
      final var request = new RoleMemberRequest(roleId, mappingRuleId, EntityType.MAPPING_RULE);
      when(roleServices.removeMember(request))
          .thenReturn(
              CompletableFuture.failedFuture(
                  ErrorMapper.mapBrokerRejection(
                      new BrokerRejection(
                          RoleIntent.REMOVE_ENTITY,
                          1L,
                          RejectionType.NOT_FOUND,
                          "Mapping not found"))));

      // when
      webClient
          .delete()
          .uri(path)
          .accept(MediaType.APPLICATION_PROBLEM_JSON)
          .exchange()
          .expectStatus()
          .isNotFound();

      // then
      verify(roleServices, times(1)).removeMember(request);
    }

    @Test
    void shouldReturnErrorForUnassigningMappingFromMissingRole() {
      // given
      final var roleId = Strings.newRandomValidIdentityId();
      final var mappingRuleId = Strings.newRandomValidIdentityId();
      final var path = "%s/%s/mapping-rules/%s".formatted(ROLE_BASE_URL, roleId, mappingRuleId);
      final var request = new RoleMemberRequest(roleId, mappingRuleId, EntityType.MAPPING_RULE);
      when(roleServices.removeMember(request))
          .thenReturn(
              CompletableFuture.failedFuture(
                  ErrorMapper.mapBrokerRejection(
                      new BrokerRejection(
                          RoleIntent.REMOVE_ENTITY,
                          1L,
                          RejectionType.NOT_FOUND,
                          "Role not found"))));

      // when
      webClient
          .delete()
          .uri(path)
          .accept(MediaType.APPLICATION_PROBLEM_JSON)
          .exchange()
          .expectStatus()
          .isNotFound();

      // then
      verify(roleServices, times(1)).removeMember(request);
    }

    @Test
    void shouldReturnErrorForAssigningMissingUserToRole() {
      // given
      final var roleId = "roleId";
      final var username = "username";
      final var path = "%s/%s/users/%s".formatted(ROLE_BASE_URL, roleId, username);
      final var request = new RoleMemberRequest(roleId, username, EntityType.USER);
      when(roleServices.addMember(request))
          .thenReturn(
              CompletableFuture.failedFuture(
                  ErrorMapper.mapBrokerRejection(
                      new BrokerRejection(
                          RoleIntent.ENTITY_ADDED,
                          1L,
                          RejectionType.NOT_FOUND,
                          "User not found"))));

      // when
      webClient
          .put()
          .uri(path)
          .accept(MediaType.APPLICATION_PROBLEM_JSON)
          .exchange()
          .expectStatus()
          .isNotFound();

      // then
      verify(roleServices, times(1)).addMember(request);
    }

    @Test
    void shouldReturnErrorForAssigningUserToMissingRole() {
      // given
      final String roleId = "roleId";
      final String username = "username";
      final var path = "%s/%s/users/%s".formatted(ROLE_BASE_URL, roleId, username);
      final var request = new RoleMemberRequest(roleId, username, EntityType.USER);
      when(roleServices.addMember(request))
          .thenReturn(
              CompletableFuture.failedFuture(
                  ErrorMapper.mapBrokerRejection(
                      new BrokerRejection(
                          RoleIntent.ENTITY_ADDED,
                          1L,
                          RejectionType.NOT_FOUND,
                          "Role not found"))));

      // when
      webClient
          .put()
          .uri(path)
          .accept(MediaType.APPLICATION_PROBLEM_JSON)
          .exchange()
          .expectStatus()
          .isNotFound();

      // then
      verify(roleServices, times(1)).addMember(request);
    }

    @Test
    void shouldReturnErrorForProvidingInvalidUsernameWhenAssigningToRole() {
      // given
      final String roleId = "roleId";
      final String username = "username!";
      final var path = "%s/%s/users/%s".formatted(ROLE_BASE_URL, roleId, username);

      // when
      webClient
          .put()
          .uri(path)
          .accept(MediaType.APPLICATION_JSON)
          .contentType(MediaType.APPLICATION_JSON)
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
                "detail": "The provided username contains illegal characters. It must match the pattern '%s'.",
                "instance": "%s"
              }"""
                  .formatted(SecurityConfiguration.DEFAULT_ID_REGEX, path),
              JsonCompareMode.STRICT);
      verifyNoInteractions(roleServices);
    }

    @Test
    void shouldReturnErrorForProvidingInvalidRoleIdWhenAssigningToRole() {
      // given
      final String roleId = "roleId!";
      final String username = "username";
      final var path = "%s/%s/users/%s".formatted(ROLE_BASE_URL, roleId, username);

      // when
      webClient
          .put()
          .uri(path)
          .accept(MediaType.APPLICATION_JSON)
          .contentType(MediaType.APPLICATION_JSON)
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
                "detail": "The provided roleId contains illegal characters. It must match the pattern '%s'.",
                "instance": "%s"
              }"""
                  .formatted(SecurityConfiguration.DEFAULT_ID_REGEX, path),
              JsonCompareMode.STRICT);
      verifyNoInteractions(roleServices);
    }

    @Test
    void shouldUnassignUserFromRoleAndReturnNoContent() {
      // given
      final var roleId = "roleId";
      final var username = "username";

      final var request = new RoleMemberRequest(roleId, username, EntityType.USER);
      when(roleServices.removeMember(request)).thenReturn(CompletableFuture.completedFuture(null));

      // when
      webClient
          .delete()
          .uri("%s/%s/users/%s".formatted(ROLE_BASE_URL, roleId, username))
          .accept(MediaType.APPLICATION_JSON)
          .exchange()
          .expectStatus()
          .isNoContent();

      // then
      verify(roleServices, times(1)).removeMember(request);
    }

    @Test
    void shouldReturnErrorForUnassigningMissingUserFromRole() {
      // given
      final var roleId = "roleId";
      final var username = "username";
      final var path = "%s/%s/users/%s".formatted(ROLE_BASE_URL, roleId, username);
      final var request = new RoleMemberRequest(roleId, username, EntityType.USER);
      when(roleServices.removeMember(request))
          .thenReturn(
              CompletableFuture.failedFuture(
                  ErrorMapper.mapBrokerRejection(
                      new BrokerRejection(
                          RoleIntent.ENTITY_ADDED,
                          1L,
                          RejectionType.NOT_FOUND,
                          "User not found"))));

      // when
      webClient
          .delete()
          .uri(path)
          .accept(MediaType.APPLICATION_PROBLEM_JSON)
          .exchange()
          .expectStatus()
          .isNotFound();

      // then
      verify(roleServices, times(1)).removeMember(request);
    }

    @Test
    void shouldReturnErrorForUnassigningUserFromMissingRole() {
      // given
      final String roleId = "roleId";
      final String username = "username";
      final var path = "%s/%s/users/%s".formatted(ROLE_BASE_URL, roleId, username);
      final var request = new RoleMemberRequest(roleId, username, EntityType.USER);
      when(roleServices.removeMember(request))
          .thenReturn(
              CompletableFuture.failedFuture(
                  ErrorMapper.mapBrokerRejection(
                      new BrokerRejection(
                          RoleIntent.ENTITY_ADDED,
                          1L,
                          RejectionType.NOT_FOUND,
                          "Role not found"))));

      // when
      webClient
          .delete()
          .uri(path)
          .accept(MediaType.APPLICATION_PROBLEM_JSON)
          .exchange()
          .expectStatus()
          .isNotFound();

      // then
      verify(roleServices, times(1)).removeMember(request);
    }

    @Test
    void shouldReturnErrorForProvidingInvalidUsernameWhenUnassigningFromRole() {
      // given
      final String roleId = "roleId";
      final String username = "username!";
      final var path = "%s/%s/users/%s".formatted(ROLE_BASE_URL, roleId, username);

      // when
      webClient
          .delete()
          .uri(path)
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
                  "detail": "The provided username contains illegal characters. It must match the pattern '%s'.",
                  "instance": "%s"
                }"""
                  .formatted(SecurityConfiguration.DEFAULT_ID_REGEX, path),
              JsonCompareMode.STRICT);
      verifyNoInteractions(roleServices);
    }

    @Test
    void shouldReturnErrorForProvidingInvalidRoleIdWhenUnassigningFromRole() {
      // given
      final String roleId = "roleId!";
      final String username = "username";
      final var path = "%s/%s/users/%s".formatted(ROLE_BASE_URL, roleId, username);

      // when
      webClient
          .delete()
          .uri(path)
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
                  "detail": "The provided roleId contains illegal characters. It must match the pattern '%s'.",
                  "instance": "%s"
                }"""
                  .formatted(SecurityConfiguration.DEFAULT_ID_REGEX, path),
              JsonCompareMode.STRICT);
      verifyNoInteractions(roleServices);
    }

    @Test
    void shouldAssignGroupToRoleAndReturnNoContent() {
      // given
      final var roleId = "roleId";
      final var groupId = "groupId";

      final var request = new RoleMemberRequest(roleId, groupId, EntityType.GROUP);
      when(roleServices.addMember(request)).thenReturn(CompletableFuture.completedFuture(null));

      // when
      webClient
          .put()
          .uri("%s/%s/groups/%s".formatted(ROLE_BASE_URL, roleId, groupId))
          .accept(MediaType.APPLICATION_JSON)
          .exchange()
          .expectStatus()
          .isNoContent();

      // then
      verify(roleServices, times(1)).addMember(request);
    }

    @Test
    void shouldReturnErrorForAssigningMissingGroupToRole() {
      // given
      final var roleId = "roleId";
      final var groupId = "groupId";
      final var path = "%s/%s/groups/%s".formatted(ROLE_BASE_URL, roleId, groupId);
      final var request = new RoleMemberRequest(roleId, groupId, EntityType.GROUP);
      when(roleServices.addMember(request))
          .thenReturn(
              CompletableFuture.failedFuture(
                  ErrorMapper.mapBrokerRejection(
                      new BrokerRejection(
                          RoleIntent.ENTITY_ADDED,
                          1L,
                          RejectionType.NOT_FOUND,
                          "Group not found"))));

      // when
      webClient
          .put()
          .uri(path)
          .accept(MediaType.APPLICATION_PROBLEM_JSON)
          .exchange()
          .expectStatus()
          .isNotFound();

      // then
      verify(roleServices, times(1)).addMember(request);
    }

    @Test
    void shouldReturnErrorForAssigningGroupToMissingRole() {
      // given
      final String roleId = "roleId";
      final String groupId = "groupId";
      final var path = "%s/%s/groups/%s".formatted(ROLE_BASE_URL, roleId, groupId);
      final var request = new RoleMemberRequest(roleId, groupId, EntityType.GROUP);
      when(roleServices.addMember(request))
          .thenReturn(
              CompletableFuture.failedFuture(
                  ErrorMapper.mapBrokerRejection(
                      new BrokerRejection(
                          RoleIntent.ENTITY_ADDED,
                          1L,
                          RejectionType.NOT_FOUND,
                          "Role not found"))));

      // when
      webClient
          .put()
          .uri(path)
          .accept(MediaType.APPLICATION_PROBLEM_JSON)
          .exchange()
          .expectStatus()
          .isNotFound();

      // then
      verify(roleServices, times(1)).addMember(request);
    }

    @Test
    void shouldReturnErrorForProvidingInvalidGroupIdWhenAssigningToRole() {
      // given
      final String roleId = "roleId";
      final String groupId = "groupId!";
      final var path = "%s/%s/groups/%s".formatted(ROLE_BASE_URL, roleId, groupId);

      // when
      webClient
          .put()
          .uri(path)
          .accept(MediaType.APPLICATION_JSON)
          .contentType(MediaType.APPLICATION_JSON)
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
                "detail": "The provided groupId contains illegal characters. It must match the pattern '%s'.",
                "instance": "%s"
              }"""
                  .formatted(SecurityConfiguration.DEFAULT_ID_REGEX, path),
              JsonCompareMode.STRICT);
      verifyNoInteractions(roleServices);
    }

    @Test
    void shouldReturnErrorForProvidingInvalidRoleIdWhenAssigningGroupToRole() {
      // given
      final String roleId = "roleId!";
      final String groupId = "groupId";
      final var path = "%s/%s/groups/%s".formatted(ROLE_BASE_URL, roleId, groupId);

      // when
      webClient
          .put()
          .uri(path)
          .accept(MediaType.APPLICATION_JSON)
          .contentType(MediaType.APPLICATION_JSON)
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
                "detail": "The provided roleId contains illegal characters. It must match the pattern '%s'.",
                "instance": "%s"
              }"""
                  .formatted(SecurityConfiguration.DEFAULT_ID_REGEX, path),
              JsonCompareMode.STRICT);
      verifyNoInteractions(roleServices);
    }

    @Test
    void shouldUnassignGroupFromRoleAndReturnNoContent() {
      // given
      final var roleId = "roleId";
      final var groupId = "groupId";

      final var request = new RoleMemberRequest(roleId, groupId, EntityType.GROUP);
      when(roleServices.removeMember(request)).thenReturn(CompletableFuture.completedFuture(null));

      // when
      webClient
          .delete()
          .uri("%s/%s/groups/%s".formatted(ROLE_BASE_URL, roleId, groupId))
          .accept(MediaType.APPLICATION_JSON)
          .exchange()
          .expectStatus()
          .isNoContent();

      // then
      verify(roleServices, times(1)).removeMember(request);
    }

    @Test
    void shouldReturnErrorForUnassigningMissingGroupFromRole() {
      // given
      final var roleId = "roleId";
      final var groupId = "groupId";
      final var path = "%s/%s/groups/%s".formatted(ROLE_BASE_URL, roleId, groupId);
      final var request = new RoleMemberRequest(roleId, groupId, EntityType.GROUP);
      when(roleServices.removeMember(request))
          .thenReturn(
              CompletableFuture.failedFuture(
                  ErrorMapper.mapBrokerRejection(
                      new BrokerRejection(
                          RoleIntent.ENTITY_REMOVED,
                          1L,
                          RejectionType.NOT_FOUND,
                          "Group not found"))));

      // when
      webClient
          .delete()
          .uri(path)
          .accept(MediaType.APPLICATION_PROBLEM_JSON)
          .exchange()
          .expectStatus()
          .isNotFound();

      // then
      verify(roleServices, times(1)).removeMember(request);
    }

    @Test
    void shouldReturnErrorForUnassigningGroupFromMissingRole() {
      // given
      final String roleId = "roleId";
      final String groupId = "groupId";
      final var path = "%s/%s/groups/%s".formatted(ROLE_BASE_URL, roleId, groupId);
      final var request = new RoleMemberRequest(roleId, groupId, EntityType.GROUP);
      when(roleServices.removeMember(request))
          .thenReturn(
              CompletableFuture.failedFuture(
                  ErrorMapper.mapBrokerRejection(
                      new BrokerRejection(
                          RoleIntent.ENTITY_ADDED,
                          1L,
                          RejectionType.NOT_FOUND,
                          "Role not found"))));

      // when
      webClient
          .delete()
          .uri(path)
          .accept(MediaType.APPLICATION_PROBLEM_JSON)
          .exchange()
          .expectStatus()
          .isNotFound();

      // then
      verify(roleServices, times(1)).removeMember(request);
    }

    @Test
    void shouldReturnErrorForProvidingInvalidGroupIdWhenUnassigningFromRole() {
      // given
      final String roleId = "roleId";
      final String groupId = "groupId!";
      final var path = "%s/%s/groups/%s".formatted(ROLE_BASE_URL, roleId, groupId);

      // when
      webClient
          .delete()
          .uri(path)
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
                  "detail": "The provided groupId contains illegal characters. It must match the pattern '%s'.",
                  "instance": "%s"
                }"""
                  .formatted(SecurityConfiguration.DEFAULT_ID_REGEX, path),
              JsonCompareMode.STRICT);
      verifyNoInteractions(roleServices);
    }

    @Test
    void shouldReturnErrorForProvidingInvalidRoleIdWhenUnassigningGroupFromRole() {
      // given
      final String roleId = "roleId!";
      final String groupId = "groupId";
      final var path = "%s/%s/groups/%s".formatted(ROLE_BASE_URL, roleId, groupId);

      // when
      webClient
          .delete()
          .uri(path)
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
                  "detail": "The provided roleId contains illegal characters. It must match the pattern '%s'.",
                  "instance": "%s"
                }"""
                  .formatted(SecurityConfiguration.DEFAULT_ID_REGEX, path),
              JsonCompareMode.STRICT);
      verifyNoInteractions(roleServices);
    }
  }

  @Nested
  @WebMvcTest(RoleController.class)
  class ByogEnabledRoleControllerTest extends RestControllerTest {

    @MockitoBean private RoleServices roleServices;
    @MockitoBean private UserServices userServices;
    @MockitoBean private MappingRuleServices mappingRuleServices;
    @MockitoBean private GroupServices groupServices;
    @MockitoBean private CamundaAuthenticationProvider authenticationProvider;

    @BeforeEach
    void setup() {
      when(authenticationProvider.getCamundaAuthentication())
          .thenReturn(AUTHENTICATION_WITH_DEFAULT_TENANT);
      when(roleServices.withAuthentication(any(CamundaAuthentication.class)))
          .thenReturn(roleServices);
      when(userServices.withAuthentication(any(CamundaAuthentication.class)))
          .thenReturn(userServices);
      when(mappingRuleServices.withAuthentication(any(CamundaAuthentication.class)))
          .thenReturn(mappingRuleServices);
      when(groupServices.withAuthentication(any(CamundaAuthentication.class)))
          .thenReturn(groupServices);
    }

    /**
     * Test for the group ID that contains special characters to simulate external groups that does
     * not match the default regex {@link SecurityConfiguration#DEFAULT_ID_REGEX}
     */
    @Test
    void shouldAssignExternalGroupToRoleAndReturnNoContentWhenGroupsAreExternallyManaged() {
      // given
      final var roleId = "roleId";
      final var groupId = "group Id";
      final var request = new RoleMemberRequest(roleId, groupId, EntityType.GROUP);

      when(roleServices.addMember(request)).thenReturn(CompletableFuture.completedFuture(null));

      // when
      webClient
          .put()
          .uri("%s/%s/groups/%s".formatted(ROLE_BASE_URL, roleId, groupId))
          .accept(MediaType.APPLICATION_JSON)
          .exchange()
          .expectStatus()
          .isNoContent();

      // then
      verify(roleServices, times(1)).addMember(request);
    }

    /**
     * Test for the group ID that contains special characters to simulate external groups that does
     * not match the default regex {@link SecurityConfiguration#DEFAULT_ID_REGEX}
     */
    @Test
    void shouldUnassignExternalGroupFromRoleAndReturnNoContentWhenGroupsAreExternallyManaged() {
      // given
      final var roleId = "roleId";
      final var groupId = "group Id";
      final var request = new RoleMemberRequest(roleId, groupId, EntityType.GROUP);

      when(roleServices.removeMember(request)).thenReturn(CompletableFuture.completedFuture(null));

      // when
      webClient
          .delete()
          .uri("%s/%s/groups/%s".formatted(ROLE_BASE_URL, roleId, groupId))
          .accept(MediaType.APPLICATION_JSON)
          .exchange()
          .expectStatus()
          .isNoContent();

      // then
      verify(roleServices, times(1)).removeMember(request);
    }

    @TestConfiguration
    static class AdditionalConfig {
      @Bean
      @Primary
      public IdentifierValidator byogIdentifierValidator() {
        return new IdentifierValidator(ID_PATTERN, DEFAULT_EXTERNAL_ID_REGEX);
      }
    }
  }
}
