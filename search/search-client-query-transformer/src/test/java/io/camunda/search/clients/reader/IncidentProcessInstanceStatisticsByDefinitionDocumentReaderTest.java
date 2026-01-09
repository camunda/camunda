/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.reader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.search.aggregation.result.IncidentProcessInstanceStatisticsByDefinitionAggregationResult;
import io.camunda.search.clients.SearchClientBasedQueryExecutor;
import io.camunda.search.clients.cache.ProcessCache;
import io.camunda.search.clients.cache.ProcessCacheItem;
import io.camunda.search.clients.cache.ProcessCacheResult;
import io.camunda.search.entities.IncidentProcessInstanceStatisticsByDefinitionEntity;
import io.camunda.search.entities.ProcessDefinitionEntity;
import io.camunda.search.query.IncidentProcessInstanceStatisticsByDefinitionQuery;
import io.camunda.search.query.ProcessDefinitionQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.webapps.schema.entities.ProcessEntity;
import java.util.Map;
import org.junit.jupiter.api.Test;

class IncidentProcessInstanceStatisticsByDefinitionDocumentReaderTest {

  @Test
  void shouldEnrichItemsWithProcessDefinitionData() {
    final var executor = mock(SearchClientBasedQueryExecutor.class);
    final var indexDescriptor = mock(IndexDescriptor.class);
    final var processCache = mock(ProcessCache.class);

    final var reader =
        new IncidentProcessInstanceStatisticsByDefinitionDocumentReader(
            executor, indexDescriptor, processCache);

    final var rawItem =
        new IncidentProcessInstanceStatisticsByDefinitionEntity(null, 1L, null, null, null, 3L);

    final ProcessCacheResult processCacheResult =
        new ProcessCacheResult(
            Map.of(1L, new ProcessCacheItem("order-process-id", "Order Process", 1, "tenant1")));

    when(processCache.getCacheItems(any())).thenReturn(processCacheResult);

    when(executor.aggregateWithQueryResult(
            any(IncidentProcessInstanceStatisticsByDefinitionQuery.class),
            eq(IncidentProcessInstanceStatisticsByDefinitionAggregationResult.class),
            any(ResourceAccessChecks.class),
            any()))
        .thenReturn(SearchQueryResult.of(rawItem));

    final var procDef =
        new ProcessDefinitionEntity(
            1L,
            "Order Process",
            "order-process-id",
            "<xml>order-process</xml>",
            "order-process.bpmn",
            1,
            null,
            "tenant1",
            null);

    when(executor.search(
            any(ProcessDefinitionQuery.class),
            eq(ProcessEntity.class),
            any(ResourceAccessChecks.class)))
        .thenReturn(SearchQueryResult.of(procDef));

    final var result =
        reader.aggregate(
            IncidentProcessInstanceStatisticsByDefinitionQuery.of(b -> b),
            ResourceAccessChecks.disabled());

    assertThat(result.items()).hasSize(1);
    final var enriched = result.items().getFirst();
    assertThat(enriched.processDefinitionId()).isEqualTo("order-process-id");
    assertThat(enriched.processDefinitionName()).isEqualTo("Order Process");
    assertThat(enriched.processDefinitionVersion()).isEqualTo(1);
    assertThat(enriched.processDefinitionKey()).isEqualTo(1L);
    assertThat(enriched.tenantId()).isEqualTo("tenant1");
    assertThat(enriched.activeInstancesWithErrorCount()).isEqualTo(3L);
  }
}
