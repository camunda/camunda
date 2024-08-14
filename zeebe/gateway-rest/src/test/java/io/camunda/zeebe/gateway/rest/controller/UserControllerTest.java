/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.service.CamundaServiceException;
import io.camunda.service.UserServices;
import io.camunda.service.entities.CamundaUserEntity;
import io.camunda.service.search.query.SearchQueryResult;
import io.camunda.service.search.query.SearchQueryResult.Builder;
import io.camunda.service.search.query.UserQuery;
import io.camunda.service.search.sort.UserSort;
import io.camunda.service.security.auth.Authentication;
import io.camunda.zeebe.gateway.protocol.rest.CamundaUserWithPasswordRequest;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import io.camunda.zeebe.gateway.rest.controller.usermanagement.UserController;
import io.camunda.zeebe.protocol.impl.record.value.user.UserRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import java.net.URI;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.stubbing.Answer;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.crypto.password.PasswordEncoder;

@WebMvcTest(UserController.class)
public class UserControllerTest extends RestControllerTest {

  static final String EXPECTED_SEARCH_RESPONSE =
      """
          {
              "items": [
                 { "username": "username1",
                   "name": "name1",
                   "email": "email1"
                 }
              ],
              "page": {
                  "totalItems": 1,
                  "firstSortValues": [],
                  "lastSortValues": [
                      "v"
                  ]
              }
          }""";
  private static final String USERS_SEARCH_URL = "/v2/users/search";

  private static final SearchQueryResult<CamundaUserEntity> SEARCH_QUERY_RESULT =
      new Builder<CamundaUserEntity>()
          .total(1L)
          .items(
              List.of(
                  new CamundaUserEntity(
                      new CamundaUserEntity.User("username1", "name1", "email1", "password1"))))
          .sortValues(new Object[] {"v"})
          .build();

  @MockBean private UserServices userServices;
  @MockBean private PasswordEncoder passwordEncoder;

  @BeforeEach
  void setup() {
    when(userServices.withAuthentication(any(Authentication.class))).thenReturn(userServices);
    when(passwordEncoder.encode(any()))
        .thenAnswer(
            (Answer<String>) invocationOnMock -> invocationOnMock.getArgument(0).toString());
  }

  @Test
  void createUserShouldCreateAndReturnNewUser() {

    final CamundaUserWithPasswordRequest dto = new CamundaUserWithPasswordRequest();
    dto.setUsername("demo");
    dto.setPassword("password");
    dto.setName("Demo");
    dto.setEmail("demo@e.c");

    final var userRecord =
        new UserRecord()
            .setUsername(dto.getUsername())
            .setName(dto.getName())
            .setEmail(dto.getEmail())
            .setPassword(dto.getPassword());

    when(userServices.createUser(
            dto.getUsername(), dto.getName(), dto.getEmail(), dto.getPassword()))
        .thenReturn(CompletableFuture.completedFuture(userRecord));

    webClient
        .post()
        .uri("/v2/users")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(dto)
        .exchange()
        .expectStatus()
        .is2xxSuccessful()
        .expectBody();

    verify(userServices, times(1))
        .createUser(dto.getUsername(), dto.getName(), dto.getEmail(), dto.getPassword());
  }

  @Test
  void createUserThrowsExceptionWhenServiceThrowsException() {
    final String message = "message";

    final CamundaUserWithPasswordRequest dto = new CamundaUserWithPasswordRequest();
    dto.setUsername("demo");
    dto.setEmail("demo@e.c");
    dto.setPassword("password");

    when(userServices.createUser(
            dto.getUsername(), dto.getName(), dto.getEmail(), dto.getPassword()))
        .thenThrow(new CamundaServiceException(RejectionType.ALREADY_EXISTS.name()));

    final var expectedBody = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, message);
    expectedBody.setTitle("Bad Request");
    expectedBody.setDetail(RejectionType.ALREADY_EXISTS.name());
    expectedBody.setInstance(URI.create("/v2/users"));

    webClient
        .post()
        .uri("/v2/users")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(dto)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody(ProblemDetail.class)
        .isEqualTo(expectedBody);
  }

  @Test
  void shouldSearchUsersWithEmptyBody() {
    // given
    when(userServices.search(any(UserQuery.class))).thenReturn(SEARCH_QUERY_RESULT);
    // when / then
    webClient
        .post()
        .uri(USERS_SEARCH_URL)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .json(EXPECTED_SEARCH_RESPONSE);

    verify(userServices).search(new UserQuery.Builder().build());
  }

  @Test
  void shouldSearchUsersWithEmptyQuery() {
    // given
    when(userServices.search(any(UserQuery.class))).thenReturn(SEARCH_QUERY_RESULT);
    final String request = "{}";
    // when / then
    webClient
        .post()
        .uri(USERS_SEARCH_URL)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .json(EXPECTED_SEARCH_RESPONSE);

    verify(userServices).search(new UserQuery.Builder().build());
  }

  @Test
  void shouldSearchUserTasksWithSorting() {
    // given
    when(userServices.search(any(UserQuery.class))).thenReturn(SEARCH_QUERY_RESULT);
    final var request =
        """
        {
            "sort": [
                {
                    "field": "name",
                    "order": "desc"
                }
            ]
        }""";
    // when / then
    webClient
        .post()
        .uri(USERS_SEARCH_URL)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .json(EXPECTED_SEARCH_RESPONSE);

    verify(userServices)
        .search(new UserQuery.Builder().sort(new UserSort.Builder().name().desc().build()).build());
  }

  @ParameterizedTest
  @MethodSource("invalidUserSearchQueries")
  void shouldInvalidateUserTasksSearchQueryWithBadQueries(
      final String request, final String expectedResponse) {
    // when / then
    webClient
        .post()
        .uri(USERS_SEARCH_URL)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .json(expectedResponse);

    verify(userServices, never()).search(any(UserQuery.class));
  }

  public static Stream<Arguments> invalidUserSearchQueries() {
    return Stream.of(
        Arguments.of(
            // invalid sort order
            """
        {
            "sort": [
                {
                    "field": "name",
                    "order": "dsc"
                }
            ]
        }""",
            String.format(
                """
    {
      "type": "about:blank",
      "title": "INVALID_ARGUMENT",
      "status": 400,
      "detail": "Unknown sortOrder: dsc.",
      "instance": "%s"
    }""",
                USERS_SEARCH_URL)),
        Arguments.of(
            // unknown field
            """
        {
            "sort": [
                {
                    "field": "unknownField",
                    "order": "asc"
                }
            ]
        }""",
            String.format(
                """
        {
          "type": "about:blank",
          "title": "INVALID_ARGUMENT",
          "status": 400,
          "detail": "Unknown sortBy: unknownField.",
          "instance": "%s"
        }""",
                USERS_SEARCH_URL)),
        Arguments.of(
            // missing sort field
            """
        {
            "sort": [
                {
                    "order": "asc"
                }
            ]
        }""",
            String.format(
                """
            {
              "type": "about:blank",
              "title": "INVALID_ARGUMENT",
              "status": 400,
              "detail": "Sort field must not be null.",
              "instance": "%s"
            }""",
                USERS_SEARCH_URL)),
        Arguments.of(
            // conflicting pagination
            """
        {
            "page": {
                "searchAfter": ["a"],
                "searchBefore": ["b"]
            }
        }""",
            String.format(
                """
            {
              "type": "about:blank",
              "title": "INVALID_ARGUMENT",
              "status": 400,
              "detail": "Both searchAfter and searchBefore cannot be set at the same time.",
              "instance": "%s"
            }""",
                USERS_SEARCH_URL)));
  }
}
