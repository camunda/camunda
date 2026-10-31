/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.reader;

import static io.camunda.search.aggregation.ProcessDefinitionInstanceStatisticsAggregation.AGGREGATION_TERMS_SIZE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.search.aggregation.result.ProcessDefinitionInstanceStatisticsAggregationResult;
import io.camunda.search.clients.SearchClientBasedQueryExecutor;
import io.camunda.search.entities.ProcessDefinitionEntity;
import io.camunda.search.entities.ProcessDefinitionEntity.ProcessDefinitionState;
import io.camunda.search.entities.ProcessDefinitionInstanceStatisticsEntity;
import io.camunda.search.query.ProcessDefinitionInstanceStatisticsQuery;
import io.camunda.search.query.ProcessDefinitionQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.webapps.schema.entities.ProcessEntity;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

// Reproduces https://github.com/camunda/camunda/issues/51617: the primary aggregation computes
// hasMultipleVersions from active process instances, which is blind to deployed versions that have
// no instances. These tests cover the reader-level correction against the process-definition index,
// without needing a live ES/OS container.
class ProcessDefinitionInstanceStatisticsDocumentReaderTest {

  private static final String PROCESS_DEFINITION_ID = "order-process-id";
  private static final String TENANT_ID = "tenant1";
  private static final String FILLER_PROCESS_DEFINITION_ID = "filler-process-id";
  private static final String FILLER_TENANT_ID = "filler-tenant";

  @Test
  void shouldOverrideHasMultipleVersionsWhenOnlyLatestVersionHasInstances() {
    final var executor = mock(SearchClientBasedQueryExecutor.class);
    final var indexDescriptor = mock(IndexDescriptor.class);
    final var reader =
        new ProcessDefinitionInstanceStatisticsDocumentReader(executor, indexDescriptor);

    // given: the primary (instance-based) aggregation only ever saw version 2's instance, so it
    // reports hasMultipleVersions=false, even though version 1 is also deployed.
    final var rawItem =
        new ProcessDefinitionInstanceStatisticsEntity(
            PROCESS_DEFINITION_ID, TENANT_ID, "Order Process", false, 1L, 0L);
    when(executor.aggregateWithQueryResult(
            any(ProcessDefinitionInstanceStatisticsQuery.class),
            eq(ProcessDefinitionInstanceStatisticsAggregationResult.class),
            any(ResourceAccessChecks.class),
            any()))
        .thenReturn(SearchQueryResult.of(rawItem));

    final var v1 =
        processDefinition(1L, PROCESS_DEFINITION_ID, 1, TENANT_ID, ProcessDefinitionState.ACTIVE);
    final var v2 =
        processDefinition(2L, PROCESS_DEFINITION_ID, 2, TENANT_ID, ProcessDefinitionState.ACTIVE);
    when(executor.search(
            any(ProcessDefinitionQuery.class),
            eq(ProcessEntity.class),
            any(ResourceAccessChecks.class)))
        .thenReturn(SearchQueryResult.of(v1, v2));

    // when
    final var result =
        reader.aggregate(
            ProcessDefinitionInstanceStatisticsQuery.of(b -> b), ResourceAccessChecks.disabled());

    // then
    assertThat(result.items()).hasSize(1);
    assertThat(result.items().getFirst().hasMultipleVersions()).isTrue();
  }

