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

import io.camunda.search.entities.MappingRuleEntity;
import io.camunda.search.exception.CamundaSearchException;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.MappingRuleQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.sort.MappingRuleSort;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.service.MappingRuleServices;
import io.camunda.service.exception.ErrorMapper;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;

@WebMvcTest(value = MappingRuleController.class)
public class MappingRuleQueryControllerTest extends RestControllerTest {
  private static final String MAPPING_RULE_BASE_URL = "/v2/mapping-rules";
  private static final Pattern ID_PATTERN = Pattern.compile(SecurityConfiguration.DEFAULT_ID_REGEX);

  @MockitoBean private MappingRuleServices mappingRuleServices;
  @MockitoBean private CamundaAuthenticationProvider authenticationProvider;
  @MockitoBean private SecurityConfiguration securityConfiguration;

  @BeforeEach
  void setup() {
    when(authenticationProvider.getCamundaAuthentication())
        .thenReturn(AUTHENTICATION_WITH_DEFAULT_TENANT);
    when(mappingRuleServices.withAuthentication(any(CamundaAuthentication.class)))
        .thenReturn(mappingRuleServices);
    when(securityConfiguration.getCompiledIdValidationPattern()).thenReturn(ID_PATTERN);
  }

  @Test
  void getMappingRuleShouldReturnOk() {
    // given
    final var mappingRule =
        new MappingRuleEntity("id", 100L, "Claim Name", "Claim Value", "Map Name");
    when(mappingRuleServices.getMappingRule(mappingRule.mappingRuleId())).thenReturn(mappingRule);

    // when
    webClient
        .get()
        .uri("%s/%s".formatted(MAPPING_RULE_BASE_URL, mappingRule.mappingRuleId()))
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .json(
            """
                          {
                            "claimName": "Claim Name",
                            "claimValue": "Claim Value",
                            "name": "Map Name",
                            "mappingRuleId": "id"
                          }""",
            JsonCompareMode.STRICT);

    // then
    verify(mappingRuleServices, times(1)).getMappingRule(mappingRule.mappingRuleId());
  }

