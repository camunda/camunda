/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.incident;

import io.camunda.exporter.ExporterMetadata;
import io.camunda.exporter.tasks.BackgroundTask;
import io.camunda.exporter.tasks.incident.IncidentUpdateRepository.ActiveIncident;
import io.camunda.exporter.tasks.incident.IncidentUpdateRepository.DocumentUpdate;
import io.camunda.exporter.tasks.incident.IncidentUpdateRepository.IncidentBulkUpdate;
import io.camunda.exporter.tasks.incident.IncidentUpdateRepository.IncidentDocument;
import io.camunda.webapps.operate.TreePath;
import io.camunda.webapps.schema.descriptors.operate.template.FlowNodeInstanceTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.IncidentTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.ListViewTemplate;
import io.camunda.webapps.schema.entities.incident.IncidentEntity;
import io.camunda.webapps.schema.entities.incident.IncidentState;
import io.camunda.zeebe.exporter.api.ExporterException;
import io.camunda.zeebe.util.VisibleForTesting;
import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;
import org.agrona.LangUtil;
import org.slf4j.Logger;

public final class IncidentUpdateTask implements BackgroundTask {
  private final ExporterMetadata metadata;
  private final IncidentUpdateRepository repository;
  private final boolean ignoreMissingData;
  private final int batchSize;
  private final Logger logger;
  private final Duration waitForRefreshInterval;

  public IncidentUpdateTask(
      final ExporterMetadata metadata,
      final IncidentUpdateRepository repository,
      final boolean ignoreMissingData,
      final int batchSize,
      final Logger logger) {
    this(metadata, repository, ignoreMissingData, batchSize, logger, Duration.ofSeconds(5));
  }

  @VisibleForTesting("allow configuring the refresh interval to speed tests up")
  IncidentUpdateTask(
      final ExporterMetadata metadata,
      final IncidentUpdateRepository repository,
      final boolean ignoreMissingData,
      final int batchSize,
      final Logger logger,
      final Duration waitForRefreshInterval) {
    this.metadata = metadata;
    this.repository = repository;
    this.ignoreMissingData = ignoreMissingData;
    this.batchSize = batchSize;
    this.logger = logger;
    this.waitForRefreshInterval = waitForRefreshInterval;
  }

  @Override
  public CompletionStage<Integer> execute() {
    try {
      return CompletableFuture.completedFuture(processNextBatch());
    } catch (final Exception e) {
      return CompletableFuture.failedFuture(e);
    }
  }

  @Override
  public String getCaption() {
    return "Incident update task";
  }

  private int processNextBatch() {
    final var data = new AdditionalData();
    final var batch = getPendingIncidentsBatch(data);
    if (batch.newIncidentStates().isEmpty()) {
      return 0;
    }

    logger.trace("Applying the following pending incident updates: {}", batch.newIncidentStates());
    searchForInstances(data);
    final var documentsUpdated = processIncidents(data, batch);

    logger.trace(
        """
          Finished applying {} pending incident updates ({} documents updated), updating last \
          incident update position to {}""",
        batch.newIncidentStates().size(),
        documentsUpdated,
        batch.highestPosition());

    if (documentsUpdated > 0) {
      metadata.setLastIncidentUpdatePosition(batch.highestPosition());
    }

    return documentsUpdated;
  }

  private void searchForInstances(final AdditionalData data) {
    final var incidents = data.incidents().values();

    try {
      queryData(incidents, data);
      checkDataAndCollectParentTreePaths(incidents, data, false);
    } catch (final ExporterException ex) {
      // if it failed once we want to give it a chance and to import more data
      // next failure will fail in case ignoring of missing data is not configured
      uncheckedThreadSleep();
      queryData(incidents, data);
      checkDataAndCollectParentTreePaths(incidents, data, ignoreMissingData);
    }

    final var flowNodeKeys =
        incidents.stream()
            .map(IncidentDocument::incident)
            .map(IncidentEntity::getFlowNodeInstanceKey)
            .map(String::valueOf)
            .toList();
    repository
        .getFlowNodesInListView(flowNodeKeys)
        .toCompletableFuture()
        .join()
        .forEach(doc -> data.addFlowNodeInstanceInListView(doc.id(), doc.index()));
  }

