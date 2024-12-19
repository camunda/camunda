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

import io.camunda.search.entities.GroupEntity;
import io.camunda.search.entities.UserEntity;
import io.camunda.search.exception.NotFoundException;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.GroupQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.sort.GroupSort;
import io.camunda.security.auth.Authentication;
import io.camunda.service.GroupServices;
import io.camunda.service.UserServices;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
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

@WebMvcTest(value = GroupController.class)
public class GroupQueryControllerTest extends RestControllerTest {

  static final String EXPECTED_GROUP_SEARCH_RESPONSE =
      """
      {
        "items":[
          {
            "groupKey":"111",
            "name":"Group 1",
            "assignedMemberKeys":[]
          },
          {
            "groupKey":"222",
            "name":"Group 2",
            "assignedMemberKeys":[]
          },
          {
            "groupKey":"333",
            "name":"Group 3",
            "assignedMemberKeys":[]
          }
        ],
        "page":{
          "totalItems":3,
          "firstSortValues":["f"],
          "lastSortValues":["v"]
        }
      }
      """;
  static final String EXPECTED_USER_SEARCH_RESPONSE =
      """
      {
        "items":[
          {
            "key":222,
            "username":"user1",
            "name":"user1",
            "email":""
          },
          {
            "key":333,
            "username":"user2",
            "name":"user2",
            "email":""
          }
        ],
        "page":{
          "totalItems":2,
          "firstSortValues":[],
          "lastSortValues":[]
        }
      }
      """;
  private static final String GROUP_BASE_URL = "/v2/groups";
  private static final String GROUP_SEARCH_URL = GROUP_BASE_URL + "/search";

  @MockBean private GroupServices groupServices;
  @MockBean private UserServices userServices;

  @BeforeEach
  void setup() {
    when(groupServices.withAuthentication(any(Authentication.class))).thenReturn(groupServices);
    when(userServices.withAuthentication(any(Authentication.class))).thenReturn(userServices);
  }

  @Test
  void shouldReturnOkOnGetGroup() {
    // given
    final var groupKey = 111L;
    final var groupName = "groupName";
    final var group = new GroupEntity(groupKey, groupName, Set.of());
    when(groupServices.getGroup(group.groupKey())).thenReturn(group);

    // when
    webClient
        .get()
        .uri("%s/%s".formatted(GROUP_BASE_URL, group.groupKey()))
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .json(
            """
            {
              "name": "%s",
              "groupKey": "%d"
            }"""
                .formatted(groupName, groupKey));

    // then
    verify(groupServices, times(1)).getGroup(group.groupKey());
  }

