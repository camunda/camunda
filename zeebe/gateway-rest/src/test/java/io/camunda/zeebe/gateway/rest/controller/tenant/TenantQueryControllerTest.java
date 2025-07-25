/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.tenant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.search.entities.GroupEntity;
import io.camunda.search.entities.MappingRuleEntity;
import io.camunda.search.entities.RoleEntity;
import io.camunda.search.entities.TenantEntity;
import io.camunda.search.entities.TenantMemberEntity;
import io.camunda.search.exception.CamundaSearchException;
import io.camunda.search.query.MappingRuleQuery;
import io.camunda.search.query.RoleQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.TenantQuery;
import io.camunda.search.sort.TenantSort;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.service.GroupServices;
import io.camunda.service.MappingRuleServices;
import io.camunda.service.RoleServices;
import io.camunda.service.TenantServices;
import io.camunda.service.UserServices;
import io.camunda.service.exception.ErrorMapper;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.List;
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

@WebMvcTest(value = TenantController.class)
public class TenantQueryControllerTest extends RestControllerTest {
  private static final String TENANT_BASE_URL = "/v2/tenants";
  private static final String SEARCH_TENANT_URL = "%s/search".formatted(TENANT_BASE_URL);

  private static final List<TenantEntity> TENANT_ENTITIES =
      List.of(
          new TenantEntity(100L, "tenant-id-1", "Tenant 1", "Description 1"),
          new TenantEntity(200L, "tenant-id-2", "Tenant 2", "Description 2"),
          new TenantEntity(300L, "tenant-id-3", "Tenant 12", "Description 3"));

  private static final List<MappingRuleEntity> MAPPING_ENTITIES =
      List.of(
          new MappingRuleEntity("mapping-rule-id-1", 1L, "claim1", "value1", "cv1"),
          new MappingRuleEntity("mapping-rule-id-2", 2L, "claim2", "value2", "cv2"));

  private static final List<GroupEntity> GROUP_ENTITIES =
      List.of(
          new GroupEntity(1L, "group-id-1", "Group 1", "Description 1"),
          new GroupEntity(2L, "group-id-2", "Group 2", "Description 2"));

  private static final List<RoleEntity> ROLE_ENTITIES =
      List.of(
          new RoleEntity(1L, "role-id-1", "Role 1", "Description 1"),
          new RoleEntity(2L, "role-id-2", "Role 2", "Description 2"));

  private static final String RESPONSE =
      """
      {
         "items": [
           {
             "name": "%s",
             "description": "%s",
             "tenantId": "%s"
           },
           {
             "name": "%s",
             "description": "%s",
             "tenantId": "%s"
           },
           {
             "name": "%s",
             "description": "%s",
             "tenantId": "%s"
           }
         ],
         "page": {
           "totalItems": %s,
           "startCursor": "f",
           "endCursor": "v",
           "hasMoreTotalItems": false
         }
       }
      """;
  private static final String EXPECTED_RESPONSE =
      RESPONSE.formatted(
          TENANT_ENTITIES.get(0).name(),
          TENANT_ENTITIES.get(0).description(),
          TENANT_ENTITIES.get(0).tenantId(),
          TENANT_ENTITIES.get(1).name(),
          TENANT_ENTITIES.get(1).description(),
          TENANT_ENTITIES.get(1).tenantId(),
          TENANT_ENTITIES.get(2).name(),
          TENANT_ENTITIES.get(2).description(),
          TENANT_ENTITIES.get(2).tenantId(),
          TENANT_ENTITIES.size());

  private static final String MAPPING_RESPONSE =
      """
      {
         "items": [
           {
             "name": "%s",
             "claimName": "%s",
             "claimValue": "%s",
             "mappingRuleId": %s
           },
           {
             "name": "%s",
             "claimName": "%s",
             "claimValue": "%s",
             "mappingRuleId": "%s"
           }
         ],
         "page": {
           "totalItems": %s,
           "startCursor": "f",
           "endCursor": "v",
           "hasMoreTotalItems": false
         }
       }
      """;

