/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.incident;

import io.camunda.exporter.ExporterMetadata;
import io.camunda.exporter.metrics.CamundaExporterMetrics;
import io.camunda.exporter.notifier.IncidentNotifier;
import io.camunda.exporter.tasks.BackgroundTask;
import io.camunda.exporter.tasks.incident.IncidentUpdateRepository.DocumentUpdate;
import io.camunda.exporter.tasks.incident.IncidentUpdateRepository.IncidentBulkUpdate;
import io.camunda.exporter.tasks.incident.IncidentUpdateRepository.IncidentDocument;
import io.camunda.webapps.operate.TreePath;
import io.camunda.webapps.schema.descriptors.template.FlowNodeInstanceTemplate;
import io.camunda.webapps.schema.descriptors.template.IncidentTemplate;
import io.camunda.webapps.schema.descriptors.template.ListViewTemplate;
import io.camunda.webapps.schema.entities.incident.IncidentEntity;
import io.camunda.webapps.schema.entities.incident.IncidentState;
import io.camunda.zeebe.exporter.api.ExporterException;
import io.camunda.zeebe.util.VisibleForTesting;
import io.camunda.zeebe.util.concurrency.FuturesUtil;
import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.Collectors;
import javax.annotation.WillCloseWhenClosed;
import org.agrona.LangUtil;
import org.slf4j.Logger;

public final class IncidentUpdateTask implements BackgroundTask {
  private final ExporterMetadata metadata;
  private final IncidentUpdateRepository repository;
  private final boolean ignoreMissingData;
  private final int batchSize;
  private final ScheduledExecutorService executor;
  private final Logger logger;
  private final Duration waitForRefreshInterval;
  private final IncidentNotifier incidentNotifier;
  private final CamundaExporterMetrics metrics;

  public IncidentUpdateTask(
      final ExporterMetadata metadata,
      final IncidentUpdateRepository repository,
      final boolean ignoreMissingData,
      final int batchSize,
      final ScheduledExecutorService executor,
      @WillCloseWhenClosed final IncidentNotifier incidentNotifier,
      final CamundaExporterMetrics metrics,
      final Logger logger) {
    this(
        metadata,
        repository,
        ignoreMissingData,
        batchSize,
        executor,
        incidentNotifier,
        metrics,
        logger,
        Duration.ofSeconds(5));
  }

  @VisibleForTesting("allow configuring the refresh interval to speed tests up")
  IncidentUpdateTask(
      final ExporterMetadata metadata,
      final IncidentUpdateRepository repository,
      final boolean ignoreMissingData,
      final int batchSize,
      final ScheduledExecutorService executor,
      @WillCloseWhenClosed final IncidentNotifier incidentNotifier,
      final CamundaExporterMetrics metrics,
      final Logger logger,
      final Duration waitForRefreshInterval) {
    this.metadata = metadata;
    this.repository = repository;
    this.ignoreMissingData = ignoreMissingData;
    this.batchSize = batchSize;
    this.executor = executor;
    this.metrics = metrics;
    this.logger = logger;
    this.waitForRefreshInterval = waitForRefreshInterval;
    this.incidentNotifier = incidentNotifier;
  }

  @Override
  public CompletionStage<Integer> execute() {
    try {
      return processNextBatch();
    } catch (final Exception e) {
      return CompletableFuture.failedFuture(e);
    }
  }

  @Override
  public String getCaption() {
    return "Incident update task";
  }

  @Override
  public void close() {
    incidentNotifier.close();
  }

