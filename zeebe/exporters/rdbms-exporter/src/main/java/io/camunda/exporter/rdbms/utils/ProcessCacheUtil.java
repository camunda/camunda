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
import io.camunda.zeebe.model.bpmn.instance.BaseElement;
import io.camunda.zeebe.model.bpmn.instance.CallActivity;
import io.camunda.zeebe.util.modelreader.ProcessModelReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

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
}
