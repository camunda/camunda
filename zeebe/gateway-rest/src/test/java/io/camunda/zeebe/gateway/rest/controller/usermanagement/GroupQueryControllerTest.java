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

import com.fasterxml.jackson.core.JsonProcessingException;
import io.camunda.search.entities.GroupEntity;
import io.camunda.search.entities.GroupMemberEntity;
import io.camunda.search.entities.MappingRuleEntity;
import io.camunda.search.entities.RoleEntity;
import io.camunda.search.exception.CamundaSearchException;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.GroupMemberQuery;
import io.camunda.search.query.GroupQuery;
import io.camunda.search.query.MappingRuleQuery;
import io.camunda.search.query.RoleQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.sort.GroupSort;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.service.GroupServices;
import io.camunda.service.MappingRuleServices;
import io.camunda.service.RoleServices;
import io.camunda.service.exception.ErrorMapper;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.test.util.Strings;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;

@WebMvcTest(value = GroupController.class)
public class GroupQueryControllerTest extends RestControllerTest {

  static final String EXPECTED_SEARCH_RESPONSE =
      """
      {
        "items":[
          {
            "groupId":"%s",
            "name":"Group 1",
            "description":"Description 1"
          },
          {
            "groupId":"%s",
            "name":"Group 2",
            "description":"Description 2"
          },
          {
            "groupId":"%s",
            "name":"Group 3",
            "description":"Description 3"
          }
        ],
        "page":{
          "totalItems":3,
          "startCursor":"f",
          "endCursor":"v",
          "hasMoreTotalItems": false
        }
      }
      """;
  private static final String GROUP_BASE_URL = "/v2/groups";
  private static final String GROUP_SEARCH_URL = GROUP_BASE_URL + "/search";
  private static final Pattern ID_PATTERN = Pattern.compile(SecurityConfiguration.DEFAULT_ID_REGEX);

  private static final List<GroupMemberEntity> GROUP_USER_ENTITIES =
      List.of(
          new GroupMemberEntity(Strings.newRandomValidIdentityId(), EntityType.USER),
          new GroupMemberEntity(Strings.newRandomValidIdentityId(), EntityType.USER),
          new GroupMemberEntity(Strings.newRandomValidIdentityId(), EntityType.USER));

  private static final String USER_RESPONSE =
      """
      {
        "items":[
          {
            "username":"%s"
          },
          {
            "username":"%s"
          },
          {
            "username":"%s"
          }
        ],
        "page":{
          "totalItems":3,
          "startCursor":"f",
          "endCursor":"v",
          "hasMoreTotalItems": false
        }
      }
      """;

  private static final List<GroupMemberEntity> GROUP_CLIENT_ENTITIES =
      List.of(
          new GroupMemberEntity(Strings.newRandomValidIdentityId(), EntityType.CLIENT),
          new GroupMemberEntity(Strings.newRandomValidIdentityId(), EntityType.CLIENT),
          new GroupMemberEntity(Strings.newRandomValidIdentityId(), EntityType.CLIENT));

  private static final String CLIENT_RESPONSE =
      """
      {
        "items":[
          {
            "clientId":"%s"
          },
          {
            "clientId":"%s"
          },
          {
            "clientId":"%s"
          }
        ],
        "page":{
          "totalItems":3,
          "startCursor":"f",
          "endCursor":"v",
          "hasMoreTotalItems": false
        }
      }
      """;

  private static final List<RoleEntity> ROLE_ENTITIES =
      List.of(
          new RoleEntity(1L, Strings.newRandomValidIdentityId(), "role1", "role1 description"),
          new RoleEntity(2L, Strings.newRandomValidIdentityId(), "role2", "role2 description"),
          new RoleEntity(3L, Strings.newRandomValidIdentityId(), "role3", "role3 description"));

  private static final String ROLE_RESPONSE =
      """
      {
        "items":[
          {
            "roleId":"%s",
            "name":"role1",
            "description":"role1 description"
          },
          {
            "roleId":"%s",
            "name":"role2",
            "description":"role2 description"
          },
          {
            "roleId":"%s",
            "name":"role3",
            "description":"role3 description"
          }
        ],
        "page":{
          "totalItems":3,
          "startCursor":"f",
          "endCursor":"v",
          "hasMoreTotalItems": false
        }
      }
      """;

