/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mcp.tool.incident;

import static io.camunda.gateway.mcp.tool.CallToolResultAssertions.assertTextContentFallback;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.gateway.mapping.http.search.contract.StrictSearchQueryResult;
import io.camunda.gateway.mcp.OperationalToolsTest;
import io.camunda.gateway.protocol.model.IncidentErrorTypeEnum;
import io.camunda.gateway.protocol.model.IncidentResult;
import io.camunda.gateway.protocol.model.IncidentStateEnum;
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
import io.camunda.service.JobServices.UpdateJobChangeset;
import io.camunda.service.exception.ServiceException;
import io.camunda.service.exception.ServiceException.Status;
import io.camunda.zeebe.protocol.impl.record.value.incident.IncidentRecord;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
@ContextConfiguration(classes = {IncidentTools.class})
class IncidentToolsTest extends OperationalToolsTest {

  static final IncidentEntity INCIDENT_ENTITY =
      new IncidentEntity(
          5L,
          23L,
          "complexProcess",
          42L,
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
  @MockitoBean private JobServices<?> jobServices;

  @Autowired private JsonMapper objectMapper;
  @Captor private ArgumentCaptor<IncidentQuery> queryCaptor;

  private void assertExampleIncident(final IncidentResult incident) {
    assertThat(incident.getIncidentKey()).isEqualTo("5");
    assertThat(incident.getProcessDefinitionKey()).isEqualTo("23");
    assertThat(incident.getProcessDefinitionId()).isEqualTo("complexProcess");
    assertThat(incident.getProcessInstanceKey()).isEqualTo("42");
    assertThat(incident.getErrorType()).isEqualTo(IncidentErrorTypeEnum.JOB_NO_RETRIES);
    assertThat(incident.getErrorMessage()).isEqualTo("No retries left.");
    assertThat(incident.getElementId()).isEqualTo("elementId");
    assertThat(incident.getElementInstanceKey()).isEqualTo("17");
    assertThat(incident.getCreationTime()).isEqualTo("2024-05-23T23:05:00.000Z");
    assertThat(incident.getState()).isEqualTo(IncidentStateEnum.ACTIVE);
    assertThat(incident.getJobKey()).isEqualTo("101");
    assertThat(incident.getTenantId()).isEqualTo("tenantId");
  }

  @Nested
  class GetIncident {

    @Test
    void shouldGetIncidentByKey() {
      // given
      when(incidentServices.getByKey(any(), any())).thenReturn(INCIDENT_ENTITY);

      // when
      final CallToolResult result =
          mcpClient.callTool(
              CallToolRequest.builder()
                  .name("getIncident")
                  .arguments(Map.of("incidentKey", 5L))
                  .build());

      // then
      assertThat(result.isError()).isFalse();
      assertThat(result.structuredContent()).isNotNull();

      final var incident =
          objectMapper.convertValue(result.structuredContent(), IncidentResult.class);
      assertExampleIncident(incident);

      verify(incidentServices).getByKey(eq(5L), any());

      assertTextContentFallback(result);
    }

    @Test
    void shouldFailGetIncidentByKeyOnException() {
      // given
      when(incidentServices.getByKey(any(), any()))
          .thenThrow(new ServiceException("Expected failure", Status.NOT_FOUND));

      // when
      final CallToolResult result =
          mcpClient.callTool(
              CallToolRequest.builder()
                  .name("getIncident")
                  .arguments(Map.of("incidentKey", 5L))
                  .build());

      // then
      assertThat(result.isError()).isTrue();
      assertThat(result.structuredContent()).isNotNull();

      final var problemDetail =
          objectMapper.convertValue(result.structuredContent(), ProblemDetail.class);
      assertThat(problemDetail.getDetail()).isEqualTo("Expected failure");
      assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
      assertThat(problemDetail.getTitle()).isEqualTo("Not Found");

      assertTextContentFallback(result);
    }

    @Test
    void shouldFailGetIncidentByKeyOnMissingKey() {
      // when
      final CallToolResult result =
          mcpClient.callTool(
              CallToolRequest.builder().name("getIncident").arguments(Map.of()).build());

      // then
      assertThat(result.isError()).isTrue();
      assertThat(result.structuredContent()).isNull();
      assertThat(result.content())
          .hasSize(1)
          .first()
          .isInstanceOfSatisfying(
              TextContent.class,
              textContent ->
                  assertThat(textContent.text())
                      .isEqualTo("incidentKey: IncidentResult key must not be null."));
    }

    @Test
    void shouldFailGetIncidentByKeyOnNullKey() {
      // when
      final var arguments = new HashMap<String, Object>();
      arguments.put("incidentKey", null);
      final CallToolResult result =
          mcpClient.callTool(
              CallToolRequest.builder().name("getIncident").arguments(arguments).build());

      // then
      assertThat(result.isError()).isTrue();
      assertThat(result.structuredContent()).isNull();
      assertThat(result.content())
          .hasSize(1)
          .first()
          .isInstanceOfSatisfying(
              TextContent.class,
              textContent ->
                  assertThat(textContent.text())
                      .isEqualTo("incidentKey: IncidentResult key must not be null."));
    }

    @Test
    void shouldFailGetIncidentByKeyOnInvalidKey() {
      // when
      final CallToolResult result =
          mcpClient.callTool(
              CallToolRequest.builder()
                  .name("getIncident")
                  .arguments(Map.of("incidentKey", -3L))
                  .build());

      // then
      assertThat(result.isError()).isTrue();
      assertThat(result.structuredContent()).isNull();
      assertThat(result.content())
          .hasSize(1)
          .first()
          .isInstanceOfSatisfying(
              TextContent.class,
              textContent ->
                  assertThat(textContent.text())
                      .isEqualTo("incidentKey: IncidentResult key must be a positive number."));
    }
  }

  @Nested
  class SearchIncidents {

    @Test
    void shouldSearchIncidentsWithCreationTimeDateRangeFilter() {
      // given
      when(incidentServices.search(any(IncidentQuery.class), any()))
          .thenReturn(SEARCH_QUERY_RESULT);

      final var creationTimeFrom = OffsetDateTime.of(2025, 5, 23, 9, 35, 12, 0, ZoneOffset.UTC);
      final var creationTimeTo = OffsetDateTime.of(2025, 12, 18, 17, 22, 33, 0, ZoneOffset.UTC);

      // when
      final CallToolResult result =
          mcpClient.callTool(
              CallToolRequest.builder()
                  .name("searchIncidents")
                  .arguments(
                      Map.of(
                          "filter",
                          Map.of(
                              "creationTime",
                              Map.of(
                                  "from", "2025-05-23T09:35:12Z", "to", "2025-12-18T17:22:33Z"))))
                  .build());

      // then
      assertThat(result.isError()).isFalse();

      verify(incidentServices).search(queryCaptor.capture(), any());
      final IncidentQuery capturedQuery = queryCaptor.getValue();

      assertThat(capturedQuery.filter().creationTimeOperations())
          .extracting(Operation::operator, Operation::value)
          .containsExactly(
              tuple(Operator.GREATER_THAN_EQUALS, creationTimeFrom),
              tuple(Operator.LOWER_THAN, creationTimeTo));
    }

    @Test
    void shouldSearchIncidentsWithFilterSortAndPaging() {
      // given
      when(incidentServices.search(any(IncidentQuery.class), any()))
          .thenReturn(SEARCH_QUERY_RESULT);

      // when
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

      // then
      assertThat(result.isError()).isFalse();
      assertThat(result.structuredContent()).isNotNull();

      @SuppressWarnings("unchecked")
      final StrictSearchQueryResult<IncidentResult> searchResult =
          (StrictSearchQueryResult<IncidentResult>)
              objectMapper.convertValue(
                  result.structuredContent(),
                  objectMapper
                      .getTypeFactory()
                      .constructParametricType(
                          StrictSearchQueryResult.class, IncidentResult.class));
      assertThat(searchResult.page().totalItems()).isEqualTo(1L);
      assertThat(searchResult.page().hasMoreTotalItems()).isFalse();
      assertThat(searchResult.page().startCursor()).isEqualTo("f");
      assertThat(searchResult.page().endCursor()).isEqualTo("v");
      assertThat(searchResult.items())
          .hasSize(1)
          .first()
          .satisfies(IncidentToolsTest.this::assertExampleIncident);

      verify(incidentServices).search(queryCaptor.capture(), any());
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

      assertTextContentFallback(result);
    }

    @Test
    void shouldFailSearchIncidentsOnException() {
      // given
      when(incidentServices.search(any(IncidentQuery.class), any()))
          .thenThrow(new ServiceException("Expected failure", Status.NOT_FOUND));

      // when
      final CallToolResult result =
          mcpClient.callTool(
              CallToolRequest.builder().name("searchIncidents").arguments(Map.of()).build());

      // then
      assertThat(result.isError()).isTrue();
      assertThat(result.structuredContent()).isNotNull();

      final var problemDetail =
          objectMapper.convertValue(result.structuredContent(), ProblemDetail.class);
      assertThat(problemDetail.getDetail()).isEqualTo("Expected failure");
      assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
      assertThat(problemDetail.getTitle()).isEqualTo("Not Found");

      assertTextContentFallback(result);
    }

    @Test
    void shouldRejectNonNumericProcessInstanceKey() {
      // when
      final CallToolResult result =
          mcpClient.callTool(
              CallToolRequest.builder()
                  .name("searchIncidents")
                  .arguments(Map.of("filter", Map.of("processInstanceKey", "abc")))
                  .build());

      // then
      assertThat(result.isError()).isTrue();
      assertThat(result.structuredContent()).isNotNull();

      final var problemDetail =
          objectMapper.convertValue(result.structuredContent(), ProblemDetail.class);
      assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
      assertThat(problemDetail.getTitle()).isEqualTo("Bad Request");
      assertThat(problemDetail.getDetail())
          .startsWith("The provided processInstanceKey 'abc' is not a valid key.");

      assertTextContentFallback(result);
    }

    @Test
    void shouldRejectInvalidCreationTime() {
      // when
      final CallToolResult result =
          mcpClient.callTool(
              CallToolRequest.builder()
                  .name("searchIncidents")
                  .arguments(Map.of("filter", Map.of("creationTime", Map.of("from", "not-a-date"))))
                  .build());

      // then
      assertThat(result.isError()).isTrue();
      assertThat(result.structuredContent()).isNotNull();

      final var problemDetail =
          objectMapper.convertValue(result.structuredContent(), ProblemDetail.class);
      assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
      assertThat(problemDetail.getTitle()).isEqualTo("Bad Request");
      assertThat(problemDetail.getDetail())
          .startsWith("The provided creationTime 'not-a-date' cannot be parsed as a date");

      assertTextContentFallback(result);
    }
  }

  @Nested
  class ResolveIncident {

    @Test
    void shouldResolveIncidentByKey() {
      // given
      when(incidentServices.resolveIncident(anyLong(), any(), any()))
          .thenReturn(CompletableFuture.completedFuture(new IncidentRecord()));

      // when
      final CallToolResult result =
          mcpClient.callTool(
              CallToolRequest.builder()
                  .name("resolveIncident")
                  .arguments(Map.of("incidentKey", 5L))
                  .build());

      // then
      assertThat(result.isError()).isFalse();
      assertThat(result.content())
          .hasSize(1)
          .first()
          .isInstanceOfSatisfying(
              TextContent.class,
              textContent ->
                  assertThat(textContent.text()).isEqualTo("IncidentResult with key 5 resolved."));

      verify(incidentServices).resolveIncident(eq(5L), isNull(), any());
    }

    @Test
    void shouldResolveJobIncidentByKey() {
      // given
      final var incidentEntity = mock(IncidentEntity.class);
      // noinspection unchecked
      when(incidentServices.resolveIncident(anyLong(), any(), any()))
          .thenReturn(
              CompletableFuture.failedFuture(
                  new ServiceException("no retries left", Status.INVALID_STATE)),
              CompletableFuture.completedFuture(new IncidentRecord()));
      when(incidentEntity.jobKey()).thenReturn(4L);
      when(incidentServices.getByKey(anyLong(), any())).thenReturn(incidentEntity);
      when(jobServices.updateJob(anyLong(), any(), any(UpdateJobChangeset.class), any()))
          .thenReturn(CompletableFuture.completedFuture(new JobRecord()));

      // when
      final CallToolResult result =
          mcpClient.callTool(
              CallToolRequest.builder()
                  .name("resolveIncident")
                  .arguments(Map.of("incidentKey", 5L))
                  .build());

      // then
      assertThat(result.isError()).isFalse();
      assertThat(result.content())
          .hasSize(1)
          .first()
          .isInstanceOfSatisfying(
              TextContent.class,
              textContent ->
                  assertThat(textContent.text()).isEqualTo("IncidentResult with key 5 resolved."));

      verify(incidentServices, times(2)).resolveIncident(eq(5L), isNull(), any());
      verify(incidentServices).getByKey(eq(5L), any());
      verify(jobServices).updateJob(eq(4L), isNull(), eq(new UpdateJobChangeset(1, null)), any());
    }

    @Test
    void shouldFailResolveJobIncidentByKey() {
      // given
      final var incidentEntity = mock(IncidentEntity.class);
      // noinspection unchecked
      when(incidentServices.resolveIncident(anyLong(), any(), any()))
          .thenReturn(
              CompletableFuture.failedFuture(
                  new ServiceException("no retries left", Status.INVALID_STATE)),
              CompletableFuture.completedFuture(new IncidentRecord()));
      when(incidentEntity.jobKey()).thenReturn(4L);
      when(incidentServices.getByKey(anyLong(), any())).thenReturn(incidentEntity);
      when(jobServices.updateJob(anyLong(), any(), any(UpdateJobChangeset.class), any()))
          .thenReturn(
              CompletableFuture.failedFuture(
                  new ServiceException("Expected failure", Status.NOT_FOUND)));

      // when
      final CallToolResult result =
          mcpClient.callTool(
              CallToolRequest.builder()
                  .name("resolveIncident")
                  .arguments(Map.of("incidentKey", 5L))
                  .build());

      // then
      assertThat(result.isError()).isTrue();
      assertThat(result.structuredContent()).isNotNull();

      final var problemDetail =
          objectMapper.convertValue(result.structuredContent(), ProblemDetail.class);
      assertThat(problemDetail.getDetail()).isEqualTo("Expected failure");
      assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
      assertThat(problemDetail.getTitle()).isEqualTo("Not Found");

      verify(incidentServices).resolveIncident(eq(5L), isNull(), any());
      verify(incidentServices).getByKey(eq(5L), any());
      verify(jobServices).updateJob(eq(4L), isNull(), eq(new UpdateJobChangeset(1, null)), any());

      assertTextContentFallback(result);
    }

    @Test
    void shouldFailResolveIncidentByKeyOnMissingKey() {
      // when
      final CallToolResult result =
          mcpClient.callTool(
              CallToolRequest.builder().name("resolveIncident").arguments(Map.of()).build());

      // then
      assertThat(result.isError()).isTrue();
      assertThat(result.structuredContent()).isNull();
      assertThat(result.content())
          .hasSize(1)
          .first()
          .isInstanceOfSatisfying(
              TextContent.class,
              textContent ->
                  assertThat(textContent.text())
                      .isEqualTo("incidentKey: IncidentResult key must not be null."));
    }

    @Test
    void shouldFailResolveIncidentByKeyOnNullKey() {
      // when
      final var arguments = new HashMap<String, Object>();
      arguments.put("incidentKey", null);
      final CallToolResult result =
          mcpClient.callTool(
              CallToolRequest.builder().name("resolveIncident").arguments(arguments).build());

      // then
      assertThat(result.isError()).isTrue();
      assertThat(result.structuredContent()).isNull();
      assertThat(result.content())
          .hasSize(1)
          .first()
          .isInstanceOfSatisfying(
              TextContent.class,
              textContent ->
                  assertThat(textContent.text())
                      .isEqualTo("incidentKey: IncidentResult key must not be null."));
    }

    @Test
    void shouldFailResolveIncidentByKeyOnInvalidKey() {
      // when
      final CallToolResult result =
          mcpClient.callTool(
              CallToolRequest.builder()
                  .name("resolveIncident")
                  .arguments(Map.of("incidentKey", -3L))
                  .build());

      // then
      assertThat(result.isError()).isTrue();
      assertThat(result.structuredContent()).isNull();
      assertThat(result.content())
          .hasSize(1)
          .first()
          .isInstanceOfSatisfying(
              TextContent.class,
              textContent ->
                  assertThat(textContent.text())
                      .isEqualTo("incidentKey: IncidentResult key must be a positive number."));
    }
  }
}