  /**
   * To better document which methods run in parallel and which work on the entire batch, this is a
   * simplified call stack hierarchy for incident processing:
   *
   * <p>processNextBatch
   *
   * <ul>
   *   <li>BATCH: getPendingIncidentsBatch
   *   <li>BATCH: searchForInstances
   *       <ul>
   *         <li>BATCH: checkDataAndCollectParentTreePaths (modifies state adding treePath)
   *       </ul>
   *   <li>BATCH: processIncidents
   *       <ul>
   *         <li>PARALLEL: mapActiveIncidentsToAffectedInstances (modifies state w/ computeIfAbsent)
   *         <li>PARALLEL: processIncident
   *             <ul>
   *               <li>PARALLEL: createProcessInstanceUpdates
   *             </ul>
   *         <li>BATCH: BulkUpdate
   *       </ul>
   * </ul>
   */
  private CompletableFuture<Integer> processNextBatch() {
    final var data = new AdditionalData();
    final var batch = getPendingIncidentsBatch(data);
    if (batch.newIncidentStates().isEmpty()) {
      return CompletableFuture.completedFuture(0);
    }
    logger.trace("Applying the following pending incident updates: {}", batch.newIncidentStates());
    final var check = searchForInstances(data);
    final int incidentCount = batch.newIncidentStates().size();
    if (check != InstancesCheck.OK) {
      // there was missing data, but this is often transient, so we just skip this batch for now.
      // we return the batch size so it's clear that we want to try again soon (as there was work
      // to do, but we just can't do it ATM)
      metrics.recordIncidentUpdatesRetriesNeeded(incidentCount);
      return CompletableFuture.completedFuture(incidentCount);
    }

    data.incidents()
        .forEach(
            (key, incidentDocument) -> {
              if (incidentDocument.incident().getTreePath() == null) {
                final var treePath = data.incidentTreePaths().get(key);
                incidentDocument.incident().setTreePath(treePath);
              }
            });
    return CompletableFuture.completedFuture(processIncidents(data, batch))
        .thenComposeAsync(
            documentsUpdated -> {
              logger.trace(
                  """
        Finished applying {} pending incident updates ({} documents updated), updating last \
        incident update position to {}""",
                  incidentCount,
                  documentsUpdated,
                  batch.highestPosition());

              metadata.setLastIncidentUpdatePosition(batch.highestPosition());

              metrics.recordIncidentUpdatesProcessed(incidentCount);
              metrics.recordIncidentUpdatesDocumentsUpdated(documentsUpdated);
              return CompletableFuture.completedFuture(documentsUpdated);
            },
            executor);
  }

