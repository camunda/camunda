/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.incident;

import io.camunda.exporter.tasks.incident.IncidentUpdateRepository.IncidentDocument;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Copied from the old post-importer */
public record AdditionalData(
    Map<String, IncidentDocument> incidents,
    Map<String, List<String>> flowNodeInstanceIndices,
    Map<String, List<String>> flowNodeInstanceInListViewIndices,
    Map<Long, String> processInstanceTreePaths,
    Map<String, String> incidentTreePaths,
    Map<String, String> processInstanceIndices,
    Map<String, Set<String>> piIdsWithIncidentIds,
    Map<String, Set<String>> fniIdsWithIncidentIds) {
  public AdditionalData() {
    this(
        new ConcurrentHashMap<>(),
        new ConcurrentHashMap<>(),
        new ConcurrentHashMap<>(),
        new ConcurrentHashMap<>(),
        new ConcurrentHashMap<>(),
        new ConcurrentHashMap<>(),
        new ConcurrentHashMap<>(),
        new ConcurrentHashMap<>());
  }

  public boolean addPiIdsWithIncidentIds(final String piId, final String incidentId) {
    return addIncidentIdByInstanceId(piIdsWithIncidentIds, piId, incidentId);
  }

  public boolean removeIncidentIdByPiId(final String piId, final String incidentId) {
    return deleteIncidentIdByInstance(piIdsWithIncidentIds, piId, incidentId);
  }

  public boolean addFniIdsWithIncidentIds(final String fniId, final String incidentId) {
    return addIncidentIdByInstanceId(fniIdsWithIncidentIds, fniId, incidentId);
  }

  public boolean removeIncidentIdByFniId(final String fniId, final String incidentId) {
    return deleteIncidentIdByInstance(fniIdsWithIncidentIds, fniId, incidentId);
  }

  public void addFlowNodeInstanceInListView(final String id, final String index) {
    flowNodeInstanceInListViewIndices.computeIfAbsent(id, k -> new ArrayList<>()).add(index);
  }

  public void addFlowNodeInstance(final String id, final String index) {
    flowNodeInstanceIndices.computeIfAbsent(id, k -> new ArrayList<>()).add(index);
  }

  private boolean addIncidentIdByInstanceId(
      final Map<String, Set<String>> incidentByInstanceIds,
      final String instanceId,
      final String incidentId) {
    final var set = incidentByInstanceIds.computeIfAbsent(instanceId, k -> new HashSet<>());
    final var added = set.add(incidentId);

    return added && set.size() == 1;
  }

  private boolean deleteIncidentIdByInstance(
      final Map<String, Set<String>> incidentByInstanceIds,
      final String instanceId,
      final String incidentId) {
    final var incidentIds = incidentByInstanceIds.get(instanceId);
    if (incidentIds == null) {
      return false;
    }

    final boolean removed = incidentIds.remove(incidentId);
    return removed && incidentIds.isEmpty();
  }
}
