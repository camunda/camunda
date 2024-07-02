/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.identity.automation.usermanagement.CamundaGroup;
import io.camunda.identity.automation.usermanagement.service.GroupService;
import io.camunda.identity.automation.usermanagement.service.UserGroupMembershipService;
import io.camunda.zeebe.gateway.protocol.rest.CamundaGroupRequest;
import io.camunda.zeebe.gateway.protocol.rest.CamundaGroupResponse;
import io.camunda.zeebe.gateway.protocol.rest.GroupSearchResponse;
import io.camunda.zeebe.gateway.protocol.rest.SearchQueryRequest;
import io.camunda.zeebe.gateway.rest.controller.usermanagement.GroupController;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;

@WebMvcTest(GroupController.class)
public class GroupControllerTest extends RestControllerTest {

  @MockBean private GroupService groupService;
  @MockBean private UserGroupMembershipService userGroupMembershipService;

  @Test
  void getGroupByIdShouldReturnGroup() {
    final CamundaGroup camundaGroup = new CamundaGroup(1L, "demo");

    final CamundaGroupResponse camundaGroupResponse = new CamundaGroupResponse();
    camundaGroupResponse.setId(1L);
    camundaGroupResponse.setName("demo");

    when(groupService.findGroupById(1L)).thenReturn(camundaGroup);

    webClient
        .get()
        .uri("/v2/groups/1")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .is2xxSuccessful()
        .expectBody(CamundaGroupResponse.class)
        .isEqualTo(camundaGroupResponse);
  }

  @Test
  void getGroupByIdThrowsIllegalArgumentExceptionWhenServiceThrowsException() {
    final String message = "message";
    when(groupService.findGroupById(1L)).thenThrow(new IllegalArgumentException(message));

    final var expectedBody = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, message);
    expectedBody.setTitle(IllegalArgumentException.class.getName());
    expectedBody.setInstance(URI.create("/v2/groups/1"));

    webClient
        .get()
        .uri("/v2/groups/1")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody(ProblemDetail.class)
        .isEqualTo(expectedBody);
  }

  @Test
  void getGroupByIdThrowsInternalServerErrorExceptionWhenServiceThrowsException() {
    final String message = "message";
    when(groupService.findGroupById(1L)).thenThrow(new RuntimeException(message));

    final var expectedBody =
        ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, message);
    expectedBody.setTitle(RuntimeException.class.getName());
    expectedBody.setInstance(URI.create("/v2/groups/1"));

    webClient
        .get()
        .uri("/v2/groups/1")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .is5xxServerError()
        .expectBody(ProblemDetail.class)
        .isEqualTo(expectedBody);
  }

  @Test
  void createGroupShouldReturnCreatedGroup() {
    final CamundaGroup camundaGroup = new CamundaGroup("demo");
    final CamundaGroup createdCamundaGroup = new CamundaGroup(1L, "demo");

    final CamundaGroupRequest camundaGroupRequest = new CamundaGroupRequest();
    camundaGroupRequest.setName("demo");

    final CamundaGroupResponse camundaGroupResponse = new CamundaGroupResponse();
    camundaGroupResponse.setId(1L);
    camundaGroupResponse.setName("demo");

    when(groupService.createGroup(camundaGroup)).thenReturn(createdCamundaGroup);

    webClient
        .post()
        .uri("/v2/groups")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(camundaGroupRequest)
        .exchange()
        .expectStatus()
        .is2xxSuccessful()
        .expectBody(CamundaGroupResponse.class)
        .isEqualTo(camundaGroupResponse);

    verify(groupService, times(1)).createGroup(camundaGroup);
  }

  @Test
  void createGroupByIdThrowsExceptionWhenServiceThrowsException() {
    final String message = "message";

    final CamundaGroup camundaGroup = new CamundaGroup("demo");

    final CamundaGroupRequest camundaGroupRequest = new CamundaGroupRequest();
    camundaGroupRequest.setName("demo");

    when(groupService.createGroup(camundaGroup)).thenThrow(new IllegalArgumentException(message));

    final var expectedBody = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, message);
    expectedBody.setTitle(IllegalArgumentException.class.getName());
    expectedBody.setInstance(URI.create("/v2/groups"));

    webClient
        .post()
        .uri("/v2/groups")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(camundaGroupRequest)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody(ProblemDetail.class)
        .isEqualTo(expectedBody);
  }

  @Test
  void updateGroupShouldReturnUpdatedGroup() {
    final CamundaGroup camundaGroup = new CamundaGroup(1L, "demoChanged");

    final CamundaGroupRequest camundaGroupRequest = new CamundaGroupRequest();
    camundaGroupRequest.setId(1L);
    camundaGroupRequest.setName("demoChanged");

    final CamundaGroupResponse camundaGroupResponse = new CamundaGroupResponse();
    camundaGroupResponse.setId(1L);
    camundaGroupResponse.setName("demoChanged");

    when(groupService.updateGroup(1L, camundaGroup)).thenReturn(camundaGroup);

    webClient
        .put()
        .uri("/v2/groups/1")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(camundaGroupRequest)
        .exchange()
        .expectStatus()
        .is2xxSuccessful()
        .expectBody(CamundaGroupResponse.class)
        .isEqualTo(camundaGroupResponse);

    verify(groupService, times(1)).updateGroup(1L, camundaGroup);
  }

  @Test
  void updateGroupByIdThrowsExceptionWhenServiceThrowsException() {
    final String message = "message";

    final CamundaGroup camundaGroup = new CamundaGroup(1L, "demoChanged");

    final CamundaGroupRequest camundaGroupRequest = new CamundaGroupRequest();
    camundaGroupRequest.setId(1L);
    camundaGroupRequest.setName("demoChanged");

    when(groupService.updateGroup(1L, camundaGroup))
        .thenThrow(new IllegalArgumentException(message));

    final var expectedBody = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, message);
    expectedBody.setTitle(IllegalArgumentException.class.getName());
    expectedBody.setInstance(URI.create("/v2/groups/1"));

    webClient
        .put()
        .uri("/v2/groups/1")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(camundaGroupRequest)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody(ProblemDetail.class)
        .isEqualTo(expectedBody);
  }

  @Test
  void deleteGroupShouldDeleteGroup() {

    webClient
        .delete()
        .uri("/v2/groups/1")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .is2xxSuccessful();

    verify(groupService, times(1)).deleteGroupById(1L);
  }

  @Test
  void searchGroupsShouldReturnGroups() {
    final CamundaGroup camundaGroup = new CamundaGroup(1L, "demo");

    final CamundaGroupResponse camundaGroupResponse = new CamundaGroupResponse();
    camundaGroupResponse.setId(1L);
    camundaGroupResponse.setName("demo");

    final GroupSearchResponse groupSearchResponse = new GroupSearchResponse();
    groupSearchResponse.setItems(List.of(camundaGroupResponse));

    final SearchQueryRequest searchQueryRequest = new SearchQueryRequest();

    when(groupService.findAllGroups()).thenReturn(List.of(camundaGroup));

    webClient
        .post()
        .uri("/v2/groups/search")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(searchQueryRequest)
        .exchange()
        .expectStatus()
        .is2xxSuccessful()
        .expectBody(GroupSearchResponse.class)
        .isEqualTo(groupSearchResponse);

    verify(groupService, times(1)).findAllGroups();
  }
}
