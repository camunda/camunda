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
import io.camunda.webapps.schema.descriptors.template.ListViewTemplate;
import io.camunda.webapps.schema.descriptors.template.OperationTemplate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class HistoryDeletionWriter implements HistoryDeletionWriteClient {

  private final DocumentBasedWriteClient documentBasedWriteClient;
  private final List<ProcessInstanceDependant> processInstanceDependants;
  private final ListViewTemplate listViewTemplate;

  public HistoryDeletionWriter(
      final DocumentBasedWriteClient documentBasedWriteClient,
      final IndexDescriptors indexDescriptors) {
    this.documentBasedWriteClient = documentBasedWriteClient;

    // TODO consider if we need to delete in a specific order. Doesn't matter for POC
    processInstanceDependants =
        indexDescriptors.templates().stream()
            .filter(ProcessInstanceDependant.class::isInstance)
            .map(ProcessInstanceDependant.class::cast)
            .toList();
    listViewTemplate = indexDescriptors.get(ListViewTemplate.class);
  }

  @Override
  public void deleteHistoricData(final long processInstanceKey) {
    final var deletionResults =
        processInstanceDependants.stream()
            .filter(t -> !(t instanceof OperationTemplate))
            .map(
                dependant -> {
                  final var dependentSourceIdx =
                      dependant.getFullQualifiedName()
                          + "*"; // Add wildcard to delete from archived indices
                  final var dependentIdFieldName = dependant.getProcessInstanceDependantField();
                  return documentBasedWriteClient.deleteByFieldValue(
                      dependentSourceIdx, dependentIdFieldName, processInstanceKey);
                })
            .collect(Collectors.toCollection(ArrayList::new));

    // TODO keep list view out of scope for the POC. The batch operation tries to update this but
    // this is not possible after it's deleted
    //    deletionResults.add(
    //        documentBasedWriteClient.deleteByFieldValue(
    //            listViewTemplate.getFullQualifiedName(),
    //            ListViewTemplate.PROCESS_INSTANCE_KEY,
    //            processInstanceKey));

    if (deletionResults.contains(false)) {
      throw new IllegalStateException("Not all deletions succeeeded!");
    }
  }
}
