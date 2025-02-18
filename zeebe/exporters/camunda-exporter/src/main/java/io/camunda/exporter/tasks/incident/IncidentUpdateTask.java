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
import io.camunda.exporter.tasks.incident.IncidentUpdateRepository.DocumentUpdate;
import io.camunda.exporter.tasks.incident.IncidentUpdateRepository.IncidentBulkUpdate;
import io.camunda.exporter.tasks.incident.IncidentUpdateRepository.IncidentDocument;
import io.camunda.webapps.operate.TreePath;
import io.camunda.webapps.schema.descriptors.operate.template.FlowNodeInstanceTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.IncidentTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.ListViewTemplate;
import io.camunda.webapps.schema.entities.operate.IncidentEntity;
import io.camunda.webapps.schema.entities.operate.IncidentState;
import io.camunda.zeebe.exporter.api.ExporterException;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.slf4j.Logger;

public final class IncidentUpdateTask implements BackgroundTask {
  private final ExporterMetadata metadata;
  private final IncidentUpdateRepository repository;
  private final boolean ignoreMissingData;
  private final int batchSize;
  private final ScheduledExecutorService executor;
  private final Logger logger;
  private final Duration waitForRefreshInterval;

  public IncidentUpdateTask(
      final ExporterMetadata metadata,
      final IncidentUpdateRepository repository,
      final boolean ignoreMissingData,
      final int batchSize,
      final ScheduledExecutorService executor,
      final Logger logger) {
    this(
        metadata,
        repository,
        ignoreMissingData,
        batchSize,
        executor,
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
      final Logger logger,
      final Duration waitForRefreshInterval) {
    this.metadata = metadata;
    this.repository = repository;
    this.ignoreMissingData = ignoreMissingData;
    this.batchSize = batchSize;
    this.executor = executor;
    this.logger = logger;
    this.waitForRefreshInterval = waitForRefreshInterval;
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

  private CompletableFuture<Integer> processNextBatch() {
    final var data = new AdditionalData();
    return getPendingIncidentsBatch(data)
        .thenComposeAsync(
            batch -> {
              if (batch.newIncidentStates().isEmpty()) {
                return CompletableFuture.completedFuture(0);
              } else {
                logger.trace(
                    "Applying the following pending incident updates: {}",
                    batch.newIncidentStates());
                return searchForInstances(data)
                    .thenComposeAsync(unused -> processIncidents(data, batch), executor)
                    .thenComposeAsync(
                        documentsUpdated -> {
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

                          return CompletableFuture.completedFuture(documentsUpdated);
                        },
                        executor);
              }
            },
            executor);
  }

  private CompletableFuture<Void> searchForInstances(final AdditionalData data) {
    final var incidents = data.incidents().values();

    CompletableFuture<?> future;
    try {
      future =
          queryData(incidents, data)
              .thenComposeAsync(
                  unused -> checkDataAndCollectParentTreePaths(incidents, data, false), executor);
    } catch (final ExporterException ex) {
      // if it failed once we want to give it a chance and to import more data
      // next failure will fail in case ignoring of missing data is not configured
      future =
          completeAfter(waitForRefreshInterval.toMillis(), TimeUnit.MILLISECONDS)
              .thenComposeAsync(ignored -> queryData(incidents, data), executor)
              .thenComposeAsync(
                  unused -> checkDataAndCollectParentTreePaths(incidents, data, ignoreMissingData),
                  executor);
    }

    final var flowNodeKeys =
        incidents.stream()
            .map(IncidentDocument::incident)
            .map(IncidentEntity::getFlowNodeInstanceKey)
            .map(String::valueOf)
            .toList();
    return future
        .thenComposeAsync(
            ignored -> repository.getFlowNodesInListView(flowNodeKeys).toCompletableFuture(),
            executor)
        .thenApply(
            documents -> {
              documents.forEach(doc -> data.addFlowNodeInstanceInListView(doc.id(), doc.index()));
              return null;
            });
  }

  private CompletableFuture<Void> checkDataAndCollectParentTreePaths(
      final Collection<IncidentDocument> incidents,
      final AdditionalData data,
      final boolean forceIgnoreMissingData) {
    final AtomicInteger countMissingInstance = new AtomicInteger(0);
    final var futures = new ArrayList<CompletableFuture<?>>(incidents.size());
    for (final Iterator<IncidentDocument> iterator = incidents.iterator(); iterator.hasNext(); ) {
      final IncidentEntity incident = iterator.next().incident();
      final String piTreePath =
          data.processInstanceTreePaths().get(incident.getProcessInstanceKey());
      CompletableFuture<String> piTreePathToInsert = CompletableFuture.completedFuture(piTreePath);
      if (piTreePath == null || piTreePath.isEmpty()) {
        piTreePathToInsert =
            repository
                .wasProcessInstanceDeleted(incident.getProcessInstanceKey())
                .toCompletableFuture()
                .thenComposeAsync(
                    wasDeleted -> {
                      if (wasDeleted) {
                        logger.debug(
                            """
                Process instance with the key {} was deleted. Incident post processing will be \
                skipped for id {}.""",
                            incident.getProcessInstanceKey(),
                            incident.getId());

                        // Concurrent modify operation: the underlying map is concurrent
                        iterator.remove();
                        return CompletableFuture.completedFuture(null);
                      } else {
                        if (!forceIgnoreMissingData) {
                          return CompletableFuture.failedFuture(
                              new ExporterException(
                                  """
                                    Process instance %d is not yet imported for incident %s; the update cannot be \
                                    correctly applied."""
                                      .formatted(
                                          incident.getProcessInstanceKey(), incident.getId())));
                        } else {
                          // AtomicInteger increment
                          countMissingInstance.incrementAndGet();
                          logger.warn(
                              """
                            Process instance {} is not yet imported for incident {}; the update cannot be \
                            correctly applied. Since ignoreMissingData is on, we will apply with sparse tree
                            path.""",
                              incident.getId(),
                              incident.getProcessInstanceKey());
                          return CompletableFuture.completedFuture(
                              new TreePath()
                                  .startTreePath(String.valueOf(incident.getProcessInstanceKey()))
                                  .toString());
                        }
                      }
                    },
                    executor);
      }
      futures.add(piTreePathToInsert);

      piTreePathToInsert.thenApplyAsync(
          piPath -> {
            // ConcurrentHashMap put
            data.incidentTreePaths()
                .put(
                    incident.getId(),
                    new TreePath(piPath)
                        .appendFlowNode(incident.getFlowNodeId())
                        .appendFlowNodeInstance(String.valueOf(incident.getFlowNodeInstanceKey()))
                        .toString());
            return null;
          },
          executor);
    }

    return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
        .thenComposeAsync(
            unused -> {
              if (countMissingInstance.get() > 0 && !ignoreMissingData) {
                final var exception =
                    new ExporterException(
                        """
            "%d process instances are not yet imported for incident post processing; operation will \
            be retried..."""
                            .formatted(countMissingInstance.get()));
                return CompletableFuture.failedFuture(exception);
              } else {
                return CompletableFuture.completedFuture(null);
              }
            },
            executor);
  }

  private CompletableFuture<Void> queryData(
      final Collection<IncidentDocument> incidents, final AdditionalData data) {
    final var processInstanceIds =
        incidents.stream()
            .map(IncidentDocument::incident)
            .map(IncidentEntity::getProcessInstanceKey)
            .map(String::valueOf)
            .toList();
    return repository
        .getProcessInstances(processInstanceIds)
        .toCompletableFuture()
        .thenApply(
            instances -> {
              for (final var processInstance : instances) {
                data.processInstanceIndices().put(processInstance.id(), processInstance.index());
                data.processInstanceTreePaths()
                    .put(processInstance.key(), processInstance.treePath());
              }
              return null;
            });
  }

  private CompletableFuture<Integer> processIncidents(
      final AdditionalData data, final IncidentUpdateRepository.PendingIncidentUpdateBatch batch) {
    final var bulkUpdate = new IncidentBulkUpdate();
    return mapActiveIncidentsToAffectedInstances(data)
        .thenComposeAsync(
            ignored ->
                FuturesUtil.traverseIgnoring(
                    data.incidents().values(),
                    incident -> processIncidentInBatch(data, incident, batch, bulkUpdate),
                    executor),
            executor)
        .thenComposeAsync(
            unused -> repository.bulkUpdate(bulkUpdate).toCompletableFuture(), executor);
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

  private CompletableFuture<IncidentUpdateRepository.PendingIncidentUpdateBatch>
      getPendingIncidentsBatch(final AdditionalData data) {
    return repository
        .getPendingIncidentsBatch(metadata.getLastIncidentUpdatePosition(), batchSize)
        .toCompletableFuture()
        .thenComposeAsync(
            pendingIncidentsBatch -> {
              if (!pendingIncidentsBatch.newIncidentStates().isEmpty()) {
                logger.trace(
                    "Processing incident ids <-> intents: {}",
                    pendingIncidentsBatch.newIncidentStates());

                final var incidentIds =
                    pendingIncidentsBatch.newIncidentStates().keySet().stream()
                        .map(String::valueOf)
                        .toList();
                return repository
                    .getIncidentDocuments(incidentIds)
                    .toCompletableFuture()
                    .thenComposeAsync(
                        incidents -> {
                          data.incidents().putAll(incidents);

                          if (pendingIncidentsBatch.newIncidentStates().size() > incidents.size()) {
                            final var absentIncidents =
                                new HashSet<>(pendingIncidentsBatch.newIncidentStates().keySet());
                            absentIncidents.removeAll(
                                incidents.values().stream()
                                    .map(i -> i.incident().getKey())
                                    .collect(Collectors.toSet()));
                            if (!ignoreMissingData) {
                              return CompletableFuture.failedFuture(
                                  new ExporterException(
                                      """
                                Failed to fetch incidents associated with post-export updates; it's possible they \
                                are simply not visible yet. Missing incident IDs: [%s]"""
                                          .formatted(absentIncidents)));
                            }

                            logger.warn(
                                """
                          Not all incidents to update are visible yet; as ignoreMissingData flag is on, we will \
                          ignore this for now, which means updates to the following incidents will be missing: \
                          [{}]""",
                                absentIncidents);
                          }
                          return CompletableFuture.completedFuture(pendingIncidentsBatch);
                        },
                        executor);
              }
              return CompletableFuture.completedFuture(pendingIncidentsBatch);
            },
            executor);
  }

  private CompletableFuture<Void> completeAfter(final long delay, final TimeUnit timeUnit) {
    final var future = new CompletableFuture<Void>();
    executor.schedule(() -> future.complete(null), delay, timeUnit);
    return future;
  }
}
