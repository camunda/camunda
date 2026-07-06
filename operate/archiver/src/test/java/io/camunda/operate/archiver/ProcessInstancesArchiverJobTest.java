/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.archiver;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.CompletableFuture;

class ProcessInstancesArchiverJobTest extends AbstractProcessInstancesArchiverJobTest {

  @Override
  protected AbstractProcessInstancesArchiverJob buildJob(final List<Integer> partitionIds) {
    return new ProcessInstancesArchiverJob(
        archiver, partitionIds, processInstanceTemplate, List.of(), metrics, archiverRepository);
  }

  @Override
  protected void mockSuccessfulArchiving() {
    when(processInstanceTemplate.getFullQualifiedName()).thenReturn("list-view");
    when(archiver.moveDocuments(anyString(), anyString(), anyString(), any()))
        .thenReturn(CompletableFuture.completedFuture(null));
  }
}
