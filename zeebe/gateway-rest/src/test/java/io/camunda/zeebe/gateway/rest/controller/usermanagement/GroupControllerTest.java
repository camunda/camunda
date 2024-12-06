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
import io.camunda.zeebe.gateway.protocol.rest.GroupCreateRequest;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import io.camunda.zeebe.protocol.impl.record.value.group.GroupRecord;
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
    final var groupName = "testGroup";
    when(groupServices.createGroup(groupName))
        .thenReturn(
            CompletableFuture.completedFuture(
                new GroupRecord().setEntityKey(1L).setName(groupName)));

    // when
    webClient
        .post()
        .uri(GROUP_BASE_URL)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(new GroupCreateRequest().name(groupName))
        .exchange()
        .expectStatus()
        .isCreated();

    // then
    verify(groupServices, times(1)).createGroup(groupName);
  }

  @Test
  void shouldFailOnCreateGroupWithNoName() {
    // when
    webClient
        .post()
        .uri(GROUP_BASE_URL)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(new GroupCreateRequest().name(""))
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
}