  private void checkDataAndCollectParentTreePaths(
      final Collection<IncidentDocument> incidents,
      final AdditionalData data,
      final boolean forceIgnoreMissingData) {
    int countMissingInstance = 0;
    for (final Iterator<IncidentDocument> iterator = incidents.iterator(); iterator.hasNext(); ) {
      final IncidentEntity incident = iterator.next().incident();
      String piTreePath = data.processInstanceTreePaths().get(incident.getProcessInstanceKey());
      if (piTreePath == null || piTreePath.isEmpty()) {
        final var wasDeleted =
            repository
                .wasProcessInstanceDeleted(incident.getProcessInstanceKey())
                .toCompletableFuture()
                .join();
        if (wasDeleted) {
          logger.debug(
              """
              Process instance with the key {} was deleted. Incident post processing will be \
              skipped for id {}.""",
              incident.getProcessInstanceKey(),
              incident.getId());
          iterator.remove();
          continue;
        } else {
          if (!forceIgnoreMissingData) {
            throw new ExporterException(
                """
                Process instance %d is not yet imported for incident %s; the update cannot be \
                correctly applied."""
                    .formatted(incident.getProcessInstanceKey(), incident.getId()));
          } else {
            countMissingInstance++;
            piTreePath =
                new TreePath()
                    .startTreePath(String.valueOf(incident.getProcessInstanceKey()))
                    .toString();
            logger.warn(
                """
                Process instance {} is not yet imported for incident {}; the update cannot be \
                correctly applied. Since ignoreMissingData is on, we will apply with sparse tree
                path.""",
                incident.getId(),
                incident.getProcessInstanceKey());
          }
        }
      }

      data.incidentTreePaths()
          .put(
              incident.getId(),
              new TreePath(piTreePath)
                  .appendFlowNode(incident.getFlowNodeId())
                  .appendFlowNodeInstance(String.valueOf(incident.getFlowNodeInstanceKey()))
                  .toString());
    }

    if (countMissingInstance > 0 && !ignoreMissingData) {
      throw new ExporterException(
          """
          "%d process instances are not yet imported for incident post processing; operation will \
          be retried..."""
              .formatted(countMissingInstance));
    }
  }

  private void queryData(final Collection<IncidentDocument> incidents, final AdditionalData data) {
    final var processInstanceIds =
        incidents.stream()
            .map(IncidentDocument::incident)
            .map(IncidentEntity::getProcessInstanceKey)
            .map(String::valueOf)
            .toList();
    final var processInstances =
        repository.getProcessInstances(processInstanceIds).toCompletableFuture().join();

    for (final var processInstance : processInstances) {
      data.processInstanceIndices().put(processInstance.id(), processInstance.index());
      data.processInstanceTreePaths().put(processInstance.key(), processInstance.treePath());
    }
  }

  private int processIncidents(
      final AdditionalData data, final IncidentUpdateRepository.PendingIncidentUpdateBatch batch) {
    final var bulkUpdate = new IncidentBulkUpdate();
    mapActiveIncidentsToAffectedInstances(data);

    for (final var incident : data.incidents().values()) {
      final var processInstanceKey = incident.incident().getProcessInstanceKey();
      final var treePath = data.incidentTreePaths().get(incident.id());
      final var newState = batch.newIncidentStates().get(incident.incident().getKey());

      if (!data.processInstanceTreePaths().containsKey(processInstanceKey)) {
        if (!ignoreMissingData) {
          throw new ExporterException(
              """
              Failed to apply incident update for incident '%s'; related process instance '%d' is \
              not visible yet, but may be later."""
                  .formatted(incident.id(), processInstanceKey));
        }

        logger.warn(
            """
            Failed to apply incident update for incident '{}'; related process instance '{}' is \
            not visible. As ignoreMissingData is on, we will skip updating the process instance or \
            flow node instances, and only update the incident.""",
            incident.id(),
            processInstanceKey);
      } else {
        final var parsedTreePath = new TreePath(treePath);
        final var piIds = parsedTreePath.extractProcessInstanceIds();
        final var fniIds = parsedTreePath.extractFlowNodeInstanceIds();

        createProcessInstanceUpdates(data, incident, newState, piIds, bulkUpdate);
        createFlowNodeInstanceUpdates(data, incident, newState, fniIds, bulkUpdate);
      }

      bulkUpdate
          .incidentRequests()
          .put(incident.id(), newIncidentUpdate(incident, newState, treePath));
    }

    return repository.bulkUpdate(bulkUpdate).toCompletableFuture().join();
  }