  private static final String EXPECTED_MAPPING_RESPONSE =
      MAPPING_RESPONSE.formatted(
          MAPPING_ENTITIES.get(0).name(),
          MAPPING_ENTITIES.get(0).claimName(),
          MAPPING_ENTITIES.get(0).claimValue(),
          MAPPING_ENTITIES.get(0).mappingRuleId(),
          MAPPING_ENTITIES.get(1).name(),
          MAPPING_ENTITIES.get(1).claimName(),
          MAPPING_ENTITIES.get(1).claimValue(),
          MAPPING_ENTITIES.get(1).mappingRuleId(),
          MAPPING_ENTITIES.size());

  private static final String GROUP_RESPONSE =
      """
      {
         "items": [
           {
             "groupId": "%s"
           },
           {
             "groupId": "%s"
           }
         ],
         "page": {
           "totalItems": %s,
           "startCursor": "f",
           "endCursor": "v",
           "hasMoreTotalItems": false
         }
       }
      """;

  private static final String EXPECTED_GROUP_RESPONSE =
      GROUP_RESPONSE.formatted(
          GROUP_ENTITIES.get(0).name(),
          GROUP_ENTITIES.get(0).description(),
          GROUP_ENTITIES.get(0).groupId(),
          GROUP_ENTITIES.get(1).name(),
          GROUP_ENTITIES.get(1).description(),
          GROUP_ENTITIES.get(1).groupId(),
          GROUP_ENTITIES.size());

  private static final String ROLE_RESPONSE =
      """
      {
         "items": [
           {
             "name": "%s",
             "description": "%s",
             "roleId": "%s"
           },
           {
             "name": "%s",
             "description": "%s",
             "roleId": "%s"
           }
         ],
         "page": {
           "totalItems": %s,
           "startCursor": "f",
           "endCursor": "v",
           "hasMoreTotalItems": false
         }
       }
      """;

  private static final String EXPECTED_ROLE_RESPONSE =
      ROLE_RESPONSE.formatted(
          ROLE_ENTITIES.get(0).name(),
          ROLE_ENTITIES.get(0).description(),
          ROLE_ENTITIES.get(0).roleId(),
          ROLE_ENTITIES.get(1).name(),
          ROLE_ENTITIES.get(1).description(),
          ROLE_ENTITIES.get(1).roleId(),
          ROLE_ENTITIES.size());

  @MockitoBean private TenantServices tenantServices;
  @MockitoBean private UserServices userServices;
  @MockitoBean private MappingRuleServices mappingRuleServices;
  @MockitoBean private GroupServices groupServices;
  @MockitoBean private RoleServices roleServices;
  @MockitoBean private CamundaAuthenticationProvider authenticationProvider;

  @BeforeEach
  void setup() {
    when(authenticationProvider.getCamundaAuthentication())
        .thenReturn(AUTHENTICATION_WITH_DEFAULT_TENANT);
    when(tenantServices.withAuthentication(any(CamundaAuthentication.class)))
        .thenReturn(tenantServices);
    when(userServices.withAuthentication(any(CamundaAuthentication.class)))
        .thenReturn(userServices);
    when(mappingRuleServices.withAuthentication(any(CamundaAuthentication.class)))
        .thenReturn(mappingRuleServices);
    when(groupServices.withAuthentication(any(CamundaAuthentication.class)))
        .thenReturn(groupServices);
    when(roleServices.withAuthentication(any(CamundaAuthentication.class)))
        .thenReturn(roleServices);
  }

