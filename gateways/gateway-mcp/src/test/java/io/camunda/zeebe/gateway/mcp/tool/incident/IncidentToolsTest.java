/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.mcp.tool.incident;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.search.entities.IncidentEntity;
import io.camunda.search.entities.IncidentEntity.ErrorType;
import io.camunda.search.entities.IncidentEntity.IncidentState;
import io.camunda.search.filter.IncidentFilter;
import io.camunda.search.filter.Operation;
import io.camunda.search.query.IncidentQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.SearchQueryResult.Builder;
import io.camunda.search.sort.SortOrder;
import io.camunda.service.IncidentServices;
import io.camunda.zeebe.gateway.mcp.tool.ToolsTest;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.springframework.beans.factory.annotation.Autowired;

class IncidentToolsTest extends ToolsTest {

  static final IncidentEntity INCIDENT_ENTITY =
      new IncidentEntity(
          5L,
          23L,
          "complexProcess",
          42L,
          ErrorType.JOB_NO_RETRIES,
          "No retries left.",
          "elementId",
          17L,
          OffsetDateTime.parse("2024-05-23T23:05:00.000Z"),
          IncidentState.ACTIVE,
          101L,
          "tenantId");

  static final SearchQueryResult<IncidentEntity> SEARCH_QUERY_RESULT =
      new Builder<IncidentEntity>()
          .total(1L)
          .items(List.of(INCIDENT_ENTITY))
          .startCursor("f")
          .endCursor("v")
          .build();

  @Autowired private IncidentServices incidentServices;
  @Autowired private ObjectMapper objectMapper;
  @Captor private ArgumentCaptor<IncidentQuery> queryCaptor;

  @BeforeEach
  void mockApiServices() {
    mockApiServiceAuthentication(incidentServices);
  }

  @Test
  void shouldGetIncidentByKey() {
    when(incidentServices.getByKey(any(Long.class))).thenReturn(INCIDENT_ENTITY);

    final CallToolResult result =
        mcpClient.callTool(
            CallToolRequest.builder()
                .name("getIncident")
                .arguments(Map.of("incidentKey", 5L))
                .build());

    assertThat(result.isError()).isFalse();
    assertThat(result.structuredContent()).isNotNull();

    final var incident =
        objectMapper.convertValue(result.structuredContent(), IncidentEntity.class);
    assertThat(incident).usingRecursiveComparison().isEqualTo(INCIDENT_ENTITY);

    verify(incidentServices).getByKey(5L);
  }

  @Test
  void shouldSearchIncidents_WithCreationTimeDateRangeFilter() {
    when(incidentServices.search(any(IncidentQuery.class))).thenReturn(SEARCH_QUERY_RESULT);

    final var creationTimeFrom = OffsetDateTime.of(2024, 5, 23, 0, 0, 0, 0, ZoneOffset.UTC);
    final var creationTimeTo = OffsetDateTime.of(2024, 5, 24, 0, 0, 0, 0, ZoneOffset.UTC);

    final CallToolResult result =
        mcpClient.callTool(
            CallToolRequest.builder()
                .name("searchIncidents")
                .arguments(
                    Map.of(
                        "filter",
                        Map.of(
                            "creationTimeFrom",
                            "2024-05-23T00:00:00.000Z",
                            "creationTimeTo",
                            "2024-05-24T00:00:00.000Z")))
                .build());

    assertThat(result.isError()).isFalse();

    verify(incidentServices).search(queryCaptor.capture());
    final IncidentFilter filter = queryCaptor.getValue().filter();
    assertThat(filter.creationTimeOperations()).hasSize(2);

    assertThat(filter.creationTimeOperations().stream().map(Operation::value).toList())
        .containsExactlyInAnyOrder(creationTimeFrom, creationTimeTo);
  }

  @Test
  void shouldSearchIncidents_WithFilterSortAndPaging() {
    when(incidentServices.search(any(IncidentQuery.class))).thenReturn(SEARCH_QUERY_RESULT);

    final CallToolResult result =
        mcpClient.callTool(
            CallToolRequest.builder()
                .name("searchIncidents")
                .arguments(
                    Map.of(
                        "filter",
                        Map.of("state", "ACTIVE", "errorType", "JOB_NO_RETRIES"),
                        "sort",
                        List.of(Map.of("field", "incidentKey", "order", "DESC")),
                        "page",
                        Map.of("limit", 25, "after", "WzEwMjRd")))
                .build());

    assertThat(result.isError()).isFalse();

    verify(incidentServices).search(queryCaptor.capture());
    final IncidentQuery capturedQuery = queryCaptor.getValue();

    final IncidentFilter filter = capturedQuery.filter();
    assertThat(filter.stateOperations()).hasSize(1);
    assertThat(filter.stateOperations().getFirst().value()).isEqualTo("ACTIVE");
    assertThat(filter.errorTypeOperations()).hasSize(1);
    assertThat(filter.errorTypeOperations().getFirst().value()).isEqualTo("JOB_NO_RETRIES");

    assertThat(capturedQuery.sort().orderings()).hasSize(1);
    assertThat(capturedQuery.sort().orderings().getFirst().field()).isEqualTo("incidentKey");
    assertThat(capturedQuery.sort().orderings().getFirst().order()).isEqualTo(SortOrder.DESC);

    assertThat(capturedQuery.page().size()).isEqualTo(25);
    assertThat(capturedQuery.page().after()).isEqualTo("WzEwMjRd");
  }
}
