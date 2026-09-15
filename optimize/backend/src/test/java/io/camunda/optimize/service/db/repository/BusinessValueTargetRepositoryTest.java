/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.HitsMetadata;
import co.elastic.clients.util.ObjectBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.optimize.query.businessvalue.BusinessValueTargetDto;
import io.camunda.optimize.service.db.DatabaseConstants;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import io.camunda.optimize.service.db.repository.es.BusinessValueTargetRepositoryES;
import io.camunda.optimize.service.db.repository.os.BusinessValueTargetRepositoryOS;
import io.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.importing.ZeebeConstants;
import java.util.List;
import java.util.function.Function;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class BusinessValueTargetRepositoryTest {

  /**
   * An empty authorized-tenant list means the caller may see nothing, and it must not be allowed to
   * fall through to the unfiltered read that {@code null} selects. Verified by asserting the client
   * is never touched rather than that the result is empty — a query that ran and happened to match
   * nothing would satisfy the weaker assertion while still having read every tenant's targets.
   */
  @Test
  void shouldReadNothingWithoutQueryingWhenTheCallerSeesNoTenants() {
    // given
    final OptimizeElasticsearchClient esClient = mock(OptimizeElasticsearchClient.class);
    final BusinessValueTargetRepositoryES repository =
        new BusinessValueTargetRepositoryES(esClient, new ObjectMapper());

    // when
    final List<BusinessValueTargetDto> targets = repository.readByTenants(List.of());

    // then
    assertThat(targets).isEmpty();
    verifyNoInteractions(esClient);
  }

  /**
   * The cap must fail loudly rather than truncate. readByTenants backs the overview read, and a
   * silently short result would drop a tenant's targets from the response with no signal — the
   * caller would see fewer targeted processes than they have and have no way to tell.
   *
   * <p>Covered per engine because the two implementations build and bound the query separately, so
   * one could regress while the other stayed correct.
   */
  @Test
  void shouldFailRatherThanTruncateWhenElasticsearchReturnsTheFetchLimit() throws Exception {
    // given a backend returning exactly the fetch cap
    final OptimizeElasticsearchClient esClient = mock(OptimizeElasticsearchClient.class);
    // The request builder resolves the index alias through the client's own indexNameService field
    // rather than a method, so a bare mock leaves it null and the builder trips before the cap
    // check is ever reached.
    final OptimizeIndexNameService nameService = mock(OptimizeIndexNameService.class);
    when(nameService.getOptimizeIndexAliasForIndex(anyString())).thenReturn("optimize-target");
    ReflectionTestUtils.setField(esClient, "indexNameService", nameService);
    final SearchResponse<BusinessValueTargetDto> response = mock(SearchResponse.class);
    final HitsMetadata<BusinessValueTargetDto> hits = mock(HitsMetadata.class);
    when(hits.hits()).thenReturn(cappedHits());
    when(response.hits()).thenReturn(hits);
    when(esClient.search(any(SearchRequest.class), eq(BusinessValueTargetDto.class)))
        .thenReturn(response);

    // when / then
    assertThatThrownBy(
            () ->
                new BusinessValueTargetRepositoryES(esClient, new ObjectMapper())
                    .readByTenants(List.of("tenant-a")))
        .isInstanceOf(OptimizeRuntimeException.class)
        .hasMessageContaining("LIST_FETCH_LIMIT");
  }

  @Test
  void shouldFailRatherThanTruncateWhenOpenSearchReturnsTheFetchLimit() {
    // given a backend returning exactly the fetch cap
    final OptimizeOpenSearchClient osClient = mock(OptimizeOpenSearchClient.class);
    when(osClient.searchValues(any(), eq(BusinessValueTargetDto.class)))
        .thenReturn(
            IntStream.range(0, DatabaseConstants.LIST_FETCH_LIMIT)
                .mapToObj(i -> targetFor("process-" + i))
                .toList());

    // when / then
    assertThatThrownBy(
            () ->
                new BusinessValueTargetRepositoryOS(osClient, mock(OptimizeIndexNameService.class))
                    .readByTenants(List.of("tenant-a")))
        .isInstanceOf(OptimizeRuntimeException.class)
        .hasMessageContaining("LIST_FETCH_LIMIT");
  }

  private static List<Hit<BusinessValueTargetDto>> cappedHits() {
    return IntStream.range(0, DatabaseConstants.LIST_FETCH_LIMIT)
        .mapToObj(
            i ->
                Hit.of(
                    (Function<
                            Hit.Builder<BusinessValueTargetDto>,
                            ObjectBuilder<Hit<BusinessValueTargetDto>>>)
                        b -> b.index("i").id(String.valueOf(i)).source(targetFor("process-" + i))))
        .toList();
  }

  private static BusinessValueTargetDto targetFor(final String processDefinitionKey) {
    return new BusinessValueTargetDto(
        processDefinitionKey, "tenant-a", 1_000L, null, null, null, "someone");
  }

  @Test
  void shouldCombineTenantAndProcessKeyIntoDocumentId() {
    assertThat(
            BusinessValueTargetRepository.documentId(
                ZeebeConstants.ZEEBE_DEFAULT_TENANT_ID, "invoice-automation"))
        .isEqualTo(ZeebeConstants.ZEEBE_DEFAULT_TENANT_ID + "::invoice-automation");
  }

  @Test
  void shouldReturnDifferentDocumentIdsForDifferentTenants() {
    final String a = BusinessValueTargetRepository.documentId("tenant-a", "invoice-automation");
    final String b = BusinessValueTargetRepository.documentId("tenant-b", "invoice-automation");
    assertThat(a).isNotEqualTo(b);
  }

  @Test
  void shouldRejectNullTenant() {
    assertThatThrownBy(() -> BusinessValueTargetRepository.documentId(null, "any-key"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("tenantId");
  }

  @Test
  void shouldRejectNullProcessKey() {
    assertThatThrownBy(
            () ->
                BusinessValueTargetRepository.documentId(
                    ZeebeConstants.ZEEBE_DEFAULT_TENANT_ID, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("processDefinitionKey");
  }

  @Test
  void shouldRejectBlankTenant() {
    assertThatThrownBy(() -> BusinessValueTargetRepository.documentId("   ", "any-key"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("tenantId");
  }

  @Test
  void shouldRejectBlankProcessKey() {
    assertThatThrownBy(
            () ->
                BusinessValueTargetRepository.documentId(
                    ZeebeConstants.ZEEBE_DEFAULT_TENANT_ID, "   "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("processDefinitionKey");
  }
}
