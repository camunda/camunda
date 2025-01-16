/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.utils;

import io.camunda.exporter.cache.ExporterEntityCache;
import io.camunda.exporter.cache.process.CachedProcessEntity;
import io.camunda.webapps.schema.entities.ProcessEntity;
import io.camunda.zeebe.model.bpmn.instance.BaseElement;
import io.camunda.zeebe.model.bpmn.instance.CallActivity;
import io.camunda.zeebe.util.modelreader.ProcessModelReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

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

    if (processDefinitionKey == null) {
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
   * Returns all call activity ids from the Process sorted lexicographically.
   *
   * @param processEntity
   * @return
   */
  public static List<String> extractCallActivityIdsFromDiagram(final ProcessEntity processEntity) {
    final String bpmnXml = processEntity.getBpmnXml();
    return ProcessModelReader.of(
            bpmnXml.getBytes(StandardCharsets.UTF_8), processEntity.getBpmnProcessId())
        .map(reader -> sortedCallActivityIds(reader.extractCallActivities()))
        .orElseGet(ArrayList::new);
  }

  public static List<String> sortedCallActivityIds(final Collection<CallActivity> callActivities) {
    return callActivities.stream().map(BaseElement::getId).sorted().toList();
  }
}
