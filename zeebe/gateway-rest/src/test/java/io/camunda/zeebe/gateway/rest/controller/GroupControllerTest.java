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

import io.camunda.identity.usermanagement.CamundaGroup;
import io.camunda.identity.usermanagement.service.GroupService;
import io.camunda.zeebe.gateway.protocol.rest.CamundaGroupDto;
import io.camunda.zeebe.gateway.protocol.rest.GroupSearchResponseDto;
import io.camunda.zeebe.gateway.protocol.rest.SearchRequestDto;
import io.camunda.zeebe.gateway.rest.GlobalControllerExceptionHandler;
import io.camunda.zeebe.gateway.rest.controller.GroupControllerTest.TestGroupControllerApplication;
import io.camunda.zeebe.gateway.rest.controller.usermanagement.GroupController;
import io.camunda.zeebe.gateway.rest.controller.usermanagement.UserController;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(
    classes = {
      TestGroupControllerApplication.class,
      GroupController.class,
      GlobalControllerExceptionHandler.class
    },
    webEnvironment = WebEnvironment.RANDOM_PORT)
public class GroupControllerTest {

  @MockBean private UserController userController;
  @MockBean private JobController jobController;
  @MockBean private ProcessInstanceController processInstanceController;
  @MockBean private TopologyController topologyController;
  @MockBean private UserTaskController userTaskController;
  @MockBean private HttpSecurity httpSecurity;
  @MockBean private GroupService groupService;

  @Autowired private WebTestClient webClient;

  @Test
  void getGroupByIdWorks() {
    final CamundaGroup camundaGroup = new CamundaGroup(1L, "demo");

    final CamundaGroupDto camundaGroupDto = new CamundaGroupDto();
    camundaGroupDto.setId(1L);
    camundaGroupDto.setName("demo");

    when(groupService.findGroupById(1L)).thenReturn(camundaGroup);

    webClient
        .get()
        .uri("/v2/groups/1")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .is2xxSuccessful()
        .expectBody(CamundaGroupDto.class)
        .isEqualTo(camundaGroupDto);
  }

  @Test
  void getGroupByIdThrowsExceptionWhenServiceThrowsException() {
    final String message = "message";
    when(groupService.findGroupById(1L)).thenThrow(new IllegalArgumentException(message));

    final var expectedBody = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, message);
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
  void createGroupWorks() {
    final CamundaGroup camundaGroup = new CamundaGroup("demo");
    final CamundaGroup createdCamundaGroup = new CamundaGroup(1L, "demo");

    final CamundaGroupDto camundaGroupDto = new CamundaGroupDto();
    camundaGroupDto.setName("demo");

    final CamundaGroupDto createdCamundaGroupDto = new CamundaGroupDto();
    createdCamundaGroupDto.setId(1L);
    createdCamundaGroupDto.setName("demo");

    when(groupService.createGroup(camundaGroup)).thenReturn(createdCamundaGroup);

    webClient
        .post()
        .uri("/v2/groups")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(camundaGroupDto)
        .exchange()
        .expectStatus()
        .is2xxSuccessful()
        .expectBody(CamundaGroupDto.class)
        .isEqualTo(createdCamundaGroupDto);

    verify(groupService, times(1)).createGroup(camundaGroup);
  }

  @Test
  void createGroupByIdThrowsExceptionWhenServiceThrowsException() {
    final String message = "message";

    final CamundaGroup camundaGroup = new CamundaGroup("demo");

    final CamundaGroupDto camundaGroupDto = new CamundaGroupDto();
    camundaGroupDto.setName("demo");

    when(groupService.createGroup(camundaGroup)).thenThrow(new IllegalArgumentException(message));

    final var expectedBody = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, message);
    expectedBody.setInstance(URI.create("/v2/groups"));

    webClient
        .post()
        .uri("/v2/groups")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(camundaGroupDto)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody(ProblemDetail.class)
        .isEqualTo(expectedBody);
  }

  @Test
  void updateGroupWorks() {
    final CamundaGroup camundaGroup = new CamundaGroup(1L, "demoChanged");

    final CamundaGroupDto camundaGroupDto = new CamundaGroupDto();
    camundaGroupDto.setId(1L);
    camundaGroupDto.setName("demoChanged");

    when(groupService.updateGroup(1L, camundaGroup)).thenReturn(camundaGroup);

    webClient
        .put()
        .uri("/v2/groups/1")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(camundaGroupDto)
        .exchange()
        .expectStatus()
        .is2xxSuccessful()
        .expectBody(CamundaGroupDto.class)
        .isEqualTo(camundaGroupDto);

    verify(groupService, times(1)).updateGroup(1L, camundaGroup);
  }

  @Test
  void updateGroupByIdThrowsExceptionWhenServiceThrowsException() {
    final String message = "message";

    final CamundaGroup camundaGroup = new CamundaGroup(1L, "demoChanged");

    final CamundaGroupDto camundaGroupDto = new CamundaGroupDto();
    camundaGroupDto.setId(1L);
    camundaGroupDto.setName("demoChanged");

    when(groupService.updateGroup(1L, camundaGroup))
        .thenThrow(new IllegalArgumentException(message));

    final var expectedBody = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, message);
    expectedBody.setInstance(URI.create("/v2/groups/1"));

    webClient
        .put()
        .uri("/v2/groups/1")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(camundaGroupDto)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody(ProblemDetail.class)
        .isEqualTo(expectedBody);
  }

  @Test
  void deleteGroupWorks() {

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
  void searchGroupsWorks() {
    final CamundaGroup camundaGroup = new CamundaGroup(1L, "demo");

    final CamundaGroupDto camundaGroupDto = new CamundaGroupDto();
    camundaGroupDto.setId(1L);
    camundaGroupDto.setName("demo");

    final GroupSearchResponseDto groupSearchResponseDto = new GroupSearchResponseDto();
    groupSearchResponseDto.setItems(List.of(camundaGroupDto));

    final SearchRequestDto searchRequestDto = new SearchRequestDto();

    when(groupService.findAllGroups()).thenReturn(List.of(camundaGroup));

    webClient
        .post()
        .uri("/v2/groups/search")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(searchRequestDto)
        .exchange()
        .expectStatus()
        .is2xxSuccessful()
        .expectBody(GroupSearchResponseDto.class)
        .isEqualTo(groupSearchResponseDto);

    verify(groupService, times(1)).findAllGroups();
  }

  @SpringBootApplication(
      exclude = {
        SecurityAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class,
        DataSourceAutoConfiguration.class
      })
  static class TestGroupControllerApplication {}
}
