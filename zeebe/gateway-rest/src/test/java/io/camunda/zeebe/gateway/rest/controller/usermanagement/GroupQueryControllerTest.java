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
import static org.mockito.Mockito.when;

import io.camunda.search.entities.GroupEntity;
import io.camunda.search.exception.NotFoundException;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.GroupQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.sort.GroupSort;
import io.camunda.security.auth.Authentication;
import io.camunda.service.GroupServices;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;

@WebMvcTest(value = GroupQueryController.class, properties = "camunda.rest.query.enabled=true")
public class GroupQueryControllerTest extends RestControllerTest {

  private static final String GROUP_BASE_URL = "/v2/groups";

  @MockBean private GroupServices groupServices;

  @BeforeEach
  void setup() {
    when(groupServices.withAuthentication(any(Authentication.class))).thenReturn(groupServices);
  }

  @Test
  void shouldReturnOkOnGetGroup() {
    // given
    final var groupKey = 111L;
    final var groupName = "groupName";
    final var group = new GroupEntity(groupKey, groupName, Set.of());
    when(groupServices.getGroup(group.key())).thenReturn(group);

    // when
    webClient
        .get()
        .uri("%s/%s".formatted(GROUP_BASE_URL, group.key()))
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .json(
            """
            {
              "name": "%s",
              "key": %d
            }"""
                .formatted(groupName, groupKey));

    // then
    verify(groupServices, times(1)).getGroup(group.key());
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
                .sortValues(new Object[] {})
                .items(
                    List.of(
                        new GroupEntity(groupKey1, groupName1, Set.of()),
                        new GroupEntity(groupKey2, groupName2, Set.of()),
                        new GroupEntity(groupKey3, groupName3, Set.of())))
                .build());

    // when / then
    webClient
        .post()
        .uri("%s/search".formatted(GROUP_BASE_URL))
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
                 "key": %d,
                 "name": "%s"
               },
               {
                 "key": %d,
                 "name": "%s"
               },
               {
                 "key": %d,
                 "name": "%s"
               }
             ],
             "page": {
               "totalItems": 3,
               "firstSortValues": [],
               "lastSortValues": []
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
              "sort":  [{"field": "name", "order":  "asc"}],
              "page":  {"from":  20, "limit":  10}
            }
             """)
        .exchange()
        .expectStatus()
        .isOk();

    verify(groupServices)
        .search(
            new GroupQuery.Builder()
                .sort(GroupSort.of(builder -> builder.name().asc()))
                .page(SearchQueryPage.of(builder -> builder.from(20).size(10)))
                .build());
  }
}
