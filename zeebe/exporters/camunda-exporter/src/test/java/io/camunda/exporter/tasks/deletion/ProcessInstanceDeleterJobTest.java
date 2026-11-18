/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.deletion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.camunda.exporter.config.ExporterConfiguration.HistoryConfiguration;
import io.camunda.exporter.index.TargetIndexLocator;
import io.camunda.exporter.metrics.CamundaExporterMetrics;
import io.camunda.exporter.tasks.util.FilterByKeysQuery;
import io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate;
import io.camunda.webapps.schema.descriptors.template.ListViewTemplate;
import io.camunda.webapps.schema.descriptors.template.TaskTemplate;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class ProcessInstanceDeleterJobTest {
  private static final Logger LOGGER = LoggerFactory.getLogger(ProcessInstanceDeleterJobTest.class);

  private final Executor executor = Runnable::run;

  private final HistoryConfiguration historyConfiguration = new HistoryConfiguration();
  private final TargetIndexLocator targetIndexLocator = new TargetIndexLocator();
  private final DeletionRepository repository = mock(DeletionRepository.class);
  private final ListViewTemplate processInstanceTemplate = new ListViewTemplate("", true);
  private final DecisionInstanceTemplate decisionInstanceTemplate =
      new DecisionInstanceTemplate("", true);
  private final TaskTemplate taskTemplate = new TaskTemplate("", true);

  private final CamundaExporterMetrics metrics = mock(CamundaExporterMetrics.class);

  private final ProcessInstanceDeleterJob job =
      new ProcessInstanceDeleterJob(
          targetIndexLocator,
          repository,
          processInstanceTemplate,
          List.of(decisionInstanceTemplate, taskTemplate),
          metrics,
          LOGGER,
          executor);

  @Test
  void shouldDeleteNothingWhenBatchEmpty() {
    // given
    when(repository.getProcessInstancesNextBatch())
        .thenReturn(CompletableFuture.completedFuture(ProcessInstanceDeletionBatch.empty()));

    // when
    final var future = job.execute();

    // then
    assertThat(future).succeedsWithin(Duration.ofSeconds(5)).isEqualTo(0);

    verify(repository).getProcessInstancesNextBatch();
    verifyNoMoreInteractions(repository);
  }

  @Test
  void shouldDeleteDependantDocumentsThenProcessInstances() {
    // given
    when(repository.getProcessInstancesNextBatch())
        .thenReturn(
            CompletableFuture.completedFuture(
                new ProcessInstanceDeletionBatch(1, List.of(1L, 2L), List.of(3L))));

    // when
    final var future = job.execute();

    // then
    assertThat(future).succeedsWithin(Duration.ofSeconds(5)).isEqualTo(3);

    final var inOrder = inOrder(repository);
    inOrder.verify(repository).getProcessInstancesNextBatch();
    // first deleting the dependant docs
    inOrder
        .verify(repository)
        .deleteDocumentsById(
            "operate-decision-instance-8.3.0_ord00001",
            FilterByKeysQuery.of(
                Map.of(
                    "processInstanceKey",
                    List.of("1", "2"),
                    "rootProcessInstanceKey",
                    List.of("3"))),
            executor);
    inOrder
        .verify(repository)
        .deleteDocumentsById(
            "tasklist-task-8.8.0_ord00001",
            FilterByKeysQuery.of(
                Map.of(
                    "processInstanceId",
                    List.of("1", "2"),
                    "rootProcessInstanceKey",
                    List.of("3"))),
            executor);
    inOrder
        .verify(repository)
        .deleteDocumentsById(
            "operate-list-view-8.3.0_ord00001",
            FilterByKeysQuery.of(
                    Map.of(
                        "processInstanceKey",
                        List.of("1", "2"),
                        "rootProcessInstanceKey",
                        List.of("3")))
                .exclusion("joinRelation", "processInstance"),
            executor);

    // deleting just the process instances themselves
    inOrder
        .verify(repository)
        .deleteDocumentsById(
            "operate-list-view-8.3.0_ord00001",
            FilterByKeysQuery.of(
                    Map.of(
                        "processInstanceKey",
                        List.of("1", "2"),
                        "rootProcessInstanceKey",
                        List.of("3")))
                .inclusion("joinRelation", "processInstance"),
            executor);
    verify(repository).getProcessInstancesNextBatch();
    verifyNoMoreInteractions(repository);
  }
}
