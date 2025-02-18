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
import io.camunda.exporter.tasks.incident.IncidentUpdateRepository.ActiveIncident;
import io.camunda.exporter.tasks.incident.IncidentUpdateRepository.Document;
import io.camunda.exporter.tasks.incident.IncidentUpdateRepository.DocumentUpdate;
import io.camunda.exporter.tasks.incident.IncidentUpdateRepository.IncidentDocument;
import io.camunda.exporter.tasks.incident.IncidentUpdateRepository.NoopIncidentUpdateRepository;
import io.camunda.exporter.tasks.incident.IncidentUpdateRepository.PendingIncidentUpdateBatch;
import io.camunda.exporter.tasks.incident.IncidentUpdateRepository.ProcessInstanceDocument;
import io.camunda.exporter.utils.TestObjectMapper;
import io.camunda.webapps.operate.TreePath;
import io.camunda.webapps.schema.descriptors.operate.template.FlowNodeInstanceTemplate;
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
import java.util.concurrent.ScheduledThreadPoolExecutor;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class IncidentUpdateTaskTest {
  private static final Logger LOGGER = LoggerFactory.getLogger(IncidentUpdateTaskTest.class);
  private static final Duration TIMEOUT = Duration.ofSeconds(5);

  @AutoClose
  private static final ScheduledThreadPoolExecutor EXECUTOR = new ScheduledThreadPoolExecutor(1);

  private final ExporterMetadata metadata = new ExporterMetadata(TestObjectMapper.objectMapper());
  private final TestRepository repository = Mockito.spy(new TestRepository());

  @Test
  void shouldReturnNothingDoneOnEmptyPendingBatch() {
    // given
    final var task = new IncidentUpdateTask(metadata, repository, false, 10, EXECUTOR, LOGGER);

    // when
    final var result = task.execute();

    // then
    assertThat(result).succeedsWithin(TIMEOUT).isEqualTo(0);
  }

  @Test
  void shouldUseMetadataPositionToFetchPendingBatch() {
    // given
    final var task = new IncidentUpdateTask(metadata, repository, false, 10, EXECUTOR, LOGGER);
    metadata.setLastIncidentUpdatePosition(5);

    // when
    task.execute().toCompletableFuture().join();

    // then
    Mockito.verify(repository).getPendingIncidentsBatch(Mockito.eq(5L), Mockito.anyInt());
  }

  @Test
  void shouldUseBatchSizeToFetchPendingBatch() {
    // given
    final var task = new IncidentUpdateTask(metadata, repository, false, 10, EXECUTOR, LOGGER);

    // when
    task.execute().toCompletableFuture().join();

    // then
    Mockito.verify(repository).getPendingIncidentsBatch(Mockito.anyLong(), Mockito.eq(10));
  }

  private static final class TestRepository extends NoopIncidentUpdateRepository {
    private CompletableFuture<PendingIncidentUpdateBatch> batch;
    private CompletableFuture<Map<String, IncidentDocument>> incidents;
    private CompletableFuture<Collection<ProcessInstanceDocument>> processInstances;
    private CompletableFuture<Collection<ActiveIncident>> activeIncidentsByTreePaths;
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
    public CompletionStage<Collection<ActiveIncident>> getActiveIncidentsByTreePaths(
        final Collection<String> treePathTerms) {
      return activeIncidentsByTreePaths != null
          ? activeIncidentsByTreePaths
          : super.getActiveIncidentsByTreePaths(treePathTerms);
    }
  }

  /**
   * All tests in this class have the following set up:
   *
   * <p>We deployed a process consisting of a call activity, which calls a process with a single
   * task.
   *
   * <p>An instance of that parent process instance (1) has a call activity instance flow node (2)
   * with a child process instance (3), with a task flow node (4).
   *
   * <p>There is a single incident update by default (5) which is being created.
   *
   * <p>The pending update has a position of 10.
   */
  @Nested
  final class IncidentTreePathTest {
    private final int highestPosition = 10;

    private final ProcessInstanceDocument parentProcessInstance =
        new ProcessInstanceDocument(
            "1", "list-view", 1, new TreePath().startTreePath(1).toString());
    private final ProcessInstanceDocument childProcessInstance =
        new ProcessInstanceDocument(
            "3",
            "list-view",
            3,
            new TreePath()
                .startTreePath(1)
                .appendFlowNodeInstance(2)
                .appendProcessInstance(3)
                .toString());
    private final Document callActivityFlowNode = new Document("2", "flow-nodes");
    private final Document callActivityFlowNodeInListView = new Document("2", "list-view");
    private final Document taskFlowNode = new Document("4", "flow-nodes");
    private final Document taskFlowNodeInListView = new Document("4", "list-view");
    private final IncidentEntity incidentEntity =
        new IncidentEntity()
            .setKey(5L)
            .setId("5")
            .setState(IncidentState.PENDING)
            .setProcessInstanceKey(3L)
            .setFlowNodeInstanceKey(4L)
            .setTreePath(
                new TreePath()
                    .startTreePath(1)
                    .appendFlowNodeInstance(2)
                    .appendProcessInstance(3)
                    .appendFlowNodeInstance(4)
                    .toString());
    private final IncidentDocument incident =
        new IncidentDocument("5", "incidents", incidentEntity);

    @BeforeEach
    void beforeEach() {
      repository.batch =
          CompletableFuture.completedFuture(
              new PendingIncidentUpdateBatch(
                  highestPosition, Map.of(incident.incident().getKey(), IncidentState.ACTIVE)));
      repository.incidents = CompletableFuture.completedFuture(Map.of(incident.id(), incident));
      repository.flowNodesInListView =
          CompletableFuture.completedFuture(
              List.of(callActivityFlowNodeInListView, taskFlowNodeInListView));
      repository.flowNodeInstances =
          CompletableFuture.completedFuture(List.of(callActivityFlowNode, taskFlowNode));
      repository.processInstances =
          CompletableFuture.completedFuture(List.of(parentProcessInstance, childProcessInstance));
    }

    @Test
    void shouldUpdateMetadataOnSuccess() {
      // given
      final var task = new IncidentUpdateTask(metadata, repository, false, 10, EXECUTOR, LOGGER);

      // when
      task.execute().toCompletableFuture().join();

      // then
      assertThat(metadata.getLastIncidentUpdatePosition()).isEqualTo(highestPosition);
    }

    @Test
    void shouldReturnNumberOfDocumentsUpdated() {
      // given
      final var task = new IncidentUpdateTask(metadata, repository, false, 10, EXECUTOR, LOGGER);

      // when
      final var result = task.execute();

      // then
      assertThat(result)
          .succeedsWithin(TIMEOUT)
          .asInstanceOf(InstanceOfAssertFactories.INTEGER)
          .isEqualTo(7);
    }

    @Test
    void shouldFailOnMissingIncident() {
      // given
      final var task =
          new IncidentUpdateTask(metadata, repository, false, 10, EXECUTOR, LOGGER, Duration.ZERO);
      repository.incidents = CompletableFuture.completedFuture(Map.of());

      // when
      final var result = task.execute();

      // then
      assertThat(result)
          .failsWithin(TIMEOUT)
          .withThrowableThat()
          .withRootCauseExactlyInstanceOf(ExporterException.class)
          .withMessageContaining("Failed to fetch incidents");
    }

    @Test
    void shouldFailOnMissingProcessInstance() {
      // given
      final var task =
          new IncidentUpdateTask(metadata, repository, false, 10, EXECUTOR, LOGGER, Duration.ZERO);
      repository.processInstances = CompletableFuture.completedFuture(List.of());

      // when
      final var result = task.execute();

      // then
      assertThat(result)
          .failsWithin(TIMEOUT)
          .withThrowableThat()
          .withRootCauseExactlyInstanceOf(ExporterException.class)
          .withMessageContaining("Process instance 3 is not yet imported for incident 5");
    }

    @Test
    void shouldFailOnMissingFlowNodeInstance() {
      // given
      final var task =
          new IncidentUpdateTask(metadata, repository, false, 10, EXECUTOR, LOGGER, Duration.ZERO);
      repository.flowNodesInListView = CompletableFuture.completedFuture(List.of());

      // when
      final var result = task.execute();

      // then
      assertThat(result)
          .failsWithin(TIMEOUT)
          .withThrowableThat()
          .withRootCauseExactlyInstanceOf(ExporterException.class)
          .withMessageContaining("Flow node instance 2 affected by incident 5");
    }

    @Test
    void shouldFailOnMissingFlowNode() {
      // given
      final var task =
          new IncidentUpdateTask(metadata, repository, false, 10, EXECUTOR, LOGGER, Duration.ZERO);
      repository.flowNodeInstances = CompletableFuture.completedFuture(List.of());

      // when
      final var result = task.execute();

      // then
      assertThat(result)
          .failsWithin(TIMEOUT)
          .withThrowableThat()
          .withRootCauseExactlyInstanceOf(ExporterException.class)
          .withMessageContaining("Flow node instance 2 affected by incident 5");
    }

    @Test
    void shouldUpdateIncidents() {
      // given
      final var task =
          new IncidentUpdateTask(metadata, repository, false, 10, EXECUTOR, LOGGER, Duration.ZERO);

      // when
      final var result = task.execute();

      // then
      assertThat(result).succeedsWithin(TIMEOUT);
      assertThat(repository.updated.incidentRequests())
          .hasSize(1)
          .containsEntry(
              "5",
              new DocumentUpdate(
                  "5",
                  "incidents",
                  Map.of(
                      IncidentTemplate.STATE,
                      IncidentState.ACTIVE,
                      IncidentTemplate.TREE_PATH,
                      "PI_1/FNI_2/PI_3/FNI_4"),
                  null));
    }

    @Test
    void shouldUpdateListView() {
      // given
      final var task =
          new IncidentUpdateTask(metadata, repository, false, 10, EXECUTOR, LOGGER, Duration.ZERO);

      // when
      final var result = task.execute();

      // then
      assertThat(result).succeedsWithin(TIMEOUT);
      assertThat(repository.updated.listViewRequests())
          .hasSize(4)
          .containsEntry(
              "1",
              new DocumentUpdate("1", "list-view", Map.of(ListViewTemplate.INCIDENT, true), "1"))
          .containsEntry(
              "2",
              new DocumentUpdate("2", "list-view", Map.of(ListViewTemplate.INCIDENT, true), "1"))
          .containsEntry(
              "3",
              new DocumentUpdate("3", "list-view", Map.of(ListViewTemplate.INCIDENT, true), "3"))
          .containsEntry(
              "4",
              new DocumentUpdate("4", "list-view", Map.of(ListViewTemplate.INCIDENT, true), "3"));
    }

    @Test
    void shouldUpdateFlowNode() {
      // given
      final var task =
          new IncidentUpdateTask(metadata, repository, false, 10, EXECUTOR, LOGGER, Duration.ZERO);

      // when
      final var result = task.execute();

      // then
      assertThat(result).succeedsWithin(TIMEOUT);
      assertThat(repository.updated.flowNodeInstanceRequests())
          .hasSize(2)
          .containsEntry(
              "2",
              new DocumentUpdate(
                  "2", "flow-nodes", Map.of(FlowNodeInstanceTemplate.INCIDENT, true), null))
          .containsEntry(
              "4",
              new DocumentUpdate(
                  "4", "flow-nodes", Map.of(FlowNodeInstanceTemplate.INCIDENT, true), null));
    }

    @Test
    void shouldResolveIncident() {
      // given
      final var task =
          new IncidentUpdateTask(metadata, repository, false, 10, EXECUTOR, LOGGER, Duration.ZERO);
      incidentEntity.setState(IncidentState.ACTIVE);
      repository.activeIncidentsByTreePaths =
          CompletableFuture.completedFuture(
              List.of(new ActiveIncident(incident.id(), incidentEntity.getTreePath())));
      repository.batch =
          CompletableFuture.completedFuture(
              new PendingIncidentUpdateBatch(
                  highestPosition, Map.of(incident.incident().getKey(), IncidentState.RESOLVED)));

      // when
      final var result = task.execute();

      // then
      assertThat(result).succeedsWithin(TIMEOUT);
      assertThat(repository.updated.incidentRequests())
          .hasSize(1)
          .containsEntry(
              "5",
              new DocumentUpdate(
                  "5", "incidents", Map.of(IncidentTemplate.STATE, IncidentState.RESOLVED), null));
      assertThat(repository.updated.flowNodeInstanceRequests())
          .hasSize(2)
          .containsEntry(
              "2",
              new DocumentUpdate(
                  "2", "flow-nodes", Map.of(FlowNodeInstanceTemplate.INCIDENT, false), null))
          .containsEntry(
              "4",
              new DocumentUpdate(
                  "4", "flow-nodes", Map.of(FlowNodeInstanceTemplate.INCIDENT, false), null));
      assertThat(repository.updated.listViewRequests())
          .hasSize(4)
          .containsEntry(
              "1",
              new DocumentUpdate("1", "list-view", Map.of(ListViewTemplate.INCIDENT, false), "1"))
          .containsEntry(
              "2",
              new DocumentUpdate("2", "list-view", Map.of(ListViewTemplate.INCIDENT, false), "1"))
          .containsEntry(
              "3",
              new DocumentUpdate("3", "list-view", Map.of(ListViewTemplate.INCIDENT, false), "3"))
          .containsEntry(
              "4",
              new DocumentUpdate("4", "list-view", Map.of(ListViewTemplate.INCIDENT, false), "3"));
    }

    @Test
    void shouldNotMarkIncidentFalseIfMoreActiveIncidents() {
      // given - we have another active incident with an overlapping tree path, but only covering
      // process instance
      final var task =
          new IncidentUpdateTask(metadata, repository, false, 10, EXECUTOR, LOGGER, Duration.ZERO);
      incidentEntity.setState(IncidentState.ACTIVE);
      repository.activeIncidentsByTreePaths =
          CompletableFuture.completedFuture(
              List.of(
                  new ActiveIncident("30", new TreePath().startTreePath("1").toString()),
                  new ActiveIncident(incident.id(), incidentEntity.getTreePath())));
      repository.batch =
          CompletableFuture.completedFuture(
              new PendingIncidentUpdateBatch(
                  highestPosition, Map.of(incident.incident().getKey(), IncidentState.RESOLVED)));

      // when
      final var result = task.execute();

      // then - we should mark the child process instance, the call activity, and the task as
      // incident free, but NOT the parent process instance as it still has an active incident
      assertThat(result).succeedsWithin(TIMEOUT);
      assertThat(repository.updated.incidentRequests())
          .hasSize(1)
          .containsEntry(
              "5",
              new DocumentUpdate(
                  "5", "incidents", Map.of(IncidentTemplate.STATE, IncidentState.RESOLVED), null));
      assertThat(repository.updated.flowNodeInstanceRequests())
          .hasSize(2)
          .containsEntry(
              "2",
              new DocumentUpdate(
                  "2", "flow-nodes", Map.of(FlowNodeInstanceTemplate.INCIDENT, false), null))
          .containsEntry(
              "4",
              new DocumentUpdate(
                  "4", "flow-nodes", Map.of(FlowNodeInstanceTemplate.INCIDENT, false), null));
      assertThat(repository.updated.listViewRequests())
          .hasSize(3)
          .containsEntry(
              "2",
              new DocumentUpdate("2", "list-view", Map.of(ListViewTemplate.INCIDENT, false), "1"))
          .containsEntry(
              "3",
              new DocumentUpdate("3", "list-view", Map.of(ListViewTemplate.INCIDENT, false), "3"))
          .containsEntry(
              "4",
              new DocumentUpdate("4", "list-view", Map.of(ListViewTemplate.INCIDENT, false), "3"));
    }

    @Test
    void shouldIgnoreDeletedProcessInstance() {
      // given
      final var task =
          new IncidentUpdateTask(metadata, repository, false, 10, EXECUTOR, LOGGER, Duration.ZERO);
      repository.processInstances =
          CompletableFuture.completedFuture(List.of(parentProcessInstance));
      repository.wasProcessInstanceDeleted = CompletableFuture.completedFuture(true);

      // when
      final var result = task.execute();

      // then
      assertThat(result).succeedsWithin(TIMEOUT);
      assertThat(repository.updated.listViewRequests()).isEmpty();
      assertThat(repository.updated.incidentRequests()).isEmpty();
      assertThat(repository.updated.flowNodeInstanceRequests()).isEmpty();
    }
  }
}