  @Test
  void shouldCorrectHasMultipleVersionsToFalseWhenAVersionWasDeleted() {
    final var executor = mock(SearchClientBasedQueryExecutor.class);
    final var indexDescriptor = mock(IndexDescriptor.class);
    final var reader =
        new ProcessDefinitionInstanceStatisticsDocumentReader(executor, indexDescriptor);

    // given: the primary aggregation still reports true from before version 1 was deleted -- the
    // correction must be able to move the flag down as well as up.
    final var rawItem =
        new ProcessDefinitionInstanceStatisticsEntity(
            PROCESS_DEFINITION_ID, TENANT_ID, "Order Process", true, 1L, 0L);
    when(executor.aggregateWithQueryResult(
            any(ProcessDefinitionInstanceStatisticsQuery.class),
            eq(ProcessDefinitionInstanceStatisticsAggregationResult.class),
            any(ResourceAccessChecks.class),
            any()))
        .thenReturn(SearchQueryResult.of(rawItem));

    // given: version 1 was deleted, so only version 2 remains deployed
    final var v1Deleted =
        processDefinition(1L, PROCESS_DEFINITION_ID, 1, TENANT_ID, ProcessDefinitionState.DELETED);
    final var v2 =
        processDefinition(2L, PROCESS_DEFINITION_ID, 2, TENANT_ID, ProcessDefinitionState.ACTIVE);
    when(executor.search(
            any(ProcessDefinitionQuery.class),
            eq(ProcessEntity.class),
            any(ResourceAccessChecks.class)))
        .thenReturn(SearchQueryResult.of(v1Deleted, v2));

    // when
    final var result =
        reader.aggregate(
            ProcessDefinitionInstanceStatisticsQuery.of(b -> b), ResourceAccessChecks.disabled());

    // then
    assertThat(result.items().getFirst().hasMultipleVersions()).isFalse();
  }

