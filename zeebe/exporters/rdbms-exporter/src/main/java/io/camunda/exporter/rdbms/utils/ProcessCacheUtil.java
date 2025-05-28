/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.utils;

import io.camunda.exporter.rdbms.cache.CachedProcessEntity;
import io.camunda.exporter.rdbms.cache.ExporterEntityCache;
import io.camunda.search.entities.ProcessDefinitionEntity;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.instance.BaseElement;
import io.camunda.zeebe.model.bpmn.instance.CallActivity;
import io.camunda.zeebe.model.bpmn.instance.FlowNode;
import io.camunda.zeebe.util.modelreader.ProcessModelReader;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class ProcessCacheUtil {

  public static List<String> extractCallActivityIdsFromDiagram(
      final ProcessDefinitionEntity entity) {
    final String bpmnXml = entity.bpmnXml();
    return ProcessModelReader.of(
            bpmnXml.getBytes(StandardCharsets.UTF_8), entity.processDefinitionId())
        .map(reader -> sortedCallActivityIds(reader.extractCallActivities()))
        .orElseGet(ArrayList::new);
  }

  public static List<String> sortedCallActivityIds(final Collection<CallActivity> callActivities) {
    return callActivities.stream().map(BaseElement::getId).sorted().toList();
  }

  public static List<List<String>> getCallActivityIds(
      final ExporterEntityCache<Long, CachedProcessEntity> processCache,
      final List<Long> processDefinitionKeys) {
    if (processDefinitionKeys == null) {
      return List.of();
    }

    return processCache.getAll(processDefinitionKeys).values().stream()
        .map(CachedProcessEntity::callElementIds)
        .toList();
  }

  /**
   * Returns all flow nodes from the Process diagram
   *
   * @param processEntity processEntity
   * @return Map where key is flowNodeId, and value is flowNodeName
   */
  public static Map<String, String> extractFlowNodesMapFromDiagram(
      final ProcessDefinitionEntity processEntity) {
    final String bpmnXml = processEntity.bpmnXml();

    final BpmnModelInstance modelInstance =
        Bpmn.readModelFromStream(
            new ByteArrayInputStream(bpmnXml.getBytes(StandardCharsets.UTF_8)));

    return getFlowNodesMap(modelInstance.getModelElementsByType(FlowNode.class));
  }

  /**
   * Returns flowNodeName from process cache by flowNodeId
   *
   * @param processCache
   * @param processDefinitionKey
   * @param flowNodeId
   * @return flowNodeId
   */
  public static Optional<String> getFlowNodeName(
      final ExporterEntityCache<Long, CachedProcessEntity> processCache,
      final Long processDefinitionKey,
      final String flowNodeId) {

    if (processDefinitionKey == null) {
      return Optional.empty();
    }
    final var cachedProcess = processCache.get(processDefinitionKey);
    if (cachedProcess.isEmpty()
        || cachedProcess.get().flowNodesMap() == null
        || flowNodeId == null) {
      return Optional.empty();
    }
    return Optional.of(cachedProcess.get().flowNodesMap().get(flowNodeId));
  }

  public static Map<String, String> getFlowNodesMap(Collection<FlowNode> flowNodes) {
    final Map<String, String> flowNodesMap = new HashMap<>();
    for (FlowNode flowNode : flowNodes) {
      flowNodesMap.put(flowNode.getId(), flowNode.getName());
    }

    return flowNodesMap;
  }
}