  @Test
  void getTenantShouldReturnOk() {
    // given
    final var tenantName = "Tenant Name";
    final var tenantId = "tenant-id";
    final var tenantDescription = "Tenant Description";
    final var tenant = new TenantEntity(100L, tenantId, tenantName, tenantDescription);
    when(tenantServices.getById(tenant.tenantId())).thenReturn(tenant);

    // when
    webClient
        .get()
        .uri("%s/%s".formatted(TENANT_BASE_URL, tenant.tenantId()))
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .json(
            """
            {
              "name": "%s",
              "description": "%s",
              "tenantId": "%s"
            }
            """
                .formatted(tenantName, tenantDescription, tenantId),
            JsonCompareMode.STRICT);

    // then
    verify(tenantServices, times(1)).getById(tenant.tenantId());
  }

  @Test
  void getNonExistingTenantShouldReturnNotFound() {
    // given
    final var tenantId = "non-existing-tenant";
    final var path = "%s/%s".formatted(TENANT_BASE_URL, tenantId);
    when(tenantServices.getById(tenantId))
        .thenThrow(
            ErrorMapper.mapSearchError(
                new CamundaSearchException(
                    "tenant not found", CamundaSearchException.Reason.NOT_FOUND)));

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
              "detail": "tenant not found",
              "instance": "%s"
            }"""
                .formatted(path),
            JsonCompareMode.STRICT);

    // then
    verify(tenantServices, times(1)).getById(tenantId);
  }

  @Test
  void shouldSearchTenantsWithEmptyQuery() {
    // given
    when(tenantServices.search(any(TenantQuery.class)))
        .thenReturn(
            new SearchQueryResult.Builder<TenantEntity>()
                .total(3)
                .startCursor("f")
                .endCursor("v")
                .items(TENANT_ENTITIES)
                .build());

    // when / then
    webClient
        .post()
        .uri(SEARCH_TENANT_URL)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{}")
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .json(EXPECTED_RESPONSE, JsonCompareMode.STRICT);

    verify(tenantServices).search(new TenantQuery.Builder().build());
  }

  @Test
  void shouldSearchTenantsWithSorting() {
    // given
    when(tenantServices.search(any(TenantQuery.class)))
        .thenReturn(
            new SearchQueryResult.Builder<TenantEntity>()
                .total(TENANT_ENTITIES.size())
                .items(TENANT_ENTITIES)
                .startCursor("f")
                .endCursor("v")
                .build());

    // when / then
    webClient
        .post()
        .uri(SEARCH_TENANT_URL)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            """
            {
              "sort": [{"field": "tenantId", "order": "ASC"}]
            }
            """)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .json(EXPECTED_RESPONSE, JsonCompareMode.STRICT);

    verify(tenantServices)
        .search(
            new TenantQuery.Builder()
                .sort(TenantSort.of(builder -> builder.tenantId().asc()))
                .build());
  }

  @Test
  void shouldSearchTenantMappingsWithSorting() {
    // given
    when(mappingRuleServices.search(any(MappingRuleQuery.class)))
        .thenReturn(
            new SearchQueryResult.Builder<MappingRuleEntity>()
                .total(MAPPING_ENTITIES.size())
                .items(MAPPING_ENTITIES)
                .startCursor("f")
                .endCursor("v")
                .build());

    // when / then
    webClient
        .post()
        .uri("%s/%s/mapping-rules/search".formatted(TENANT_BASE_URL, "tenantId"))
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
  void shouldSearchTenantMappingsWithEmptyQuery() {
    // given
    when(mappingRuleServices.search(any(MappingRuleQuery.class)))
        .thenReturn(
            new SearchQueryResult.Builder<MappingRuleEntity>()
                .total(MAPPING_ENTITIES.size())
                .items(MAPPING_ENTITIES)
                .startCursor("f")
                .endCursor("v")
                .build());

    // when / then
    webClient
        .post()
        .uri("%s/%s/mapping-rules/search".formatted(TENANT_BASE_URL, "tenantId"))
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
  void shouldSearchTenantRolesWithSorting() {
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
        .uri("%s/%s/roles/search".formatted(TENANT_BASE_URL, "tenantId"))
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            """
            {
              "sort": [{"field": "roleId", "order": "ASC"}]
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
  void shouldSearchTenantRolesWithEmptyQuery() {
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
        .uri("%s/%s/roles/search".formatted(TENANT_BASE_URL, "tenantId"))
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

  @ParameterizedTest
  @MethodSource("invalidTenantSearchQueries")
  void shouldInvalidateTenantsSearchQueryWithBadQueries(
      final String request, final String expectedResponse) {
    // when / then
    webClient
        .post()
        .uri(SEARCH_TENANT_URL)
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

    verify(tenantServices, never()).search(any(TenantQuery.class));
  }

  @Test
  void shouldListMembersOfTypeClient() {
    // given
    when(tenantServices.searchMembers(any(TenantQuery.class)))
        .thenReturn(
            new SearchQueryResult.Builder<TenantMemberEntity>()
                .total(3)
                .items(
                    List.of(
                        new TenantMemberEntity("client1", EntityType.CLIENT),
                        new TenantMemberEntity("client2", EntityType.CLIENT),
                        new TenantMemberEntity("client3", EntityType.CLIENT)))
                .build());

    // when / then
    webClient
        .post()
        .uri("%s/%s/clients/search".formatted(TENANT_BASE_URL, "tenantId"))
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .json(
            // language=json
            """
                {
                  "items": [
                    { "clientId":"client1" },
                    { "clientId":"client2" },
                    { "clientId":"client3" }
                  ],
                  "page": {
                    "totalItems":3,
                    "hasMoreTotalItems": false
                  }
                }
            """,
            JsonCompareMode.STRICT);
  }

  @Test
  void shouldListMembersOfTypeUser() {
    // given
    when(tenantServices.searchMembers(any(TenantQuery.class)))
        .thenReturn(
            new SearchQueryResult.Builder<TenantMemberEntity>()
                .total(3)
                .items(
                    List.of(
                        new TenantMemberEntity("user1", EntityType.USER),
                        new TenantMemberEntity("user2", EntityType.USER),
                        new TenantMemberEntity("user3", EntityType.USER)))
                .build());

    // when / then
    webClient
        .post()
        .uri("%s/%s/users/search".formatted(TENANT_BASE_URL, "tenantId"))
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .json(
            // language=json
            """
                {
                  "items": [
                    { "username":"user1" },
                    { "username":"user2" },
                    { "username":"user3" }
                  ],
                  "page": {
                    "totalItems": 3,
                    "hasMoreTotalItems": false
                  }
                }
            """,
            JsonCompareMode.STRICT);
  }

  @Test
  void shouldListMembersOfTypeGroup() {
    // given
    when(tenantServices.searchMembers(any(TenantQuery.class)))
        .thenReturn(
            new SearchQueryResult.Builder<TenantMemberEntity>()
                .total(3)
                .items(
                    List.of(
                        new TenantMemberEntity("group1", EntityType.GROUP),
                        new TenantMemberEntity("group2", EntityType.GROUP),
                        new TenantMemberEntity("group3", EntityType.GROUP)))
                .build());

    // when / then
    webClient
        .post()
        .uri("%s/%s/groups/search".formatted(TENANT_BASE_URL, "tenantId"))
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .json(
            // language=json
            """
                {
                  "items": [
                    { "groupId":"group1" },
                    { "groupId":"group2" },
                    { "groupId":"group3" }
                  ],
                  "page": {
                    "totalItems":3,
                    "hasMoreTotalItems": false
                  }
                }
            """,
            JsonCompareMode.STRICT);
  }

  public static Stream<Arguments> invalidTenantSearchQueries() {
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
                SEARCH_TENANT_URL)),
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
                      "detail": "Unexpected value 'unknownField' for enum field 'field'. Use any of the following values: [key, name, tenantId]",
                      "instance": "%s"
                    }""",
                SEARCH_TENANT_URL)),
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
                SEARCH_TENANT_URL)),
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
                SEARCH_TENANT_URL)));
  }
}
