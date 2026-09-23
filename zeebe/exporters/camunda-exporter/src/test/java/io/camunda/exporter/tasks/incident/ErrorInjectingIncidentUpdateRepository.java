/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.incident;

import io.camunda.zeebe.exporter.api.ExporterException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

class ErrorInjectingIncidentUpdateRepository implements IncidentUpdateRepository {
  private IncidentUpdateRepository realUpdateRepository;
  private volatile boolean failFlowNodeBulkUpdates;
  private final Set<String> incidentUpdatesToFail = ConcurrentHashMap.newKeySet();

  void setRealUpdateRepository(final IncidentUpdateRepository realUpdateRepository) {
    this.realUpdateRepository = realUpdateRepository;
  }

  void setFailFlowNodeBulkUpdates(final boolean failFlowNodeBulkUpdates) {
    this.failFlowNodeBulkUpdates = failFlowNodeBulkUpdates;
  }

  void addIncidentIdToFail(final String id) {
    incidentUpdatesToFail.add(id);
  }

  void removeIncidentIdToFail(final String id) {
    incidentUpdatesToFail.remove(id);
  }

  @Override
  public void close() throws Exception {
    realUpdateRepository.close();
  }

  @Override
  public CompletionStage<Integer> getCountOfPendingIncidentUpdates(final long fromPosition) {
    return realUpdateRepository.getCountOfPendingIncidentUpdates(fromPosition);
  }

  @Override
  public CompletionStage<PendingIncidentUpdateBatch> getPendingIncidentsBatch(
      final long fromPosition, final int size) {
    return realUpdateRepository.getPendingIncidentsBatch(fromPosition, size);
  }

  @Override
  public CompletionStage<Collection<IncidentDocument>> getIncidentDocuments(
      final List<String> incidentIds) {
    return realUpdateRepository.getIncidentDocuments(incidentIds);
  }

  @Override
  public CompletionStage<Collection<Document>> getFlowNodesInListView(
      final List<String> flowNodeKeys) {
    return realUpdateRepository.getFlowNodesInListView(flowNodeKeys);
  }

  @Override
  public CompletionStage<Collection<Document>> getFlowNodeInstances(
      final List<String> flowNodeKeys) {
    return realUpdateRepository.getFlowNodeInstances(flowNodeKeys);
  }

  @Override
  public CompletionStage<Collection<ProcessInstanceDocument>> getProcessInstances(
      final List<String> processInstanceIds) {
    return realUpdateRepository.getProcessInstances(processInstanceIds);
  }

  @Override
  public CompletionStage<Set<Long>> deletedProcessInstances(final Set<Long> processInstanceKeys) {
    return realUpdateRepository.deletedProcessInstances(processInstanceKeys);
  }

  @Override
  public CompletionStage<IncidentUpdateIdsResponse> bulkUpdate(final IncidentBulkUpdate update) {
    if (!incidentUpdatesToFail.isEmpty()) {
      final IncidentBulkUpdate updateWithoutIncidents =
          new IncidentBulkUpdate(
              update.incidentRequests().stream()
                  .filter(incident -> !incidentUpdatesToFail.contains(incident.id()))
                  .toList());
      return realUpdateRepository
          .bulkUpdate(updateWithoutIncidents)
          .thenApply(
              response ->
                  response.withError(
                      new ExporterException("Simulated failure for incident bulk updates")));
    }
    return realUpdateRepository.bulkUpdate(update);
  }

  @Override
  public CompletionStage<IncidentUpdateIdsResponse> bulkUpdate(final NonIncidentBulkUpdate update) {
    if (failFlowNodeBulkUpdates) {
      // Simulate a failure for flow node bulk updates and ensure that they are not updated
      // but allow the other updates to proceed (to simulate a partial update)
      final NonIncidentBulkUpdate updateWithoutFlowNodes =
          new NonIncidentBulkUpdate(update.listViewRequests(), Collections.emptyList());
      return realUpdateRepository
          .bulkUpdate(updateWithoutFlowNodes)
          .thenApply(
              updatedIds -> {
                throw new ExporterException("Simulated failure for flow node bulk updates");
              });
    }

    return realUpdateRepository.bulkUpdate(update);
  }

  @Override
  public CompletionStage<List<String>> analyzeTreePath(final String treePath) {
    return realUpdateRepository.analyzeTreePath(treePath);
  }

  @Override
  public CompletionStage<Collection<ActiveIncident>> getActiveIncidentsByTreePaths(
      final Collection<String> treePathTerms) {
    return realUpdateRepository.getActiveIncidentsByTreePaths(treePathTerms);
  }
}
