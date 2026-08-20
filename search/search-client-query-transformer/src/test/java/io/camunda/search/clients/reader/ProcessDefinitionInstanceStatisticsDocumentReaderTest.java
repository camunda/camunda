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

import io.camunda.search.aggregation.result.ProcessDefinitionInstanceStatisticsAggregationResult;
import io.camunda.search.clients.SearchClientBasedQueryExecutor;
import io.camunda.search.entities.ProcessDefinitionEntity;
import io.camunda.search.entities.ProcessDefinitionEntity.ProcessDefinitionState;
import io.camunda.search.entities.ProcessDefinitionInstanceStatisticsEntity;
import io.camunda.search.query.ProcessDefinitionInstanceStatisticsQuery;
import io.camunda.search.query.ProcessDefinitionQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.core.authz.ResourceAccessChecks;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.webapps.schema.entities.ProcessEntity;
import org.junit.jupiter.api.Test;

// Reproduces https://github.com/camunda/camunda/issues/51617: the primary aggregation computes
// hasMultipleVersions from active process instances, which is blind to deployed versions that have
// no instances. These tests cover the reader-level correction against the process-definition index,
// without needing a live ES/OS container.
class ProcessDefinitionInstanceStatisticsDocumentReaderTest {

  private static final String PROCESS_DEFINITION_ID = "order-process-id";
  private static final String TENANT_ID = "tenant1";

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

    final var v1 = processDefinition(1, ProcessDefinitionState.ACTIVE);
    final var v2 = processDefinition(2, ProcessDefinitionState.ACTIVE);
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
  void shouldIgnoreDeletedVersionsWhenCountingDeployedVersions() {
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

    // given: version 1 was deleted, so only version 2 remains deployed
    final var v1Deleted = processDefinition(1, ProcessDefinitionState.DELETED);
    final var v2 = processDefinition(2, ProcessDefinitionState.ACTIVE);
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

  private static ProcessDefinitionEntity processDefinition(
      final int version, final ProcessDefinitionState state) {
    return new ProcessDefinitionEntity(
        (long) version,
        "Order Process",
        PROCESS_DEFINITION_ID,
        "<xml>order-process</xml>",
        "order-process.bpmn",
        version,
        null,
        TENANT_ID,
        null,
        state);
  }
}
