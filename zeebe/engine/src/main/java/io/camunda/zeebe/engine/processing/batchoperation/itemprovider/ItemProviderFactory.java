/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.batchoperation.itemprovider;

import io.camunda.search.clients.SearchClientsProxy;
import io.camunda.search.entities.JobEntity.JobState;
import io.camunda.search.entities.ProcessInstanceEntity.ProcessInstanceState;
import io.camunda.search.filter.DecisionInstanceFilter;
import io.camunda.search.filter.JobFilter;
import io.camunda.search.filter.Operation;
import io.camunda.search.filter.ProcessInstanceFilter;
import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.zeebe.engine.metrics.BatchOperationMetrics;
import io.camunda.zeebe.engine.state.batchoperation.PersistedBatchOperation;
import java.util.List;

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
      case SUSPEND_PROCESS_INSTANCE ->
          forSuspendProcessInstance(
              batchOperation.getEntityFilter(ProcessInstanceFilter.class),
              batchOperation.getAuthentication());
      case RESUME_PROCESS_INSTANCE ->
          forResumeProcessInstance(
              batchOperation.getEntityFilter(ProcessInstanceFilter.class),
              batchOperation.getAuthentication());
      case UPDATE_JOB ->
          forUpdateJob(
              batchOperation.getEntityFilter(JobFilter.class), batchOperation.getAuthentication());
      case DELETE_PROCESS_INSTANCE ->
          forDeleteProcessInstance(
              batchOperation.getEntityFilter(ProcessInstanceFilter.class),
              batchOperation.getAuthentication());
      case DELETE_DECISION_INSTANCE ->
          forDeleteDecisionInstance(
              batchOperation.getEntityFilter(DecisionInstanceFilter.class),
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
            .states(ProcessInstanceState.ACTIVE.name(), ProcessInstanceState.SUSPENDED.name())
            .replaceParentProcessInstanceKeyOperations(Operation.exists(false))
            .build(),
        authentication);
  }

  private ProcessInstanceItemProvider forModifyProcessInstance(
      final ProcessInstanceFilter filter, final CamundaAuthentication authentication) {
    // Narrows rather than replaces, like cancel (#64350): a caller-supplied state is ANDed with
    // ACTIVE instead of discarded, so a conflicting value narrows to zero items rather than
    // silently being ignored. The REST layer rejects anything other than ACTIVE with a 400
    // before it reaches here (see ProcessInstanceRequestValidator).
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
    // See forModifyProcessInstance: narrowed and rejected at the REST layer, not replaced.
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
    // Narrowed like modify/migrate above, but NOT yet rejected at the REST layer: Operate's
    // default toolbar filter for retry sends an incidents branch with no state constraint, so a
    // naive reject-list would break that flow. Left as narrow-only pending a follow-up that
    // works out the allowed-state list with Operate. See the open thread on #64070.
    return new IncidentItemProvider(
        searchClientsProxy,
        metrics,
        filter.toBuilder()
            .partitionId(partitionId)
            .states(ProcessInstanceState.ACTIVE.name())
            .build(),
        authentication);
  }

  private ProcessInstanceItemProvider forDeleteProcessInstance(
      final ProcessInstanceFilter filter, final CamundaAuthentication authentication) {
    return new ProcessInstanceItemProvider(
        searchClientsProxy,
        metrics,
        filter.toBuilder().partitionId(partitionId).build(),
        authentication);
  }

  private ProcessInstanceItemProvider forSuspendProcessInstance(
      final ProcessInstanceFilter filter, final CamundaAuthentication authentication) {
    // Unlike cancel, not restricted to root instances. The parent-key filter is still overridden
    // (cleared) rather than omitted, so a caller-supplied filter can't empty the batch.
    return new ProcessInstanceItemProvider(
        searchClientsProxy,
        metrics,
        filter.toBuilder()
            .partitionId(partitionId)
            .replaceStates(ProcessInstanceState.ACTIVE.name())
            .replaceParentProcessInstanceKeyOperations(List.of())
            .build(),
        authentication);
  }

  private ProcessInstanceItemProvider forResumeProcessInstance(
      final ProcessInstanceFilter filter, final CamundaAuthentication authentication) {
    // Unlike cancel, not restricted to root instances. The parent-key filter is still overridden
    // (cleared) rather than omitted, so a caller-supplied filter can't empty the batch.
    return new ProcessInstanceItemProvider(
        searchClientsProxy,
        metrics,
        filter.toBuilder()
            .partitionId(partitionId)
            .replaceStates(ProcessInstanceState.SUSPENDED.name())
            .replaceParentProcessInstanceKeyOperations(List.of())
            .build(),
        authentication);
  }

  private JobItemProvider forUpdateJob(
      final JobFilter filter, final CamundaAuthentication authentication) {
    return new JobItemProvider(
        searchClientsProxy,
        metrics,
        filter.toBuilder()
            .partitionId(partitionId)
            // Non-terminal job states only. The engine still rejects any job that is not actually
            // updatable, so this is a safe upper bound rather than an exact match of the engine
            // precondition states.
            .stateOperations(
                Operation.in(
                    JobState.nonTerminalStates().stream().map(Enum::name).toArray(String[]::new)))
            .build(),
        authentication);
  }

  private ItemProvider forDeleteDecisionInstance(
      final DecisionInstanceFilter filter, final CamundaAuthentication authentication) {
    return new DecisionInstanceItemProvider(
        searchClientsProxy,
        metrics,
        filter.toBuilder().partitionId(partitionId).build(),
        authentication);
  }
}