  private static final String EXPECTED_ROLE_RESPONSE =
      ROLE_RESPONSE.formatted(
          ROLE_ENTITIES.get(0).roleId(),
          ROLE_ENTITIES.get(1).roleId(),
          ROLE_ENTITIES.get(2).roleId());

  private static final String EXPECTED_USER_RESPONSE =
      USER_RESPONSE.formatted(
          GROUP_USER_ENTITIES.get(0).id(),
          GROUP_USER_ENTITIES.get(1).id(),
          GROUP_USER_ENTITIES.get(2).id());

  private static final String EXPECTED_CLIENT_RESPONSE =
      CLIENT_RESPONSE.formatted(
          GROUP_CLIENT_ENTITIES.get(0).id(),
          GROUP_CLIENT_ENTITIES.get(1).id(),
          GROUP_CLIENT_ENTITIES.get(2).id());

  private static final List<MappingRuleEntity> MAPPNING_ENTITIES =
      List.of(
          new MappingRuleEntity(
              Strings.newRandomValidIdentityId(), 1L, "claimName1", "claimValue1", "name"),
          new MappingRuleEntity(
              Strings.newRandomValidIdentityId(), 2L, "claimName2", "claimValue2", "name"));

  private static final String MAPPING_RESPONSE =
      """
      {
        "items":[
          {
            "name":"name",
            "mappingRuleId":"%s",
            "claimName":"claimName1",
            "claimValue":"claimValue1"
          },
          {
            "name":"name",
            "mappingRuleId":"%s",
            "claimName":"claimName2",
            "claimValue":"claimValue2"
          }
        ],
        "page":{
          "totalItems":2,
          "startCursor":"f",
          "endCursor":"v",
          "hasMoreTotalItems": false
        }
      }
      """;

  private static final String EXPECTED_MAPPING_RESPONSE =
      MAPPING_RESPONSE.formatted(
          MAPPNING_ENTITIES.get(0).mappingRuleId(), MAPPNING_ENTITIES.get(1).mappingRuleId());

  @MockitoBean private GroupServices groupServices;
  @MockitoBean private MappingRuleServices mappingServices;
  @MockitoBean private RoleServices roleServices;
  @MockitoBean private CamundaAuthenticationProvider authenticationProvider;
  @MockitoBean private SecurityConfiguration securityConfiguration;

  @BeforeEach
  void setup() {
    when(authenticationProvider.getCamundaAuthentication())
        .thenReturn(AUTHENTICATION_WITH_DEFAULT_TENANT);
    when(groupServices.withAuthentication(any(CamundaAuthentication.class)))
        .thenReturn(groupServices);
    when(roleServices.withAuthentication(any(CamundaAuthentication.class)))
        .thenReturn(roleServices);
    when(mappingServices.withAuthentication(any(CamundaAuthentication.class)))
        .thenReturn(mappingServices);
    when(securityConfiguration.getCompiledIdValidationPattern()).thenReturn(ID_PATTERN);
  }

  @Test
  void shouldReturnOkOnGetGroup() {
    // given
    final var groupKey = 111L;
    final var groupId = Strings.newRandomValidIdentityId();
    final var groupName = "groupName";
    final var groupDescription = "groupDescription";
    final var group = new GroupEntity(groupKey, groupId, groupName, groupDescription);
    when(groupServices.getGroup(group.groupId())).thenReturn(group);

    // when
    webClient
        .get()
        .uri("%s/%s".formatted(GROUP_BASE_URL, group.groupId()))
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .json(
            """
            {
              "groupId": "%s",
              "name": "%s",
              "description": "%s"
            }"""
                .formatted(groupId, groupName, groupDescription),
            JsonCompareMode.STRICT);

    // then
    verify(groupServices, times(1)).getGroup(group.groupId());
  }

  @Test
  void shouldReturnNotFoundOnGetNonExistingGroup() {
    // given
    final var groupId = Strings.newRandomValidIdentityId();
    final var path = "%s/%s".formatted(GROUP_BASE_URL, groupId);
    when(groupServices.getGroup(groupId))
        .thenThrow(
            ErrorMapper.mapSearchError(
                new CamundaSearchException(
                    "group not found", CamundaSearchException.Reason.NOT_FOUND)));

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
                .formatted(path),
            JsonCompareMode.STRICT);