  @Test
  void getNonExistingMappingRuleShouldReturnNotFound() {
    // given
    final var mappingRuleId = "id";
    final var path = "%s/%s".formatted(MAPPING_RULE_BASE_URL, mappingRuleId);
    when(mappingRuleServices.getMappingRule(mappingRuleId))
        .thenThrow(
            ErrorMapper.mapSearchError(
                new CamundaSearchException(
                    "mapping rule not found", CamundaSearchException.Reason.NOT_FOUND)));

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
              "detail": "mapping rule not found",
              "instance": "%s"
            }"""
                .formatted(path),
            JsonCompareMode.STRICT);

    // then
    verify(mappingRuleServices, times(1)).getMappingRule(mappingRuleId);
  }

  @Test
  void shouldSearchMappingRulesWithEmptyQuery() {
    // given
    when(mappingRuleServices.search(any(MappingRuleQuery.class)))
        .thenReturn(
            new SearchQueryResult.Builder<MappingRuleEntity>()
                .total(3)
                .startCursor("f")
                .endCursor("v")
                .items(
                    List.of(
                        new MappingRuleEntity(
                            "id1", 100L, "Claim Name1", "Claim Value1", "Map Name1"),
                        new MappingRuleEntity(
                            "id2", 200L, "Claim Name2", "Claim Value2", "Map Name2"),
                        new MappingRuleEntity(
                            "id3", 300L, "Claim Name3", "Claim Value3", "Map Name3")))
                .build());

    // when / then
    webClient
        .post()
        .uri("%s/search".formatted(MAPPING_RULE_BASE_URL))
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
                 "claimName": "Claim Name1",
                 "claimValue": "Claim Value1",
                 "name": "Map Name1",
                 "mappingRuleId": "id1"
               },
               {
                 "claimName": "Claim Name2",
                 "claimValue": "Claim Value2",
                 "name": "Map Name2",
                 "mappingRuleId": "id2"
               },
               {
                 "claimName": "Claim Name3",
                 "claimValue": "Claim Value3",
                 "name": "Map Name3",
                 "mappingRuleId": "id3"
               }
             ],
             "page": {
               "totalItems": 3,
               "startCursor": "f",
               "endCursor": "v",
               "hasMoreTotalItems": false
             }
           }""",
            JsonCompareMode.STRICT);

    verify(mappingRuleServices).search(new MappingRuleQuery.Builder().build());
  }

  @Test
  void shouldSortAndPaginateSearchResult() {
    // given
    when(mappingRuleServices.search(any(MappingRuleQuery.class)))
        .thenReturn(
            new SearchQueryResult.Builder<MappingRuleEntity>()
                .total(3)
                .items(
                    List.of(
                        new MappingRuleEntity(
                            "id1", 100L, "Claim Name1", "Claim Value1", "Map Name1"),
                        new MappingRuleEntity(
                            "id2", 200L, "Claim Name2", "Claim Value2", "Map Name2"),
                        new MappingRuleEntity(
                            "id3", 300L, "Claim Name3", "Claim Value3", "Map Name3")))
                .build());

    // when / then
    webClient
        .post()
        .uri("%s/search".formatted(MAPPING_RULE_BASE_URL))
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            """
            {
              "sort":  [{"field": "claimName", "order":  "ASC"}],
              "page":  {"from":  20, "limit":  10}
            }
             """)
        .exchange()
        .expectStatus()
        .isOk();

    verify(mappingRuleServices)
        .search(
            new MappingRuleQuery.Builder()
                .sort(MappingRuleSort.of(builder -> builder.claimName().asc()))
                .page(SearchQueryPage.of(builder -> builder.from(20).size(10)))
                .build());
  }

  @Test
  void shouldSortAndPaginateByLimitOnlySearchResult() {
    // given
    when(mappingRuleServices.search(any(MappingRuleQuery.class)))
        .thenReturn(
            new SearchQueryResult.Builder<MappingRuleEntity>()
                .total(3)
                .items(
                    List.of(
                        new MappingRuleEntity(
                            "id1", 100L, "Claim Name1", "Claim Value1", "Map Name1"),
                        new MappingRuleEntity(
                            "id2", 200L, "Claim Name2", "Claim Value2", "Map Name2"),
                        new MappingRuleEntity(
                            "id3", 300L, "Claim Name3", "Claim Value3", "Map Name3")))
                .build());

    // when / then
    webClient
        .post()
        .uri("%s/search".formatted(MAPPING_RULE_BASE_URL))
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            """
            {
              "sort":  [{"field": "claimName", "order":  "ASC"}],
              "page":  {"limit":  10}
            }
             """)
        .exchange()
        .expectStatus()
        .isOk();

    verify(mappingRuleServices)
        .search(
            new MappingRuleQuery.Builder()
                .sort(MappingRuleSort.of(builder -> builder.claimName().asc()))
                .page(SearchQueryPage.of(builder -> builder.size(10)))
                .build());
  }

  @Test
  void shouldSortAndPaginateSearchResultByName() {
    // given
    when(mappingRuleServices.search(any(MappingRuleQuery.class)))
        .thenReturn(
            new SearchQueryResult.Builder<MappingRuleEntity>()
                .total(3)
                .items(
                    List.of(
                        new MappingRuleEntity(
                            "id1", 100L, "Claim Name1", "Claim Value1", "Map Name3"),
                        new MappingRuleEntity(
                            "id2", 200L, "Claim Name2", "Claim Value2", "Map Name1"),
                        new MappingRuleEntity(
                            "id3", 300L, "Claim Name3", "Claim Value3", "Map Name2")))
                .build());

    // when / then
    webClient
        .post()
        .uri("%s/search".formatted(MAPPING_RULE_BASE_URL))
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

    verify(mappingRuleServices)
        .search(
            new MappingRuleQuery.Builder()
                .sort(MappingRuleSort.of(builder -> builder.name().asc()))
                .page(SearchQueryPage.of(builder -> builder.from(20).size(10)))
                .build());
  }
}
