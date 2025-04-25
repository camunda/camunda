/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.util;

import io.camunda.search.entities.ProcessDefinitionEntity;
import io.camunda.search.query.ProcessDefinitionQuery;
import io.camunda.service.ProcessDefinitionServices;
import io.camunda.zeebe.util.modelreader.ProcessModelReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Set;
import java.util.function.BiConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProcessElementProvider {

  private static final Logger LOG = LoggerFactory.getLogger(ProcessElementProvider.class);
  private final ProcessDefinitionServices processDefinitionServices;

  public ProcessElementProvider(final ProcessDefinitionServices processDefinitionServices) {
    this.processDefinitionServices = processDefinitionServices;
  }

  public void extractElementNames(
      final Long processDefinitionKey,
      final BiConsumer<Long, ProcessElement> processDefinitionKeyElementConsumer) {
    final var processDefinition = processDefinitionServices.getByKey(processDefinitionKey);
    extractElementNames(processDefinition, processDefinitionKeyElementConsumer);
  }

  public void extractElementNames(
      final Set<Long> processDefinitionKeys,
      final BiConsumer<Long, ProcessElement> processDefinitionKeyElementConsumer) {
    final var keysList = new ArrayList<>(processDefinitionKeys);
    final var result =
        processDefinitionServices.search(
            ProcessDefinitionQuery.of(
                q ->
                    q.filter(f -> f.processDefinitionKeys(keysList))
                        .page(p -> p.size(processDefinitionKeys.size()))));

    if (result.total() < processDefinitionKeys.size()) {
      LOG.warn("Could not load all required process definitions");
    }

    for (final ProcessDefinitionEntity processDefinition : result.items()) {
      extractElementNames(processDefinition, processDefinitionKeyElementConsumer);
    }
  }

  private void extractElementNames(
      final ProcessDefinitionEntity processDefinition,
      final BiConsumer<Long, ProcessElement> elementConsumer) {
    final byte[] bpmnBytes = processDefinition.bpmnXml().getBytes(StandardCharsets.UTF_8);
    ProcessModelReader.of(bpmnBytes, processDefinition.processDefinitionId())
        .ifPresent(
            reader ->
                reader.extractFlowNodes().stream()
                    .filter(fn -> fn.getName() != null)
                    .map(fn -> new ProcessElement(fn.getId(), fn.getName()))
                    .forEach(
                        fn ->
                            elementConsumer.accept(processDefinition.processDefinitionKey(), fn)));
  }

  public record ProcessElement(String id, String name) {}
}
