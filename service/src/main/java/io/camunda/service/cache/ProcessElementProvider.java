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
    try {
      final var processDefinition =
          processDefinitionServices
              .withAuthentication(CamundaAuthentication.anonymous())
              .getByKey(processDefinitionKey);
      extractElementNames(processDefinition, processDefinitionKeyElementConsumer);
    } catch (final Exception e) {
      LOG.warn(
          "Could not load process definition with key {} into cache. Skipping element extraction.",
          processDefinitionKey,
          e);
    }
  }

  public void extractElementNames(
      final Set<Long> processDefinitionKeys,
      final BiConsumer<Long, ProcessElement> processDefinitionKeyElementConsumer) {
    final var keysList = new ArrayList<>(processDefinitionKeys);

    try {
      final var result =
          processDefinitionServices
              .withAuthentication(CamundaAuthentication.anonymous())
              .search(
                  ProcessDefinitionQuery.of(
                      q ->
                          q.filter(f -> f.processDefinitionKeys(keysList))
                              .page(p -> p.size(keysList.size()))));

      if (result.total() < processDefinitionKeys.size()) {
        LOG.warn("Could not load all required process definitions into the cache");
      }

      for (final ProcessDefinitionEntity processDefinition : result.items()) {
        extractElementNames(processDefinition, processDefinitionKeyElementConsumer);
      }
    } catch (final Exception e) {
      LOG.warn(
          "Could not load process definitions into the cache. Skipping element extraction.", e);
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
