/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.mcp.tool.incident;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.search.entities.IncidentEntity;
import io.camunda.search.entities.IncidentEntity.ErrorType;
import io.camunda.search.entities.IncidentEntity.IncidentState;
import io.camunda.search.filter.IncidentFilter;
import io.camunda.search.filter.Operation;
import io.camunda.search.filter.Operator;
import io.camunda.search.query.IncidentQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.SearchQueryResult.Builder;
import io.camunda.search.sort.SortOption.FieldSorting;
import io.camunda.search.sort.SortOrder;
import io.camunda.service.IncidentServices;
import io.camunda.service.JobServices;
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
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@ContextConfiguration(classes = {IncidentTools.class})
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

  @MockitoBean private IncidentServices incidentServices;
  @MockitoBean private JobServices jobServices;

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
  void shouldSearchIncidentsWithCreationTimeDateRangeFilter() {
    when(incidentServices.search(any(IncidentQuery.class))).thenReturn(SEARCH_QUERY_RESULT);

    final var creationTimeFrom = OffsetDateTime.of(2025, 5, 23, 9, 35, 12, 0, ZoneOffset.UTC);
    final var creationTimeTo = OffsetDateTime.of(2025, 12, 18, 17, 22, 33, 0, ZoneOffset.UTC);

    final CallToolResult result =
        mcpClient.callTool(
            CallToolRequest.builder()
                .name("searchIncidents")
                .arguments(
                    Map.of(
                        "filter",
                        Map.of(
                            "creationTimeFrom",
                            "2025-05-23T09:35:12Z",
                            "creationTimeTo",
                            "2025-12-18T17:22:33Z")))
                .build());

    assertThat(result.isError()).isFalse();

    verify(incidentServices).search(queryCaptor.capture());
    final IncidentQuery capturedQuery = queryCaptor.getValue();

    assertThat(capturedQuery.filter().creationTimeOperations())
        .extracting(Operation::operator, Operation::value)
        .containsExactly(
            tuple(Operator.GREATER_THAN_EQUALS, creationTimeFrom),
            tuple(Operator.LOWER_THAN, creationTimeTo));
  }

  @Test
  void shouldSearchIncidentsWithFilterSortAndPaging() {
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
    assertThat(filter.stateOperations())
        .extracting(Operation::operator, Operation::value)
        .containsExactly(tuple(Operator.EQUALS, "ACTIVE"));

    assertThat(filter.errorTypeOperations())
        .extracting(Operation::operator, Operation::value)
        .containsExactly(tuple(Operator.EQUALS, "JOB_NO_RETRIES"));

    assertThat(capturedQuery.sort().orderings())
        .extracting(FieldSorting::field, FieldSorting::order)
        .containsExactly(tuple("incidentKey", SortOrder.DESC));

    assertThat(capturedQuery.page().size()).isEqualTo(25);
    assertThat(capturedQuery.page().after()).isEqualTo("WzEwMjRd");
  }
}
