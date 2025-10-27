/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.reader;

import io.camunda.search.clients.DocumentBasedWriteClient;
import io.camunda.search.clients.HistoryDeletionWriteClient;
import io.camunda.webapps.schema.descriptors.IndexDescriptors;
import io.camunda.webapps.schema.descriptors.ProcessInstanceDependant;
import io.camunda.webapps.schema.descriptors.template.OperationTemplate;
import java.util.List;

public class HistoryDeletionWriter implements HistoryDeletionWriteClient {

  private final DocumentBasedWriteClient documentBasedWriteClient;
  private final IndexDescriptors indexDescriptors;

  public HistoryDeletionWriter(
      final DocumentBasedWriteClient documentBasedWriteClient,
      final IndexDescriptors indexDescriptors) {
    this.documentBasedWriteClient = documentBasedWriteClient;
    this.indexDescriptors = indexDescriptors;
  }

  @Override
  public void deleteHistoricData(final long processInstanceKey) {
    final List<ProcessInstanceDependant> processInstanceDependants =
        indexDescriptors.templates().stream()
            .filter(ProcessInstanceDependant.class::isInstance)
            .map(ProcessInstanceDependant.class::cast)
            .toList();

    final var deletionResults =
        processInstanceDependants.stream()
            .filter(t -> !(t instanceof OperationTemplate))
            .map(
                dependant -> {
                  final var dependentSourceIdx = dependant.getFullQualifiedName();
                  final var dependentIdFieldName = dependant.getProcessInstanceDependantField();
                  return documentBasedWriteClient.deleteByFieldValue(
                      dependentSourceIdx, dependentIdFieldName, processInstanceKey);
                })
            .toList();

    if (deletionResults.contains(false)) {
      throw new IllegalStateException("Not all deletions succeeeded!");
    }
  }
}
