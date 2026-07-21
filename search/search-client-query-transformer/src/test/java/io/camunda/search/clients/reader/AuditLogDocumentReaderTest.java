/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.reader;

import static io.camunda.search.entities.AuditLogEntity.AuditLogEntityType.USER_TASK;
import static io.camunda.search.entities.AuditLogEntity.AuditLogOperationResult.SUCCESS;
import static io.camunda.search.filter.Operator.EQUALS;
import static io.camunda.search.filter.Operator.IN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.search.aggregation.result.AuditLogLatestSuccessfulAggregationResult;
import io.camunda.search.clients.SearchClientBasedQueryExecutor;
import io.camunda.search.entities.AuditLogEntity;
import io.camunda.search.query.AuditLogLatestSuccessfulQuery;
import io.camunda.security.core.authz.ResourceAccessChecks;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AuditLogDocumentReaderTest {

  @Test
  void shouldReturnEmptyWithoutExecutingAggregation() {
    final var executor = mock(SearchClientBasedQueryExecutor.class);
    final var reader = new AuditLogDocumentReader(executor, mock(IndexDescriptor.class));

    assertThat(
            reader.searchLatestSuccessfulByEntityKeys(
                USER_TASK, List.of(), ResourceAccessChecks.disabled()))
        .isEmpty();
    verifyNoInteractions(executor);
  }

  @Test
  void shouldDeduplicateKeysAndPreserveResourceAccessChecks() {
    final var executor = mock(SearchClientBasedQueryExecutor.class);
    final var reader = new AuditLogDocumentReader(executor, mock(IndexDescriptor.class));
    final var accessChecks = ResourceAccessChecks.disabled();
    final var expected = mock(AuditLogEntity.class);
    final var queryCaptor = ArgumentCaptor.forClass(AuditLogLatestSuccessfulQuery.class);
    when(executor.aggregate(
            queryCaptor.capture(),
            eq(AuditLogLatestSuccessfulAggregationResult.class),
            eq(accessChecks)))
        .thenReturn(new AuditLogLatestSuccessfulAggregationResult(List.of(expected)));

    final var result =
        reader.searchLatestSuccessfulByEntityKeys(
            USER_TASK, List.of("key-1", "key-2", "key-1"), accessChecks);

    assertThat(result).containsExactly(expected);
    final var query = queryCaptor.getValue();
    assertThat(query.page().size()).isEqualTo(2);
    assertThat(query.filter().entityKeyOperations())
        .singleElement()
        .satisfies(
            operation -> {
              assertThat(operation.operator()).isEqualTo(IN);
              assertThat(operation.values()).containsExactly("key-1", "key-2");
            });
    assertThat(query.filter().entityTypeOperations())
        .singleElement()
        .satisfies(
            operation -> {
              assertThat(operation.operator()).isEqualTo(EQUALS);
              assertThat(operation.value()).isEqualTo(USER_TASK.name());
            });
    assertThat(query.filter().resultOperations())
        .singleElement()
        .satisfies(
            operation -> {
              assertThat(operation.operator()).isEqualTo(EQUALS);
              assertThat(operation.value()).isEqualTo(SUCCESS.name());
            });
    verify(executor)
        .aggregate(query, AuditLogLatestSuccessfulAggregationResult.class, accessChecks);
  }
}
