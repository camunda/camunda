/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.common.utils;

import io.camunda.search.entities.ProcessDefinitionEntity;
import io.camunda.zeebe.exporter.common.cache.ExporterEntityCache;
import io.camunda.zeebe.exporter.common.cache.process.CachedProcessEntity;
import io.camunda.zeebe.exporter.common.cache.process.ProcessDiagramData;
import io.camunda.zeebe.model.bpmn.instance.BaseElement;
import io.camunda.zeebe.model.bpmn.instance.CallActivity;
import io.camunda.zeebe.model.bpmn.instance.FlowNode;
import io.camunda.zeebe.util.modelreader.ProcessModelReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class ProcessCacheUtil {

  private ProcessCacheUtil() {
    // utility class
  }

  /**
   * Returns callActivityId from process cache by the index in call activities list sorted
   * lexicographically.
   *
   * @param processCache
   * @param processDefinitionKey
   * @param callActivityIndex
   * @return
   */
  public static Optional<String> getCallActivityId(
      final ExporterEntityCache<Long, CachedProcessEntity> processCache,
      final Long processDefinitionKey,
      final Integer callActivityIndex) {

    if (processDefinitionKey == null || callActivityIndex == null) {
      return Optional.empty();
    }
    final var cachedProcess = processCache.get(processDefinitionKey);
    if (cachedProcess.isEmpty()
        || cachedProcess.get().callElementIds() == null
        || callActivityIndex == null) {
      return Optional.empty();
    }
    return Optional.of(cachedProcess.get().callElementIds().get(callActivityIndex));
  }

  /**
   * Returns callActivityIds from process cache.
   *
   * @param processCache
   * @param processDefinitionKeys
   * @return
   */
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
   * Returns relevant data from process diagram
   *
   * @param processDefinitionEntity
   * @return ProcessDiagramData
   */
  public static ProcessDiagramData extractProcessDiagramData(
      final ProcessDefinitionEntity processDefinitionEntity) {
    return extractProcessDiagramData(
        processDefinitionEntity.bpmnXml(), processDefinitionEntity.processDefinitionId());
  }

  /**
   * Returns relevant data from process diagram
   *
   * @param bpmnXml
   * @param bpmnProcessId
   * @return ProcessDiagramData
   */
  public static ProcessDiagramData extractProcessDiagramData(String bpmnXml, String bpmnProcessId) {

    final ProcessModelReader reader =
        ProcessModelReader.of(bpmnXml.getBytes(StandardCharsets.UTF_8), bpmnProcessId).orElse(null);

    if (reader != null) {
      final List<String> callActivityIds = sortedCallActivityIds(reader.extractCallActivities());
      final Map<String, String> flowNodesMap = getFlowNodesMap(reader.extractFlowNodes());
      return new ProcessDiagramData(callActivityIds, flowNodesMap);
    }

    return new ProcessDiagramData(List.of(), Map.of());
  }

  public static List<String> sortedCallActivityIds(final Collection<CallActivity> callActivities) {
    return callActivities.stream().map(BaseElement::getId).sorted().toList();
  }

  /**
   * Returns flowNodeName from process cache by flowNodeId
   *
   * @param processCache
   * @param processDefinitionKey
   * @param flowNodeId
   * @return flowNodeName
   */
  public static Optional<String> getFlowNodeName(
      final ExporterEntityCache<Long, CachedProcessEntity> processCache,
      final Long processDefinitionKey,
      final String flowNodeId) {

    if (processDefinitionKey == null || flowNodeId == null) {
      return Optional.empty();
    }

    return processCache
        .get(processDefinitionKey)
        .map(CachedProcessEntity::flowNodesMap)
        .map(map -> map.get(flowNodeId));
  }

  public static Map<String, String> getFlowNodesMap(Collection<FlowNode> flowNodes) {
    return flowNodes.stream()
        .collect(HashMap::new, (map, fn) -> map.put(fn.getId(), fn.getName()), HashMap::putAll);
  }
}
