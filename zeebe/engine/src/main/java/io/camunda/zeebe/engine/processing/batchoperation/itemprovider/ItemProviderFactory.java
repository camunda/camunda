/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.batchoperation.itemprovider;

import io.camunda.search.clients.SearchClientsProxy;
import io.camunda.search.entities.ProcessInstanceEntity.ProcessInstanceState;
import io.camunda.search.filter.Operation;
import io.camunda.search.filter.ProcessInstanceFilter;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.zeebe.engine.metrics.BatchOperationMetrics;
import io.camunda.zeebe.engine.state.batchoperation.PersistedBatchOperation;

public class ItemProviderFactory {

  private final SearchClientsProxy searchClientsProxy;
  private final BatchOperationMetrics metrics;
  private final int partitionId;

  public ItemProviderFactory(
      final SearchClientsProxy searchClientsProxy,
      final BatchOperationMetrics metrics,
      final int partitionId) {
    this.searchClientsProxy = searchClientsProxy;
    this.metrics = metrics;
    this.partitionId = partitionId;
  }

  public ItemProvider fromBatchOperation(final PersistedBatchOperation batchOperation) {
    return switch (batchOperation.getBatchOperationType()) {
      case CANCEL_PROCESS_INSTANCE ->
          forCancelProcessInstance(
              batchOperation.getEntityFilter(ProcessInstanceFilter.class),
              batchOperation.getAuthentication());
      case MIGRATE_PROCESS_INSTANCE ->
          forMigrateProcessInstance(
              batchOperation.getEntityFilter(ProcessInstanceFilter.class),
              batchOperation.getAuthentication());
      case MODIFY_PROCESS_INSTANCE ->
          forModifyProcessInstance(
              batchOperation.getEntityFilter(ProcessInstanceFilter.class),
              batchOperation.getAuthentication());
      case RESOLVE_INCIDENT ->
          forResolveIncident(
              batchOperation.getEntityFilter(ProcessInstanceFilter.class),
              batchOperation.getAuthentication());
    };
  }

  private ProcessInstanceItemProvider forCancelProcessInstance(
      final ProcessInstanceFilter filter, final CamundaAuthentication authentication) {
    return new ProcessInstanceItemProvider(
        searchClientsProxy,
        metrics,
        filter.toBuilder()
            .partitionId(partitionId)
            .states(ProcessInstanceState.ACTIVE.name())
            .parentProcessInstanceKeyOperations(Operation.exists(false))
            .build(),
        authentication);
  }

  private ProcessInstanceItemProvider forModifyProcessInstance(
      final ProcessInstanceFilter filter, final CamundaAuthentication authentication) {
    return new ProcessInstanceItemProvider(
        searchClientsProxy,
        metrics,
        filter.toBuilder()
            .partitionId(partitionId)
            .states(ProcessInstanceState.ACTIVE.name())
            .build(),
        authentication);
  }

  private ProcessInstanceItemProvider forMigrateProcessInstance(
      final ProcessInstanceFilter filter, final CamundaAuthentication authentication) {
    return new ProcessInstanceItemProvider(
        searchClientsProxy,
        metrics,
        filter.toBuilder()
            .partitionId(partitionId)
            .states(ProcessInstanceState.ACTIVE.name())
            .build(),
        authentication);
  }

  private IncidentItemProvider forResolveIncident(
      final ProcessInstanceFilter filter, final CamundaAuthentication authentication) {
    return new IncidentItemProvider(
        searchClientsProxy,
        metrics,
        filter.toBuilder()
            .partitionId(partitionId)
            .states(ProcessInstanceState.ACTIVE.name())
            .build(),
        authentication);
  }
}
