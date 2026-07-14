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
import io.camunda.zeebe.exporter.common.tasks.BackgroundTask;
import io.camunda.zeebe.util.VisibleForTesting;
import io.camunda.zeebe.util.concurrency.FuturesUtil;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;
import javax.annotation.WillCloseWhenClosed;
import org.agrona.LangUtil;
import org.slf4j.Logger;

public final class IncidentUpdateTask implements BackgroundTask {
  private final ExporterMetadata metadata;
  private final IncidentUpdateRepository repository;
  private final boolean ignoreMissingData;
  private final int batchSize;
  private final ExecutorService executor;
  private final Logger logger;
  private final Duration waitForRefreshInterval;
  private final IncidentNotifier incidentNotifier;
  private final CamundaExporterMetrics metrics;

  public IncidentUpdateTask(
      final ExporterMetadata metadata,
      final IncidentUpdateRepository repository,
      final boolean ignoreMissingData,
      final int batchSize,
      final ExecutorService executor,
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
      final ExecutorService executor,
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
    final var state = new IncidentsState();
    final var batch = getPendingIncidentsBatch(state);
    if (batch.newIncidentStates().isEmpty()) {
      return CompletableFuture.completedFuture(0);
    }
    logger.trace("Applying the following pending incident updates: {}", batch.newIncidentStates());
    final var check = searchForInstances(state);
    final int incidentCount = batch.newIncidentStates().size();
    if (check != InstancesCheck.OK) {
      // there was missing data, but this is often transient, so we just skip this batch for now.
      // we return the batch size so it's clear that we want to try again soon (as there was work
      // to do, but we just can't do it ATM)
      metrics.recordIncidentUpdatesRetriesNeeded(incidentCount);
      return CompletableFuture.completedFuture(incidentCount);
    }

    state
        .incidents()
        .forEach(
            (key, incidentDocument) -> {
              if (incidentDocument.incident().getTreePath() == null) {
                final var treePath = state.incidentTreePaths().get(key);
                incidentDocument.incident().setTreePath(treePath);
              }
            });
    return CompletableFuture.completedFuture(processIncidents(state, batch))
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

  private InstancesCheck searchForInstances(final IncidentsState state) {
    final var incidents = state.incidents().values();

    queryData(incidents, state);
    if (checkDataAndCollectParentTreePaths(incidents, state, false) != InstancesCheck.OK) {
      // if it failed once we want to give it a chance and to import more data
      // next failure will fail in case ignoring of missing data is not configured
      uncheckedThreadSleep();
      queryData(incidents, state);
      final var check = checkDataAndCollectParentTreePaths(incidents, state, ignoreMissingData);
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
        .forEach(doc -> state.addFlowNodeInstanceInListView(doc.id(), doc.index()));

    return InstancesCheck.OK;
  }

  private InstancesCheck checkDataAndCollectParentTreePaths(
      final Collection<IncidentDocument> incidents,
      final IncidentsState state,
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
      String piTreePath = state.processInstanceTreePaths().get(incident.getProcessInstanceKey());
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

      state
          .incidentTreePaths()
          .put(
              incident.getId(),
              new TreePath(piTreePath)
                  .appendFlowNode(incident.getFlowNodeId())
                  .appendFlowNodeInstance(String.valueOf(incident.getFlowNodeInstanceKey()))
                  .toString());
    }
    return InstancesCheck.OK;
  }

  private void queryData(final Collection<IncidentDocument> incidents, final IncidentsState state) {
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
          state.processInstanceIndices().put(instance.id(), instance.index());
          // An imported process instance may have a null/empty treePath (e.g. migrated 8.7 data, or
          // a partial list-view document re-created via upsert after archival, where
          // ListViewProcessInstanceFromProcessInstanceHandler only writes treePath when non-null).
          // The treePath map is a ConcurrentHashMap, which rejects null values, so a raw put would
          // throw an NPE. This is a permanent condition that would never resolve by retrying, so
          // instead of skipping the entry (which would stall downstream as MISSING_DATA, or fail in
          // processIncidentInBatch when the key is absent), we fall back to a sparse tree path so
          // the incident update can still make progress. A genuinely not-yet-imported instance is
          // absent from this result entirely, so it still has no entry here and is retried.
          final var treePath = instance.treePath();
          if (treePath == null || treePath.isEmpty()) {
            final var sparseTreePath =
                new TreePath().startTreePath(String.valueOf(instance.key())).toString();
            logger.warn(
                "Process instance {} has no treePath. Falling back to the sparse tree path {} so incident updates can make progress.",
                instance.key(),
                sparseTreePath);
            state.processInstanceTreePaths().put(instance.key(), sparseTreePath);
          } else {
            state.processInstanceTreePaths().put(instance.key(), treePath);
          }
        });
  }

  private int processIncidents(
      final IncidentsState state, final IncidentUpdateRepository.PendingIncidentUpdateBatch batch) {
    final var bulkUpdate = new IncidentBulkUpdate();
    return mapActiveIncidentsToAffectedInstances(state)
        .thenApplyAsync(
            ignored -> {
              seedResolvedIncidentsAsActive(state, batch);
              return null;
            },
            executor)
        .thenComposeAsync(
            ignored ->
                // processIncident one at a time, stopping if an error is raised
                FuturesUtil.traverseIgnoring(
                    state.incidents().values(),
                    incident -> processIncidentInBatch(state, incident, batch, bulkUpdate),
                    executor),
            executor)
        .thenCompose(ignored -> repository.bulkUpdate(bulkUpdate))
        .thenCompose(
            updatedIds ->
                notifyIncidents(updatedIds, bulkUpdate.incidentRequests(), state.incidents()))
        .join();
  }

  private void seedResolvedIncidentsAsActive(
      final IncidentsState state, final IncidentUpdateRepository.PendingIncidentUpdateBatch batch) {
    for (final var incident : state.incidents().values()) {
      final var newState = batch.newIncidentStates().get(incident.incident().getKey());
      if (newState != IncidentState.RESOLVED) {
        continue;
      }

      final var treePath = state.incidentTreePaths().get(incident.id());
      if (treePath == null) {
        continue;
      }

      final var parsedTreePath = new TreePath(treePath);
      parsedTreePath
          .extractProcessInstanceIds()
          .forEach(id -> state.addPiIdsWithIncidentIds(id, incident.id()));
      parsedTreePath
          .extractFlowNodeInstanceIds()
          .forEach(id -> state.addFniIdsWithIncidentIds(id, incident.id()));
    }
  }

  private CompletableFuture<Void> processIncidentInBatch(
      final IncidentsState state,
      final IncidentDocument incident,
      final IncidentUpdateRepository.PendingIncidentUpdateBatch batch,
      final IncidentBulkUpdate bulkUpdate) {
    final var processInstanceKey = incident.incident().getProcessInstanceKey();
    final var treePath = state.incidentTreePaths().get(incident.id());
    final var newState = batch.newIncidentStates().get(incident.incident().getKey());

    final CompletableFuture<?> future;
    if (!state.processInstanceTreePaths().containsKey(processInstanceKey)) {
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
      final var fniIds =
          removeProcessInstanceIds(parsedTreePath.extractFlowNodeInstanceIds(), piIds);

      future =
          createProcessInstanceUpdates(state, incident, newState, piIds, bulkUpdate)
              .thenComposeAsync(
                  unused ->
                      createFlowNodeInstanceUpdates(state, incident, newState, fniIds, bulkUpdate),
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

  private List<String> removeProcessInstanceIds(
      final List<String> fniIds, final List<String> piIds) {
    final var piIdSet = Set.copyOf(piIds);
    final var fnIdsFiltered = new ArrayList<String>(fniIds.size());
    for (final var fnId : fniIds) {
      if (!piIdSet.contains(fnId)) {
        fnIdsFiltered.add(fnId);
      } else {
        logger.debug("Skipping flow node instance id {} as it is also a process instance id", fnId);
      }
    }
    return fnIdsFiltered;
  }

  private CompletableFuture<Void> createFlowNodeInstanceUpdates(
      final IncidentsState state,
      final IncidentDocument incident,
      final IncidentState newState,
      final List<String> fniIds,
      final IncidentBulkUpdate updates) {
    final CompletableFuture<?>[] futures = {
      CompletableFuture.completedFuture(null), CompletableFuture.completedFuture(null)
    };
    if (!state.flowNodeInstanceIndices().keySet().containsAll(fniIds)) {
      futures[0] =
          repository
              .getFlowNodeInstances(fniIds)
              .toCompletableFuture()
              .thenApply(
                  documents -> {
                    for (final var document : documents) {
                      state.addFlowNodeInstance(document.id(), document.index());
                    }
                    return null;
                  });
    }

    if (!state.flowNodeInstanceInListViewIndices().keySet().containsAll(fniIds)) {
      futures[1] =
          repository
              .getFlowNodesInListView(fniIds)
              .toCompletableFuture()
              .thenApply(
                  documents -> {
                    for (final var document : documents) {
                      state.addFlowNodeInstanceInListView(document.id(), document.index());
                    }
                    return null;
                  });
    }

    return CompletableFuture.allOf(futures)
        .thenComposeAsync(
            ignored -> {
              for (final var fniId : fniIds) {
                final var listViewIndices = state.flowNodeInstanceInListViewIndices().get(fniId);
                final var flowNodeIndices = state.flowNodeInstanceIndices().get(fniId);
                if (listViewIndices != null
                    && !listViewIndices.isEmpty()
                    && flowNodeIndices != null
                    && !flowNodeIndices.isEmpty()) {
                  createFlowNodeInstanceUpdate(
                      state, incident, newState, fniId, flowNodeIndices, updates, listViewIndices);
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
      final IncidentsState state,
      final IncidentDocument incident,
      final IncidentState newState,
      final String fniId,
      final List<String> flowNodeIndices,
      final IncidentBulkUpdate updates,
      final List<String> listViewIndices) {
    final var hasIncident = IncidentState.ACTIVE == newState;
    final boolean changedState;
    if (hasIncident) {
      changedState = state.addFniIdsWithIncidentIds(fniId, incident.id());
    } else {
      changedState = state.removeIncidentIdByFniId(fniId, incident.id());
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
      final IncidentsState state,
      final IncidentDocument incident,
      final IncidentState newState,
      final List<String> piIds,
      final IncidentBulkUpdate updates) {
    var fetchMissingProcessInstances = CompletableFuture.completedFuture(null);
    if (!state.processInstanceIndices().keySet().containsAll(piIds)) {
      fetchMissingProcessInstances =
          repository
              .getProcessInstances(piIds)
              .toCompletableFuture()
              .thenApply(
                  processInstances -> {
                    for (final var processInstance : processInstances) {
                      state
                          .processInstanceIndices()
                          .put(processInstance.id(), processInstance.index());
                    }
                    return null;
                  });
    }

    return fetchMissingProcessInstances.thenComposeAsync(
        ignored -> {
          for (final var piId : piIds) {
            final var index = state.processInstanceIndices().get(piId);
            if (index != null) {
              createProcessInstanceUpdate(state, incident.id(), newState, piId, updates, index);
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
      final IncidentsState state,
      final String incidentId,
      final IncidentState newState,
      final String piId,
      final IncidentBulkUpdate updates,
      final String index) {
    final var hasIncident = IncidentState.ACTIVE == newState;
    final boolean changedState;
    if (hasIncident) {
      changedState = state.addPiIdsWithIncidentIds(piId, incidentId);
    } else {
      changedState = state.removeIncidentIdByPiId(piId, incidentId);
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

  private CompletableFuture<Void> mapActiveIncidentsToAffectedInstances(
      final IncidentsState state) {
    final CompletableFuture<List<String>> treePathTermsFutures =
        FuturesUtil.parTraverse(
                state.incidentTreePaths().values(),
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

                piIds.forEach(id -> state.addPiIdsWithIncidentIds(id, activeIncidentTreePath.id()));
                fniIds.forEach(
                    id -> state.addFniIdsWithIncidentIds(id, activeIncidentTreePath.id()));
              }
              return null;
            },
            executor);
  }

  private IncidentUpdateRepository.PendingIncidentUpdateBatch getPendingIncidentsBatch(
      final IncidentsState state) {
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
    state.incidents().putAll(incidents);

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