  @Test
  void shouldKeepPrimaryValueWhenProcessDefinitionLookupFindsNothing() {
    final var executor = mock(SearchClientBasedQueryExecutor.class);
    final var indexDescriptor = mock(IndexDescriptor.class);
    final var reader =
        new ProcessDefinitionInstanceStatisticsDocumentReader(executor, indexDescriptor);

    final var rawItem =
        new ProcessDefinitionInstanceStatisticsEntity(
            PROCESS_DEFINITION_ID, TENANT_ID, "Order Process", true, 1L, 0L);
    when(executor.aggregateWithQueryResult(
            any(ProcessDefinitionInstanceStatisticsQuery.class),
            eq(ProcessDefinitionInstanceStatisticsAggregationResult.class),
            any(ResourceAccessChecks.class),
            any()))
        .thenReturn(SearchQueryResult.of(rawItem));

    // given: the defensive process-definition lookup comes back empty (e.g. an access-check
    // mismatch) rather than genuinely finding a single version
    when(executor.search(
            any(ProcessDefinitionQuery.class),
            eq(ProcessEntity.class),
            any(ResourceAccessChecks.class)))
        .thenReturn(SearchQueryResult.empty());

    // when
    final var result =
        reader.aggregate(
            ProcessDefinitionInstanceStatisticsQuery.of(b -> b), ResourceAccessChecks.disabled());

    // then: the primary aggregation's value is kept rather than forced to false
    assertThat(result.items().getFirst().hasMultipleVersions()).isTrue();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void shouldCountVersionsBeyondFirstLookupPage(final boolean originalHasMultipleVersions) {
    final var executor = mock(SearchClientBasedQueryExecutor.class);
    final var indexDescriptor = mock(IndexDescriptor.class);
    final var reader =
        new ProcessDefinitionInstanceStatisticsDocumentReader(executor, indexDescriptor);

    // given: the primary aggregation reports one pair whose true multiplicity depends on a second
    // deployed version that only shows up on a later lookup page, and one unrelated filler pair
    // whose correct answer (single version) must be unaffected by the pagination.
    final var targetItem =
        new ProcessDefinitionInstanceStatisticsEntity(
            PROCESS_DEFINITION_ID, TENANT_ID, "Order Process", originalHasMultipleVersions, 3L, 2L);
    final var fillerItem =
        new ProcessDefinitionInstanceStatisticsEntity(
            FILLER_PROCESS_DEFINITION_ID, FILLER_TENANT_ID, "Filler Process", false, 5L, 0L);
    when(executor.aggregateWithQueryResult(
            any(ProcessDefinitionInstanceStatisticsQuery.class),
            eq(ProcessDefinitionInstanceStatisticsAggregationResult.class),
            any(ResourceAccessChecks.class),
            any()))
        .thenReturn(
            new SearchQueryResult<>(
                2, true, List.of(targetItem, fillerItem), "outer-start", "outer-end"));

    // given: a full first lookup page (9,999 filler definitions plus the target's V1) and a short
    // second page holding only the target's V2 -- reproducing the truncation from #51617.
    final var firstPage =
        fullPageOf(
            List.of(
                processDefinition(
                    1L, PROCESS_DEFINITION_ID, 1, TENANT_ID, ProcessDefinitionState.ACTIVE)));
    final var secondPage =
        List.of(
            processDefinition(
                2L, PROCESS_DEFINITION_ID, 2, TENANT_ID, ProcessDefinitionState.ACTIVE));

    doAnswer(
            invocation -> {
              final ProcessDefinitionQuery query = invocation.getArgument(0);
              if (query.page().after() == null) {
                return new SearchQueryResult<>(
                    firstPage.size(), false, firstPage, null, "lookup-page-1-end");
              }
              assertThat(query.page().after()).isEqualTo("lookup-page-1-end");
              return new SearchQueryResult<>(
                  secondPage.size(), false, secondPage, "lookup-page-1-end", null);
            })
        .when(executor)
        .search(
            any(ProcessDefinitionQuery.class),
            eq(ProcessEntity.class),
            any(ResourceAccessChecks.class));

    // when
    final var result =
        reader.aggregate(
            ProcessDefinitionInstanceStatisticsQuery.of(b -> b), ResourceAccessChecks.disabled());

    // then: outer pagination metadata from the primary aggregation is preserved untouched
    assertThat(result.total()).isEqualTo(2);
    assertThat(result.hasMoreTotalItems()).isTrue();
    assertThat(result.startCursor()).isEqualTo("outer-start");
    assertThat(result.endCursor()).isEqualTo("outer-end");

    // then: the target's flag is now true regardless of what the primary aggregation originally
    // reported, and its other fields are untouched
    final var target = itemFor(result, PROCESS_DEFINITION_ID, TENANT_ID);
    assertThat(target.hasMultipleVersions()).isTrue();
    assertThat(target.latestProcessDefinitionName()).isEqualTo("Order Process");
    assertThat(target.activeInstancesWithoutIncidentCount()).isEqualTo(3L);
    assertThat(target.activeInstancesWithIncidentCount()).isEqualTo(2L);

    // then: the unrelated filler pair is unaffected by processing the large lookup page
    assertThat(
            itemFor(result, FILLER_PROCESS_DEFINITION_ID, FILLER_TENANT_ID).hasMultipleVersions())
        .isFalse();

    // then: continuation reused the same filter and an explicit stable sort across both pages
    final var queryCaptor = ArgumentCaptor.forClass(ProcessDefinitionQuery.class);
    verify(executor, times(2))
        .search(queryCaptor.capture(), eq(ProcessEntity.class), any(ResourceAccessChecks.class));
    final var queries = queryCaptor.getAllValues();
    assertThat(queries.get(0).filter()).isEqualTo(queries.get(1).filter());
    assertThat(queries.get(0).sort()).isEqualTo(queries.get(1).sort());
    assertThat(queries.get(0).sort().getFieldSortings()).isNotEmpty();
  }

  @Test
  void shouldTrackVersionsIndependentlyPerTenantAcrossPages() {
    final var executor = mock(SearchClientBasedQueryExecutor.class);
    final var indexDescriptor = mock(IndexDescriptor.class);
    final var reader =
        new ProcessDefinitionInstanceStatisticsDocumentReader(executor, indexDescriptor);

    final var tenantA = "tenant-a";
    final var tenantB = "tenant-b";

    // given: the same process ID deployed in two tenants -- tenant A ends up with two versions
    // (one of them beyond the first lookup page), tenant B with only one.
    final var itemTenantA =
        new ProcessDefinitionInstanceStatisticsEntity(
            PROCESS_DEFINITION_ID, tenantA, "Order Process", false, 1L, 0L);
    final var itemTenantB =
        new ProcessDefinitionInstanceStatisticsEntity(
            PROCESS_DEFINITION_ID, tenantB, "Order Process", true, 1L, 0L);
    when(executor.aggregateWithQueryResult(
            any(ProcessDefinitionInstanceStatisticsQuery.class),
            eq(ProcessDefinitionInstanceStatisticsAggregationResult.class),
            any(ResourceAccessChecks.class),
            any()))
        .thenReturn(SearchQueryResult.of(itemTenantA, itemTenantB));

    // given: the first page also contains an unrelated cross-pair (same process ID as the
    // requested tenants but a tenant nobody asked about) that must be ignored rather than
    // mistaken for one of the requested rows or treated as ending the pagination early.
    final var firstPage =
        fullPageOf(
            List.of(
                processDefinition(
                    1L, PROCESS_DEFINITION_ID, 1, tenantA, ProcessDefinitionState.ACTIVE),
                processDefinition(
                    2L, PROCESS_DEFINITION_ID, 1, tenantB, ProcessDefinitionState.ACTIVE),
                processDefinition(
                    3L,
                    PROCESS_DEFINITION_ID,
                    9,
                    "unrequested-tenant",
                    ProcessDefinitionState.ACTIVE)));
    final var secondPage =
        List.of(
            processDefinition(
                4L, PROCESS_DEFINITION_ID, 2, tenantA, ProcessDefinitionState.ACTIVE));

    doReturn(new SearchQueryResult<>(firstPage.size(), false, firstPage, null, "cursor-1"))
        .doReturn(new SearchQueryResult<>(secondPage.size(), false, secondPage, "cursor-1", null))
        .when(executor)
        .search(
            any(ProcessDefinitionQuery.class),
            eq(ProcessEntity.class),
            any(ResourceAccessChecks.class));

    // when
    final var result =
        reader.aggregate(
            ProcessDefinitionInstanceStatisticsQuery.of(b -> b), ResourceAccessChecks.disabled());

    // then
    assertThat(itemFor(result, PROCESS_DEFINITION_ID, tenantA).hasMultipleVersions()).isTrue();
    assertThat(itemFor(result, PROCESS_DEFINITION_ID, tenantB).hasMultipleVersions()).isFalse();
    verify(executor, times(2))
        .search(
            any(ProcessDefinitionQuery.class),
            eq(ProcessEntity.class),
            any(ResourceAccessChecks.class));
  }

  @Test
  void shouldStopPaginatingAfterShortLookupPage() {
    final var executor = mock(SearchClientBasedQueryExecutor.class);
    final var indexDescriptor = mock(IndexDescriptor.class);
    final var reader =
        new ProcessDefinitionInstanceStatisticsDocumentReader(executor, indexDescriptor);

    final var rawItem =
        new ProcessDefinitionInstanceStatisticsEntity(
            PROCESS_DEFINITION_ID, TENANT_ID, "Order Process", false, 1L, 0L);
    when(executor.aggregateWithQueryResult(
            any(ProcessDefinitionInstanceStatisticsQuery.class),
            eq(ProcessDefinitionInstanceStatisticsAggregationResult.class),
            any(ResourceAccessChecks.class),
            any()))
        .thenReturn(SearchQueryResult.of(rawItem));

    // given: a short page (fewer than the terms-query limit) that still carries a non-null end
    // cursor -- this must be treated as the last page, not as "keep going".
    final var shortPage =
        List.of(
            processDefinition(
                1L, PROCESS_DEFINITION_ID, 1, TENANT_ID, ProcessDefinitionState.ACTIVE));
    doReturn(new SearchQueryResult<>(1, false, shortPage, null, "cursor-that-must-be-ignored"))
        .when(executor)
        .search(
            any(ProcessDefinitionQuery.class),
            eq(ProcessEntity.class),
            any(ResourceAccessChecks.class));

    // when
    reader.aggregate(
        ProcessDefinitionInstanceStatisticsQuery.of(b -> b), ResourceAccessChecks.disabled());

    // then: only a single lookup request was issued
    verify(executor, times(1))
        .search(
            any(ProcessDefinitionQuery.class),
            eq(ProcessEntity.class),
            any(ResourceAccessChecks.class));
  }

  @Test
  void shouldStopPaginatingAfterFullPageFollowedByEmptyPage() {
    final var executor = mock(SearchClientBasedQueryExecutor.class);
    final var indexDescriptor = mock(IndexDescriptor.class);
    final var reader =
        new ProcessDefinitionInstanceStatisticsDocumentReader(executor, indexDescriptor);

    final var rawItem =
        new ProcessDefinitionInstanceStatisticsEntity(
            PROCESS_DEFINITION_ID, TENANT_ID, "Order Process", false, 1L, 0L);
    when(executor.aggregateWithQueryResult(
            any(ProcessDefinitionInstanceStatisticsQuery.class),
            eq(ProcessDefinitionInstanceStatisticsAggregationResult.class),
            any(ResourceAccessChecks.class),
            any()))
        .thenReturn(SearchQueryResult.of(rawItem));

    // given: a full page (exactly at the terms-query limit) followed by an empty page -- this
    // must terminate rather than loop forever.
    final var fullPage =
        fullPageOf(
            List.of(
                processDefinition(
                    1L, PROCESS_DEFINITION_ID, 1, TENANT_ID, ProcessDefinitionState.ACTIVE)));
    doReturn(new SearchQueryResult<>(fullPage.size(), false, fullPage, null, "cursor-1"))
        .doReturn(new SearchQueryResult<>(0, false, List.of(), "cursor-1", null))
        .when(executor)
        .search(
            any(ProcessDefinitionQuery.class),
            eq(ProcessEntity.class),
            any(ResourceAccessChecks.class));

    // when
    final var result =
        reader.aggregate(
            ProcessDefinitionInstanceStatisticsQuery.of(b -> b), ResourceAccessChecks.disabled());

    // then: only one deployed version was ever found, so the flag stays false, and pagination
    // stopped after the empty page instead of looping
    assertThat(result.items().getFirst().hasMultipleVersions()).isFalse();
    verify(executor, times(2))
        .search(
            any(ProcessDefinitionQuery.class),
            eq(ProcessEntity.class),
            any(ResourceAccessChecks.class));
  }

  private static ProcessDefinitionInstanceStatisticsEntity itemFor(
      final SearchQueryResult<ProcessDefinitionInstanceStatisticsEntity> result,
      final String processDefinitionId,
      final String tenantId) {
    return result.items().stream()
        .filter(
            i ->
                processDefinitionId.equals(i.processDefinitionId())
                    && tenantId.equals(i.tenantId()))
        .findFirst()
        .orElseThrow();
  }

  /**
   * Pads {@code specificEntries} with distinct filler definitions up to {@link
   * io.camunda.search.aggregation.ProcessDefinitionInstanceStatisticsAggregation#AGGREGATION_TERMS_SIZE}
   * entries, modelling a full (potentially truncated) lookup page.
   */
  private static List<ProcessDefinitionEntity> fullPageOf(
      final List<ProcessDefinitionEntity> specificEntries) {
    final var page = new ArrayList<>(specificEntries);
    long fillerKey = 0;
    while (page.size() < AGGREGATION_TERMS_SIZE) {
      page.add(
          processDefinition(
              -(++fillerKey),
              FILLER_PROCESS_DEFINITION_ID,
              1,
              FILLER_TENANT_ID,
              ProcessDefinitionState.ACTIVE));
    }
    return page;
  }

  private static ProcessDefinitionEntity processDefinition(
      final long key,
      final String processDefinitionId,
      final int version,
      final String tenantId,
      final ProcessDefinitionState state) {
    return new ProcessDefinitionEntity(
        key,
        "Order Process",
        processDefinitionId,
        "<xml>order-process</xml>",
        "order-process.bpmn",
        version,
        null,
        tenantId,
        null,
        state);
  }
}