  private void createFlowNodeInstanceUpdates(
      final AdditionalData data,
      final IncidentDocument incident,
      final IncidentState newState,
      final List<String> fniIds,
      final IncidentBulkUpdate updates) {
    if (!data.flowNodeInstanceIndices().keySet().containsAll(fniIds)) {
      final var documents = repository.getFlowNodeInstances(fniIds).toCompletableFuture().join();
      for (final var document : documents) {
        data.addFlowNodeInstance(document.id(), document.index());
      }
    }

    if (!data.flowNodeInstanceInListViewIndices().keySet().containsAll(fniIds)) {
      final var documents = repository.getFlowNodesInListView(fniIds).toCompletableFuture().join();
      for (final var document : documents) {
        data.addFlowNodeInstanceInListView(document.id(), document.index());
      }
    }

    for (final var fniId : fniIds) {
      final var listViewIndices = data.flowNodeInstanceInListViewIndices().get(fniId);
      final var flowNodeIndices = data.flowNodeInstanceIndices().get(fniId);
      if (listViewIndices != null
          && !listViewIndices.isEmpty()
          && flowNodeIndices != null
          && !flowNodeIndices.isEmpty()) {
        createFlowNodeInstanceUpdate(
            data, incident, newState, fniId, flowNodeIndices, updates, listViewIndices);
      } else {
        if (!ignoreMissingData) {
          throw new ExporterException(
              """
              Flow node instance %s affected by incident %s cannot be updated because there is no \
              document for it in the list view index yet; this will be retried later."""
                  .formatted(fniId, incident.id()));
        }

        logger.warn(
            """
            Flow node instance {} affected by incident {} cannot be updated because there is no \
            document for it in the list view index yet; since ignoreMissingData is on, we will \
            skip updating for now, which may result in inconsistencies.""",
            fniId,
            incident.id());
      }
    }
  }

  private void createFlowNodeInstanceUpdate(
      final AdditionalData data,
      final IncidentDocument incident,
      final IncidentState newState,
      final String fniId,
      final List<String> flowNodeIndices,
      final IncidentBulkUpdate updates,
      final List<String> listViewIndices) {
    final var hasIncident = IncidentState.ACTIVE == newState;
    final boolean changedState;
    if (hasIncident) {
      changedState = data.addFniIdsWithIncidentIds(fniId, incident.id());
    } else {
      changedState = data.removeIncidentIdByFniId(fniId, incident.id());
    }

    final var treePath = new TreePath(incident.incident().getTreePath());
    final var routing =
        treePath
            .processInstanceForFni(fniId)
            .orElseGet(() -> String.valueOf(incident.incident().getProcessInstanceKey()));
    if (changedState) {
      flowNodeIndices.forEach(
          index ->
              updates
                  .flowNodeInstanceRequests()
                  .put(fniId, newFlowNodeInstanceUpdate(fniId, index, hasIncident)));
      listViewIndices.forEach(
          index ->
              updates
                  .listViewRequests()
                  .put(fniId, newListViewInstanceUpdate(fniId, index, hasIncident, routing)));
    }
  }

  private void createProcessInstanceUpdates(
      final AdditionalData data,
      final IncidentDocument incident,
      final IncidentState newState,
      final List<String> piIds,
      final IncidentBulkUpdate updates) {
    if (!data.processInstanceIndices().keySet().containsAll(piIds)) {
      final var processInstances =
          repository.getProcessInstances(piIds).toCompletableFuture().join();
      for (final var processInstance : processInstances) {
        data.processInstanceIndices().put(processInstance.id(), processInstance.index());
      }
    }

    for (final var piId : piIds) {
      final var index = data.processInstanceIndices().get(piId);
      if (index != null) {
        createProcessInstanceUpdate(data, incident.id(), newState, piId, updates, index);
      } else {
        if (!ignoreMissingData) {
          throw new ExporterException(
              """
              Process instance %s affected by incident %s cannot be updated because there is no \
              document for it in the list view index yet; this will be retried later."""
                  .formatted(piId, incident.id()));
        }

        logger.warn(
            """
          Process instance {} affected by incident {} cannot be updated because there is no \
          document for it in the list view index yet; since ignoreMissingData is on, we will \
          skip updating for now, which may result in inconsistencies.""",
            piId,
            incident.id());
      }
    }
  }

