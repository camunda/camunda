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

import io.camunda.search.entities.TenantEntity;
import io.camunda.search.exception.NotFoundException;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.TenantQuery;
import io.camunda.search.sort.TenantSort;
import io.camunda.security.auth.Authentication;
import io.camunda.service.TenantServices;
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

@WebMvcTest(value = TenantQueryController.class, properties = "camunda.rest.query.enabled=true")
public class TenantQueryControllerTest extends RestControllerTest {
  private static final String TENANT_BASE_URL = "/v2/tenants";
  private static final String SEARCH_TENANT_URL = "%s/search".formatted(TENANT_BASE_URL);

  private static final List<TenantEntity> TENANT_ENTITIES =
      List.of(
          new TenantEntity(100L, "tenant-id-1", "Tenant 1", Set.of()),
          new TenantEntity(200L, "tenant-id-2", "Tenant 2", Set.of(1L, 2L)),
          new TenantEntity(300L, "tenant-id-3", "Tenant 12", Set.of(3L)));

  private static final String EXPECTED_RESPONSE =
      """
      {
         "items": [
           {
             "tenantKey": %d,
             "name": "%s",
             "tenantId": "%s",
             "assignedMemberKeys": %s
           },
           {
             "tenantKey": %d,
             "name": "%s",
             "tenantId": "%s",
             "assignedMemberKeys": %s
           },
           {
             "tenantKey": %d,
             "name": "%s",
             "tenantId": "%s",
             "assignedMemberKeys": %s
           }
         ],
         "page": {
           "totalItems": %d,
           "firstSortValues": [],
           "lastSortValues": []
         }
       }
      """
          .formatted(
              TENANT_ENTITIES.get(0).key(),
              TENANT_ENTITIES.get(0).name(),
              TENANT_ENTITIES.get(0).tenantId(),
              formatSet(TENANT_ENTITIES.get(0).assignedMemberKeys()),
              TENANT_ENTITIES.get(1).key(),
              TENANT_ENTITIES.get(1).name(),
              TENANT_ENTITIES.get(1).tenantId(),
              formatSet(TENANT_ENTITIES.get(1).assignedMemberKeys()),
              TENANT_ENTITIES.get(2).key(),
              TENANT_ENTITIES.get(2).name(),
              TENANT_ENTITIES.get(2).tenantId(),
              formatSet(TENANT_ENTITIES.get(2).assignedMemberKeys()),
              TENANT_ENTITIES.size());

  @MockBean private TenantServices tenantServices;

  private static String formatSet(final Set<Long> set) {
    return set.isEmpty() ? "[]" : set.toString();
  }

  @BeforeEach
  void setup() {
    when(tenantServices.withAuthentication(any(Authentication.class))).thenReturn(tenantServices);
  }

  @Test
  void getTenantShouldReturnOk() {
    // given
    final var tenantName = "Tenant Name";
    final var tenantId = "tenant-id";
    final var tenant = new TenantEntity(100L, tenantId, tenantName, Set.of());
    when(tenantServices.getByKey(tenant.key())).thenReturn(tenant);

    // when
    webClient
        .get()
        .uri("%s/%s".formatted(TENANT_BASE_URL, tenant.key()))
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .json(
            """
            {
              "tenantKey": %d,
              "name": "%s",
              "tenantId": "%s",
              "assignedMemberKeys": []
            }
            """
                .formatted(tenant.key(), tenantName, tenantId));

    // then
    verify(tenantServices, times(1)).getByKey(tenant.key());
  }

  @Test
  void getNonExistingTenantShouldReturnNotFound() {
    // given
    final var tenantKey = 100L;
    final var path = "%s/%s".formatted(TENANT_BASE_URL, tenantKey);
    when(tenantServices.getByKey(tenantKey)).thenThrow(new NotFoundException("tenant not found"));

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
                .formatted(path));

    // then
    verify(tenantServices, times(1)).getByKey(tenantKey);
  }

  @Test
  void shouldSearchTenantsWithEmptyQuery() {
    // given
    when(tenantServices.search(any(TenantQuery.class)))
        .thenReturn(
            new SearchQueryResult.Builder<TenantEntity>()
                .total(3)
                .sortValues(new Object[] {})
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
        .json(EXPECTED_RESPONSE);

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
              "sort": [{"field": "tenantId", "order": "asc"}]
            }
            """)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .json(EXPECTED_RESPONSE);

    verify(tenantServices)
        .search(
            new TenantQuery.Builder()
                .sort(TenantSort.of(builder -> builder.tenantId().asc()))
                .build());
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
        .json(expectedResponse);

    verify(tenantServices, never()).search(any(TenantQuery.class));
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
                      "detail": "Unexpected value 'dsc'",
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
                SEARCH_TENANT_URL)),
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
                SEARCH_TENANT_URL)),
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
                SEARCH_TENANT_URL)));
  }
}
