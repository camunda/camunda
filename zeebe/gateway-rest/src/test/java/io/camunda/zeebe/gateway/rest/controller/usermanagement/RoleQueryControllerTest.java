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
import io.camunda.search.entities.MappingEntity;
import io.camunda.search.entities.RoleEntity;
import io.camunda.search.entities.RoleMemberEntity;
import io.camunda.search.exception.CamundaSearchException;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.GroupQuery;
import io.camunda.search.query.MappingQuery;
import io.camunda.search.query.RoleQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.sort.RoleSort;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.service.GroupServices;
import io.camunda.service.MappingServices;
import io.camunda.service.RoleServices;
import io.camunda.service.UserServices;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.test.util.Strings;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@WebMvcTest(value = RoleController.class)
public class RoleQueryControllerTest extends RestControllerTest {
  private static final String ROLE_BASE_URL = "/v2/roles";

  @MockitoBean private RoleServices roleServices;
  @MockitoBean private UserServices userServices;
  @MockitoBean private MappingServices mappingsServices;
  @MockitoBean private GroupServices groupServices;
  @MockitoBean private CamundaAuthenticationProvider authenticationProvider;

  @BeforeEach
  void setup() {
    when(authenticationProvider.getCamundaAuthentication())
        .thenReturn(AUTHENTICATION_WITH_DEFAULT_TENANT);
    when(roleServices.withAuthentication(any(CamundaAuthentication.class)))
        .thenReturn(roleServices);
    when(userServices.withAuthentication(any(CamundaAuthentication.class)))
        .thenReturn(userServices);
    when(mappingsServices.withAuthentication(any(CamundaAuthentication.class)))
        .thenReturn(mappingsServices);
    when(groupServices.withAuthentication(any(CamundaAuthentication.class)))
        .thenReturn(groupServices);
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
                .startCursor("f")
                .endCursor("v")
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
                 "name": "Role 1",
                 "roleId": "role1",
                 "description": "description 1"
               },
               {
                 "name": "Role 2",
                 "roleId": "role2",
                 "description": "description 2"
               },
               {
                 "name": "Role 12",
                 "roleId": "role12",
                 "description": "description 12"
               }
             ],
             "page": {
               "totalItems": 3,
               "startCursor": "f",
               "endCursor": "v"
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
    when(roleServices.searchMembers(any(RoleQuery.class)))
        .thenReturn(
            new SearchQueryResult.Builder<RoleMemberEntity>()
                .total(3)
                .items(
                    List.of(
                        new RoleMemberEntity("user1", EntityType.USER),
                        new RoleMemberEntity("user2", EntityType.USER),
                        new RoleMemberEntity("user3", EntityType.USER)))
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
                 "username": "user1"
               },
               {
                 "username": "user2"
               },
               {
                 "username": "user3"
               }
             ],
             "page": {
               "totalItems": 3
             }
           }""");

    verify(roleServices)
        .searchMembers(
            new RoleQuery.Builder()
                .filter(f -> f.memberType(EntityType.USER).joinParentId(roleId))
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
                 "mappingId": "mapping1",
                 "claimName": "claim1",
                 "claimValue": "value1",
                 "name": "Mapping 1"
               },
                {
                  "mappingId": "mapping2",
                  "claimName": "claim2",
                  "claimValue": "value2",
                  "name": "Mapping 2"
                },
                {
                  "mappingId": "mapping3",
                  "claimName": "claim3",
                  "claimValue": "value3",
                  "name": "Mapping 3"
                }
             ],
             "page": {
               "totalItems": 3
             }
           }""");

    verify(mappingsServices)
        .search(
            new MappingQuery.Builder().filter(f -> f.roleId(roleId).claimNames(List.of())).build());
  }

  @Test
  public void shouldSearchClientsByRole() {
    // given
    final var roleId = "roleId";
    when(roleServices.searchMembers(any(RoleQuery.class)))
        .thenReturn(
            new SearchQueryResult.Builder<RoleMemberEntity>()
                .total(3)
                .items(
                    List.of(
                        new RoleMemberEntity("client1", EntityType.CLIENT),
                        new RoleMemberEntity("client2", EntityType.CLIENT),
                        new RoleMemberEntity("client3", EntityType.CLIENT)))
                .build());

    // when /then
    webClient
        .post()
        .uri("%s/%s/clients/search".formatted(ROLE_BASE_URL, roleId))
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
                 "clientId": "client1"
               },
               {
                 "clientId": "client2"
               },
               {
                 "clientId": "client3"
               }
             ],
             "page": {
               "totalItems": 3
             }
           }""");

    verify(roleServices)
        .searchMembers(
            new RoleQuery.Builder()
                .filter(f -> f.joinParentId(roleId).memberType(EntityType.CLIENT))
                .build());
  }

  @Test
  void shouldSearchGroupsByRole() {
    // given
    final var roleId = "roleId";
    when(groupServices.search(any(GroupQuery.class)))
        .thenReturn(
            new SearchQueryResult.Builder<GroupEntity>()
                .total(2)
                .items(
                    List.of(
                        new GroupEntity(1L, "group1", "Group 1", "desc 1"),
                        new GroupEntity(2L, "group2", "Group 2", "desc 2")))
                .build());
    // when / then
    webClient
        .post()
        .uri("%s/%s/groups/search".formatted(ROLE_BASE_URL, roleId))
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
                  "groupId": "group1",
                  "name": "Group 1",
                  "description": "desc 1"
                },
                {
                  "groupId": "group2",
                  "name": "Group 2",
                  "description": "desc 2"
                }
              ],
              "page": {
                "totalItems": 2
              }
            }""");

    verify(groupServices).search(new GroupQuery.Builder().filter(f -> f.roleId(roleId)).build());
  }
}
