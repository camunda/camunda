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

import io.camunda.search.entities.AuthorizationEntity;
import io.camunda.search.exception.CamundaSearchException;
import io.camunda.search.query.AuthorizationQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.SearchQueryResult.Builder;
import io.camunda.search.sort.AuthorizationSort;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.service.AuthorizationServices;
import io.camunda.service.exception.ErrorMapper;
import io.camunda.zeebe.gateway.protocol.rest.OwnerTypeEnum;
import io.camunda.zeebe.gateway.protocol.rest.ResourceTypeEnum;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceMatcher;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;

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
               "startCursor": "f",
               "endCursor": "v",
               "hasMoreTotalItems": false
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
                      AuthorizationResourceMatcher.ID.value(),
                      "2",
                      Set.of(PermissionType.CREATE))))
          .startCursor("f")
          .endCursor("v")
          .build();

  @MockitoBean private AuthorizationServices authorizationServices;
  @MockitoBean private CamundaAuthenticationProvider authenticationProvider;

  @BeforeEach
  void setup() {
    when(authenticationProvider.getCamundaAuthentication())
        .thenReturn(AUTHENTICATION_WITH_DEFAULT_TENANT);
    when(authorizationServices.withAuthentication(any(CamundaAuthentication.class)))
        .thenReturn(authorizationServices);
  }

  @Test
  void getAuthorizationShouldReturnOk() {
    // given
    final AuthorizationEntity authorizationEntity =
        new AuthorizationEntity(
            100L,
            "ownerId",
            OwnerTypeEnum.USER.getValue(),
            ResourceTypeEnum.PROCESS_DEFINITION.getValue(),
            AuthorizationResourceMatcher.ID.value(),
            "resourceId",
            Set.of(PermissionType.CREATE));

    final Long authorizationKey = authorizationEntity.authorizationKey();
    when(authorizationServices.getAuthorization(authorizationKey)).thenReturn(authorizationEntity);

    // when
    webClient
        .get()
        .uri("%s/%d".formatted("/v2/authorizations", authorizationKey))
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .json(
            """
                {
                  "authorizationKey": "100",
                  "ownerId": "ownerId",
                  "ownerType": "USER",
                  "resourceId": "resourceId",
                  "resourceType": "PROCESS_DEFINITION",
                  "permissionTypes": ["CREATE"]
                }""",
            JsonCompareMode.STRICT);

    // then
    verify(authorizationServices, times(1)).getAuthorization(authorizationKey);
  }

  @Test
  void getNonExistingRoleShouldReturnNotFound() {
    // given
    final var authorizationKey = 100L;
    final var path = "%s/%s".formatted("/v2/authorizations", authorizationKey);
    when(authorizationServices.getAuthorization(authorizationKey))
        .thenThrow(
            ErrorMapper.mapSearchError(
                new CamundaSearchException(
                    "authorization not found", CamundaSearchException.Reason.NOT_FOUND)));

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
              "detail": "authorization not found",
              "instance": "%s"
            }"""
                .formatted(path),
            JsonCompareMode.STRICT);

    // then
    verify(authorizationServices, times(1)).getAuthorization(authorizationKey);
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
        .json(EXPECTED_SEARCH_RESPONSE, JsonCompareMode.STRICT);

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
        .json(EXPECTED_SEARCH_RESPONSE, JsonCompareMode.STRICT);

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
        .json(EXPECTED_SEARCH_RESPONSE, JsonCompareMode.STRICT);

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
        .json(expectedResponse, JsonCompareMode.STRICT);

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
                endpoint)));
  }
}
