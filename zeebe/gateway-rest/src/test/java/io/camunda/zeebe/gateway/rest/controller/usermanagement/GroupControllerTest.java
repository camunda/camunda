/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.usermanagement;

import static io.camunda.zeebe.gateway.rest.config.ApiFiltersConfiguration.GROUPS_API_DISABLED_ERROR_MESSAGE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.service.GroupServices;
import io.camunda.service.GroupServices.GroupDTO;
import io.camunda.service.GroupServices.GroupMemberDTO;
import io.camunda.service.MappingRuleServices;
import io.camunda.service.RoleServices;
import io.camunda.service.UserServices;
import io.camunda.service.exception.ErrorMapper;
import io.camunda.zeebe.broker.client.api.dto.BrokerRejection;
import io.camunda.zeebe.gateway.protocol.rest.GroupCreateRequest;
import io.camunda.zeebe.gateway.protocol.rest.GroupUpdateRequest;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import io.camunda.zeebe.gateway.rest.config.ApiFiltersConfiguration;
import io.camunda.zeebe.gateway.rest.validator.IdentifierPatterns;
import io.camunda.zeebe.protocol.impl.record.value.group.GroupRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.GroupIntent;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.test.util.Strings;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;

public class GroupControllerTest {

  private static final String GROUP_BASE_URL = "/v2/groups";

  @Nested
  @WebMvcTest(GroupController.class)
  @Import(ApiFiltersConfiguration.class)
  @TestPropertySource(properties = "camunda.security.authentication.oidc.groupsClaim=g1")
  public class CamundaGroupsDisabledTest extends RestControllerTest {

    public static final String FORBIDDEN_MESSAGE =
        """
        {
          "type": "about:blank",
          "status": 403,
          "title": "Access issue",
          "detail": "%%s endpoint is not accessible: %s",
          "instance": "%%s"
        }"""
            .formatted(GROUPS_API_DISABLED_ERROR_MESSAGE);

    @Test
    void shouldReturnErrorOnCreate() {
      // given
      final var groupName = "testGroup";
      final var description = "description";
      final var groupId = "g1";
      // when
      webClient
          .post()
          .uri(GROUP_BASE_URL)
          .accept(MediaType.APPLICATION_JSON)
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(
              new GroupCreateRequest().name(groupName).groupId(groupId).description(description))
          .exchange()
          .expectStatus()
          .isForbidden()
          .expectBody()
          .json(
              FORBIDDEN_MESSAGE.formatted(GROUP_BASE_URL, GROUP_BASE_URL), JsonCompareMode.STRICT);
    }

    @Test
    void shouldReturnErrorOnUpdate() {
      // given
      final var groupKey = 111L;
      final var groupId = "111";
      final var groupName = "updatedName";
      final var description = "updatedDescription";
      final var uri = GROUP_BASE_URL + "/" + groupId;
      // when
      webClient
          .put()
          .uri(uri)
          .accept(MediaType.APPLICATION_JSON)
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(new GroupUpdateRequest().name(groupName).description(description))
          .exchange()
          .expectStatus()
          .isForbidden()
          .expectBody()
          .json(FORBIDDEN_MESSAGE.formatted(uri, uri), JsonCompareMode.STRICT);
    }

    @Test
    void shouldReturnErrorOnDelete() {
      // given
      final String groupId = "111";
      final var uri = GROUP_BASE_URL + "/" + groupId;
      // when
      webClient
          .delete()
          .uri(uri)
          .accept(MediaType.APPLICATION_JSON)
          .exchange()
          .expectStatus()
          .isForbidden()
          .expectBody()
          .json(FORBIDDEN_MESSAGE.formatted(uri, uri), JsonCompareMode.STRICT);
    }

    @Test
    void shouldReturnErrorOnAssignUser() {
      // given
      final String groupId = "111";
      final String username = "222";
      final var uri = "%s/%s/users/%s".formatted(GROUP_BASE_URL, groupId, username);

      // when
      webClient
          .put()
          .uri(uri)
          .accept(MediaType.APPLICATION_JSON)
          .exchange()
          .expectStatus()
          .isForbidden()
          .expectBody()
          .json(FORBIDDEN_MESSAGE.formatted(uri, uri), JsonCompareMode.STRICT);
    }

