/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.batchoperation.itemprovider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import io.camunda.search.clients.SearchClientsProxy;
import io.camunda.search.filter.Operation;
import io.camunda.search.filter.ProcessInstanceFilter;
import io.camunda.zeebe.engine.metrics.BatchOperationMetrics;
import io.camunda.zeebe.engine.state.batchoperation.PersistedBatchOperation;
import io.camunda.zeebe.protocol.record.value.BatchOperationType;
import org.junit.jupiter.api.Test;

class ItemProviderFactoryTest {

  private final SearchClientsProxy searchClientsProxy = mock(SearchClientsProxy.class);
  private final BatchOperationMetrics metrics = mock(BatchOperationMetrics.class);
  private final ItemProviderFactory factory =
      new ItemProviderFactory(searchClientsProxy, metrics, 1);

  @Test
  void shouldSetFiltersForCancelProcessInstance() {
    // Arrange
    final var filter = new ProcessInstanceFilter.Builder().build();
    final var batchOperation = mock(PersistedBatchOperation.class);
    when(batchOperation.getBatchOperationType())
        .thenReturn(BatchOperationType.CANCEL_PROCESS_INSTANCE);
    when(batchOperation.getEntityFilter(ProcessInstanceFilter.class)).thenReturn(filter);

    // Act
    final var itemProvider = factory.fromBatchOperation(batchOperation);

    // Assert
    assertThat(itemProvider).isNotNull();
    assertThat(itemProvider).isInstanceOf(ProcessInstanceItemProvider.class);

    final var usedFilter = ((ProcessInstanceItemProvider) itemProvider).getFilter();
    assertThat(usedFilter.parentProcessInstanceKeyOperations()).contains(Operation.exists(false));
    assertThat(usedFilter.stateOperations()).contains(Operation.eq("ACTIVE"));
    assertThat(usedFilter.partitionId()).isEqualTo(1);
  }

  @Test
  void shouldSetFiltersForMigrateProcessInstance() {
    final var filter = new ProcessInstanceFilter.Builder().build();
    final var batchOperation = mock(PersistedBatchOperation.class);
    when(batchOperation.getBatchOperationType())
        .thenReturn(BatchOperationType.MIGRATE_PROCESS_INSTANCE);
    when(batchOperation.getEntityFilter(ProcessInstanceFilter.class)).thenReturn(filter);

    // Act
    final var itemProvider = factory.fromBatchOperation(batchOperation);

    // Assert
    assertThat(itemProvider).isNotNull();
    assertThat(itemProvider).isInstanceOf(ProcessInstanceItemProvider.class);

    final var usedFilter = ((ProcessInstanceItemProvider) itemProvider).getFilter();
    assertThat(usedFilter.parentProcessInstanceKeyOperations()).isEmpty();
    assertThat(usedFilter.stateOperations()).contains(Operation.eq("ACTIVE"));
    assertThat(usedFilter.partitionId()).isEqualTo(1);
  }

  @Test
  void shouldSetFiltersForModifyProcessInstance() {
    final var filter = new ProcessInstanceFilter.Builder().build();
    final var batchOperation = mock(PersistedBatchOperation.class);
    when(batchOperation.getBatchOperationType())
        .thenReturn(BatchOperationType.MODIFY_PROCESS_INSTANCE);
    when(batchOperation.getEntityFilter(ProcessInstanceFilter.class)).thenReturn(filter);

    // Act
    final var itemProvider = factory.fromBatchOperation(batchOperation);

    // Assert
    assertThat(itemProvider).isNotNull();
    assertThat(itemProvider).isInstanceOf(ProcessInstanceItemProvider.class);

    final var usedFilter = ((ProcessInstanceItemProvider) itemProvider).getFilter();
    assertThat(usedFilter.parentProcessInstanceKeyOperations()).isEmpty();
    assertThat(usedFilter.stateOperations()).contains(Operation.eq("ACTIVE"));
    assertThat(usedFilter.partitionId()).isEqualTo(1);
  }

  @Test
  void shouldSetFiltersForResolveIncident() {
    final var filter = new ProcessInstanceFilter.Builder().build();
    final var batchOperation = mock(PersistedBatchOperation.class);
    when(batchOperation.getBatchOperationType()).thenReturn(BatchOperationType.RESOLVE_INCIDENT);
    when(batchOperation.getEntityFilter(ProcessInstanceFilter.class)).thenReturn(filter);

    // Act
    final var itemProvider = factory.fromBatchOperation(batchOperation);

    // Assert
    assertThat(itemProvider).isNotNull();
    assertThat(itemProvider).isInstanceOf(IncidentItemProvider.class);

    final var usedFilter = ((IncidentItemProvider) itemProvider).getFilter();
    assertThat(usedFilter.parentProcessInstanceKeyOperations()).isEmpty();
    assertThat(usedFilter.stateOperations()).contains(Operation.eq("ACTIVE"));
    assertThat(usedFilter.partitionId()).isEqualTo(1);
  }
}
