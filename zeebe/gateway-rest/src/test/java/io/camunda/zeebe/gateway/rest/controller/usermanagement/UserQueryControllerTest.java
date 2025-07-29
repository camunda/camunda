/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.usermanagement;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.search.entities.UserEntity;
import io.camunda.search.exception.CamundaSearchException;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.SearchQueryResult.Builder;
import io.camunda.search.query.UserQuery;
import io.camunda.search.sort.UserSort;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.service.RoleServices;
import io.camunda.service.UserServices;
import io.camunda.service.exception.ErrorMapper;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import io.camunda.zeebe.test.util.Strings;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;

@WebMvcTest(value = UserController.class)
public class UserQueryControllerTest extends RestControllerTest {

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
                  "startCursor": "f",
                  "endCursor": "v",
                  "hasMoreTotalItems": false
              }
          }""";
  private static final String USERS_SEARCH_URL = "/v2/users/search";

  private static final SearchQueryResult<UserEntity> SEARCH_QUERY_RESULT =
      new Builder<UserEntity>()
          .total(1L)
          .items(List.of(new UserEntity(1L, "username1", "name1", "email1", "password1")))
          .startCursor("f")
          .endCursor("v")
          .build();

  @MockitoBean UserServices userServices;
  @MockitoBean RoleServices roleServices;
  @MockitoBean PasswordEncoder passwordEncoder;
  @MockitoBean CamundaAuthenticationProvider authenticationProvider;

  @BeforeEach
  void setup() {
    when(authenticationProvider.getCamundaAuthentication())
        .thenReturn(AUTHENTICATION_WITH_DEFAULT_TENANT);
    when(userServices.withAuthentication(any(CamundaAuthentication.class)))
        .thenReturn(userServices);
  }

  @Test
  void getUserShouldReturnOk() {
    // given
    final var user = new UserEntity(100L, "username", "User Name", "email@email.com", "password");
    when(userServices.getUser(user.username())).thenReturn(user);

    // when
    webClient
        .get()
        .uri("%s/%s".formatted("/v2/users/", user.username()))
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .json(
            """
            {
              "username": "username",
              "name": "User Name",
              "email": "email@email.com"
            }""",
            JsonCompareMode.STRICT);

    // then
    verify(userServices, times(1)).getUser(user.username());
  }

  @Test
  void getNonExistingUserShouldReturnNotFound() {
    // given
    final var username = Strings.newRandomValidIdentityId();
    final var path = "%s/%s".formatted("/v2/users", username);
    when(userServices.getUser(username))
        .thenThrow(
            ErrorMapper.mapSearchError(
                new CamundaSearchException(
                    "user not found", CamundaSearchException.Reason.NOT_FOUND)));

    // when
    webClient
        .get()
        .uri(path)
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isNotFound()
        .expectBody()
        .json(
            """
            {
              "type": "about:blank",
              "title": "NOT_FOUND",
              "status": 404,
              "detail": "user not found",
              "instance": "%s"
            }"""
                .formatted(path),
            JsonCompareMode.STRICT);

    // then
    verify(userServices, times(1)).getUser(username);
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
        .json(EXPECTED_SEARCH_RESPONSE, JsonCompareMode.STRICT);

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
        .json(EXPECTED_SEARCH_RESPONSE, JsonCompareMode.STRICT);

    verify(userServices).search(new UserQuery.Builder().build());
  }

  @Test
  void shouldSearchUsersWithSorting() {
    // given
    when(userServices.search(any(UserQuery.class))).thenReturn(SEARCH_QUERY_RESULT);
    final var request =
        """
            {
                "sort": [
                    {
                        "field": "name",
                        "order": "DESC"
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
        .json(EXPECTED_SEARCH_RESPONSE, JsonCompareMode.STRICT);

    verify(userServices)
        .search(new UserQuery.Builder().sort(new UserSort.Builder().name().desc().build()).build());
  }

  @ParameterizedTest
  @MethodSource("invalidUserSearchQueries")
  void shouldInvalidateUsersSearchQueryWithBadQueries(
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
        .json(expectedResponse, JsonCompareMode.STRICT);

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
                      "title": "Bad Request",
                      "status": 400,
                      "detail": "Unexpected value 'dsc' for enum field 'order'. Use any of the following values: [ASC, DESC]",
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
                            "order": "ASC"
                        }
                    ]
                }""",
            String.format(
                """
                    {
                      "type": "about:blank",
                      "title": "Bad Request",
                      "status": 400,
                      "detail": "Unexpected value 'unknownField' for enum field 'field'. Use any of the following values: [username, name, email]",
                      "instance": "%s"
                    }""",
                USERS_SEARCH_URL)),
        Arguments.of(
            // missing sort field
            """
                {
                    "sort": [
                        {
                            "order": "ASC"
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
                        "after": "a",
                        "before": "b"
                    }
                }""",
            String.format(
                """
                    {
                      "type": "about:blank",
                      "title": "INVALID_ARGUMENT",
                      "status": 400,
                      "detail": "Both after and before cannot be set at the same time.",
                      "instance": "%s"
                    }""",
                USERS_SEARCH_URL)));
  }
}
