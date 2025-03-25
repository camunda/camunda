/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.usermanagement;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.security.auth.Authentication;
import io.camunda.service.GroupServices;
import io.camunda.service.exception.CamundaBrokerException;
import io.camunda.zeebe.broker.client.api.dto.BrokerRejection;
import io.camunda.zeebe.gateway.protocol.rest.GroupChangeset;
import io.camunda.zeebe.gateway.protocol.rest.GroupCreateRequest;
import io.camunda.zeebe.gateway.protocol.rest.GroupUpdateRequest;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import io.camunda.zeebe.protocol.impl.record.value.group.GroupRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.GroupIntent;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

  @Test
  void shouldAcceptCreateGroupRequest() {
    // given
    final var groupId = "groupId";
    final var groupName = "testGroup";
    when(groupServices.createGroup(groupId, groupName))
        .thenReturn(
            CompletableFuture.completedFuture(
                new GroupRecord().setEntityKey(1L).setName(groupName)));

    // when
    webClient
        .post()
        .uri(GROUP_BASE_URL)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(new GroupCreateRequest().name(groupName).groupId(groupId))
        .exchange()
        .expectStatus()
        .isCreated();

    // then
    verify(groupServices, times(1)).createGroup(groupId, groupName);
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
              "detail": "The value for groupId is '%s' but must be less than 256 characters.",
              "instance": "%s"
            }"""
                .formatted(groupId, GROUP_BASE_URL));

    // then
    verifyNoInteractions(groupServices);
  }

  @Test
  void shouldFailOnCreateGroupWithNonAlphanumericGroupId() {
    // given
    final var groupId = "groupId123@";

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
              "detail": "The value for groupId is 'groupId123@' but must be alphanumeric.",
              "instance": "%s"
            }"""
                .formatted(GROUP_BASE_URL));

    // then
    verifyNoInteractions(groupServices);
  }

  @Test
  void shouldUpdateGroupAndReturnNoContent() {
    // given
    final var groupKey = 111L;
    final var groupName = "updatedName";
    when(groupServices.updateGroup(groupKey, groupName))
        .thenReturn(
            CompletableFuture.completedFuture(
                new GroupRecord().setEntityKey(222L).setName(groupName)));

    // when
    webClient
        .patch()
        .uri("%s/%s".formatted(GROUP_BASE_URL, groupKey))
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(new GroupUpdateRequest().changeset(new GroupChangeset().name(groupName)))
        .exchange()
        .expectStatus()
        .isNoContent();

    // then
    verify(groupServices, times(1)).updateGroup(groupKey, groupName);
  }

  @Test
  void shouldFailOnUpdateGroupWithEmptyName() {
    // given
    final var groupKey = 111L;
    final var emptyGroupName = "";
    final var uri = "%s/%s".formatted(GROUP_BASE_URL, groupKey);

    // when / then
    webClient
        .patch()
        .uri(uri)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(new GroupUpdateRequest().changeset(new GroupChangeset().name(emptyGroupName)))
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
  void shouldReturnErrorOnNonExistingGroupUpdate() {
    // given
    final var groupKey = 111L;
    final var groupName = "newName";
    final var path = "%s/%s".formatted(GROUP_BASE_URL, groupKey);
    when(groupServices.updateGroup(groupKey, groupName))
        .thenReturn(
            CompletableFuture.failedFuture(
                new CamundaBrokerException(
                    new BrokerRejection(
                        GroupIntent.UPDATE,
                        groupKey,
                        RejectionType.NOT_FOUND,
                        "Group not found"))));

    // when / then
    webClient
        .patch()
        .uri(path)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(new GroupUpdateRequest().changeset(new GroupChangeset().name(groupName)))
        .exchange()
        .expectStatus()
        .isNotFound();

    verify(groupServices, times(1)).updateGroup(groupKey, groupName);
  }

  @Test
  void deleteGroupShouldReturnNoContent() {
    // given
    final long groupKey = 111L;

    final var groupRecord = new GroupRecord().setGroupKey(groupKey);

    when(groupServices.deleteGroup(groupKey))
        .thenReturn(CompletableFuture.completedFuture(groupRecord));

    // when
    webClient
        .delete()
        .uri("%s/%s".formatted(GROUP_BASE_URL, groupKey))
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isNoContent();

    // then
    verify(groupServices, times(1)).deleteGroup(groupKey);
  }

  @Test
  void shouldAssignUserToGroupAndReturnAccepted() {
    // given
    final long groupKey = 111L;
    final long userKey = 222L;

    when(groupServices.assignMember(groupKey, userKey, EntityType.USER))
        .thenReturn(CompletableFuture.completedFuture(null));

    // when
    webClient
        .post()
        .uri("%s/%s/users/%s".formatted(GROUP_BASE_URL, groupKey, userKey))
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isAccepted();

    // then
    verify(groupServices, times(1)).assignMember(groupKey, userKey, EntityType.USER);
  }

  @Test
  void shouldReturnErrorForAddingMissingUserToGroup() {
    // given
    final var groupKey = 111L;
    final var userKey = 222L;
    final var path = "%s/%d/users/%d".formatted(GROUP_BASE_URL, groupKey, userKey);
    when(groupServices.assignMember(groupKey, userKey, EntityType.USER))
        .thenReturn(
            CompletableFuture.failedFuture(
                new CamundaBrokerException(
                    new BrokerRejection(
                        GroupIntent.ENTITY_ADDED,
                        groupKey,
                        RejectionType.NOT_FOUND,
                        "User not found"))));

    // when
    webClient
        .post()
        .uri(path)
        .accept(MediaType.APPLICATION_PROBLEM_JSON)
        .exchange()
        .expectStatus()
        .isNotFound();

    // then
    verify(groupServices, times(1)).assignMember(groupKey, userKey, EntityType.USER);
  }

  @Test
  void shouldReturnErrorForAddingUserToMissingGroup() {
    // given
    final var groupKey = 111L;
    final var userKey = 222L;
    final var path = "%s/%d/users/%d".formatted(GROUP_BASE_URL, groupKey, userKey);
    when(groupServices.assignMember(groupKey, userKey, EntityType.USER))
        .thenReturn(
            CompletableFuture.failedFuture(
                new CamundaBrokerException(
                    new BrokerRejection(
                        GroupIntent.ENTITY_ADDED,
                        groupKey,
                        RejectionType.NOT_FOUND,
                        "Group not found"))));

    // when
    webClient
        .post()
        .uri(path)
        .accept(MediaType.APPLICATION_PROBLEM_JSON)
        .exchange()
        .expectStatus()
        .isNotFound();

    // then
    verify(groupServices, times(1)).assignMember(groupKey, userKey, EntityType.USER);
  }

  @Test
  void shouldUnassignUserToGroupAndReturnAccepted() {
    // given
    final long groupKey = 111L;
    final long userKey = 222L;

    when(groupServices.removeMember(groupKey, userKey, EntityType.USER))
        .thenReturn(CompletableFuture.completedFuture(null));

    // when
    webClient
        .delete()
        .uri("%s/%s/users/%s".formatted(GROUP_BASE_URL, groupKey, userKey))
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isAccepted();

    // then
    verify(groupServices, times(1)).removeMember(groupKey, userKey, EntityType.USER);
  }

  @Test
  void shouldReturnErrorForRemovingMissingUserFromGroup() {
    // given
    final var groupKey = 111L;
    final var userKey = 222L;
    final var path = "%s/%d/users/%d".formatted(GROUP_BASE_URL, groupKey, userKey);
    when(groupServices.removeMember(groupKey, userKey, EntityType.USER))
        .thenReturn(
            CompletableFuture.failedFuture(
                new CamundaBrokerException(
                    new BrokerRejection(
                        GroupIntent.ENTITY_ADDED,
                        groupKey,
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
    verify(groupServices, times(1)).removeMember(groupKey, userKey, EntityType.USER);
  }

  @Test
  void shouldReturnErrorForRemovingUserFromMissingGroup() {
    // given
    final var groupKey = 111L;
    final var userKey = 222L;
    final var path = "%s/%d/users/%d".formatted(GROUP_BASE_URL, groupKey, userKey);
    when(groupServices.removeMember(groupKey, userKey, EntityType.USER))
        .thenReturn(
            CompletableFuture.failedFuture(
                new CamundaBrokerException(
                    new BrokerRejection(
                        GroupIntent.ENTITY_ADDED,
                        groupKey,
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
    verify(groupServices, times(1)).removeMember(groupKey, userKey, EntityType.USER);
  }
}
