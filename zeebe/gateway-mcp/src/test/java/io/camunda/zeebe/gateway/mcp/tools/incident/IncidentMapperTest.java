/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.mcp.tools.incident;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.search.entities.IncidentEntity;
import io.camunda.search.query.IncidentQuery;
import io.camunda.zeebe.gateway.mcp.tools.incident.IncidentSearchRequest.IncidentFilter;
import io.camunda.zeebe.gateway.mcp.tools.incident.IncidentSearchRequest.IncidentPage;
import io.camunda.zeebe.gateway.mcp.tools.incident.IncidentSearchRequest.IncidentSort;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class IncidentMapperTest {

  @Test
  void shouldReturnEmptyQueryWhenRequestIsNull() {
    // when
    final IncidentQuery result = IncidentMapper.buildIncidentQuery((IncidentSearchRequest) null);

    // then
    assertThat(result).isNotNull();
    assertThat(result.filter()).isNotNull();
    assertThat(result.sort()).isNotNull();
    assertThat(result.page()).isNotNull();
  }

  @Test
  void shouldBuildQueryWithFilter() {
    // given
    final var filter =
        new IncidentFilter(
            List.of(1L, 2L), // processInstanceKeys
            List.of(100L), // processDefinitionKeys
            List.of("process-id"), // processDefinitionIds
            List.of(500L), // incidentKeys
            List.of("ACTIVE"), // states
            List.of("JOB_NO_RETRIES"), // errorTypes
            List.of("error message"), // errorMessages
            List.of("task-1"), // flowNodeIds
            List.of(300L), // flowNodeInstanceKeys
            OffsetDateTime.now().minusDays(1), // creationTimeFrom
            OffsetDateTime.now(), // creationTimeTo
            List.of(600L), // jobKeys
            List.of("tenant-1")); // tenantIds
    final var request = new IncidentSearchRequest(filter, null, null);

    // when
    final IncidentQuery result = IncidentMapper.buildIncidentQuery(request);

    // then
    assertThat(result).isNotNull();
    assertThat(result.filter()).isNotNull();
  }

  @Test
  void shouldBuildQueryWithSort() {
    // given
    final var sorts =
        List.of(new IncidentSort("key", "desc"), new IncidentSort("creationTime", "asc"));
    final var request = new IncidentSearchRequest(null, sorts, null);

    // when
    final IncidentQuery result = IncidentMapper.buildIncidentQuery(request);

    // then
    assertThat(result).isNotNull();
    assertThat(result.sort()).isNotNull();
    assertThat(result.sort().getFieldSortings()).hasSize(2);
  }

  @Test
  void shouldBuildQueryWithPage() {
    // given
    final var page = new IncidentPage(50, List.of("search", "after", "values"));
    final var request = new IncidentSearchRequest(null, null, page);

    // when
    final IncidentQuery result = IncidentMapper.buildIncidentQuery(request);

    // then
    assertThat(result).isNotNull();
    assertThat(result.page()).isNotNull();
    assertThat(result.page().size()).isEqualTo(50);
  }

  @Test
  void shouldCapPageSizeAt100() {
    // given
    final var page = new IncidentPage(200, null);
    final var request = new IncidentSearchRequest(null, null, page);

    // when
    final IncidentQuery result = IncidentMapper.buildIncidentQuery(request);

    // then
    assertThat(result).isNotNull();
    assertThat(result.page()).isNotNull();
    assertThat(result.page().size()).isEqualTo(100);
  }

  @Test
  void shouldConvertIncidentEntityToIncident() {
    // given
    final var entity = mock(IncidentEntity.class);
    when(entity.incidentKey()).thenReturn(123L);
    when(entity.processInstanceKey()).thenReturn(456L);
    when(entity.processDefinitionKey()).thenReturn(789L);
    when(entity.errorType())
        .thenReturn(io.camunda.search.entities.IncidentEntity.ErrorType.JOB_NO_RETRIES);
    when(entity.state()).thenReturn(io.camunda.search.entities.IncidentEntity.IncidentState.ACTIVE);
    when(entity.errorMessage()).thenReturn("Test error message");
    when(entity.flowNodeId()).thenReturn("task-1");
    when(entity.flowNodeInstanceKey()).thenReturn(111L);
    when(entity.creationTime()).thenReturn(OffsetDateTime.parse("2024-01-01T10:00:00Z"));
    when(entity.jobKey()).thenReturn(222L);
    when(entity.tenantId()).thenReturn("tenant-1");

    // when
    final Incident result = IncidentMapper.toIncident(entity);

    // then
    assertThat(result.key()).isEqualTo(123L);
    assertThat(result.processInstanceKey()).isEqualTo(456L);
    assertThat(result.processDefinitionKey()).isEqualTo(789L);
    assertThat(result.type()).isEqualTo("JOB_NO_RETRIES");
    assertThat(result.state()).isEqualTo("ACTIVE");
    assertThat(result.errorMessage()).isEqualTo("Test error message");
    assertThat(result.errorType()).isEqualTo("JOB_NO_RETRIES");
    assertThat(result.flowNodeId()).isEqualTo("task-1");
    assertThat(result.flowNodeInstanceKey()).isEqualTo(111L);
    assertThat(result.creationTime()).isEqualTo("2024-01-01T10:00Z");
    assertThat(result.jobKey()).isEqualTo(222L);
    assertThat(result.tenantId()).isEqualTo("tenant-1");
  }

  @Test
  void shouldHandleNullValuesInIncidentEntity() {
    // given
    final var entity = mock(IncidentEntity.class);
    when(entity.incidentKey()).thenReturn(123L);
    when(entity.processInstanceKey()).thenReturn(456L);
    when(entity.processDefinitionKey()).thenReturn(789L);
    when(entity.errorType()).thenReturn(null);
    when(entity.state()).thenReturn(null);
    when(entity.errorMessage()).thenReturn(null);
    when(entity.flowNodeId()).thenReturn(null);
    when(entity.flowNodeInstanceKey()).thenReturn(null);
    when(entity.creationTime()).thenReturn(null);
    when(entity.jobKey()).thenReturn(null);
    when(entity.tenantId()).thenReturn(null);

    // when
    final Incident result = IncidentMapper.toIncident(entity);

    // then
    assertThat(result.key()).isEqualTo(123L);
    assertThat(result.processInstanceKey()).isEqualTo(456L);
    assertThat(result.processDefinitionKey()).isEqualTo(789L);
    assertThat(result.type()).isNull();
    assertThat(result.state()).isNull();
    assertThat(result.errorMessage()).isNull();
    assertThat(result.errorType()).isNull();
    assertThat(result.flowNodeId()).isNull();
    assertThat(result.flowNodeInstanceKey()).isNull();
    assertThat(result.creationTime()).isNull();
    assertThat(result.jobKey()).isNull();
    assertThat(result.tenantId()).isNull();
  }
}