  @Test
  void shouldReturnNotFoundOnGetNonExistingGroup() {
    // given
    final var groupKey = 100L;
    final var path = "%s/%s".formatted(GROUP_BASE_URL, groupKey);
    when(groupServices.getGroup(groupKey)).thenThrow(new NotFoundException("group not found"));

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
              "detail": "group not found",
              "instance": "%s"
            }"""
                .formatted(path));

    // then
    verify(groupServices, times(1)).getGroup(groupKey);
  }

  @Test
  void shouldSearchGroupsWithEmptyQuery() {
    // given
    final var groupKey1 = 111L;
    final var groupKey2 = 222L;
    final var groupKey3 = 333L;
    final var groupName1 = "Group 1";
    final var groupName2 = "Group 2";
    final var groupName3 = "Group 3";
    when(groupServices.search(any(GroupQuery.class)))
        .thenReturn(
            new SearchQueryResult.Builder<GroupEntity>()
                .total(3)
                .firstSortValues(new Object[] {"f"})
                .lastSortValues(new Object[] {"v"})
                .items(
                    List.of(
                        new GroupEntity(groupKey1, groupName1, Set.of()),
                        new GroupEntity(groupKey2, groupName2, Set.of()),
                        new GroupEntity(groupKey3, groupName3, Set.of())))
                .build());

    // when / then
    webClient
        .post()
        .uri(GROUP_SEARCH_URL)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{}")
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .json(
            """
          {
             "items": [
               {
                 "groupKey": "%d",
                 "name": "%s"
               },
               {
                 "groupKey": "%d",
                 "name": "%s"
               },
               {
                 "groupKey": "%d",
                 "name": "%s"
               }
             ],
             "page": {
               "totalItems": 3,
               "firstSortValues": ["f"],
               "lastSortValues": ["v"]
             }
           }"""
                .formatted(groupKey1, groupName1, groupKey2, groupName2, groupKey3, groupName3));

    verify(groupServices).search(new GroupQuery.Builder().build());
  }

  @Test
  void shouldSortAndPaginateSearchResult() {
    // given
    final var groupKey1 = 111L;
    final var groupKey2 = 222L;
    final var groupKey3 = 333L;
    final var groupName1 = "Group 1";
    final var groupName2 = "Group 2";
    final var groupName3 = "Group 3";
    when(groupServices.search(any(GroupQuery.class)))
        .thenReturn(
            new SearchQueryResult.Builder<GroupEntity>()
                .total(3)
                .items(
                    List.of(
                        new GroupEntity(groupKey1, groupName1, Set.of()),
                        new GroupEntity(groupKey2, groupName2, Set.of()),
                        new GroupEntity(groupKey3, groupName3, Set.of())))
                .firstSortValues(new Object[] {"f"})
                .lastSortValues(new Object[] {"v"})
                .build());

    // when / then
    webClient
        .post()
        .uri("%s/search".formatted(GROUP_BASE_URL))
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            """
            {
              "sort":  [{"field": "name", "order":  "ASC"}],
              "page":  {"from":  20, "limit":  2}
            }
            """)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .json(EXPECTED_GROUP_SEARCH_RESPONSE);

    verify(groupServices)
        .search(
            new GroupQuery.Builder()
                .sort(GroupSort.of(builder -> builder.name().asc()))
                .page(SearchQueryPage.of(builder -> builder.from(20).size(2)))
                .build());
  }

  @Test
  void shouldReturnUsersAssignedToGroup() {
    // given
    final var groupKey = 111L;
    final var groupName = "groupName";
    final var userKey1 = 222L;
    final var userKey2 = 333L;
    final var groupUser1 = new GroupEntity(groupKey, groupName, Set.of(userKey1));
    final var groupUser2 = new GroupEntity(groupKey, groupName, Set.of(userKey2));
    when(groupServices.search(any()))
        .thenReturn(
            new SearchQueryResult.Builder<GroupEntity>()
                .total(2)
                .items(List.of(groupUser1, groupUser2))
                .build());
    when(groupServices.getUsersByGroupKey(any()))
        .thenReturn(
            new SearchQueryResult.Builder<UserEntity>()
                .total(2)
                .items(
                    List.of(
                        new UserEntity(userKey1, "user1", "user1", "", ""),
                        new UserEntity(userKey2, "user2", "user2", "", "")))
                .build());

    // when
    webClient
        .post()
        .uri("%s/%s/users/search".formatted(GROUP_BASE_URL, groupKey))
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            """
            {
              "sort":  [{"field": "username", "order":  "asc"}],
              "page":  {"from":  20, "limit":  2}
            }
            """)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .json(EXPECTED_USER_SEARCH_RESPONSE);

    verify(groupServices).getUserKeysByGroupKey(groupKey);
    verify(groupServices).getUsersByGroupKey(any());
  }

  @ParameterizedTest
  @MethodSource("invalidGroupSearchQueries")
  void shouldInvalidateAuthorizationsSearchQueryWithBadQueries(
      final String request, final String expectedResponse) {
    // when / then
    webClient
        .post()
        .uri(GROUP_SEARCH_URL)
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

    verify(groupServices, never()).search(any(GroupQuery.class));
  }

  public static Stream<Arguments> invalidGroupSearchQueries() {
    return invalidGroupSearchQueriesForEndpoint(GROUP_SEARCH_URL);
  }

  private static Stream<Arguments> invalidGroupSearchQueriesForEndpoint(final String endpoint) {
    return Stream.of(
        Arguments.of(
            // invalid sort order
            """
                {
                    "sort": [
                        {
                            "field": "groupKey",
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
