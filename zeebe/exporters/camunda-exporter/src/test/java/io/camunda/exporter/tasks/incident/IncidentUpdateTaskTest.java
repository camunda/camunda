/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.incident;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.exporter.ExporterMetadata;
import io.camunda.exporter.tasks.incident.IncidentUpdateRepository.Document;
import io.camunda.exporter.tasks.incident.IncidentUpdateRepository.DocumentUpdate;
import io.camunda.exporter.tasks.incident.IncidentUpdateRepository.IncidentDocument;
import io.camunda.exporter.tasks.incident.IncidentUpdateRepository.NoopIncidentUpdateRepository;
import io.camunda.exporter.tasks.incident.IncidentUpdateRepository.PendingIncidentUpdateBatch;
import io.camunda.exporter.tasks.incident.IncidentUpdateRepository.ProcessInstanceDocument;
import io.camunda.webapps.operate.TreePath;
import io.camunda.webapps.schema.descriptors.operate.template.IncidentTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.ListViewTemplate;
import io.camunda.webapps.schema.entities.operate.IncidentEntity;
import io.camunda.webapps.schema.entities.operate.IncidentState;
import io.camunda.zeebe.exporter.api.ExporterException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class IncidentUpdateTaskTest {
  private static final Logger LOGGER = LoggerFactory.getLogger(IncidentUpdateTaskTest.class);
  private final ExporterMetadata metadata = new ExporterMetadata();
  private final TestRepository repository = Mockito.spy(new TestRepository());

  @Test
  void shouldReturnNothingDoneOnEmptyPendingBatch() {
    // given
    final var task = new IncidentUpdateTask(metadata, repository, false, 10, LOGGER);

    // when
    final var result = task.execute();

    // then
    assertThat(result).succeedsWithin(Duration.ZERO).isEqualTo(0);
  }

  @Test
  void shouldUseMetadataPositionToFetchPendingBatch() {
    // given
    final var task = new IncidentUpdateTask(metadata, repository, false, 10, LOGGER);
    metadata.setLastIncidentUpdatePosition(5);

    // when
    task.execute().toCompletableFuture().join();

    // then
    Mockito.verify(repository).getPendingIncidentsBatch(Mockito.eq(5L), Mockito.anyInt());
  }

  @Test
  void shouldUseBatchSizeToFetchPendingBatch() {
    // given
    final var task = new IncidentUpdateTask(metadata, repository, false, 10, LOGGER);

    // when
    task.execute().toCompletableFuture().join();

    // then
    Mockito.verify(repository).getPendingIncidentsBatch(Mockito.anyLong(), Mockito.eq(10));
  }

  private static final class TestRepository extends NoopIncidentUpdateRepository {
    private CompletableFuture<PendingIncidentUpdateBatch> batch;
    private CompletableFuture<Map<String, IncidentDocument>> incidents;
    private CompletableFuture<Collection<ProcessInstanceDocument>> processInstances;
    private CompletableFuture<List<ActiveIncident>> activeIncidentsByTreePaths;
    private CompletableFuture<Integer> bulkUpdate;
    private CompletableFuture<Collection<Document>> flowNodesInListView;
    private CompletableFuture<Collection<Document>> flowNodeInstances;
    private CompletableFuture<Boolean> wasProcessInstanceDeleted;

    private IncidentBulkUpdate updated;

    @Override
    public CompletionStage<PendingIncidentUpdateBatch> getPendingIncidentsBatch(
        final long fromPosition, final int size) {
      return batch != null ? batch : super.getPendingIncidentsBatch(fromPosition, size);
    }

    @Override
    public CompletionStage<Map<String, IncidentDocument>> getIncidentDocuments(
        final List<String> incidentIds) {
      return incidents != null ? incidents : super.getIncidentDocuments(incidentIds);
    }

    @Override
    public CompletionStage<Collection<Document>> getFlowNodesInListView(
        final List<String> flowNodeKeys) {
      return flowNodesInListView != null
          ? flowNodesInListView
          : super.getFlowNodesInListView(flowNodeKeys);
    }

    @Override
    public CompletionStage<Collection<Document>> getFlowNodeInstances(
        final List<String> flowNodeKeys) {
      return flowNodeInstances != null
          ? flowNodeInstances
          : super.getFlowNodeInstances(flowNodeKeys);
    }

    @Override
    public CompletionStage<Collection<ProcessInstanceDocument>> getProcessInstances(
        final List<String> processInstanceIds) {
      return processInstances != null
          ? processInstances
          : super.getProcessInstances(processInstanceIds);
    }

    @Override
    public CompletionStage<Boolean> wasProcessInstanceDeleted(final long processInstanceKey) {
      return wasProcessInstanceDeleted != null
          ? wasProcessInstanceDeleted
          : super.wasProcessInstanceDeleted(processInstanceKey);
    }

    @Override
    public CompletionStage<Integer> bulkUpdate(final IncidentBulkUpdate update) {
      updated = update;
      return bulkUpdate != null
          ? bulkUpdate
          : CompletableFuture.completedFuture(
              update.incidentRequests().size()
                  + update.listViewRequests().size()
                  + update.flowNodeInstanceRequests().size());
    }

    @Override
    public CompletionStage<List<String>> analyzeTreePath(final String treePath) {
      return CompletableFuture.completedFuture(Arrays.asList(treePath.split("/")));
    }

    @Override
    public CompletionStage<List<ActiveIncident>> getActiveIncidentsByTreePaths(
        final List<String> treePathTerms) {
      return activeIncidentsByTreePaths != null
          ? activeIncidentsByTreePaths
          : super.getActiveIncidentsByTreePaths(treePathTerms);
    }
  }

  /**
   * All tests in this class have the following set up:
   *
   * <p>A single incident (3), a single process instance (1), and a single flow node (2). The
   * incident was pending, and now has a single update to set it active.
   *
   * <p>The pending update has a position of 10.
   */
  @Nested
  final class SingleIncidentTest {
    private final int highestPosition = 10;

    private final ProcessInstanceDocument processInstance =
        new ProcessInstanceDocument(
            "1", "list-view", 1, new TreePath().startTreePath(1).toString());
    private final Document flowNodeInstance = new Document("2", "flow-nodes");
    private final Document flowNodeInstanceInListView = new Document("2", "list-view");
    private final IncidentEntity incidentEntity =
        new IncidentEntity()
            .setKey(3L)
            .setId("3")
            .setState(IncidentState.PENDING)
            .setProcessInstanceKey(1L)
            .setFlowNodeInstanceKey(2L)
            .setTreePath(new TreePath().startTreePath(1).appendFlowNodeInstance(2).toString());
    private final IncidentDocument incident =
        new IncidentDocument("3", "incidents", incidentEntity);

    @BeforeEach
    void beforeEach() {
      repository.batch =
          CompletableFuture.completedFuture(
              new PendingIncidentUpdateBatch(
                  highestPosition, Map.of(incident.incident().getKey(), IncidentState.ACTIVE)));
      repository.incidents = CompletableFuture.completedFuture(Map.of(incident.id(), incident));
      repository.flowNodesInListView =
          CompletableFuture.completedFuture(List.of(flowNodeInstanceInListView));
      repository.flowNodeInstances = CompletableFuture.completedFuture(List.of(flowNodeInstance));
      repository.processInstances = CompletableFuture.completedFuture(List.of(processInstance));
    }

    @Test
    void shouldUpdateMetadataOnSuccess() {
      // given
      final var task = new IncidentUpdateTask(metadata, repository, false, 10, LOGGER);

      // when
      task.execute().toCompletableFuture().join();

      // then
      assertThat(metadata.getLastIncidentUpdatePosition()).isEqualTo(highestPosition);
    }

    @Test
    void shouldReturnNumberOfDocumentsUpdated() {
      // given
      final var task = new IncidentUpdateTask(metadata, repository, false, 10, LOGGER);

      // when
      final var result = task.execute();

      // then
      assertThat(result)
          .succeedsWithin(Duration.ZERO)
          .asInstanceOf(InstanceOfAssertFactories.INTEGER)
          .isEqualTo(4);
    }

    @Test
    void shouldFailOnMissingIncident() {
      // given
      final var task =
          new IncidentUpdateTask(metadata, repository, false, 10, LOGGER, Duration.ZERO);
      repository.incidents = CompletableFuture.completedFuture(Map.of());

      // when
      final var result = task.execute();

      // then
      assertThat(result)
          .failsWithin(Duration.ZERO)
          .withThrowableThat()
          .withRootCauseExactlyInstanceOf(ExporterException.class)
          .withMessageContaining("Failed to fetch incidents");
    }

    @Test
    void shouldFailOnMissingProcessInstance() {
      // given
      final var task =
          new IncidentUpdateTask(metadata, repository, false, 10, LOGGER, Duration.ZERO);
      repository.processInstances = CompletableFuture.completedFuture(List.of());

      // when
      final var result = task.execute();

      // then
      assertThat(result)
          .failsWithin(Duration.ZERO)
          .withThrowableThat()
          .withRootCauseExactlyInstanceOf(ExporterException.class)
          .withMessageContaining("Process instance 1 is not yet imported for incident 3");
    }

    @Test
    void shouldFailOnMissingFlowNodeInstance() {
      // given
      final var task =
          new IncidentUpdateTask(metadata, repository, false, 10, LOGGER, Duration.ZERO);
      repository.flowNodesInListView = CompletableFuture.completedFuture(List.of());

      // when
      final var result = task.execute();

      // then
      assertThat(result)
          .failsWithin(Duration.ZERO)
          .withThrowableThat()
          .withRootCauseExactlyInstanceOf(ExporterException.class)
          .withMessageContaining("Flow node instance 2 affected by incident 3");
    }

    @Test
    void shouldFailOnMissingFlowNode() {
      // given
      final var task =
          new IncidentUpdateTask(metadata, repository, false, 10, LOGGER, Duration.ZERO);
      repository.flowNodeInstances = CompletableFuture.completedFuture(List.of());

      // when
      final var result = task.execute();

      // then
      assertThat(result)
          .failsWithin(Duration.ZERO)
          .withThrowableThat()
          .withRootCauseExactlyInstanceOf(ExporterException.class)
          .withMessageContaining("Flow node instance 2 affected by incident 3");
    }

    @Test
    void shouldUpdateIncidents() {
      // given
      final var task =
          new IncidentUpdateTask(metadata, repository, false, 10, LOGGER, Duration.ZERO);

      // when
      final var result = task.execute();

      // then
      assertThat(result).succeedsWithin(Duration.ZERO);
      assertThat(repository.updated.incidentRequests())
          .hasSize(1)
          .containsEntry(
              "3",
              new DocumentUpdate(
                  "3",
                  "incidents",
                  Map.of(
                      IncidentTemplate.STATE,
                      IncidentState.ACTIVE,
                      IncidentTemplate.TREE_PATH,
                      "PI_1/FNI_2"),
                  "1"));
    }

    @Test
    void shouldUpdateListView() {
      // given
      final var task =
          new IncidentUpdateTask(metadata, repository, false, 10, LOGGER, Duration.ZERO);

      // when
      final var result = task.execute();

      // then
      assertThat(result).succeedsWithin(Duration.ZERO);
      assertThat(repository.updated.listViewRequests())
          .hasSize(2)
          .containsEntry(
              "1",
              new DocumentUpdate("1", "list-view", Map.of(ListViewTemplate.INCIDENT, true), "1"))
          .containsEntry(
              "2",
              new DocumentUpdate("2", "list-view", Map.of(ListViewTemplate.INCIDENT, true), "1"));
    }

    @Test
    void shouldUpdateFlowNode() {
      // given
      final var task =
          new IncidentUpdateTask(metadata, repository, false, 10, LOGGER, Duration.ZERO);

      // when
      final var result = task.execute();

      // then
      assertThat(result).succeedsWithin(Duration.ZERO);
      assertThat(repository.updated.flowNodeInstanceRequests())
          .hasSize(1)
          .containsEntry(
              "2",
              new DocumentUpdate("2", "flow-nodes", Map.of(ListViewTemplate.INCIDENT, true), "1"));
    }
  }
}
