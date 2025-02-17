/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.incident;

import io.camunda.webapps.schema.entities.operate.IncidentEntity;
import io.camunda.webapps.schema.entities.operate.IncidentState;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Encapsulates all accesses to the underlying storage for the {@link IncidentUpdateTask}, allowing
 * it to work with various databases.
 */
public interface IncidentUpdateRepository extends AutoCloseable {

  /**
   * Returns the next batch of sorted pending incident updates.
   *
   * @param fromPosition the position of the update to start from; any updates with a lower position
   *     will be ignored
   * @param size the maximum number of pending updates to return in the batch
   * @return a collection of pending updates, sorted by position ascending
   */
  CompletionStage<PendingIncidentUpdateBatch> getPendingIncidentsBatch(
      final long fromPosition, final int size);

  /**
   * Returns a map of {@link IncidentDocument} indexed by their IDs. It will only return documents
   * whose ID is contained in the given {@code incidentIds}. Note that it may return less, as some
   * documents may not be visible yet.
   *
   * @param incidentIds the IDs to filter on
   * @return a map of {@link IncidentDocument} indexed by ID
   */
  CompletionStage<Map<String, IncidentDocument>> getIncidentDocuments(
      final List<String> incidentIds);

  /**
   * Returns a list of flow node instance {@link Document} - that is, pairs of document ID and their
   * index - from one of the list view indices. Only returns results whose flow node key is
   * contained in the given {@code flowNodeKeys}. Note that it may return fewer results, as some
   * documents may not be visible yet.
   *
   * @param flowNodeKeys the set of flow node instance keys to filter on
   * @return a collection of flow node instance {@link Document}
   */
  CompletionStage<Collection<Document>> getFlowNodesInListView(final List<String> flowNodeKeys);

  /**
   * Returns a list of flow node instance {@link Document} - that is, pairs of document ID and their
   * index - from one of the flow node indices. Only returns results whose flow node key is
   * contained in the given {@code flowNodeKeys}. Note that it may return fewer results, as some
   * documents may not be visible yet.
   *
   * @param flowNodeKeys the set of flow node instance keys to filter on
   * @return a collection of flow node instance {@link Document}
   */
  CompletionStage<Collection<Document>> getFlowNodeInstances(final List<String> flowNodeKeys);

  /**
   * Returns a list of process instance {@link ProcessInstanceDocument} from one of the list view
   * indices. Only returns results whose document ID is contained in the given {@code
   * processInstanceIds}. Note that it may return fewer results, as some documents may not be
   * visible yet.
   *
   * @param processInstanceIds the set of IDs to filter on
   * @return a collection of process instance {@link ProcessInstanceDocument}
   */
  CompletionStage<Collection<ProcessInstanceDocument>> getProcessInstances(
      List<String> processInstanceIds);

  /**
   * Returns whether the process instance was explicitly deleted, meaning a user executed an
   * operation to explicitly delete it from the historic data.
   *
   * @param processInstanceKey the key of the process instance
   * @return true if it was deleted, false otherwise
   */
  CompletionStage<Boolean> wasProcessInstanceDeleted(final long processInstanceKey);

  /**
   * Executes the given bulk update against the underlying document store, waiting until the
   * affected indices are refreshed. This ensures you will later read your own writes.
   *
   * @param update the bulk update to execute
   * @return the number of documents updated
   */
  CompletionStage<Integer> bulkUpdate(final IncidentBulkUpdate update);

  /**
   * Returns the tree path as tokenized by an analyze request to the underlying document store.
   *
   * @param treePath the tree path to analyze
   * @return a set of terms which can be used to query tree path attributes in other indices
   */
  CompletionStage<List<String>> analyzeTreePath(final String treePath);

  /**
   * Returns the list of active incidents from the incident indices which contain any of the terms
   * given in their own tree path.
   *
   * @param treePathTerms the complete list of tree path terms to filter on
   * @return a list of active incidents with at least one tree path term overlapping the given list
   */
  CompletionStage<Collection<ActiveIncident>> getActiveIncidentsByTreePaths(
      final Collection<String> treePathTerms);

