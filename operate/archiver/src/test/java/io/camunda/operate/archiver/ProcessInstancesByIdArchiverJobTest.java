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
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

class ProcessInstancesByIdArchiverJobTest extends AbstractProcessInstancesArchiverJobTest {

  @Override
  protected AbstractProcessInstancesArchiverJob buildJob(final List<Integer> partitionIds) {
    final ThreadPoolTaskScheduler executor = new ThreadPoolTaskScheduler();
    executor.setPoolSize(1);
    executor.setThreadNamePrefix("archiver-test-");
    executor.setDaemon(true);
    executor.initialize();
    return new ProcessInstancesByIdArchiverJob(
        archiver,
        partitionIds,
        processInstanceTemplate,
        List.of(),
        metrics,
        archiverRepository,
        executor);
  }

  @Override
  protected void mockSuccessfulArchiving() {
    when(processInstanceTemplate.getFullQualifiedName()).thenReturn("list-view");
    when(archiver.getDestinationIndexName(anyString(), anyString())).thenReturn("dest-index");
    when(archiverRepository.moveDocumentsById(anyString(), anyString(), any(), any(), any(), any()))
        .thenReturn(CompletableFuture.completedFuture(null));
  }
}
