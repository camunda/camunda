/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.mcp.tools.incident;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.search.entities.IncidentEntity;
import io.camunda.search.query.IncidentQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.service.IncidentServices;
import io.camunda.zeebe.gateway.mcp.tools.incident.IncidentSearchRequest.IncidentFilter;
import io.camunda.zeebe.gateway.mcp.tools.incident.IncidentSearchRequest.IncidentPage;
import io.camunda.zeebe.gateway.mcp.tools.incident.IncidentSearchRequest.IncidentSort;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClusterIncidentsToolTest {

  @Mock private IncidentServices incidentServices;
  @Mock private CamundaAuthenticationProvider authenticationProvider;

  private ClusterIncidentsTool clusterIncidentsTool;

  @BeforeEach
  void setUp() {
    clusterIncidentsTool = new ClusterIncidentsTool(incidentServices, authenticationProvider);

    // Mock authentication
    final var authentication = mock(CamundaAuthentication.class);
    when(authenticationProvider.getCamundaAuthentication()).thenReturn(authentication);
  }

  @Test
  void shouldExecuteSuccessfulIncidentSearch() {
    // given
    final var incidentEntity = createMockIncidentEntity();
    final var searchResult = mock(SearchQueryResult.class);
    when(searchResult.items()).thenReturn(List.of(incidentEntity));
    when(searchResult.total()).thenReturn(1L);

    final var authenticatedServices = mock(IncidentServices.class);
    when(incidentServices.withAuthentication(any(CamundaAuthentication.class)))
        .thenReturn(authenticatedServices);
    when(authenticatedServices.search(any(IncidentQuery.class))).thenReturn(searchResult);

    final var request = createIncidentSearchRequest();

    // when
    final IncidentSearchResponse response = clusterIncidentsTool.searchIncidents(request);

    // then
    assertThat(response).isNotNull();
    assertThat(response.incidents()).hasSize(1);
    assertThat(response.total()).isEqualTo(1L);
    assertThat(response.error()).isNull();
    verify(authenticatedServices).search(any(IncidentQuery.class));
  }

  @Test
  void shouldHandleEmptySearchResults() {
    // given
    final var searchResult = mock(SearchQueryResult.class);
    when(searchResult.items()).thenReturn(List.of());
    when(searchResult.total()).thenReturn(0L);

    final var authenticatedServices = mock(IncidentServices.class);
    when(incidentServices.withAuthentication(any(CamundaAuthentication.class)))
        .thenReturn(authenticatedServices);
    when(authenticatedServices.search(any(IncidentQuery.class))).thenReturn(searchResult);

    final var request = createIncidentSearchRequest();

    // when
    final IncidentSearchResponse response = clusterIncidentsTool.searchIncidents(request);

    // then
    assertThat(response).isNotNull();
    assertThat(response.incidents()).isEmpty();
    assertThat(response.total()).isEqualTo(0L);
    assertThat(response.error()).isNull();
  }

  @Test
  void shouldHandleServiceException() {
    // given
    final var authenticatedServices = mock(IncidentServices.class);
    when(incidentServices.withAuthentication(any(CamundaAuthentication.class)))
        .thenReturn(authenticatedServices);
    when(authenticatedServices.search(any(IncidentQuery.class)))
        .thenThrow(new RuntimeException("Service unavailable"));

    final var request = createIncidentSearchRequest();

    // when
    final IncidentSearchResponse response = clusterIncidentsTool.searchIncidents(request);

    // then
    assertThat(response).isNotNull();
    assertThat(response.incidents()).isNull();
    assertThat(response.total()).isNull();
    assertThat(response.error()).isEqualTo("Error searching incidents: Service unavailable");
  }

  @Test
  void shouldHandleNullRequest() {
    // given
    final var searchResult = mock(SearchQueryResult.class);
    when(searchResult.items()).thenReturn(List.of());
    when(searchResult.total()).thenReturn(0L);

    final var authenticatedServices = mock(IncidentServices.class);
    when(incidentServices.withAuthentication(any(CamundaAuthentication.class)))
        .thenReturn(authenticatedServices);
    when(authenticatedServices.search(any(IncidentQuery.class))).thenReturn(searchResult);

    // when
    final IncidentSearchResponse response = clusterIncidentsTool.searchIncidents(null);

    // then
    assertThat(response).isNotNull();
    assertThat(response.incidents()).isEmpty();
    assertThat(response.total()).isEqualTo(0L);
    assertThat(response.error()).isNull();
    verify(authenticatedServices).search(any(IncidentQuery.class));
  }

  @Test
  void shouldProcessComplexFilterRequest() {
    // given
    final var incidentEntity = createMockIncidentEntity();
    final var searchResult = mock(SearchQueryResult.class);
    when(searchResult.items()).thenReturn(List.of(incidentEntity));
    when(searchResult.total()).thenReturn(1L);

    final var authenticatedServices = mock(IncidentServices.class);
    when(incidentServices.withAuthentication(any(CamundaAuthentication.class)))
        .thenReturn(authenticatedServices);
    when(authenticatedServices.search(any(IncidentQuery.class))).thenReturn(searchResult);

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

    final var sort = List.of(new IncidentSort("creationTime", "desc"));
    final var page = new IncidentPage(50, List.of("search", "after"));
    final var request = new IncidentSearchRequest(filter, sort, page);

    // when
    final IncidentSearchResponse response = clusterIncidentsTool.searchIncidents(request);

    // then
    assertThat(response).isNotNull();
    assertThat(response.incidents()).hasSize(1);
    assertThat(response.total()).isEqualTo(1L);
    assertThat(response.error()).isNull();
    verify(authenticatedServices).search(any(IncidentQuery.class));
  }

  private IncidentEntity createMockIncidentEntity() {
    final var entity = mock(IncidentEntity.class);
    when(entity.incidentKey()).thenReturn(123L);
    when(entity.processInstanceKey()).thenReturn(456L);
    when(entity.processDefinitionKey()).thenReturn(789L);
    when(entity.errorType()).thenReturn(IncidentEntity.ErrorType.JOB_NO_RETRIES);
    when(entity.state()).thenReturn(IncidentEntity.IncidentState.ACTIVE);
    when(entity.errorMessage()).thenReturn("Test error message");
    when(entity.flowNodeId()).thenReturn("task-1");
    when(entity.flowNodeInstanceKey()).thenReturn(111L);
    when(entity.creationTime()).thenReturn(OffsetDateTime.parse("2024-01-01T10:00:00Z"));
    when(entity.jobKey()).thenReturn(222L);
    when(entity.tenantId()).thenReturn("tenant-1");
    return entity;
  }

  @Test
  void shouldVerifyCorrectQueryBuilding() {
    // given
    final var searchResult = mock(SearchQueryResult.class);
    when(searchResult.items()).thenReturn(List.of());
    when(searchResult.total()).thenReturn(0L);

    final var authenticatedServices = mock(IncidentServices.class);
    when(incidentServices.withAuthentication(any(CamundaAuthentication.class)))
        .thenReturn(authenticatedServices);
    when(authenticatedServices.search(any(IncidentQuery.class))).thenReturn(searchResult);

    final var filter =
        new IncidentFilter(
            List.of(123L), // processInstanceKeys
            List.of(456L), // processDefinitionKeys
            List.of("test-process"), // processDefinitionIds
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null);

    final var request = new IncidentSearchRequest(filter, null, null);

    // when
    clusterIncidentsTool.searchIncidents(request);

    // then
    verify(authenticatedServices).search(any(IncidentQuery.class));
    verify(incidentServices).withAuthentication(any(CamundaAuthentication.class));
  }

  @Test
  void shouldHandleEmptyFilterLists() {
    // given
    final var searchResult = mock(SearchQueryResult.class);
    when(searchResult.items()).thenReturn(List.of());
    when(searchResult.total()).thenReturn(0L);

    final var authenticatedServices = mock(IncidentServices.class);
    when(incidentServices.withAuthentication(any(CamundaAuthentication.class)))
        .thenReturn(authenticatedServices);
    when(authenticatedServices.search(any(IncidentQuery.class))).thenReturn(searchResult);

    final var filter =
        new IncidentFilter(
            List.of(), // empty processInstanceKeys
            List.of(), // empty processDefinitionKeys
            List.of(), // empty processDefinitionIds
            List.of(), // empty incidentKeys
            List.of(), // empty states
            List.of(), // empty errorTypes
            List.of(), // empty errorMessages
            List.of(), // empty flowNodeIds
            List.of(), // empty flowNodeInstanceKeys
            null, null, List.of(), // empty jobKeys
            List.of()); // empty tenantIds

    final var request = new IncidentSearchRequest(filter, null, null);

    // when
    final IncidentSearchResponse response = clusterIncidentsTool.searchIncidents(request);

    // then
    assertThat(response).isNotNull();
    assertThat(response.incidents()).isEmpty();
    assertThat(response.error()).isNull();
  }

  @Test
  void shouldHandlePaginationLimits() {
    // given
    final var searchResult = mock(SearchQueryResult.class);
    when(searchResult.items()).thenReturn(List.of());
    when(searchResult.total()).thenReturn(0L);

    final var authenticatedServices = mock(IncidentServices.class);
    when(incidentServices.withAuthentication(any(CamundaAuthentication.class)))
        .thenReturn(authenticatedServices);
    when(authenticatedServices.search(any(IncidentQuery.class))).thenReturn(searchResult);

    final var page = new IncidentPage(150, null); // size over limit
    final var request = new IncidentSearchRequest(null, null, page);

    // when
    final IncidentSearchResponse response = clusterIncidentsTool.searchIncidents(request);

    // then
    assertThat(response).isNotNull();
    assertThat(response.error()).isNull();
    verify(authenticatedServices).search(any(IncidentQuery.class));
  }

  @Test
  void shouldHandleAllSortFields() {
    // given
    final var searchResult = mock(SearchQueryResult.class);
    when(searchResult.items()).thenReturn(List.of());
    when(searchResult.total()).thenReturn(0L);

    final var authenticatedServices = mock(IncidentServices.class);
    when(incidentServices.withAuthentication(any(CamundaAuthentication.class)))
        .thenReturn(authenticatedServices);
    when(authenticatedServices.search(any(IncidentQuery.class))).thenReturn(searchResult);

    final var sorts =
        List.of(
            new IncidentSort("key", "asc"),
            new IncidentSort("creationTime", "desc"),
            new IncidentSort("state", "asc"),
            new IncidentSort("errorType", "desc"),
            new IncidentSort("processInstanceKey", "asc"),
            new IncidentSort("processDefinitionKey", "desc"),
            new IncidentSort("unknownField", "asc") // should default to key
            );
    final var request = new IncidentSearchRequest(null, sorts, null);

    // when
    final IncidentSearchResponse response = clusterIncidentsTool.searchIncidents(request);

    // then
    assertThat(response).isNotNull();
    assertThat(response.error()).isNull();
    verify(authenticatedServices).search(any(IncidentQuery.class));
  }

  @Test
  void shouldVerifyIncidentMappingFromEntity() {
    // given
    final var incidentEntity = createMockIncidentEntity();
    final var searchResult = mock(SearchQueryResult.class);
    when(searchResult.items()).thenReturn(List.of(incidentEntity));
    when(searchResult.total()).thenReturn(1L);

    final var authenticatedServices = mock(IncidentServices.class);
    when(incidentServices.withAuthentication(any(CamundaAuthentication.class)))
        .thenReturn(authenticatedServices);
    when(authenticatedServices.search(any(IncidentQuery.class))).thenReturn(searchResult);

    final var request = createIncidentSearchRequest();

    // when
    final IncidentSearchResponse response = clusterIncidentsTool.searchIncidents(request);

    // then
    assertThat(response.incidents()).hasSize(1);
    final var incident = response.incidents().get(0);
    assertThat(incident.key()).isEqualTo(123L);
    assertThat(incident.processInstanceKey()).isEqualTo(456L);
    assertThat(incident.processDefinitionKey()).isEqualTo(789L);
    assertThat(incident.errorType()).isEqualTo("JOB_NO_RETRIES");
    assertThat(incident.state()).isEqualTo("ACTIVE");
    assertThat(incident.errorMessage()).isEqualTo("Test error message");
    assertThat(incident.flowNodeId()).isEqualTo("task-1");
    assertThat(incident.flowNodeInstanceKey()).isEqualTo(111L);
    assertThat(incident.creationTime()).isEqualTo("2024-01-01T10:00Z");
    assertThat(incident.jobKey()).isEqualTo(222L);
    assertThat(incident.tenantId()).isEqualTo("tenant-1");
  }

  @Test
  void shouldHandleNullEntityFields() {
    // given
    final var incidentEntity = mock(IncidentEntity.class);
    when(incidentEntity.incidentKey()).thenReturn(123L);
    when(incidentEntity.processInstanceKey()).thenReturn(456L);
    when(incidentEntity.processDefinitionKey()).thenReturn(789L);
    when(incidentEntity.errorType()).thenReturn(null);
    when(incidentEntity.state()).thenReturn(null);
    when(incidentEntity.errorMessage()).thenReturn(null);
    when(incidentEntity.flowNodeId()).thenReturn(null);
    when(incidentEntity.flowNodeInstanceKey()).thenReturn(null);
    when(incidentEntity.creationTime()).thenReturn(null);
    when(incidentEntity.jobKey()).thenReturn(null);
    when(incidentEntity.tenantId()).thenReturn(null);

    final var searchResult = mock(SearchQueryResult.class);
    when(searchResult.items()).thenReturn(List.of(incidentEntity));
    when(searchResult.total()).thenReturn(1L);

    final var authenticatedServices = mock(IncidentServices.class);
    when(incidentServices.withAuthentication(any(CamundaAuthentication.class)))
        .thenReturn(authenticatedServices);
    when(authenticatedServices.search(any(IncidentQuery.class))).thenReturn(searchResult);

    final var request = createIncidentSearchRequest();

    // when
    final IncidentSearchResponse response = clusterIncidentsTool.searchIncidents(request);

    // then
    assertThat(response.incidents()).hasSize(1);
    final var incident = response.incidents().get(0);
    assertThat(incident.key()).isEqualTo(123L);
    assertThat(incident.processInstanceKey()).isEqualTo(456L);
    assertThat(incident.processDefinitionKey()).isEqualTo(789L);
    assertThat(incident.errorType()).isNull();
    assertThat(incident.state()).isNull();
    assertThat(incident.errorMessage()).isNull();
    assertThat(incident.flowNodeId()).isNull();
    assertThat(incident.flowNodeInstanceKey()).isNull();
    assertThat(incident.creationTime()).isNull();
    assertThat(incident.jobKey()).isNull();
    assertThat(incident.tenantId()).isNull();
  }

  private IncidentSearchRequest createIncidentSearchRequest() {
    final var filter =
        new IncidentFilter(
            List.of(1L), // processInstanceKeys
            null, // processDefinitionKeys
            null, // processDefinitionIds
            null, // incidentKeys
            List.of("ACTIVE"), // states
            null, // errorTypes
            null, // errorMessages
            null, // flowNodeIds
            null, // flowNodeInstanceKeys
            null, // creationTimeFrom
            null, // creationTimeTo
            null, // jobKeys
            null); // tenantIds

    return new IncidentSearchRequest(filter, null, null);
  }
}
