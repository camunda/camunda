/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.usermanagement;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.security.auth.Authentication;
import io.camunda.service.GroupServices;
import io.camunda.service.GroupServices.GroupDTO;
import io.camunda.service.exception.CamundaBrokerException;
import io.camunda.zeebe.broker.client.api.dto.BrokerRejection;
import io.camunda.zeebe.gateway.protocol.rest.GroupCreateRequest;
import io.camunda.zeebe.gateway.protocol.rest.GroupUpdateRequest;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import io.camunda.zeebe.gateway.rest.validator.IdentifierPatterns;
import io.camunda.zeebe.protocol.impl.record.value.group.GroupRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.GroupIntent;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.test.util.Strings;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;

@WebMvcTest(GroupController.class)
public class GroupControllerTest extends RestControllerTest {

  private static final String GROUP_BASE_URL = "/v2/groups";

  @MockBean private GroupServices groupServices;

  @BeforeEach
  void setup() {
    when(groupServices.withAuthentication(any(Authentication.class))).thenReturn(groupServices);
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
                new GroupRecord().setGroupId(groupId).setEntityId("entityId").setName(groupName)));

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
                .formatted(GROUP_BASE_URL));

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
                .formatted(GROUP_BASE_URL));

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
                .formatted(GROUP_BASE_URL));

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
                .formatted(GROUP_BASE_URL));

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
                .formatted(IdentifierPatterns.ID_PATTERN, GROUP_BASE_URL));
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
              "groupKey": "%d",
              "groupId": "%s",
              "name": "%s",
              "description": "%s"
            }
            """
                .formatted(groupKey, groupId, groupName, description));

    // then
    verify(groupServices, times(1)).updateGroup(groupId, groupName, description);
  }

  @Test
  void shouldFailOnUpdateGroupWithEmptyName() {
    // given
    final var groupKey = 111L;
    final var emptyGroupName = "";
    final var uri = "%s/%s".formatted(GROUP_BASE_URL, groupKey);

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
                .formatted(uri));

    verifyNoInteractions(groupServices);
  }

  @Test
  void shouldFailOnUpdateGroupWithoutDescription() {
    // given
    final var groupKey = 111L;
    final var name = "name";
    final var uri = "%s/%s".formatted(GROUP_BASE_URL, groupKey);

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
                .formatted(uri));

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
                new CamundaBrokerException(
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
                .formatted(IdentifierPatterns.ID_PATTERN, path));
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
  void shouldAssignUserToGroupAndReturnAccepted() {
    // given
    final String groupId = "111";
    final String username = "222";

    when(groupServices.assignMember(groupId, username, EntityType.USER))
        .thenReturn(CompletableFuture.completedFuture(null));

    // when
    webClient
        .put()
        .uri("%s/%s/users/%s".formatted(GROUP_BASE_URL, groupId, username))
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isAccepted();

    // then
    verify(groupServices, times(1)).assignMember(groupId, username, EntityType.USER);
  }

  @Test
  void shouldReturnErrorForAddingMissingUserToGroup() {
    // given
    final String groupId = "111";
    final String username = "222";
    final var path = "%s/%s/users/%s".formatted(GROUP_BASE_URL, groupId, username);
    when(groupServices.assignMember(groupId, username, EntityType.USER))
        .thenReturn(
            CompletableFuture.failedFuture(
                new CamundaBrokerException(
                    new BrokerRejection(
                        GroupIntent.ENTITY_ADDED, 1L, RejectionType.NOT_FOUND, "User not found"))));

    // when
    webClient
        .put()
        .uri(path)
        .accept(MediaType.APPLICATION_PROBLEM_JSON)
        .exchange()
        .expectStatus()
        .isNotFound();

    // then
    verify(groupServices, times(1)).assignMember(groupId, username, EntityType.USER);
  }

  @Test
  void shouldReturnErrorForAddingUserToMissingGroup() {
    // given
    final String groupId = "111";
    final String username = "222";
    final var path = "%s/%s/users/%s".formatted(GROUP_BASE_URL, groupId, username);
    when(groupServices.assignMember(groupId, username, EntityType.USER))
        .thenReturn(
            CompletableFuture.failedFuture(
                new CamundaBrokerException(
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
    verify(groupServices, times(1)).assignMember(groupId, username, EntityType.USER);
  }

  @Test
  void shouldAssignMappingToGroupAndReturnAccepted() {
    // given
    final var groupId = Strings.newRandomValidIdentityId();
    final var mappingId = Strings.newRandomValidIdentityId();
    when(groupServices.assignMember(groupId, mappingId, EntityType.MAPPING))
        .thenReturn(CompletableFuture.completedFuture(null));

    // when
    webClient
        .put()
        .uri("%s/%s/mapping-rules/%s".formatted(GROUP_BASE_URL, groupId, mappingId))
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isAccepted();

    // then
    verify(groupServices, times(1)).assignMember(anyString(), anyString(), any());
  }

  @Test
  void shouldReturnErrorForAddingMissingMappingToGroup() {
    // given
    final var groupId = Strings.newRandomValidIdentityId();
    final var mappingId = Strings.newRandomValidIdentityId();
    when(groupServices.assignMember(groupId, mappingId, EntityType.MAPPING))
        .thenReturn(
            CompletableFuture.failedFuture(
                new CamundaBrokerException(
                    new BrokerRejection(
                        GroupIntent.ENTITY_ADDED,
                        1L,
                        RejectionType.NOT_FOUND,
                        "Mapping rule not found"))));

    // when
    webClient
        .put()
        .uri("%s/%s/mapping-rules/%s".formatted(GROUP_BASE_URL, groupId, mappingId))
        .accept(MediaType.APPLICATION_PROBLEM_JSON)
        .exchange()
        .expectStatus()
        .isNotFound();

    // then
    verify(groupServices, times(1)).assignMember(anyString(), anyString(), any());
  }

  @Test
  void shouldReturnErrorForAddingMappingToMissingGroup() {
    // given
    final var groupId = Strings.newRandomValidIdentityId();
    final var mappingId = Strings.newRandomValidIdentityId();
    when(groupServices.assignMember(groupId, mappingId, EntityType.MAPPING))
        .thenReturn(
            CompletableFuture.failedFuture(
                new CamundaBrokerException(
                    new BrokerRejection(
                        GroupIntent.ENTITY_ADDED,
                        1L,
                        RejectionType.NOT_FOUND,
                        "Group not found"))));

    // when
    webClient
        .put()
        .uri("%s/%s/mapping-rules/%s".formatted(GROUP_BASE_URL, groupId, mappingId))
        .accept(MediaType.APPLICATION_PROBLEM_JSON)
        .exchange()
        .expectStatus()
        .isNotFound();

    // then
    verify(groupServices, times(1)).assignMember(anyString(), anyString(), any());
  }

  @Test
  void shouldReturnErrorForProvidingInvalidMappingIdWhenAddingToGroup() {
    // given
    final String groupId = Strings.newRandomValidIdentityId();
    final String mappingId = "mappingId!";
    final var path = "%s/%s/mapping-rules/%s".formatted(GROUP_BASE_URL, groupId, mappingId);

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
                "detail": "The provided mappingId contains illegal characters. It must match the pattern '%s'.",
                "instance": "%s"
              }"""
                .formatted(IdentifierPatterns.ID_PATTERN, path));
    verifyNoInteractions(groupServices);
  }

  @Test
  void shouldReturnErrorForProvidingInvalidGroupIdWhenAddingToGroup() {
    // given
    final String groupId = "groupId!";
    final String mappingId = Strings.newRandomValidIdentityId();
    final var path = "%s/%s/mapping-rules/%s".formatted(GROUP_BASE_URL, groupId, mappingId);

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
                .formatted(IdentifierPatterns.ID_PATTERN, path));
    verifyNoInteractions(groupServices);
  }

  @Test
  void shouldUnassignUserToGroupAndReturnAccepted() {
    // given
    final String groupId = "111";
    final String username = "222";

    when(groupServices.removeMember(groupId, username, EntityType.USER))
        .thenReturn(CompletableFuture.completedFuture(null));

    // when
    webClient
        .delete()
        .uri("%s/%s/users/%s".formatted(GROUP_BASE_URL, groupId, username))
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isAccepted();

    // then
    verify(groupServices, times(1)).removeMember(groupId, username, EntityType.USER);
  }

  @Test
  void shouldReturnErrorForRemovingMissingUserFromGroup() {
    // given
    final String groupId = "111";
    final String username = "222";
    final var path = "%s/%s/users/%s".formatted(GROUP_BASE_URL, groupId, username);
    when(groupServices.removeMember(groupId, username, EntityType.USER))
        .thenReturn(
            CompletableFuture.failedFuture(
                new CamundaBrokerException(
                    new BrokerRejection(
                        GroupIntent.ENTITY_ADDED, 1L, RejectionType.NOT_FOUND, "User not found"))));

    // when
    webClient
        .delete()
        .uri(path)
        .accept(MediaType.APPLICATION_PROBLEM_JSON)
        .exchange()
        .expectStatus()
        .isNotFound();

    // then
    verify(groupServices, times(1)).removeMember(groupId, username, EntityType.USER);
  }

  @Test
  void shouldReturnErrorForRemovingUserFromMissingGroup() {
    // given
    final String groupId = "111";
    final String username = "222";
    final var path = "%s/%s/users/%s".formatted(GROUP_BASE_URL, groupId, username);
    when(groupServices.removeMember(groupId, username, EntityType.USER))
        .thenReturn(
            CompletableFuture.failedFuture(
                new CamundaBrokerException(
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
    verify(groupServices, times(1)).removeMember(groupId, username, EntityType.USER);
  }

  @Test
  void shouldUnassignMappingFromGroupAndReturnAccepted() {
    // given
    final var groupId = Strings.newRandomValidIdentityId();
    final var mappingId = Strings.newRandomValidIdentityId();
    when(groupServices.removeMember(groupId, mappingId, EntityType.MAPPING))
        .thenReturn(CompletableFuture.completedFuture(null));

    // when
    webClient
        .delete()
        .uri("%s/%s/mapping-rules/%s".formatted(GROUP_BASE_URL, groupId, mappingId))
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isAccepted();

    // then
    verify(groupServices, times(1)).removeMember(anyString(), anyString(), any());
  }

  @Test
  void shouldReturnErrorForRemovingMissingMappingFromGroup() {
    // given
    final var groupId = Strings.newRandomValidIdentityId();
    final var mappingId = Strings.newRandomValidIdentityId();
    when(groupServices.removeMember(groupId, mappingId, EntityType.MAPPING))
        .thenReturn(
            CompletableFuture.failedFuture(
                new CamundaBrokerException(
                    new BrokerRejection(
                        GroupIntent.ENTITY_ADDED,
                        1L,
                        RejectionType.NOT_FOUND,
                        "Mapping not found"))));

    // when
    webClient
        .delete()
        .uri("%s/%s/mapping-rules/%s".formatted(GROUP_BASE_URL, groupId, mappingId))
        .accept(MediaType.APPLICATION_PROBLEM_JSON)
        .exchange()
        .expectStatus()
        .isNotFound();

    // then
    verify(groupServices, times(1)).removeMember(anyString(), anyString(), any());
  }

  @Test
  void shouldReturnErrorForRemovingMappingFromMissingGroup() {
    // given
    final var groupId = Strings.newRandomValidIdentityId();
    final var mappingId = Strings.newRandomValidIdentityId();
    when(groupServices.removeMember(groupId, mappingId, EntityType.MAPPING))
        .thenReturn(
            CompletableFuture.failedFuture(
                new CamundaBrokerException(
                    new BrokerRejection(
                        GroupIntent.ENTITY_ADDED,
                        1L,
                        RejectionType.NOT_FOUND,
                        "Group not found"))));

    // when
    webClient
        .delete()
        .uri("%s/%s/mapping-rules/%s".formatted(GROUP_BASE_URL, groupId, mappingId))
        .accept(MediaType.APPLICATION_PROBLEM_JSON)
        .exchange()
        .expectStatus()
        .isNotFound();

    // then
    verify(groupServices, times(1)).removeMember(anyString(), anyString(), any());
  }

  @Test
  void shouldReturnErrorForProvidingInvalidMappingIdWhenRemovingFromGroup() {
    // given
    final var groupId = Strings.newRandomValidIdentityId();
    final var mappingId = "mappingId!";
    final var path = "%s/%s/mapping-rules/%s".formatted(GROUP_BASE_URL, groupId, mappingId);

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
                  "detail": "The provided mappingId contains illegal characters. It must match the pattern '%s'.",
                  "instance": "%s"
                }"""
                .formatted(IdentifierPatterns.ID_PATTERN, path));
    verifyNoInteractions(groupServices);
  }

  @Test
  void shouldReturnErrorForProvidingInvalidGroupIdWhenRemovingFromGroup() {
    // given
    final String groupId = "groupId!";
    final var mappingId = Strings.newRandomValidIdentityId();
    final var path = "%s/%s/mapping-rules/%s".formatted(GROUP_BASE_URL, groupId, mappingId);

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
                .formatted(IdentifierPatterns.ID_PATTERN, path));
    verifyNoInteractions(groupServices);
  }
}
