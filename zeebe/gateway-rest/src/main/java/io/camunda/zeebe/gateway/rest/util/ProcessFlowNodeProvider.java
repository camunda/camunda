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

public class ProcessFlowNodeProvider {

  private static final Logger LOG = LoggerFactory.getLogger(ProcessFlowNodeProvider.class);
  private final ProcessDefinitionServices processDefinitionServices;

  public ProcessFlowNodeProvider(final ProcessDefinitionServices processDefinitionServices) {
    this.processDefinitionServices = processDefinitionServices;
  }

  public void extractFlowNodeNames(
      final Long processDefinitionKey,
      final BiConsumer<Long, ProcessFlowNode> processDefinitionKeyFlowNodeConsumer) {
    final var processDefinition = processDefinitionServices.getByKey(processDefinitionKey);
    extractFlowNodeNames(processDefinition, processDefinitionKeyFlowNodeConsumer);
  }

  public void extractFlowNodeNames(
      final Set<Long> processDefinitionKeys,
      final BiConsumer<Long, ProcessFlowNode> processDefinitionKeyFlowNodeConsumer) {
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
      extractFlowNodeNames(processDefinition, processDefinitionKeyFlowNodeConsumer);
    }
  }

  private void extractFlowNodeNames(
      final ProcessDefinitionEntity processDefinition,
      final BiConsumer<Long, ProcessFlowNode> flowNodeConsumer) {
    final byte[] bpmnBytes = processDefinition.bpmnXml().getBytes(StandardCharsets.UTF_8);
    ProcessModelReader.of(bpmnBytes, processDefinition.processDefinitionId())
        .ifPresent(
            reader ->
                reader.extractFlowNodes().stream()
                    .filter(fn -> fn.getName() != null)
                    .map(fn -> new ProcessFlowNode(fn.getId(), fn.getName()))
                    .forEach(
                        fn ->
                            flowNodeConsumer.accept(processDefinition.processDefinitionKey(), fn)));
  }

  public record ProcessFlowNode(String id, String name) {}
}