    // then
    verify(groupServices, times(1)).getGroup(groupId);
  }

  @Test
  void shouldSearchGroupsWithEmptyQuery() {
    // given
    final var groupKey1 = 111L;
    final var groupKey2 = 222L;
    final var groupKey3 = 333L;
    final var groupId1 = Strings.newRandomValidIdentityId();
    final var groupId2 = Strings.newRandomValidIdentityId();
    final var groupId3 = Strings.newRandomValidIdentityId();
    final var groupName1 = "Group 1";
    final var groupName2 = "Group 2";
    final var groupName3 = "Group 3";
    final var description1 = "Description 1";
    final var description2 = "Description 2";
    final var description3 = "Description 3";
    when(groupServices.search(any(GroupQuery.class)))
        .thenReturn(
            new SearchQueryResult.Builder<GroupEntity>()
                .total(3)
                .startCursor("f")
                .endCursor("v")
                .items(
                    List.of(
                        new GroupEntity(groupKey1, groupId1, groupName1, description1),
                        new GroupEntity(groupKey2, groupId2, groupName2, description2),
                        new GroupEntity(groupKey3, groupId3, groupName3, description3)))
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
                 "groupId": "%s",
                 "name": "%s",
                 "description": "%s"
               },
               {
                 "groupId": "%s",
                 "name": "%s",
                 "description": "%s"
               },
               {
                 "groupId": "%s",
                 "name": "%s",
                 "description": "%s"
               }
             ],
             "page": {
               "totalItems": 3,
               "startCursor": "f",
               "endCursor": "v",
               "hasMoreTotalItems": false
             }
           }"""
                .formatted(
                    groupId1,
                    groupName1,
                    description1,
                    groupId2,
                    groupName2,
                    description2,
                    groupId3,
                    groupName3,
                    description3),
            JsonCompareMode.STRICT);

    verify(groupServices).search(new GroupQuery.Builder().build());
  }

  @Test
  void shouldSearchGroupsWithIdsWithEmptyQuery() throws JsonProcessingException {
    // given
    final var groupKey1 = 111L;
    final var groupKey2 = 222L;
    final var groupKey3 = 333L;
    final var groupId1 = Strings.newRandomValidIdentityId();
    final var groupId2 = Strings.newRandomValidIdentityId();
    final var groupId3 = Strings.newRandomValidIdentityId();
    final var groupName1 = "Group 1";
    final var groupName2 = "Group 2";
    final var groupName3 = "Group 3";
    final var description1 = "Description 1";
    final var description2 = "Description 2";
    final var description3 = "Description 3";
    when(groupServices.search(any(GroupQuery.class)))
        .thenReturn(
            new SearchQueryResult.Builder<GroupEntity>()
                .total(3)
                .startCursor("f")
                .endCursor("v")
                .items(
                    List.of(
                        new GroupEntity(groupKey1, groupId1, groupName1, description1),
                        new GroupEntity(groupKey2, groupId2, groupName2, description2),
                        new GroupEntity(groupKey3, groupId3, groupName3, description3)))
                .build());

    // when / then
    webClient
        .post()
        .uri(GROUP_SEARCH_URL)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            "{\"sort\":[],\"filter\":{\"groupId\":{\"$in\":[\""
                + groupId1
                + "\",\""
                + groupId2
                + "\"],\"$notIn\":[]}}}")
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
                 "groupId": "%s",
                 "name": "%s",
                 "description": "%s"
               },
               {
                 "groupId": "%s",
                 "name": "%s",
                 "description": "%s"
               },
               {
                 "groupId": "%s",
                 "name": "%s",
                 "description": "%s"
               }
             ],
             "page": {
               "totalItems": 3,
               "startCursor": "f",
               "endCursor": "v",
               "hasMoreTotalItems": false
             }
           }"""
                .formatted(
                    groupId1,
                    groupName1,
                    description1,
                    groupId2,
                    groupName2,
                    description2,
                    groupId3,
                    groupName3,
                    description3),
            JsonCompareMode.STRICT);

    verify(groupServices)
        .search(new GroupQuery.Builder().filter(fn -> fn.groupIds(groupId1, groupId2)).build());
  }

  @Test
  void shouldSortAndPaginateSearchResult() {
    // given
    final var groupKey1 = 111L;
    final var groupKey2 = 222L;
    final var groupKey3 = 333L;
    final var groupId1 = Strings.newRandomValidIdentityId();
    final var groupId2 = Strings.newRandomValidIdentityId();
    final var groupId3 = Strings.newRandomValidIdentityId();
    final var groupName1 = "Group 1";
    final var groupName2 = "Group 2";
    final var groupName3 = "Group 3";
    final var description1 = "Description 1";
    final var description2 = "Description 2";
    final var description3 = "Description 3";
    when(groupServices.search(any(GroupQuery.class)))
        .thenReturn(
            new SearchQueryResult.Builder<GroupEntity>()
                .total(3)
                .items(
                    List.of(
                        new GroupEntity(groupKey1, groupId1, groupName1, description1),
                        new GroupEntity(groupKey2, groupId2, groupName2, description2),
                        new GroupEntity(groupKey3, groupId3, groupName3, description3)))
                .startCursor("f")
                .endCursor("v")
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
        .json(
            EXPECTED_SEARCH_RESPONSE.formatted(groupId1, groupId2, groupId3),
            JsonCompareMode.STRICT);

    verify(groupServices)
        .search(
            new GroupQuery.Builder()
                .sort(GroupSort.of(builder -> builder.name().asc()))
                .page(SearchQueryPage.of(builder -> builder.from(20).size(2)))
                .build());
  }

  @Test
  void shouldSortAndPaginateByLimitOnlySearchResult() {
    // given
    final var groupKey1 = 111L;
    final var groupKey2 = 222L;
    final var groupKey3 = 333L;
    final var groupId1 = Strings.newRandomValidIdentityId();
    final var groupId2 = Strings.newRandomValidIdentityId();
    final var groupId3 = Strings.newRandomValidIdentityId();
    final var groupName1 = "Group 1";
    final var groupName2 = "Group 2";
    final var groupName3 = "Group 3";
    final var description1 = "Description 1";
    final var description2 = "Description 2";
    final var description3 = "Description 3";
    when(groupServices.search(any(GroupQuery.class)))
        .thenReturn(
            new SearchQueryResult.Builder<GroupEntity>()
                .total(3)
                .items(
                    List.of(
                        new GroupEntity(groupKey1, groupId1, groupName1, description1),
                        new GroupEntity(groupKey2, groupId2, groupName2, description2),
                        new GroupEntity(groupKey3, groupId3, groupName3, description3)))
                .startCursor("f")
                .endCursor("v")
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
              "page":  {"limit":  20}
            }
            """)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .json(
            EXPECTED_SEARCH_RESPONSE.formatted(groupId1, groupId2, groupId3),
            JsonCompareMode.STRICT);

    verify(groupServices)
        .search(
            new GroupQuery.Builder()
                .sort(GroupSort.of(builder -> builder.name().asc()))
                .page(SearchQueryPage.of(builder -> builder.size(20)))
                .build());
  }

  @Test
  void shouldSearchGroupUsersWithSorting() {
    // given
    when(groupServices.searchMembers(any(GroupMemberQuery.class)))
        .thenReturn(
            new SearchQueryResult.Builder<GroupMemberEntity>()
                .total(GROUP_USER_ENTITIES.size())
                .items(GROUP_USER_ENTITIES)
                .startCursor("f")
                .endCursor("v")
                .build());

    // when / then
    webClient
        .post()
        .uri("%s/%s/users/search".formatted(GROUP_BASE_URL, "groupId"))
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            """
            {
              "sort": [{"field": "username", "order": "ASC"}]
            }
            """)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .json(EXPECTED_USER_RESPONSE, JsonCompareMode.STRICT);
  }

  @Test
  void shouldSearchGroupUsersWithEmptyQuery() {
    // given
    when(groupServices.searchMembers(any(GroupMemberQuery.class)))
        .thenReturn(
            new SearchQueryResult.Builder<GroupMemberEntity>()
                .total(GROUP_USER_ENTITIES.size())
                .items(GROUP_USER_ENTITIES)
                .startCursor("f")
                .endCursor("v")
                .build());

    // when / then
    webClient
        .post()
        .uri("%s/%s/users/search".formatted(GROUP_BASE_URL, "tenantId"))
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            """
            {
            }
            """)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .json(EXPECTED_USER_RESPONSE, JsonCompareMode.STRICT);
  }

  @Test
  void shouldSearchGroupMappingsWithSorting() {
    // given
    when(mappingServices.search(any(MappingRuleQuery.class)))
        .thenReturn(
            new SearchQueryResult.Builder<MappingRuleEntity>()
                .total(MAPPNING_ENTITIES.size())
                .items(MAPPNING_ENTITIES)
                .startCursor("f")
                .endCursor("v")
                .build());

    // when / then
    webClient
        .post()
        .uri("%s/%s/mapping-rules/search".formatted(GROUP_BASE_URL, "groupId"))
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            """
            {
              "sort": [{"field": "mappingRuleId", "order": "ASC"}]
            }
            """)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .json(EXPECTED_MAPPING_RESPONSE, JsonCompareMode.STRICT);
  }

  @Test
  void shouldSearchGroupMappingsWithEmptyQuery() {
    // given
    when(mappingServices.search(any(MappingRuleQuery.class)))
        .thenReturn(
            new SearchQueryResult.Builder<MappingRuleEntity>()
                .total(MAPPNING_ENTITIES.size())
                .items(MAPPNING_ENTITIES)
                .startCursor("f")
                .endCursor("v")
                .build());

    // when / then
    webClient
        .post()
        .uri("%s/%s/mapping-rules/search".formatted(GROUP_BASE_URL, "tenantId"))
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            """
            {
            }
            """)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .json(EXPECTED_MAPPING_RESPONSE, JsonCompareMode.STRICT);
  }

  @Test
  void shouldSearchGroupRolesWithSorting() {
    // given
    when(roleServices.search(any(RoleQuery.class)))
        .thenReturn(
            new SearchQueryResult.Builder<RoleEntity>()
                .total(ROLE_ENTITIES.size())
                .items(ROLE_ENTITIES)
                .startCursor("f")
                .endCursor("v")
                .build());

    // when / then
    webClient
        .post()
        .uri("%s/%s/roles/search".formatted(GROUP_BASE_URL, "groupId"))
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            """
            {
              "sort": [{"field": "name", "order": "ASC"}]
            }
            """)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .json(EXPECTED_ROLE_RESPONSE, JsonCompareMode.STRICT);
  }

  @Test
  void shouldSearchGroupRolesWithEmptyQuery() {
    // given
    when(roleServices.search(any(RoleQuery.class)))
        .thenReturn(
            new SearchQueryResult.Builder<RoleEntity>()
                .total(ROLE_ENTITIES.size())
                .items(ROLE_ENTITIES)
                .startCursor("f")
                .endCursor("v")
                .build());

    // when / then
    webClient
        .post()
        .uri("%s/%s/roles/search".formatted(GROUP_BASE_URL, "tenantId"))
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            """
            {
            }
            """)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .json(EXPECTED_ROLE_RESPONSE, JsonCompareMode.STRICT);
  }

  @Test
  void shouldSearchGroupClientsWithSorting() {
    // given
    when(groupServices.searchMembers(any(GroupMemberQuery.class)))
        .thenReturn(
            new SearchQueryResult.Builder<GroupMemberEntity>()
                .total(GROUP_CLIENT_ENTITIES.size())
                .items(GROUP_CLIENT_ENTITIES)
                .startCursor("f")
                .endCursor("v")
                .build());

    // when / then
    webClient
        .post()
        .uri("%s/%s/clients/search".formatted(GROUP_BASE_URL, "groupId"))
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            """
            {
              "sort": [{"field": "clientId", "order": "ASC"}]
            }
            """)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .json(EXPECTED_CLIENT_RESPONSE, JsonCompareMode.STRICT);
  }

  @Test
  void shouldSearchGroupClientsWithEmptyQuery() {
    // given
    when(groupServices.searchMembers(any(GroupMemberQuery.class)))
        .thenReturn(
            new SearchQueryResult.Builder<GroupMemberEntity>()
                .total(GROUP_CLIENT_ENTITIES.size())
                .items(GROUP_CLIENT_ENTITIES)
                .startCursor("f")
                .endCursor("v")
                .build());

    // when / then
    webClient
        .post()
        .uri("%s/%s/clients/search".formatted(GROUP_BASE_URL, "groupId"))
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            """
            {
            }
            """)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .json(EXPECTED_CLIENT_RESPONSE, JsonCompareMode.STRICT);
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
        .json(expectedResponse, JsonCompareMode.STRICT);

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
                            "field": "groupId",
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
                      "detail": "Unexpected value 'unknownField' for enum field 'field'. Use any of the following values: [name, groupId]",
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
                      "title": "Bad Request",
                      "status": 400,
                      "detail": "Only one of [from, after, before] is allowed.",
                      "instance": "%s"
                    }""",
                endpoint)));
  }
}