    @Test
    void shouldReturnErrorOnAssignMappingRule() {
      // given
      final var groupId = Strings.newRandomValidIdentityId();
      final var mappingRuleId = Strings.newRandomValidIdentityId();
      final var uri = "%s/%s/mapping-rules/%s".formatted(GROUP_BASE_URL, groupId, mappingRuleId);
      // when
      webClient
          .put()
          .uri(uri)
          .accept(MediaType.APPLICATION_JSON)
          .exchange()
          .expectStatus()
          .isForbidden()
          .expectBody()
          .json(FORBIDDEN_MESSAGE.formatted(uri, uri), JsonCompareMode.STRICT);
    }

    @Test
    void shouldReturnErrorOnUnassignUser() {
      // given
      final String groupId = "111";
      final String username = "222";
      final var uri = "%s/%s/users/%s".formatted(GROUP_BASE_URL, groupId, username);
      // when
      webClient
          .delete()
          .uri(uri)
          .accept(MediaType.APPLICATION_JSON)
          .exchange()
          .expectStatus()
          .isForbidden()
          .expectBody()
          .json(FORBIDDEN_MESSAGE.formatted(uri, uri), JsonCompareMode.STRICT);
    }

    @Test
    void shouldReturnErrorOnUnassignMappingRule() {
      // given
      final var groupId = Strings.newRandomValidIdentityId();
      final var mappingRuleId = Strings.newRandomValidIdentityId();

      final var uri = "%s/%s/mapping-rules/%s".formatted(GROUP_BASE_URL, groupId, mappingRuleId);

      // when
      webClient
          .delete()
          .uri(uri)
          .accept(MediaType.APPLICATION_PROBLEM_JSON)
          .exchange()
          .expectStatus()
          .isForbidden()
          .expectBody()
          .json(FORBIDDEN_MESSAGE.formatted(uri, uri), JsonCompareMode.STRICT);
    }
  }

  @Nested
  @WebMvcTest(GroupController.class)
  @TestPropertySource(properties = "camunda.security.authentication.oidc.groupsClaim=")
  public class CamundaGroupsEnabledTest extends RestControllerTest {
    @MockitoBean private GroupServices groupServices;
    @MockitoBean private UserServices userServices;
    @MockitoBean private RoleServices roleServices;
    @MockitoBean private MappingRuleServices mappingRuleServices;
    @MockitoBean private CamundaAuthenticationProvider authenticationProvider;

    @BeforeEach
    void setup() {
      when(authenticationProvider.getCamundaAuthentication())
          .thenReturn(AUTHENTICATION_WITH_DEFAULT_TENANT);
      when(groupServices.withAuthentication(any(CamundaAuthentication.class)))
          .thenReturn(groupServices);
    }

    @ParameterizedTest
    @ValueSource(strings = {"foo", "Foo", "foo123", "foo_", "foo.", "foo@"})
    void shouldAcceptCreateGroupRequest(final String groupId) {
      // given
      final var groupName = "testGroup";
      final var description = "description";
      final var createGroupRequest = new GroupDTO(groupId, groupName, description);
      when(groupServices.createGroup(createGroupRequest))
          .thenReturn(
              CompletableFuture.completedFuture(
                  new GroupRecord()
                      .setGroupId(groupId)
                      .setEntityId("memberId")
                      .setName(groupName)));

      // when
      webClient
          .post()
          .uri(GROUP_BASE_URL)
          .accept(MediaType.APPLICATION_JSON)
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(
              new GroupCreateRequest().name(groupName).groupId(groupId).description(description))
          .exchange()
          .expectStatus()
          .isCreated();

      // then
      verify(groupServices, times(1)).createGroup(createGroupRequest);
    }

    @Test
    void shouldFailOnCreateGroupWithNoName() {
      // when
      webClient
          .post()
          .uri(GROUP_BASE_URL)
          .accept(MediaType.APPLICATION_JSON)
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(new GroupCreateRequest().name("").groupId("groupId"))
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
                  .formatted(GROUP_BASE_URL),
              JsonCompareMode.STRICT);

      // then
      verifyNoInteractions(groupServices);
    }