  private void createProcessInstanceUpdate(
      final AdditionalData data,
      final String incidentId,
      final IncidentState newState,
      final String piId,
      final IncidentBulkUpdate updates,
      final String index) {
    final var hasIncident = IncidentState.ACTIVE == newState;
    final boolean changedState;
    if (hasIncident) {
      changedState = data.addPiIdsWithIncidentIds(piId, incidentId);
    } else {
      changedState = data.removeIncidentIdByPiId(piId, incidentId);
    }

    if (changedState) {
      updates
          .listViewRequests()
          .put(piId, newListViewInstanceUpdate(piId, index, hasIncident, piId));
    }
  }

  private DocumentUpdate newIncidentUpdate(
      final IncidentDocument incident, final IncidentState state, final String treePath) {
    final Map<String, Object> fields = new HashMap<>();
    fields.put(IncidentTemplate.STATE, state);
    if (IncidentState.ACTIVE == state) {
      fields.put(IncidentTemplate.TREE_PATH, treePath);
    }

    return new DocumentUpdate(incident.id(), incident.index(), fields, null);
  }

  private DocumentUpdate newListViewInstanceUpdate(
      final String id, final String index, final boolean hasIncident, final String routing) {
    return new DocumentUpdate(id, index, Map.of(ListViewTemplate.INCIDENT, hasIncident), routing);
  }

  private DocumentUpdate newFlowNodeInstanceUpdate(
      final String id, final String index, final boolean hasIncident) {
    return new DocumentUpdate(
        id, index, Map.of(FlowNodeInstanceTemplate.INCIDENT, hasIncident), null);
  }

  private void mapActiveIncidentsToAffectedInstances(final AdditionalData data) {
    final List<String> treePathTerms =
        data.incidentTreePaths().values().stream()
            .map(repository::analyzeTreePath)
            .map(CompletionStage::toCompletableFuture)
            .map(CompletableFuture::join)
            .flatMap(List::stream)
            .collect(Collectors.toList());
    final Collection<ActiveIncident> activeIncidentTreePaths =
        repository.getActiveIncidentsByTreePaths(treePathTerms).toCompletableFuture().join();
    for (final var activeIncidentTreePath : activeIncidentTreePaths) {
      final var treePath = new TreePath(activeIncidentTreePath.treePath());
      final var piIds = treePath.extractProcessInstanceIds();
      final var fniIds = treePath.extractFlowNodeInstanceIds();

      piIds.forEach(id -> data.addPiIdsWithIncidentIds(id, activeIncidentTreePath.id()));
      fniIds.forEach(id -> data.addFniIdsWithIncidentIds(id, activeIncidentTreePath.id()));
    }
  }

  private IncidentUpdateRepository.PendingIncidentUpdateBatch getPendingIncidentsBatch(
      final AdditionalData data) {
    final IncidentUpdateRepository.PendingIncidentUpdateBatch pendingIncidentsBatch =
        repository
            .getPendingIncidentsBatch(metadata.getLastIncidentUpdatePosition(), batchSize)
            .toCompletableFuture()
            .join();

    if (pendingIncidentsBatch.newIncidentStates().isEmpty()) {
      return pendingIncidentsBatch;
    }

    logger.trace(
        "Processing incident ids <-> intents: {}", pendingIncidentsBatch.newIncidentStates());

    final var incidentIds =
        pendingIncidentsBatch.newIncidentStates().keySet().stream().map(String::valueOf).toList();
    final Map<String, IncidentDocument> incidents =
        repository.getIncidentDocuments(incidentIds).toCompletableFuture().join();
    data.incidents().putAll(incidents);

    if (pendingIncidentsBatch.newIncidentStates().size() > incidents.size()) {
      final var absentIncidents = new HashSet<>(pendingIncidentsBatch.newIncidentStates().keySet());
      absentIncidents.removeAll(
          incidents.values().stream().map(i -> i.incident().getKey()).collect(Collectors.toSet()));
      if (!ignoreMissingData) {
        throw new ExporterException(
            """
                Failed to fetch incidents associated with post-export updates; it's possible they \
                are simply not visible yet. Missing incident IDs: [%s]"""
                .formatted(absentIncidents));
      }

      logger.warn(
          """
            Not all incidents to update are visible yet; as ignoreMissingData flag is on, we will \
            ignore this for now, which means updates to the following incidents will be missing: \
            [{}]""",
          absentIncidents);
    }

    return pendingIncidentsBatch;
  }

  // TODO: replace this when moving the pipeline to an asynchronous model
  private void uncheckedThreadSleep() {
    try {
      Thread.sleep(waitForRefreshInterval.toMillis(), waitForRefreshInterval.toNanosPart());
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      LangUtil.rethrowUnchecked(e);
    }
  }
}