  /**
   * Represents an incident document: the source identity and its ID and index.
   *
   * <p>Keeping the index is useful as we typically query by alias, so we don't know beforehand
   * which index the document originated from.
   */
  record IncidentDocument(String id, String index, IncidentEntity incident) {}

  /**
   * Represents a process instance document from the list view: its ID, index, key, and tree path.
   *
   * <p>Keeping the index is useful as we typically query by alias, so we don't know beforehand
   * which index the document originated from.
   */
  record ProcessInstanceDocument(String id, String index, long key, String treePath) {}

  /**
   * A simple ID and index pair, mostly to allow us to properly update the right document later on,
   * as we typically query by alias and cannot deterministically encode where the original document
   * came from otherwise.
   */
  record Document(String id, String index) {}

  /** Represents an active incident, and the tree path which it currently affects. */
  record ActiveIncident(String id, String treePath) {}

  /**
   * A search store agnostic representation of a bulk update for this task. It allows us to collect
   * all the update queries into one, and pass it down to the store specific implementation to
   * execute.
   */
  record IncidentBulkUpdate(
      Map<String, DocumentUpdate> listViewRequests,
      Map<String, DocumentUpdate> flowNodeInstanceRequests,
      Map<String, DocumentUpdate> incidentRequests) {
    public IncidentBulkUpdate() {
      this(new ConcurrentHashMap<>(), new ConcurrentHashMap<>(), new ConcurrentHashMap<>());
    }

    public Stream<DocumentUpdate> stream() {
      return Stream.concat(
          Stream.concat(
              listViewRequests.values().stream(), flowNodeInstanceRequests.values().stream()),
          incidentRequests.values().stream());
    }
  }

  /**
   * Represents a specific document store agnostic update to execute.
   *
   * <p>All fields are expected to be non-null, except routing.
   */
  record DocumentUpdate(String id, String index, Map<String, Object> doc, String routing) {}

  class NoopIncidentUpdateRepository implements IncidentUpdateRepository {

    @Override
    public CompletionStage<PendingIncidentUpdateBatch> getPendingIncidentsBatch(
        final long fromPosition, final int size) {
      return CompletableFuture.completedFuture(
          new PendingIncidentUpdateBatch(-1, Collections.emptyMap()));
    }

    @Override
    public CompletionStage<Map<String, IncidentDocument>> getIncidentDocuments(
        final List<String> incidentIds) {
      return CompletableFuture.completedFuture(Map.of());
    }

    @Override
    public CompletionStage<Collection<Document>> getFlowNodesInListView(
        final List<String> flowNodeKeys) {
      return CompletableFuture.completedFuture(List.of());
    }

    @Override
    public CompletionStage<Collection<Document>> getFlowNodeInstances(
        final List<String> flowNodeKeys) {
      return CompletableFuture.completedFuture(List.of());
    }

    @Override
    public CompletionStage<Collection<ProcessInstanceDocument>> getProcessInstances(
        final List<String> processInstanceIds) {
      return CompletableFuture.completedFuture(List.of());
    }

    @Override
    public CompletionStage<Boolean> wasProcessInstanceDeleted(final long processInstanceKey) {
      return CompletableFuture.completedFuture(false);
    }

    @Override
    public CompletionStage<Integer> bulkUpdate(final IncidentBulkUpdate update) {
      return CompletableFuture.completedFuture(0);
    }

    @Override
    public CompletionStage<List<String>> analyzeTreePath(final String treePath) {
      return CompletableFuture.completedFuture(List.of());
    }

    @Override
    public CompletionStage<Collection<ActiveIncident>> getActiveIncidentsByTreePaths(
        final Collection<String> treePathTerms) {
      return CompletableFuture.completedFuture(List.of());
    }

    @Override
    public void close() throws Exception {}
  }

  /**
   * A batch of pending incident updates fetched from the post importer queue. The {@code
   * highestPosition} returns the greatest position of the updates fetched, and the states are keyed
   * by incident key.
   */
  record PendingIncidentUpdateBatch(
      long highestPosition, Map<Long, IncidentState> newIncidentStates) {}
}