  private InstancesCheck searchForInstances(final AdditionalData data) {
    final var incidents = data.incidents().values();

    queryData(incidents, data);
    if (checkDataAndCollectParentTreePaths(incidents, data, false) != InstancesCheck.OK) {
      // if it failed once we want to give it a chance and to import more data
      // next failure will fail in case ignoring of missing data is not configured
      uncheckedThreadSleep();
      queryData(incidents, data);
      final var check = checkDataAndCollectParentTreePaths(incidents, data, ignoreMissingData);
      if (check != InstancesCheck.OK) {
        return check;
      }
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

    return InstancesCheck.OK;
  }

  private InstancesCheck checkDataAndCollectParentTreePaths(
      final Collection<IncidentDocument> incidents,
      final AdditionalData data,
      final boolean forceIgnoreMissingData) {

    final Set<Long> processInstanceKeys =
        incidents.stream()
            .map(IncidentDocument::incident)
            .map(IncidentEntity::getProcessInstanceKey)
            .collect(Collectors.toSet());

    final Set<Long> deletedProcessInstances =
        repository.deletedProcessInstances(processInstanceKeys).toCompletableFuture().join();

    for (final Iterator<IncidentDocument> iterator = incidents.iterator(); iterator.hasNext(); ) {
      final IncidentEntity incident = iterator.next().incident();
      String piTreePath = data.processInstanceTreePaths().get(incident.getProcessInstanceKey());
      if (piTreePath == null || piTreePath.isEmpty()) {
        if (deletedProcessInstances.contains(incident.getProcessInstanceKey())) {
          logger.debug(
              """
                Process instance with the key {} was deleted. Incident post processing will be \
                skipped for id {}.""",
              incident.getProcessInstanceKey(),
              incident.getId());

          // Concurrent modify operation: the underlying map is concurrent
          iterator.remove();
          continue;
        }
        if (!forceIgnoreMissingData) {
          logger.warn(
              """
            Process instance {} is not yet imported for incident {}; the update cannot be \
            correctly applied. Operation will be retried...
            """,
              incident.getProcessInstanceKey(),
              incident.getId());
          return InstancesCheck.MISSING_DATA;
        } else {
          logger.warn(
              """
                Process instance {} is not yet imported for incident {}; the update cannot be \
                correctly applied. Since ignoreMissingData is on, we will apply with sparse tree
                path.
                """,
              incident.getId(),
              incident.getProcessInstanceKey());
          piTreePath =
              new TreePath()
                  .startTreePath(String.valueOf(incident.getProcessInstanceKey()))
                  .toString();
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
    return InstancesCheck.OK;
  }

  private void queryData(final Collection<IncidentDocument> incidents, final AdditionalData data) {
    final var processInstanceIds =
        incidents.stream()
            .map(IncidentDocument::incident)
            .map(IncidentEntity::getProcessInstanceKey)
            .map(String::valueOf)
            .toList();

    final var instances =
        repository.getProcessInstances(processInstanceIds).toCompletableFuture().join();

    instances.forEach(
        instance -> {
          data.processInstanceIndices().put(instance.id(), instance.index());
          data.processInstanceTreePaths().put(instance.key(), instance.treePath());
        });
  }

  private int processIncidents(
      final AdditionalData data, final IncidentUpdateRepository.PendingIncidentUpdateBatch batch) {
    final var bulkUpdate = new IncidentBulkUpdate();
    return mapActiveIncidentsToAffectedInstances(data)
        .thenComposeAsync(
            ignored ->
                // processIncident one at a time, stopping if an error is raised
                FuturesUtil.traverseIgnoring(
                    data.incidents().values(),
                    incident -> processIncidentInBatch(data, incident, batch, bulkUpdate),
                    executor),
            executor)
        .thenCompose(ignored -> repository.bulkUpdate(bulkUpdate))
        .thenCompose(
            updatedIds ->
                notifyIncidents(updatedIds, bulkUpdate.incidentRequests(), data.incidents()))
        .join();
  }

  private CompletableFuture<Void> processIncidentInBatch(
      final AdditionalData data,
      final IncidentDocument incident,
      final IncidentUpdateRepository.PendingIncidentUpdateBatch batch,
      final IncidentBulkUpdate bulkUpdate) {
    final var processInstanceKey = incident.incident().getProcessInstanceKey();
    final var treePath = data.incidentTreePaths().get(incident.id());
    final var newState = batch.newIncidentStates().get(incident.incident().getKey());

    final CompletableFuture<?> future;
    if (!data.processInstanceTreePaths().containsKey(processInstanceKey)) {
      if (!ignoreMissingData) {
        return CompletableFuture.failedFuture(
            new ExporterException(
                "Failed to apply incident update for incident '%s'; related process instance '%d' is not visible yet, but may be later."
                    .formatted(incident.id(), processInstanceKey)));
      }

      logger.warn(
          """
            Failed to apply incident update for incident '{}'; related process instance '{}' is \
            not visible. As ignoreMissingData is on, we will skip updating the process instance or \
            flow node instances, and only update the incident.""",
          incident.id(),
          processInstanceKey);
      future = CompletableFuture.completedFuture(null);
    } else {
      final var parsedTreePath = new TreePath(treePath);
      final var piIds = parsedTreePath.extractProcessInstanceIds();
      final var fniIds = parsedTreePath.extractFlowNodeInstanceIds();

      future =
          createProcessInstanceUpdates(data, incident, newState, piIds, bulkUpdate)
              .thenComposeAsync(
                  unused ->
                      createFlowNodeInstanceUpdates(data, incident, newState, fniIds, bulkUpdate),
                  executor);
    }

    return future.thenApplyAsync(
        unused -> {
          bulkUpdate
              .incidentRequests()
              .put(incident.id(), newIncidentUpdate(incident, newState, treePath));
          return null;
        },
        executor);
  }

  private CompletableFuture<Void> createFlowNodeInstanceUpdates(
      final AdditionalData data,
      final IncidentDocument incident,
      final IncidentState newState,
      final List<String> fniIds,
      final IncidentBulkUpdate updates) {
    final CompletableFuture<?>[] futures = {
      CompletableFuture.completedFuture(null), CompletableFuture.completedFuture(null)
    };
    if (!data.flowNodeInstanceIndices().keySet().containsAll(fniIds)) {
      futures[0] =
          repository
              .getFlowNodeInstances(fniIds)
              .toCompletableFuture()
              .thenApply(
                  documents -> {
                    for (final var document : documents) {
                      data.addFlowNodeInstance(document.id(), document.index());
                    }
                    return null;
                  });
    }

    if (!data.flowNodeInstanceInListViewIndices().keySet().containsAll(fniIds)) {
      futures[1] =
          repository
              .getFlowNodesInListView(fniIds)
              .toCompletableFuture()
              .thenApply(
                  documents -> {
                    for (final var document : documents) {
                      data.addFlowNodeInstanceInListView(document.id(), document.index());
                    }
                    return null;
                  });
    }

    return CompletableFuture.allOf(futures)
        .thenComposeAsync(
            ignored -> {
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
                    return CompletableFuture.failedFuture(
                        new ExporterException(
                            """
              Flow node instance %s affected by incident %s cannot be updated because there is no \
              document for it in the list view index yet; this will be retried later."""
                                .formatted(fniId, incident.id())));
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
              return CompletableFuture.completedFuture(null);
            },
            executor);
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

  private CompletableFuture<Void> createProcessInstanceUpdates(
      final AdditionalData data,
      final IncidentDocument incident,
      final IncidentState newState,
      final List<String> piIds,
      final IncidentBulkUpdate updates) {
    var fetchMissingProcessInstances = CompletableFuture.completedFuture(null);
    if (!data.processInstanceIndices().keySet().containsAll(piIds)) {
      fetchMissingProcessInstances =
          repository
              .getProcessInstances(piIds)
              .toCompletableFuture()
              .thenApply(
                  processInstances -> {
                    for (final var processInstance : processInstances) {
                      data.processInstanceIndices()
                          .put(processInstance.id(), processInstance.index());
                    }
                    return null;
                  });
    }

    return fetchMissingProcessInstances.thenComposeAsync(
        ignored -> {
          for (final var piId : piIds) {
            final var index = data.processInstanceIndices().get(piId);
            if (index != null) {
              createProcessInstanceUpdate(data, incident.id(), newState, piId, updates, index);
            } else {
              if (!ignoreMissingData) {
                return CompletableFuture.failedFuture(
                    new ExporterException(
                        """
                Process instance %s affected by incident %s cannot be updated because there is no \
                document for it in the list view index yet; this will be retried later."""
                            .formatted(piId, incident.id())));
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
          return CompletableFuture.completedFuture(null);
        },
        executor);
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

  private CompletableFuture<Integer> notifyIncidents(
      final List<String> updatedIds,
      final Map<String, DocumentUpdate> incidentUpdates,
      final Map<String, IncidentDocument> incidents) {
    final var incidentsToNotify =
        updatedIds.stream()
            .filter(incidentUpdates::containsKey)
            .filter(id -> shouldNotifyAboutUpdate(incidentUpdates.get(id)))
            .map(incidents::get)
            .map(IncidentDocument::incident)
            .toList();
    if (incidentsToNotify.isEmpty()) {
      return CompletableFuture.completedFuture(updatedIds.size());
    }
    return incidentNotifier.notifyAsync(incidentsToNotify).thenApply(ignored -> updatedIds.size());
  }

  private boolean shouldNotifyAboutUpdate(final DocumentUpdate update) {
    if (update.doc().containsKey(IncidentTemplate.STATE)) {
      final var stateUpdate = update.doc().get(IncidentTemplate.STATE);
      return IncidentState.RESOLVED != stateUpdate && IncidentState.MIGRATED != stateUpdate;
    }
    return false;
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

  private CompletableFuture<Void> mapActiveIncidentsToAffectedInstances(final AdditionalData data) {
    final CompletableFuture<List<String>> treePathTermsFutures =
        FuturesUtil.parTraverse(
                data.incidentTreePaths().values(),
                treePath -> repository.analyzeTreePath(treePath).toCompletableFuture())
            .thenApply(lists -> lists.stream().flatMap(List::stream).collect(Collectors.toList()));

    return treePathTermsFutures
        .thenComposeAsync(
            treePathTerms ->
                repository.getActiveIncidentsByTreePaths(treePathTerms).toCompletableFuture(),
            executor)
        .thenApplyAsync(
            activeIncidentTreePaths -> {
              for (final var activeIncidentTreePath : activeIncidentTreePaths) {
                final var treePath = new TreePath(activeIncidentTreePath.treePath());
                final var piIds = treePath.extractProcessInstanceIds();
                final var fniIds = treePath.extractFlowNodeInstanceIds();

                piIds.forEach(id -> data.addPiIdsWithIncidentIds(id, activeIncidentTreePath.id()));
                fniIds.forEach(
                    id -> data.addFniIdsWithIncidentIds(id, activeIncidentTreePath.id()));
              }
              return null;
            },
            executor);
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

  private void uncheckedThreadSleep() {
    try {
      Thread.sleep(waitForRefreshInterval.toMillis(), waitForRefreshInterval.toNanosPart());
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      LangUtil.rethrowUnchecked(e);
    }
  }

  enum InstancesCheck {
    OK,
    MISSING_DATA
  }
}
