/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.incident;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import io.camunda.exporter.ExporterMetadata;
import io.camunda.exporter.config.ExporterConfiguration.IncidentNotifierConfiguration;
import io.camunda.exporter.metrics.CamundaExporterMetrics;
import io.camunda.exporter.notifier.IncidentNotifier;
import io.camunda.exporter.tasks.incident.IncidentUpdateRepository.ActiveIncident;
import io.camunda.exporter.tasks.incident.IncidentUpdateRepository.Document;
import io.camunda.exporter.tasks.incident.IncidentUpdateRepository.DocumentUpdate;
import io.camunda.exporter.tasks.incident.IncidentUpdateRepository.IncidentBulkUpdate;
import io.camunda.exporter.tasks.incident.IncidentUpdateRepository.IncidentDocument;
import io.camunda.exporter.tasks.incident.IncidentUpdateRepository.PendingIncidentUpdateBatch;
import io.camunda.exporter.tasks.incident.IncidentUpdateRepository.ProcessInstanceDocument;
import io.camunda.search.test.utils.TestObjectMapper;
import io.camunda.webapps.operate.TreePath;
import io.camunda.webapps.schema.descriptors.template.FlowNodeInstanceTemplate;
import io.camunda.webapps.schema.descriptors.template.IncidentTemplate;
import io.camunda.webapps.schema.descriptors.template.ListViewTemplate;
import io.camunda.webapps.schema.entities.incident.IncidentEntity;
import io.camunda.webapps.schema.entities.incident.IncidentState;
import io.camunda.zeebe.exporter.api.ExporterException;
import io.camunda.zeebe.exporter.common.cache.ExporterEntityCache;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class IncidentUpdateTaskTest {
  private static final Logger LOGGER = LoggerFactory.getLogger(IncidentUpdateTaskTest.class);
  private static final Duration TIMEOUT = Duration.ofSeconds(5);

  @AutoClose
  private static final ScheduledThreadPoolExecutor EXECUTOR = new ScheduledThreadPoolExecutor(1);

  private final ExporterMetadata metadata = new ExporterMetadata(TestObjectMapper.objectMapper());
  private final IncidentNotifier incidentNotifier = Mockito.spy(createIncidentNotifier());
  private final IncidentUpdateRepository repository = Mockito.mock(IncidentUpdateRepository.class);
  private final CamundaExporterMetrics metrics = Mockito.mock(CamundaExporterMetrics.class);

  private final ArgumentCaptor<IncidentBulkUpdate> bulkUpdateCaptor =
      ArgumentCaptor.forClass(IncidentBulkUpdate.class);

  @BeforeEach
  void beforeEach() {
    when(repository.getPendingIncidentsBatch(anyLong(), anyInt()))
        .thenReturn(
            CompletableFuture.completedFuture(
                new PendingIncidentUpdateBatch(-1, Collections.emptyMap())));
  }

  @Test
  void shouldReturnNothingDoneOnEmptyPendingBatch() {
    // given
    final var task =
        new IncidentUpdateTask(
            metadata, repository, false, 10, EXECUTOR, incidentNotifier, metrics, LOGGER);

    // when
    final var result = task.execute();

    // then
    assertThat(result).succeedsWithin(TIMEOUT).isEqualTo(0);
    verify(incidentNotifier, times(0)).notifyAsync(any());
  }

  @Test
  void shouldUseMetadataPositionToFetchPendingBatch() {
    // given
    final var task =
        new IncidentUpdateTask(
            metadata, repository, false, 10, EXECUTOR, incidentNotifier, metrics, LOGGER);
    metadata.setLastIncidentUpdatePosition(5);

    // when
    task.execute().toCompletableFuture().join();

    // then
    Mockito.verify(repository).getPendingIncidentsBatch(Mockito.eq(5L), anyInt());
  }

  @Test
  void shouldUseBatchSizeToFetchPendingBatch() {
    // given
    final var task =
        new IncidentUpdateTask(
            metadata, repository, false, 10, EXECUTOR, incidentNotifier, metrics, LOGGER);

    // when
    task.execute().toCompletableFuture().join();

    // then
    Mockito.verify(repository).getPendingIncidentsBatch(anyLong(), Mockito.eq(10));
  }

  private IncidentNotifier createIncidentNotifier() {
    final var config = new IncidentNotifierConfiguration();
    config.setWebhook(null);
    final var processCache = mock(ExporterEntityCache.class);
    final var objectMapper = new ObjectMapper();

    return new IncidentNotifier(processCache, config, EXECUTOR, objectMapper);
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
      when(repository.getPendingIncidentsBatch(anyLong(), anyInt()))
          .thenReturn(
              CompletableFuture.completedFuture(
                  new PendingIncidentUpdateBatch(
                      highestPosition,
                      Map.of(incident.incident().getKey(), IncidentState.ACTIVE))));
      when(repository.getIncidentDocuments(any()))
          .thenReturn(CompletableFuture.completedFuture(Map.of(incident.id(), incident)));
      when(repository.getFlowNodesInListView(any()))
          .thenReturn(
              CompletableFuture.completedFuture(
                  List.of(callActivityFlowNodeInListView, taskFlowNodeInListView)));
      when(repository.getFlowNodeInstances(any()))
          .thenReturn(
              CompletableFuture.completedFuture(List.of(callActivityFlowNode, taskFlowNode)));
      when(repository.getProcessInstances(any()))
          .thenReturn(
              CompletableFuture.completedFuture(
                  List.of(parentProcessInstance, childProcessInstance)));
      when(repository.deletedProcessInstances(any()))
          .thenReturn(CompletableFuture.completedFuture(Set.of()));
      when(repository.analyzeTreePath(any()))
          .thenReturn(CompletableFuture.completedFuture(List.of()));
      when(repository.getActiveIncidentsByTreePaths(any()))
          .thenReturn(CompletableFuture.completedFuture(List.of()));

      when(repository.bulkUpdate(any()))
          .then(
              inv -> {
                final IncidentBulkUpdate update = inv.getArgument(0);
                return CompletableFuture.completedFuture(
                    ImmutableList.<String>builder()
                        .addAll(update.incidentRequests().keySet())
                        .addAll(update.listViewRequests().keySet())
                        .addAll(update.flowNodeInstanceRequests().keySet())
                        .build());
              });
    }

    @Test
    void shouldUpdateMetadataOnSuccess() {
      // given
      final var task =
          new IncidentUpdateTask(
              metadata, repository, false, 10, EXECUTOR, incidentNotifier, metrics, LOGGER);

      // when
      task.execute().toCompletableFuture().join();

      // then
      assertThat(metadata.getLastIncidentUpdatePosition()).isEqualTo(highestPosition);
    }

    @Test
    void shouldReturnNumberOfDocumentsUpdated() {
      // given
      final var task =
          new IncidentUpdateTask(
              metadata, repository, false, 10, EXECUTOR, incidentNotifier, metrics, LOGGER);

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
          new IncidentUpdateTask(
              metadata,
              repository,
              false,
              10,
              EXECUTOR,
              incidentNotifier,
              metrics,
              LOGGER,
              Duration.ZERO);
      when(repository.getIncidentDocuments(any()))
          .thenReturn(CompletableFuture.completedFuture(Map.of()));

      // when
      final var result = task.execute();

      // then
      assertThat(result)
          .failsWithin(TIMEOUT)
          .withThrowableThat()
          .withRootCauseExactlyInstanceOf(ExporterException.class)
          .withMessageContaining("Failed to fetch incidents");
      verify(incidentNotifier, times(0)).notifyAsync(any());
    }

    @Test
    void shouldRetryOnMissingProcessInstance() {
      // given
      final var task =
          new IncidentUpdateTask(
              metadata,
              repository,
              false,
              10,
              EXECUTOR,
              incidentNotifier,
              metrics,
              LOGGER,
              Duration.ZERO);
      when(repository.getProcessInstances(any()))
          .thenReturn(CompletableFuture.completedFuture(List.of()));

      // when
      final var result = task.execute();

      // then
      assertThat(result).succeedsWithin(TIMEOUT).isEqualTo(1);

      verify(metrics).recordIncidentUpdatesRetriesNeeded(1);
      verify(repository, never()).bulkUpdate(any());
      verify(incidentNotifier, never()).notifyAsync(any());
      assertThat(metadata.getLastIncidentUpdatePosition()).isEqualTo(-1L);
    }

    @Test
    void shouldFailOnMissingFlowNodeInstance() {
      // given
      final var task =
          new IncidentUpdateTask(
              metadata,
              repository,
              false,
              10,
              EXECUTOR,
              incidentNotifier,
              metrics,
              LOGGER,
              Duration.ZERO);

      when(repository.getFlowNodesInListView(any()))
          .thenReturn(CompletableFuture.completedFuture(List.of()));

      // when
      final var result = task.execute();

      // then
      assertThat(result)
          .failsWithin(TIMEOUT)
          .withThrowableThat()
          .withRootCauseExactlyInstanceOf(ExporterException.class)
          .withMessageContaining("Flow node instance 2 affected by incident 5");
      verify(incidentNotifier, times(0)).notifyAsync(any());
    }

    @Test
    void shouldFailOnMissingFlowNode() {
      // given
      final var task =
          new IncidentUpdateTask(
              metadata,
              repository,
              false,
              10,
              EXECUTOR,
              incidentNotifier,
              metrics,
              LOGGER,
              Duration.ZERO);

      when(repository.getFlowNodeInstances(any()))
          .thenReturn(CompletableFuture.completedFuture(List.of()));

      // when
      final var result = task.execute();

      // then
      assertThat(result)
          .failsWithin(TIMEOUT)
          .withThrowableThat()
          .withRootCauseExactlyInstanceOf(ExporterException.class)
          .withMessageContaining("Flow node instance 2 affected by incident 5");
      verify(incidentNotifier, times(0)).notifyAsync(any());
    }

    @Test
    void shouldUpdateIncidents() {
      // given
      final var task =
          new IncidentUpdateTask(
              metadata,
              repository,
              false,
              10,
              EXECUTOR,
              incidentNotifier,
              metrics,
              LOGGER,
              Duration.ZERO);

      // when
      final var result = task.execute();

      // then
      assertThat(result).succeedsWithin(TIMEOUT);
      verify(repository).bulkUpdate(bulkUpdateCaptor.capture());
      final var update = bulkUpdateCaptor.getValue();
      assertThat(update.incidentRequests())
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
      final var incident =
          new IncidentEntity()
              .setKey(5L)
              .setId("5")
              .setFlowNodeInstanceKey(4L)
              .setTreePath("PI_1/FNI_2/PI_3/FNI_4")
              .setProcessInstanceKey(3L)
              .setState(IncidentState.PENDING);
      verify(incidentNotifier, times(1)).notifyAsync(List.of(incident));
      verify(metrics).recordIncidentUpdatesProcessed(1);
      verify(metrics).recordIncidentUpdatesDocumentsUpdated(7);
    }

    @Test
    void shouldUpdateListView() {
      // given
      final var task =
          new IncidentUpdateTask(
              metadata,
              repository,
              false,
              10,
              EXECUTOR,
              incidentNotifier,
              metrics,
              LOGGER,
              Duration.ZERO);

      // when
      final var result = task.execute();

      // then
      assertThat(result).succeedsWithin(TIMEOUT);
      verify(repository).bulkUpdate(bulkUpdateCaptor.capture());
      final var update = bulkUpdateCaptor.getValue();
      assertThat(update.listViewRequests())
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
      final var incident =
          new IncidentEntity()
              .setKey(5L)
              .setId("5")
              .setFlowNodeInstanceKey(4L)
              .setTreePath("PI_1/FNI_2/PI_3/FNI_4")
              .setProcessInstanceKey(3L)
              .setState(IncidentState.PENDING);
      verify(incidentNotifier, times(1)).notifyAsync(List.of(incident));
      verify(metrics).recordIncidentUpdatesProcessed(1);
      verify(metrics).recordIncidentUpdatesDocumentsUpdated(7);
    }

    @Test
    void shouldUpdateFlowNode() {
      // given
      final var task =
          new IncidentUpdateTask(
              metadata,
              repository,
              false,
              10,
              EXECUTOR,
              incidentNotifier,
              metrics,
              LOGGER,
              Duration.ZERO);

      // when
      final var result = task.execute();

      // then
      assertThat(result).succeedsWithin(TIMEOUT);
      verify(repository).bulkUpdate(bulkUpdateCaptor.capture());
      final var update = bulkUpdateCaptor.getValue();
      assertThat(update.flowNodeInstanceRequests())
          .hasSize(2)
          .containsEntry(
              "2",
              new DocumentUpdate(
                  "2", "flow-nodes", Map.of(FlowNodeInstanceTemplate.INCIDENT, true), null))
          .containsEntry(
              "4",
              new DocumentUpdate(
                  "4", "flow-nodes", Map.of(FlowNodeInstanceTemplate.INCIDENT, true), null));
      final var incident =
          new IncidentEntity()
              .setKey(5L)
              .setId("5")
              .setFlowNodeInstanceKey(4L)
              .setTreePath("PI_1/FNI_2/PI_3/FNI_4")
              .setProcessInstanceKey(3L)
              .setState(IncidentState.PENDING);
      verify(incidentNotifier, times(1)).notifyAsync(List.of(incident));
      verify(metrics).recordIncidentUpdatesProcessed(1);
      verify(metrics).recordIncidentUpdatesDocumentsUpdated(7);
    }

    @Test
    void shouldResolveIncident() {
      // given
      final var task =
          new IncidentUpdateTask(
              metadata,
              repository,
              false,
              10,
              EXECUTOR,
              incidentNotifier,
              metrics,
              LOGGER,
              Duration.ZERO);
      incidentEntity.setState(IncidentState.ACTIVE);
      when(repository.getActiveIncidentsByTreePaths(any()))
          .thenReturn(
              CompletableFuture.completedFuture(
                  List.of(new ActiveIncident(incident.id(), incidentEntity.getTreePath()))));
      when(repository.getPendingIncidentsBatch(anyLong(), anyInt()))
          .thenReturn(
              CompletableFuture.completedFuture(
                  new PendingIncidentUpdateBatch(
                      highestPosition,
                      Map.of(incident.incident().getKey(), IncidentState.RESOLVED))));

      // when
      final var result = task.execute();

      // then
      assertThat(result).succeedsWithin(TIMEOUT);
      verify(repository).bulkUpdate(bulkUpdateCaptor.capture());
      final var update = bulkUpdateCaptor.getValue();
      assertThat(update.incidentRequests())
          .hasSize(1)
          .containsEntry(
              "5",
              new DocumentUpdate(
                  "5", "incidents", Map.of(IncidentTemplate.STATE, IncidentState.RESOLVED), null));
      assertThat(update.flowNodeInstanceRequests())
          .hasSize(2)
          .containsEntry(
              "2",
              new DocumentUpdate(
                  "2", "flow-nodes", Map.of(FlowNodeInstanceTemplate.INCIDENT, false), null))
          .containsEntry(
              "4",
              new DocumentUpdate(
                  "4", "flow-nodes", Map.of(FlowNodeInstanceTemplate.INCIDENT, false), null));
      assertThat(update.listViewRequests())
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

      verify(incidentNotifier, times(0)).notifyAsync(any());
      verify(metrics).recordIncidentUpdatesProcessed(1);
      verify(metrics).recordIncidentUpdatesDocumentsUpdated(7);
    }

    @Test
    void shouldNotMarkIncidentFalseIfMoreActiveIncidents() {
      // given - we have another active incident with an overlapping tree path, but only covering
      // process instance
      final var task =
          new IncidentUpdateTask(
              metadata,
              repository,
              false,
              10,
              EXECUTOR,
              incidentNotifier,
              metrics,
              LOGGER,
              Duration.ZERO);
      incidentEntity.setState(IncidentState.ACTIVE);
      when(repository.getActiveIncidentsByTreePaths(any()))
          .thenReturn(
              CompletableFuture.completedFuture(
                  List.of(
                      new ActiveIncident("30", new TreePath().startTreePath("1").toString()),
                      new ActiveIncident(incident.id(), incidentEntity.getTreePath()))));
      when(repository.getPendingIncidentsBatch(anyLong(), anyInt()))
          .thenReturn(
              CompletableFuture.completedFuture(
                  new PendingIncidentUpdateBatch(
                      highestPosition,
                      Map.of(incident.incident().getKey(), IncidentState.RESOLVED))));

      // when
      final var result = task.execute();

      // then - we should mark the child process instance, the call activity, and the task as
      // incident free, but NOT the parent process instance as it still has an active incident
      assertThat(result).succeedsWithin(TIMEOUT);
      verify(repository).bulkUpdate(bulkUpdateCaptor.capture());
      final var update = bulkUpdateCaptor.getValue();
      assertThat(update.incidentRequests())
          .hasSize(1)
          .containsEntry(
              "5",
              new DocumentUpdate(
                  "5", "incidents", Map.of(IncidentTemplate.STATE, IncidentState.RESOLVED), null));
      assertThat(update.flowNodeInstanceRequests())
          .hasSize(2)
          .containsEntry(
              "2",
              new DocumentUpdate(
                  "2", "flow-nodes", Map.of(FlowNodeInstanceTemplate.INCIDENT, false), null))
          .containsEntry(
              "4",
              new DocumentUpdate(
                  "4", "flow-nodes", Map.of(FlowNodeInstanceTemplate.INCIDENT, false), null));
      assertThat(update.listViewRequests())
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
      verify(metrics).recordIncidentUpdatesProcessed(1);
      verify(metrics).recordIncidentUpdatesDocumentsUpdated(6);
    }

    @Test
    void shouldIgnoreDeletedProcessInstance() {
      // given
      final var task =
          new IncidentUpdateTask(
              metadata,
              repository,
              false,
              10,
              EXECUTOR,
              incidentNotifier,
              metrics,
              LOGGER,
              Duration.ZERO);
      when(repository.getProcessInstances(any()))
          .thenReturn(CompletableFuture.completedFuture(List.of(parentProcessInstance)));
      final long deletedProcessInstanceKey = childProcessInstance.key();
      when(repository.deletedProcessInstances(Set.of(deletedProcessInstanceKey)))
          .thenReturn(CompletableFuture.completedFuture(Set.of(deletedProcessInstanceKey)));

      // when
      final var result = task.execute();

      // then
      assertThat(result).succeedsWithin(TIMEOUT);
      verify(repository).bulkUpdate(bulkUpdateCaptor.capture());
      final var update = bulkUpdateCaptor.getValue();
      assertThat(update.listViewRequests()).isEmpty();
      assertThat(update.incidentRequests()).isEmpty();
      assertThat(update.flowNodeInstanceRequests()).isEmpty();
      verify(incidentNotifier, times(0)).notifyAsync(any());
      verify(metrics).recordIncidentUpdatesProcessed(1);
      verify(metrics).recordIncidentUpdatesDocumentsUpdated(0);
    }
  }
}
