/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.cache;

import io.camunda.search.entities.ProcessDefinitionEntity;
import io.camunda.search.query.ProcessDefinitionQuery;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.ProcessDefinitionServices;
import io.camunda.zeebe.util.modelreader.ProcessModelReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProcessDefinitionProvider {

  private static final Logger LOG = LoggerFactory.getLogger(ProcessDefinitionProvider.class);
  private final ProcessDefinitionServices processDefinitionServices;

  public ProcessDefinitionProvider(final ProcessDefinitionServices processDefinitionServices) {
    this.processDefinitionServices = processDefinitionServices;
  }

  public ProcessCacheData extractProcessData(final Long processDefinitionKey) {
    try {
      final var processDefinition =
          processDefinitionServices
              .withAuthentication(CamundaAuthentication.anonymous())
              .getByKey(processDefinitionKey);
      return extractProcessData(processDefinition);
    } catch (final Exception e) {
      LOG.warn(
          "Could not load process definition with key {} into cache. Skipping element extraction.",
          processDefinitionKey,
          e);
      return new ProcessCacheData(null, Map.of());
    }
  }

  public Map<Long, ProcessCacheData> extractProcessData(final Set<Long> processDefinitionKeys) {
    final var keysList = new ArrayList<>(processDefinitionKeys);
    final var result = new HashMap<Long, ProcessCacheData>();

    try {
      final var searchResult =
          processDefinitionServices
              .withAuthentication(CamundaAuthentication.anonymous())
              .search(
                  ProcessDefinitionQuery.of(
                      q ->
                          q.filter(f -> f.processDefinitionKeys(keysList))
                              .page(p -> p.size(keysList.size()))));

      if (searchResult.total() < processDefinitionKeys.size()) {
        LOG.warn("Could not load all required process definitions into the cache");
      }

      for (final ProcessDefinitionEntity processDefinition : searchResult.items()) {
        final var processData = extractProcessData(processDefinition);
        result.put(processDefinition.processDefinitionKey(), processData);
      }
    } catch (final Exception e) {
      LOG.warn(
          "Could not load process definitions into the cache. Skipping element extraction.", e);
    }

    return result;
  }

  private ProcessCacheData extractProcessData(final ProcessDefinitionEntity processDefinition) {
    final var elements = new HashMap<String, String>();
    final byte[] bpmnBytes = processDefinition.bpmnXml().getBytes(StandardCharsets.UTF_8);
    ProcessModelReader.of(bpmnBytes, processDefinition.processDefinitionId())
        .ifPresent(
            reader ->
                reader.extractFlowNodes().stream()
                    .filter(fn -> fn.getName() != null)
                    .map(fn -> new ProcessElement(fn.getId(), fn.getName()))
                    .forEach(fn -> elements.put(fn.id(), fn.name())));

    return new ProcessCacheData(processDefinition.name(), elements);
  }

  public record ProcessElement(String id, String name) {}

  public record ProcessCacheData(String processName, Map<String, String> elementIdNameMap) {}
}
