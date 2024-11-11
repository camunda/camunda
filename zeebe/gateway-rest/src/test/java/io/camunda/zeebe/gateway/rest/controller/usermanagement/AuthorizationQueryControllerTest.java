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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.search.entities.AuthorizationEntity;
import io.camunda.search.query.AuthorizationQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.SearchQueryResult.Builder;
import io.camunda.search.sort.AuthorizationSort;
import io.camunda.security.auth.Authentication;
import io.camunda.security.entity.Permission;
import io.camunda.service.AuthorizationServices;
import io.camunda.zeebe.gateway.protocol.rest.OwnerTypeEnum;
import io.camunda.zeebe.gateway.protocol.rest.ResourceTypeEnum;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;

@WebMvcTest(
    value = AuthorizationQueryController.class,
    properties = "camunda.rest.query.enabled=true")
public class AuthorizationQueryControllerTest extends RestControllerTest {

  static final String EXPECTED_SEARCH_RESPONSE =
      """
          {
             "items": [
               {
                 "ownerKey": 1,
                 "ownerType": "USER",
                 "resourceType": "PROCESS_DEFINITION",
                 "permissions": [
                   {
                     "permissionType": "CREATE",
                     "resourceIds": [
                       "2"
                     ]
                   }
                 ]
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
  private static final String AUTHORIZATION_SEARCH_URL = "/v2/authorizations/search";
  private static final String USERS_AUTHORIZATION_SEARCH_URL =
      "/v2/users/{ownerKey}/authorizations/search";

  private static final SearchQueryResult<AuthorizationEntity> SEARCH_QUERY_RESULT =
      new Builder<AuthorizationEntity>()
          .total(1L)
          .items(
              List.of(
                  new AuthorizationEntity(
                      "1",
                      OwnerTypeEnum.USER.getValue(),
                      ResourceTypeEnum.PROCESS_DEFINITION.getValue(),
                      List.of(new Permission(PermissionType.CREATE, Set.of("2"))))))
          .sortValues(new Object[] {"v"})
          .build();

  @MockBean private AuthorizationServices authorizationServices;

  @BeforeEach
  void setup() {
    when(authorizationServices.withAuthentication(any(Authentication.class)))
        .thenReturn(authorizationServices);
  }

  @Test
  void shouldSearchAuthorizationsWithEmptyBody() {
    // given
    when(authorizationServices.search(any(AuthorizationQuery.class)))
        .thenReturn(SEARCH_QUERY_RESULT);
    // when / then

    webClient
        .post()
        .uri(AUTHORIZATION_SEARCH_URL)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .json(EXPECTED_SEARCH_RESPONSE);

    verify(authorizationServices).search(new AuthorizationQuery.Builder().build());
  }

  @Test
  void shouldSearchAuthorizationsWithEmptyQuery() {
    // given
    when(authorizationServices.search(any(AuthorizationQuery.class)))
        .thenReturn(SEARCH_QUERY_RESULT);
    final String request = "{}";
    // when / then
    webClient
        .post()
        .uri(AUTHORIZATION_SEARCH_URL)
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

    verify(authorizationServices).search(new AuthorizationQuery.Builder().build());
  }

  @Test
  void shouldSearchAuthorizationsWithSorting() {
    // given
    when(authorizationServices.search(any(AuthorizationQuery.class)))
        .thenReturn(SEARCH_QUERY_RESULT);
    final var request =
        """
            {
                "sort": [
                    {
                        "field": "ownerType",
                        "order": "desc"
                    }
                ]
            }""";
    // when / then
    webClient
        .post()
        .uri(AUTHORIZATION_SEARCH_URL)
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

    verify(authorizationServices)
        .search(
            new AuthorizationQuery.Builder()
                .sort(new AuthorizationSort.Builder().ownerType().desc().build())
                .build());
  }

  @ParameterizedTest
  @MethodSource("invalidAuthorizationSearchQueries")
  void shouldInvalidateAuthorizationsSearchQueryWithBadQueries(
      final String request, final String expectedResponse) {
    // when / then
    webClient
        .post()
        .uri(AUTHORIZATION_SEARCH_URL)
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

    verify(authorizationServices, never()).search(any(AuthorizationQuery.class));
  }

  @Test
  void shouldSearchUserAuthorizationsWithEmptyBody() {
    // given
    when(authorizationServices.search(any(AuthorizationQuery.class)))
        .thenReturn(SEARCH_QUERY_RESULT);
    // when / then

    webClient
        .post()
        .uri(USERS_AUTHORIZATION_SEARCH_URL, 1)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .json(EXPECTED_SEARCH_RESPONSE);

    verify(authorizationServices)
        .search(
            new AuthorizationQuery.Builder()
                .filter(f -> f.ownerType(OwnerTypeEnum.USER.getValue()).ownerKeys(1L))
                .build());
  }

  @Test
  void shouldSearchUserAuthorizationsWithEmptyQuery() {
    // given
    when(authorizationServices.search(any(AuthorizationQuery.class)))
        .thenReturn(SEARCH_QUERY_RESULT);
    final String request = "{}";
    // when / then
    webClient
        .post()
        .uri(USERS_AUTHORIZATION_SEARCH_URL, 1)
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

    verify(authorizationServices)
        .search(
            new AuthorizationQuery.Builder()
                .filter(f -> f.ownerType(OwnerTypeEnum.USER.getValue()).ownerKeys(1L))
                .build());
  }

  @Test
  void shouldSearchUserAuthorizationsWithRequestedFilter() {
    // given
    when(authorizationServices.search(any(AuthorizationQuery.class)))
        .thenReturn(SEARCH_QUERY_RESULT);
    final String request =
        """
    {"filter": {"ownerType": null, "ownerKey": 2}}
    """;
    // when / then
    webClient
        .post()
        .uri(USERS_AUTHORIZATION_SEARCH_URL, 1)
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

    verify(authorizationServices)
        .search(
            new AuthorizationQuery.Builder()
                .filter(f -> f.ownerType(OwnerTypeEnum.USER.getValue()).ownerKeys(1L))
                .build());
  }

  @Test
  void shouldSearchUserAuthorizationsWithSorting() {
    // given
    when(authorizationServices.search(any(AuthorizationQuery.class)))
        .thenReturn(SEARCH_QUERY_RESULT);
    final var request =
        """
            {
                "sort": [
                    {
                        "field": "ownerType",
                        "order": "desc"
                    }
                ]
            }""";
    // when / then
    webClient
        .post()
        .uri(USERS_AUTHORIZATION_SEARCH_URL, 1)
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

    verify(authorizationServices)
        .search(
            new AuthorizationQuery.Builder()
                .filter(f -> f.ownerType(OwnerTypeEnum.USER.getValue()).ownerKeys(1L))
                .sort(new AuthorizationSort.Builder().ownerType().desc().build())
                .build());
  }

  @ParameterizedTest
  @MethodSource("invalidUserAuthorizationSearchQueries")
  void shouldInvalidateUserAuthorizationsSearchQueryWithBadQueries(
      final String request, final String expectedResponse) {
    // when / then
    webClient
        .post()
        .uri(USERS_AUTHORIZATION_SEARCH_URL, 1)
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

    verify(authorizationServices, never()).search(any(AuthorizationQuery.class));
  }

  public static Stream<Arguments> invalidAuthorizationSearchQueries() {
    return invalidAuthorizationSearchQueriesForEndpoint(AUTHORIZATION_SEARCH_URL);
  }

  public static Stream<Arguments> invalidUserAuthorizationSearchQueries() {
    return invalidAuthorizationSearchQueriesForEndpoint("/v2/users/1/authorizations/search");
  }

  private static Stream<Arguments> invalidAuthorizationSearchQueriesForEndpoint(
      final String endpoint) {
    return Stream.of(
        Arguments.of(
            // invalid sort order
            """
                {
                    "sort": [
                        {
                            "field": "ownerKey",
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
                endpoint)),
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
                endpoint)),
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
                endpoint)),
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
                endpoint)));
  }
}
