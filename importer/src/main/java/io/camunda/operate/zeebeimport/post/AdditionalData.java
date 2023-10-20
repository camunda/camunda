/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.zeebeimport.post;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class AdditionalData {

    private Map<String, String> incidentIndices = new ConcurrentHashMap<>();
    private Map<String, List<String>> flowNodeInstanceIndices = new ConcurrentHashMap<>();
    private Map<String, List<String>> flowNodeInstanceInListViewIndices = new ConcurrentHashMap<>();
    private Map<Long, String> processInstanceTreePaths = new ConcurrentHashMap<>();
    private Map<String, String> incidentTreePaths = new ConcurrentHashMap<>();
    private Map<String, String> processInstanceIndices = new ConcurrentHashMap<>();
    private Map<String, Set<String>> piIdsWithIncidentIds = new ConcurrentHashMap<>();     //piId <-> active incident ids
    private Map<String, Set<String>> fniIdsWithIncidentIds = new ConcurrentHashMap<>();    //flowNodeInstanceId <-> active incident ids

    public Map<String, List<String>> getFlowNodeInstanceIndices() {
        return flowNodeInstanceIndices;
    }

    public AdditionalData setFlowNodeInstanceIndices(Map<String, List<String>> flowNodeInstanceIndices) {
        this.flowNodeInstanceIndices = flowNodeInstanceIndices;
        return this;
    }

    public Map<String, List<String>> getFlowNodeInstanceInListViewIndices() {
        return flowNodeInstanceInListViewIndices;
    }

    public AdditionalData setFlowNodeInstanceInListViewIndices(
            Map<String, List<String>> flowNodeInstanceInListViewIndices) {
        this.flowNodeInstanceInListViewIndices = flowNodeInstanceInListViewIndices;
        return this;
    }

    public Map<Long, String> getProcessInstanceTreePaths() {
        return processInstanceTreePaths;
    }

    public AdditionalData setProcessInstanceTreePaths(Map<Long, String> processInstanceTreePaths) {
        this.processInstanceTreePaths = processInstanceTreePaths;
        return this;
    }

    public Map<String, String> getProcessInstanceIndices() {
        return processInstanceIndices;
    }

    public AdditionalData setProcessInstanceIndices(Map<String, String> processInstanceIndices) {
        this.processInstanceIndices = processInstanceIndices;
        return this;
    }

    public Map<String, String> getIncidentIndices() {
        return incidentIndices;
    }

    public AdditionalData setIncidentIndices(Map<String, String> incidentIndices) {
        this.incidentIndices = incidentIndices;
        return this;
    }

    public Map<String, Set<String>> getPiIdsWithIncidentIds() {
        return piIdsWithIncidentIds;
    }

    public void addPiIdsWithIncidentIds(String piId, String incidentId) {
        if (piIdsWithIncidentIds.get(piId) == null) {
            piIdsWithIncidentIds.put(piId, new HashSet<>());
        }
        piIdsWithIncidentIds.get(piId).add(incidentId);
    }

    public void deleteIncidentIdByPiId(String piId, String incidentId) {
        if (piIdsWithIncidentIds.get(piId) != null) {
            piIdsWithIncidentIds.get(piId).remove(incidentId);
        }
    }

    public AdditionalData setPiIdsWithIncidentIds(Map<String, Set<String>> piIdsWithIncidentIds) {
        this.piIdsWithIncidentIds = piIdsWithIncidentIds;
        return this;
    }

    public Map<String, Set<String>> getFniIdsWithIncidentIds() {
        return fniIdsWithIncidentIds;
    }

    public void addFniIdsWithIncidentIds(String fniId, String incidentId) {
        if (fniIdsWithIncidentIds.get(fniId) == null) {
            fniIdsWithIncidentIds.put(fniId, new HashSet<>());
        }
        fniIdsWithIncidentIds.get(fniId).add(incidentId);
    }

    public void deleteIncidentIdByFniId(String fniId, String incidentId) {
        if (fniIdsWithIncidentIds.get(fniId) != null) {
            fniIdsWithIncidentIds.get(fniId).remove(incidentId);
        }
    }

    public AdditionalData setFniIdsWithIncidentIds(Map<String, Set<String>> fniIdsWithIncidentIds) {
        this.fniIdsWithIncidentIds = fniIdsWithIncidentIds;
        return this;
    }

    public Map<String, String> getIncidentTreePaths() {
        return incidentTreePaths;
    }

    public AdditionalData setIncidentTreePaths(Map<String, String> incidentTreePaths) {
        this.incidentTreePaths = incidentTreePaths;
        return this;
    }
}
