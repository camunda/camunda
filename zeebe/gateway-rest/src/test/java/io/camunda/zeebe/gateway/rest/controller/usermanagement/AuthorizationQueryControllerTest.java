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

@WebMvcTest(value = AuthorizationController.class)
public class AuthorizationQueryControllerTest extends RestControllerTest {

  static final String EXPECTED_SEARCH_RESPONSE =
      """
          {
             "items": [
               {
                 "authorizationKey": "1",
                 "ownerId": "foo",
                 "ownerType": "USER",
                 "resourceType": "PROCESS_DEFINITION",
                 "resourceId": "2",
                 "permissionTypes": ["CREATE"]
               }
             ],
             "page": {
               "totalItems": 1,
                "firstSortValues": [
                  { "value": "f", "type": "string" }
                ],
                "lastSortValues": [
                  { "value": "v", "type": "string" }
                ]
             }
           }""";
  private static final String AUTHORIZATION_SEARCH_URL = "/v2/authorizations/search";

  private static final SearchQueryResult<AuthorizationEntity> SEARCH_QUERY_RESULT =
      new Builder<AuthorizationEntity>()
          .total(1L)
          .items(
              List.of(
                  new AuthorizationEntity(
                      1L,
                      "foo",
                      OwnerTypeEnum.USER.getValue(),
                      ResourceTypeEnum.PROCESS_DEFINITION.getValue(),
                      "2",
                      Set.of(PermissionType.CREATE))))
          .firstSortValues(new Object[] {"f"})
          .lastSortValues(new Object[] {"v"})
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
                        "order": "DESC"
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

  public static Stream<Arguments> invalidAuthorizationSearchQueries() {
    return invalidAuthorizationSearchQueriesForEndpoint(AUTHORIZATION_SEARCH_URL);
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
                            "field": "ownerId",
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
                endpoint)),
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
                      "detail": "Unexpected value 'unknownField' for enum field 'field'. Use any of the following values: [ownerId, ownerType, resourceId, resourceType]",
                      "instance": "%s"
                    }""",
                endpoint)),
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
                endpoint)),
        Arguments.of(
            // conflicting pagination
            """
                {
                    "page": {
                        "searchAfter": [{"value": "\\"a\\"", "type": "string"}],
                        "searchBefore": [{"value": "\\"b\\"", "type": "string"}]
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
