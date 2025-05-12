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

import io.camunda.search.entities.MappingEntity;
import io.camunda.search.entities.RoleEntity;
import io.camunda.search.entities.UserEntity;
import io.camunda.search.exception.CamundaSearchException;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.MappingQuery;
import io.camunda.search.query.RoleQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.UserQuery;
import io.camunda.search.sort.RoleSort;
import io.camunda.security.auth.Authentication;
import io.camunda.service.MappingServices;
import io.camunda.service.RoleServices;
import io.camunda.service.UserServices;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import io.camunda.zeebe.test.util.Strings;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;

@WebMvcTest(value = RoleController.class)
public class RoleQueryControllerTest extends RestControllerTest {
  private static final String ROLE_BASE_URL = "/v2/roles";

  @MockBean private RoleServices roleServices;
  @MockBean private UserServices userServices;
  @MockBean private MappingServices mappingsServices;

  @BeforeEach
  void setup() {
    when(roleServices.withAuthentication(any(Authentication.class))).thenReturn(roleServices);
    when(userServices.withAuthentication(any(Authentication.class))).thenReturn(userServices);
    when(mappingsServices.withAuthentication(any(Authentication.class)))
        .thenReturn(mappingsServices);
  }

  @Test
  void getRoleShouldReturnOk() {
    // given
    final var role = new RoleEntity(100L, "roleId", "Role Name", "description");
    when(roleServices.getRole(role.roleId())).thenReturn(role);

    // when
    webClient
        .get()
        .uri("%s/%s".formatted(ROLE_BASE_URL, role.roleId()))
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .json(
            """
            {
              "name": "Role Name",
              "roleKey": "100",
              "roleId": "roleId",
              "description": "description"
            }""");

    // then
    verify(roleServices, times(1)).getRole(role.roleId());
  }

  @Test
  void getNonExistingRoleShouldReturnNotFound() {
    // given
    final var roleId = Strings.newRandomValidIdentityId();
    final var path = "%s/%s".formatted(ROLE_BASE_URL, roleId);
    when(roleServices.getRole(roleId))
        .thenThrow(
            new CamundaSearchException("role not found", CamundaSearchException.Reason.NOT_FOUND));

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
              "detail": "role not found",
              "instance": "%s"
            }"""
                .formatted(path));

    // then
    verify(roleServices, times(1)).getRole(roleId);
  }

  @Test
  void shouldSearchRolesWithEmptyQuery() {
    // given
    when(roleServices.search(any(RoleQuery.class)))
        .thenReturn(
            new SearchQueryResult.Builder<RoleEntity>()
                .total(3)
                .firstSortValues(new Object[] {"f"})
                .lastSortValues(new Object[] {"v"})
                .items(
                    List.of(
                        new RoleEntity(100L, "role1", "Role 1", "description 1"),
                        new RoleEntity(200L, "role2", "Role 2", "description 2"),
                        new RoleEntity(300L, "role12", "Role 12", "description 12")))
                .build());

    // when / then
    webClient
        .post()
        .uri("%s/search".formatted(ROLE_BASE_URL))
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
                 "roleKey": "100",
                 "name": "Role 1",
                 "roleId": "role1",
                 "description": "description 1"
               },
               {
                 "roleKey": "200",
                 "name": "Role 2",
                 "roleId": "role2",
                 "description": "description 2"
               },
               {
                 "roleKey": "300",
                 "name": "Role 12",
                 "roleId": "role12",
                 "description": "description 12"
               }
             ],
             "page": {
               "totalItems": 3,
               "firstSortValues": ["f"],
               "lastSortValues": ["v"]
             }
           }""");

    verify(roleServices).search(new RoleQuery.Builder().build());
  }

  @Test
  void shouldSortAndPaginateSearchResult() {
    // given
    when(roleServices.search(any(RoleQuery.class)))
        .thenReturn(
            new SearchQueryResult.Builder<RoleEntity>()
                .total(3)
                .items(
                    List.of(
                        new RoleEntity(100L, "role1", "Role 1", "description 1"),
                        new RoleEntity(300L, "role12", "Role 12", "description 12"),
                        new RoleEntity(200L, "role2", "Role 2", "description 2")))
                .build());

    // when / then
    webClient
        .post()
        .uri("%s/search".formatted(ROLE_BASE_URL))
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            """
            {
              "sort":  [{"field": "name", "order":  "ASC"}],
              "page":  {"from":  20, "limit":  10}
            }
             """)
        .exchange()
        .expectStatus()
        .isOk();

    verify(roleServices)
        .search(
            new RoleQuery.Builder()
                .sort(RoleSort.of(builder -> builder.name().asc()))
                .page(SearchQueryPage.of(builder -> builder.from(20).size(10)))
                .build());
  }

  @Test
  void shouldSearchUsersByRole() {
    // given
    final var roleId = "roleId";
    when(userServices.search(any(UserQuery.class)))
        .thenReturn(
            new SearchQueryResult.Builder<UserEntity>()
                .total(3)
                .items(
                    List.of(
                        new UserEntity(100L, "user1", "User 1", "user1@example.com", "password1"),
                        new UserEntity(200L, "user2", "User 2", "user2@example.com", "password2"),
                        new UserEntity(300L, "user3", "User 3", "user3@example.com", "password3")))
                .build());

    // when /then
    webClient
        .post()
        .uri("%s/%s/users/search".formatted(ROLE_BASE_URL, roleId))
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
                 "userKey": "100",
                 "username": "user1",
                 "name": "User 1",
                 "email": "user1@example.com"
               },
               {
                 "userKey": "200",
                 "username": "user2",
                 "name": "User 2",
                 "email": "user2@example.com"
               },
               {
                 "userKey": "300",
                 "username": "user3",
                 "name": "User 3",
                 "email": "user3@example.com"
               }
             ],
             "page": {
               "totalItems": 3,
               "firstSortValues": [],
               "lastSortValues": []
             }
           }""");

    verify(userServices)
        .search(
            new UserQuery.Builder()
                .filter(f -> f.roleId(roleId).usernames(Collections.emptySet()))
                .build());
  }

  @Test
  void shouldSearchMappingsByRole() {
    // given
    final var roleId = "roleId";
    when(mappingsServices.search(any(MappingQuery.class)))
        .thenReturn(
            new SearchQueryResult.Builder<MappingEntity>()
                .total(3)
                .items(
                    List.of(
                        new MappingEntity("mapping1", 1L, "claim1", "value1", "Mapping 1"),
                        new MappingEntity("mapping2", 2L, "claim2", "value2", "Mapping 2"),
                        new MappingEntity("mapping3", 3L, "claim3", "value3", "Mapping 3")))
                .build());

    // when /then
    webClient
        .post()
        .uri("%s/%s/mapping-rules/search".formatted(ROLE_BASE_URL, roleId))
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
                 "mappingRuleId": "mapping1",
                 "claimName": "claim1",
                 "claimValue": "value1",
                 "name": "Mapping 1"
               },
                {
                  "mappingRuleId": "mapping2",
                  "claimName": "claim2",
                  "claimValue": "value2",
                  "name": "Mapping 2"
                },
                {
                  "mappingRuleId": "mapping3",
                  "claimName": "claim3",
                  "claimValue": "value3",
                  "name": "Mapping 3"
                }
             ],
             "page": {
               "totalItems": 3,
               "firstSortValues": [],
               "lastSortValues": []
             }
           }""");

    verify(mappingsServices)
        .search(
            new MappingQuery.Builder().filter(f -> f.roleId(roleId).claimNames(List.of())).build());
  }
}
