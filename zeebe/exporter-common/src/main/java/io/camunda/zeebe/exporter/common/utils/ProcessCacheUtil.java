/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.common.utils;

import io.camunda.zeebe.exporter.common.cache.ExporterEntityCache;
import io.camunda.zeebe.exporter.common.cache.process.CachedProcessEntity;
import io.camunda.zeebe.exporter.common.extensionproperty.ExtensionPropertyConfiguration;
import io.camunda.zeebe.model.bpmn.instance.CallActivity;
import io.camunda.zeebe.model.bpmn.instance.FlowNode;
import io.camunda.zeebe.util.modelreader.ProcessModelReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ProcessCacheUtil {

  private static final Logger LOGGER = LoggerFactory.getLogger(ProcessCacheUtil.class);

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

    final List<String> callElementIds = cachedProcess.get().callElementIds();
    if (callActivityIndex < 0 || callActivityIndex >= callElementIds.size()) {
      // TODO: This check prevents the application from crashing. A better solution shall be
      //  implemented in the future. For more context, see
      //  https://github.com/camunda/camunda/issues/42110
      LOGGER.warn(
          "Cache index {} is out of bounds for process definition key {}. Skipping cache lookup.",
          callActivityIndex,
          processDefinitionKey);
      return Optional.empty();
    }

    return Optional.of(callElementIds.get(callActivityIndex));
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

  public static CachedProcessEntity createCachedProcessEntity(
      final String name,
      final int version,
      final String versionTag,
      final String bpmnXml,
      final String bpmnProcessId,
      final ExtensionPropertyConfiguration extensionPropertiesConfiguration) {
    final var reader =
        StringUtils.isBlank(bpmnXml)
            ? null
            : ProcessModelReader.of(bpmnXml.getBytes(StandardCharsets.UTF_8), bpmnProcessId)
                .orElse(null);
    return createCachedProcessEntity(
        name, version, versionTag, reader, extensionPropertiesConfiguration);
  }

  public static CachedProcessEntity createCachedProcessEntity(
      final String name,
      final int version,
      final String versionTag,
      final ProcessModelReader reader,
      final ExtensionPropertyConfiguration extensionPropertiesConfiguration) {
    if (reader != null) {
      final var callActivityIds = sortedCallActivityIds(reader.extractCallActivities());
      final var flowNodes = reader.extractFlowNodes();
      final var flowNodesMap = getFlowNodesMap(flowNodes);
      final var hasUserTasks = ProcessModelReader.hasUserTasks(flowNodes);
      final var extensionProperties =
          ProcessModelReader.extractExtensionProperties(
              flowNodes, extensionPropertiesConfiguration.extensionPropertyFilter());
      return new CachedProcessEntity(
          name,
          version,
          versionTag,
          callActivityIds,
          flowNodesMap,
          hasUserTasks,
          extensionProperties);
    }
    return new CachedProcessEntity(name, version, versionTag, List.of(), Map.of(), true, Map.of());
  }

  private static List<String> sortedCallActivityIds(final Collection<CallActivity> callActivities) {
    return callActivities.stream().map(CallActivity::getId).sorted().toList();
  }

  /**
   * @return flowNodeName from process cache by flowNodeId
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

  private static Map<String, String> getFlowNodesMap(final Collection<FlowNode> flowNodes) {
    return flowNodes.stream()
        .collect(HashMap::new, (map, fn) -> map.put(fn.getId(), fn.getName()), HashMap::putAll);
  }
}