    @Test
    void shouldFailOnCreateGroupWithNoGroupId() {
      // when
      webClient
          .post()
          .uri(GROUP_BASE_URL)
          .accept(MediaType.APPLICATION_JSON)
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(new GroupCreateRequest().name("name"))
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
              "detail": "No groupId provided.",
              "instance": "%s"
            }"""
                  .formatted(GROUP_BASE_URL),
              JsonCompareMode.STRICT);

      // then
      verifyNoInteractions(groupServices);
    }

    @Test
    void shouldFailOnCreateGroupWithEmptyGroupId() {
      // when
      webClient
          .post()
          .uri(GROUP_BASE_URL)
          .accept(MediaType.APPLICATION_JSON)
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(new GroupCreateRequest().name("name").groupId(""))
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
              "detail": "No groupId provided.",
              "instance": "%s"
            }"""
                  .formatted(GROUP_BASE_URL),
              JsonCompareMode.STRICT);

      // then
      verifyNoInteractions(groupServices);
    }

    @Test
    void shouldFailOnCreateGroupWithTooLongGroupId() {
      // given
      final var groupId = "x".repeat(257);

      // when
      webClient
          .post()
          .uri(GROUP_BASE_URL)
          .accept(MediaType.APPLICATION_JSON)
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(new GroupCreateRequest().name("name").groupId(groupId))
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
              "detail": "The provided groupId exceeds the limit of 256 characters.",
              "instance": "%s"
            }"""
                  .formatted(GROUP_BASE_URL),
              JsonCompareMode.STRICT);

      // then
      verifyNoInteractions(groupServices);
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
          "foo~", "foo!", "foo#", "foo$", "foo%", "foo^", "foo&", "foo*", "foo(", "foo)", "foo=",
          "foo+", "foo{", "foo[", "foo}", "foo]", "foo|", "foo\\", "foo:", "foo;", "foo\"", "foo'",
          "foo<", "foo>", "foo,", "foo?", "foo/", "foo ", "foo\t", "foo\n", "foo\r"
        })
    void shouldRejectGroupCreationWithIllegalCharactersInId(final String groupId) {
      // when then
      webClient
          .post()
          .uri(GROUP_BASE_URL)
          .accept(MediaType.APPLICATION_JSON)
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(new GroupCreateRequest().name("name").groupId(groupId))
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
                  .formatted(IdentifierPatterns.ID_PATTERN, GROUP_BASE_URL),
              JsonCompareMode.STRICT);
      verifyNoInteractions(groupServices);
    }

    @Test
    void shouldUpdateGroupAndReturnResponse() {
      // given
      final var groupKey = 111L;
      final var groupId = "111";
      final var groupName = "updatedName";
      final var description = "updatedDescription";
      when(groupServices.updateGroup(groupId, groupName, description))
          .thenReturn(
              CompletableFuture.completedFuture(
                  new GroupRecord()
                      .setGroupKey(groupKey)
                      .setGroupId(groupId)
                      .setName(groupName)
                      .setDescription(description)));

      // when
      webClient
          .put()
          .uri("%s/%s".formatted(GROUP_BASE_URL, groupId))
          .accept(MediaType.APPLICATION_JSON)
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(new GroupUpdateRequest().name(groupName).description(description))
          .exchange()
          .expectStatus()
          .isOk()
          .expectBody()
          .json(
              """
            {
              "groupId": "%s",
              "name": "%s",
              "description": "%s"
            }
            """
                  .formatted(groupId, groupName, description),
              JsonCompareMode.STRICT);

      // then
      verify(groupServices, times(1)).updateGroup(groupId, groupName, description);
    }

    @Test
    void shouldFailOnUpdateGroupWithEmptyName() {
      // given
      final var groupId = "groupId";
      final var emptyGroupName = "";
      final var uri = "%s/%s".formatted(GROUP_BASE_URL, groupId);

      // when / then
      webClient
          .put()
          .uri(uri)
          .accept(MediaType.APPLICATION_JSON)
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(new GroupUpdateRequest().name(emptyGroupName).description("description"))
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

      verifyNoInteractions(groupServices);
    }

    @Test
    void shouldFailOnUpdateGroupWithoutDescription() {
      // given
      final var groupId = "groupId";
      final var name = "name";
      final var uri = "%s/%s".formatted(GROUP_BASE_URL, groupId);

      // when / then
      webClient
          .put()
          .uri(uri)
          .accept(MediaType.APPLICATION_JSON)
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(new GroupUpdateRequest().name(name))
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

      verifyNoInteractions(groupServices);
    }

    @Test
    void shouldReturnErrorOnNonExistingGroupUpdate() {
      // given
      final var groupId = "111";
      final var groupName = "newName";
      final var path = "%s/%s".formatted(GROUP_BASE_URL, groupId);
      final var description = "updatedDescription";
      when(groupServices.updateGroup(groupId, groupName, description))
          .thenReturn(
              CompletableFuture.failedFuture(
                  ErrorMapper.mapBrokerRejection(
                      new BrokerRejection(
                          GroupIntent.UPDATE, 1L, RejectionType.NOT_FOUND, "Group not found"))));

      // when / then
      webClient
          .put()
          .uri(path)
          .accept(MediaType.APPLICATION_JSON)
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(new GroupUpdateRequest().name(groupName).description(description))
          .exchange()
          .expectStatus()
          .isNotFound();

      verify(groupServices, times(1)).updateGroup(groupId, groupName, description);
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
          "foo~", "foo!", "foo$", "foo&", "foo*", "foo(", "foo)", "foo=", "foo+", "foo:", "foo'",
          "foo,"
        })
    void shouldRejectGroupUpdateWithIllegalCharactersInId(final String groupId) {
      // when then
      final var path = "%s/%s".formatted(GROUP_BASE_URL, groupId);
      webClient
          .put()
          .uri(path)
          .accept(MediaType.APPLICATION_JSON)
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(new GroupUpdateRequest().name("updatedName").description("description"))
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
                  .formatted(IdentifierPatterns.ID_PATTERN, path),
              JsonCompareMode.STRICT);
      verifyNoInteractions(groupServices);
    }

    @Test
    void deleteGroupShouldReturnNoContent() {
      // given
      final String groupId = "111";

      final var groupRecord = new GroupRecord().setGroupId(groupId);

      when(groupServices.deleteGroup(groupId))
          .thenReturn(CompletableFuture.completedFuture(groupRecord));

      // when
      webClient
          .delete()
          .uri("%s/%s".formatted(GROUP_BASE_URL, groupId))
          .accept(MediaType.APPLICATION_JSON)
          .exchange()
          .expectStatus()
          .isNoContent();

      // then
      verify(groupServices, times(1)).deleteGroup(groupId);
    }

    @Test
    void shouldAssignUserToGroupAndReturnNoContent() {
      // given
      final String groupId = "111";
      final String username = "222";
      final var request = new GroupMemberDTO(groupId, username, EntityType.USER);
      when(groupServices.assignMember(request)).thenReturn(CompletableFuture.completedFuture(null));

      // when
      webClient
          .put()
          .uri("%s/%s/users/%s".formatted(GROUP_BASE_URL, groupId, username))
          .accept(MediaType.APPLICATION_JSON)
          .exchange()
          .expectStatus()
          .isNoContent();

      // then
      verify(groupServices, times(1)).assignMember(request);
    }

    @Test
    void shouldReturnErrorForAddingMissingUserToGroup() {
      // given
      final String groupId = "111";
      final String username = "222";
      final var path = "%s/%s/users/%s".formatted(GROUP_BASE_URL, groupId, username);
      final var request = new GroupMemberDTO(groupId, username, EntityType.USER);
      when(groupServices.assignMember(request))
          .thenReturn(
              CompletableFuture.failedFuture(
                  ErrorMapper.mapBrokerRejection(
                      new BrokerRejection(
                          GroupIntent.ENTITY_ADDED,
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
      verify(groupServices, times(1)).assignMember(request);
    }

    @Test
    void shouldReturnErrorForAddingUserToMissingGroup() {
      // given
      final String groupId = "111";
      final String username = "222";
      final var path = "%s/%s/users/%s".formatted(GROUP_BASE_URL, groupId, username);
      final var request = new GroupMemberDTO(groupId, username, EntityType.USER);
      when(groupServices.assignMember(request))
          .thenReturn(
              CompletableFuture.failedFuture(
                  ErrorMapper.mapBrokerRejection(
                      new BrokerRejection(
                          GroupIntent.ENTITY_ADDED,
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
      verify(groupServices, times(1)).assignMember(request);
    }

    @Test
    void shouldAssignMemberToGroupAndReturnNoContent() {
      // given
      final var groupId = Strings.newRandomValidIdentityId();
      final var mappingRuleId = Strings.newRandomValidIdentityId();
      final var request = new GroupMemberDTO(groupId, mappingRuleId, EntityType.MAPPING_RULE);
      when(groupServices.assignMember(request)).thenReturn(CompletableFuture.completedFuture(null));

      // when
      webClient
          .put()
          .uri("%s/%s/mapping-rules/%s".formatted(GROUP_BASE_URL, groupId, mappingRuleId))
          .accept(MediaType.APPLICATION_JSON)
          .exchange()
          .expectStatus()
          .isNoContent();

      // then
      verify(groupServices, times(1)).assignMember(request);
    }

    @Test
    void shouldReturnErrorForAddingMissingMappingRuleToGroup() {
      // given
      final var groupId = Strings.newRandomValidIdentityId();
      final var mappingRuleId = Strings.newRandomValidIdentityId();
      final var request = new GroupMemberDTO(groupId, mappingRuleId, EntityType.MAPPING_RULE);
      when(groupServices.assignMember(request))
          .thenReturn(
              CompletableFuture.failedFuture(
                  ErrorMapper.mapBrokerRejection(
                      new BrokerRejection(
                          GroupIntent.ENTITY_ADDED,
                          1L,
                          RejectionType.NOT_FOUND,
                          "Mapping rule not found"))));

      // when
      webClient
          .put()
          .uri("%s/%s/mapping-rules/%s".formatted(GROUP_BASE_URL, groupId, mappingRuleId))
          .accept(MediaType.APPLICATION_PROBLEM_JSON)
          .exchange()
          .expectStatus()
          .isNotFound();

      // then
      verify(groupServices, times(1)).assignMember(request);
    }

    @Test
    void shouldReturnErrorForAddingMappingRuleToMissingGroup() {
      // given
      final var groupId = Strings.newRandomValidIdentityId();
      final var mappingRuleId = Strings.newRandomValidIdentityId();
      final var request = new GroupMemberDTO(groupId, mappingRuleId, EntityType.MAPPING_RULE);
      when(groupServices.assignMember(request))
          .thenReturn(
              CompletableFuture.failedFuture(
                  ErrorMapper.mapBrokerRejection(
                      new BrokerRejection(
                          GroupIntent.ENTITY_ADDED,
                          1L,
                          RejectionType.NOT_FOUND,
                          "Group not found"))));

      // when
      webClient
          .put()
          .uri("%s/%s/mapping-rules/%s".formatted(GROUP_BASE_URL, groupId, mappingRuleId))
          .accept(MediaType.APPLICATION_PROBLEM_JSON)
          .exchange()
          .expectStatus()
          .isNotFound();

      // then
      verify(groupServices, times(1)).assignMember(request);
    }

    @Test
    void shouldReturnErrorForProvidingInvalidMappingRuleIdWhenAddingToGroup() {
      // given
      final String groupId = Strings.newRandomValidIdentityId();
      final String mappingRuleId = "mappingRuleId!";
      final var path = "%s/%s/mapping-rules/%s".formatted(GROUP_BASE_URL, groupId, mappingRuleId);

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
                  .formatted(IdentifierPatterns.ID_PATTERN, path),
              JsonCompareMode.STRICT);
      verifyNoInteractions(groupServices);
    }

    @Test
    void shouldReturnErrorForProvidingInvalidGroupIdWhenAddingToGroup() {
      // given
      final String groupId = "groupId!";
      final String mappingRuleId = Strings.newRandomValidIdentityId();
      final var path = "%s/%s/mapping-rules/%s".formatted(GROUP_BASE_URL, groupId, mappingRuleId);

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
                  .formatted(IdentifierPatterns.ID_PATTERN, path),
              JsonCompareMode.STRICT);
      verifyNoInteractions(groupServices);
    }

    @Test
    void shouldUnassignUserToGroupAndReturnNoContent() {
      // given
      final String groupId = "111";
      final String username = "222";
      final var request = new GroupMemberDTO(groupId, username, EntityType.USER);
      when(groupServices.removeMember(request)).thenReturn(CompletableFuture.completedFuture(null));

      // when
      webClient
          .delete()
          .uri("%s/%s/users/%s".formatted(GROUP_BASE_URL, groupId, username))
          .accept(MediaType.APPLICATION_JSON)
          .exchange()
          .expectStatus()
          .isNoContent();

      // then
      verify(groupServices, times(1)).removeMember(request);
    }

    @Test
    void shouldReturnErrorForRemovingMissingUserFromGroup() {
      // given
      final String groupId = "111";
      final String username = "222";
      final var path = "%s/%s/users/%s".formatted(GROUP_BASE_URL, groupId, username);
      final var request = new GroupMemberDTO(groupId, username, EntityType.USER);
      when(groupServices.removeMember(request))
          .thenReturn(
              CompletableFuture.failedFuture(
                  ErrorMapper.mapBrokerRejection(
                      new BrokerRejection(
                          GroupIntent.ENTITY_ADDED,
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
      verify(groupServices, times(1)).removeMember(request);
    }

    @Test
    void shouldReturnErrorForRemovingUserFromMissingGroup() {
      // given
      final String groupId = "111";
      final String username = "222";
      final var path = "%s/%s/users/%s".formatted(GROUP_BASE_URL, groupId, username);
      final var request = new GroupMemberDTO(groupId, username, EntityType.USER);
      when(groupServices.removeMember(request))
          .thenReturn(
              CompletableFuture.failedFuture(
                  ErrorMapper.mapBrokerRejection(
                      new BrokerRejection(
                          GroupIntent.ENTITY_ADDED,
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
      verify(groupServices, times(1)).removeMember(request);
    }

    @Test
    void shouldUnassignMemberFromGroupAndReturnNoContent() {
      // given
      final var groupId = Strings.newRandomValidIdentityId();
      final var mappingRuleId = Strings.newRandomValidIdentityId();
      final var request = new GroupMemberDTO(groupId, mappingRuleId, EntityType.MAPPING_RULE);
      when(groupServices.removeMember(request)).thenReturn(CompletableFuture.completedFuture(null));

      // when
      webClient
          .delete()
          .uri("%s/%s/mapping-rules/%s".formatted(GROUP_BASE_URL, groupId, mappingRuleId))
          .accept(MediaType.APPLICATION_JSON)
          .exchange()
          .expectStatus()
          .isNoContent();

      // then
      verify(groupServices, times(1)).removeMember(request);
    }

    @Test
    void shouldReturnErrorForRemovingMissingMappingFromGroup() {
      // given
      final var groupId = Strings.newRandomValidIdentityId();
      final var mappingRuleId = Strings.newRandomValidIdentityId();
      final var request = new GroupMemberDTO(groupId, mappingRuleId, EntityType.MAPPING_RULE);
      when(groupServices.removeMember(request))
          .thenReturn(
              CompletableFuture.failedFuture(
                  ErrorMapper.mapBrokerRejection(
                      new BrokerRejection(
                          GroupIntent.ENTITY_ADDED,
                          1L,
                          RejectionType.NOT_FOUND,
                          "Mapping not found"))));

      // when
      webClient
          .delete()
          .uri("%s/%s/mapping-rules/%s".formatted(GROUP_BASE_URL, groupId, mappingRuleId))
          .accept(MediaType.APPLICATION_PROBLEM_JSON)
          .exchange()
          .expectStatus()
          .isNotFound();

      // then
      verify(groupServices, times(1)).removeMember(request);
    }

    @Test
    void shouldReturnErrorForRemovingMappingRuleFromMissingGroup() {
      // given
      final var groupId = Strings.newRandomValidIdentityId();
      final var mappingRuleId = Strings.newRandomValidIdentityId();
      final var request = new GroupMemberDTO(groupId, mappingRuleId, EntityType.MAPPING_RULE);
      when(groupServices.removeMember(request))
          .thenReturn(
              CompletableFuture.failedFuture(
                  ErrorMapper.mapBrokerRejection(
                      new BrokerRejection(
                          GroupIntent.ENTITY_ADDED,
                          1L,
                          RejectionType.NOT_FOUND,
                          "Group not found"))));

      // when
      webClient
          .delete()
          .uri("%s/%s/mapping-rules/%s".formatted(GROUP_BASE_URL, groupId, mappingRuleId))
          .accept(MediaType.APPLICATION_PROBLEM_JSON)
          .exchange()
          .expectStatus()
          .isNotFound();

      // then
      verify(groupServices, times(1)).removeMember(request);
    }

    @Test
    void shouldReturnErrorForProvidingInvalidMappingRuleIdWhenRemovingFromGroup() {
      // given
      final var groupId = Strings.newRandomValidIdentityId();
      final var mappingRuleId = "mappingRuleId!";
      final var path = "%s/%s/mapping-rules/%s".formatted(GROUP_BASE_URL, groupId, mappingRuleId);

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
                  "detail": "The provided mappingRuleId contains illegal characters. It must match the pattern '%s'.",
                  "instance": "%s"
                }"""
                  .formatted(IdentifierPatterns.ID_PATTERN, path),
              JsonCompareMode.STRICT);
      verifyNoInteractions(groupServices);
    }

    @Test
    void shouldReturnErrorForProvidingInvalidGroupIdWhenRemovingFromGroup() {
      // given
      final String groupId = "groupId!";
      final var mappingRuleId = Strings.newRandomValidIdentityId();
      final var path = "%s/%s/mapping-rules/%s".formatted(GROUP_BASE_URL, groupId, mappingRuleId);

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
                  .formatted(IdentifierPatterns.ID_PATTERN, path),
              JsonCompareMode.STRICT);
      verifyNoInteractions(groupServices);
    }
  }
}
