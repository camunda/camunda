/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.historydeletion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atMostOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.exporter.tasks.utils.TestExporterResourceProvider;
import io.camunda.webapps.schema.descriptors.ProcessInstanceDependant;
import io.camunda.webapps.schema.descriptors.index.DecisionIndex;
import io.camunda.webapps.schema.descriptors.index.DecisionRequirementsIndex;
import io.camunda.webapps.schema.descriptors.index.HistoryDeletionIndex;
import io.camunda.webapps.schema.descriptors.index.ProcessIndex;
import io.camunda.webapps.schema.descriptors.template.AuditLogTemplate;
import io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate;
import io.camunda.webapps.schema.descriptors.template.ListViewTemplate;
import io.camunda.webapps.schema.descriptors.template.OperationTemplate;
import io.camunda.webapps.schema.entities.HistoryDeletionEntity;
import io.camunda.zeebe.protocol.record.value.HistoryDeletionType;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class HistoryDeletionJobTest {

  private static final Logger LOGGER = LoggerFactory.getLogger(HistoryDeletionJobTest.class);
  private HistoryDeletionJob job;
  private HistoryDeletionRepository repository;
  private final Executor executor = Runnable::run;
  private List<ProcessInstanceDependant> dependants;
  private HistoryDeletionIndex historyDeletionIndex;
  private ListViewTemplate listViewTemplate;
  private ProcessIndex processIndex;
  private DecisionInstanceTemplate decisionInstanceTemplate;
  private DecisionRequirementsIndex decisionRequirementsIndex;
  private DecisionIndex decisionIndex;

  @BeforeEach
  void setUp() {
    repository = mock(HistoryDeletionRepository.class);
    final TestExporterResourceProvider resourceProvider =
        new TestExporterResourceProvider("", true);
    dependants =
        resourceProvider.getIndexTemplateDescriptors().stream()
            .filter(ProcessInstanceDependant.class::isInstance)
            .map(ProcessInstanceDependant.class::cast)
            .toList();
    historyDeletionIndex = resourceProvider.getIndexDescriptor(HistoryDeletionIndex.class);
    listViewTemplate = resourceProvider.getIndexTemplateDescriptor(ListViewTemplate.class);
    processIndex = resourceProvider.getIndexDescriptor(ProcessIndex.class);
    decisionInstanceTemplate =
        resourceProvider.getIndexTemplateDescriptor(DecisionInstanceTemplate.class);
    decisionRequirementsIndex =
        resourceProvider.getIndexDescriptor(DecisionRequirementsIndex.class);
    decisionIndex = resourceProvider.getIndexDescriptor(DecisionIndex.class);
    job = new HistoryDeletionJob(dependants, executor, repository, LOGGER, resourceProvider);
  }

  @Test
  void shouldReturnZeroIfNothingToDelete() {
    // given empty batch
    when(repository.getNextBatch())
        .thenReturn(CompletableFuture.completedFuture(new HistoryDeletionBatch(List.of())));

    // when
    final var count = job.execute().toCompletableFuture().join();

    // then
    assertThat(count).isEqualTo(0);
  }

  @Test
  void shouldDeleteProcessInstanceHistory() {
    // given
    final var entity1 =
        new HistoryDeletionEntity()
            .setId("id1")
            .setResourceKey(1L)
            .setResourceType(HistoryDeletionType.PROCESS_INSTANCE)
            .setBatchOperationKey(2L)
            .setPartitionId(1);
    final var entity2 =
        new HistoryDeletionEntity()
            .setId("id2")
            .setResourceKey(2L)
            .setResourceType(HistoryDeletionType.PROCESS_INSTANCE)
            .setBatchOperationKey(2L)
            .setPartitionId(1);
    when(repository.getNextBatch())
        .thenReturn(
            CompletableFuture.completedFuture(new HistoryDeletionBatch(List.of(entity1, entity2))));
    when(repository.deleteDocumentsByField(anyString(), anyString(), anyList()))
        .thenReturn(CompletableFuture.completedFuture(List.of()));
    when(repository.deleteDocumentsById(anyString(), anyList()))
        .thenReturn(CompletableFuture.completedFuture(0));

    // when
    job.execute().toCompletableFuture().join();

    // then
    dependants.stream()
        .filter(t -> !(t instanceof OperationTemplate))
        .filter(t -> !(t instanceof AuditLogTemplate))
        .forEach(
            dependant ->
                verify(repository)
                    .deleteDocumentsByField(
                        dependant.getFullQualifiedName() + "*",
                        dependant.getProcessInstanceDependantField(),
                        List.of(entity1.getResourceKey(), entity2.getResourceKey())));
    verify(repository)
        .deleteDocumentsByField(
            listViewTemplate.getIndexPattern(),
            ListViewTemplate.PROCESS_INSTANCE_KEY,
            List.of(entity1.getResourceKey(), entity2.getResourceKey()));
    verify(repository)
        .deleteDocumentsById(
            historyDeletionIndex.getFullQualifiedName(), List.of(entity1.getId(), entity2.getId()));
  }

  @Test
  void shouldNotDeleteFromDeletionIndexIfPiDependantDeletionFailed() {
    // given
    final var entity =
        new HistoryDeletionEntity()
            .setId("id1")
            .setResourceKey(1L)
            .setResourceType(HistoryDeletionType.PROCESS_INSTANCE)
            .setBatchOperationKey(2L)
            .setPartitionId(1);
    when(repository.getNextBatch())
        .thenReturn(CompletableFuture.completedFuture(new HistoryDeletionBatch(List.of(entity))));
    when(repository.deleteDocumentsByField(anyString(), anyString(), anyList()))
        .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Failed deleting")));

    // when
    job.execute().exceptionally(ex -> 0).toCompletableFuture().join();

    // then
    dependants.stream()
        .filter(t -> !(t instanceof OperationTemplate))
        .filter(t -> !(t instanceof AuditLogTemplate))
        .forEach(
            dependant ->
                verify(repository)
                    .deleteDocumentsByField(
                        dependant.getFullQualifiedName() + "*",
                        dependant.getProcessInstanceDependantField(),
                        List.of(entity.getResourceKey())));

    verify(repository, never())
        .deleteDocumentsByField(
            listViewTemplate.getIndexPattern(),
            ListViewTemplate.PROCESS_INSTANCE_KEY,
            List.of(entity.getResourceKey()));
    verify(repository, never()).deleteDocumentsById(any(), any());
  }

  @Test
  void shouldNotDeleteFromDeletionIndexIfPiListViewDeletionFailed() {
    // given
    final var entity =
        new HistoryDeletionEntity()
            .setId("id1")
            .setResourceKey(1L)
            .setResourceType(HistoryDeletionType.PROCESS_INSTANCE)
            .setBatchOperationKey(2L)
            .setPartitionId(1);
    when(repository.getNextBatch())
        .thenReturn(CompletableFuture.completedFuture(new HistoryDeletionBatch(List.of(entity))));
    when(repository.deleteDocumentsByField(anyString(), anyString(), anyList()))
        .thenReturn(CompletableFuture.completedFuture(List.of()));
    when(repository.deleteDocumentsByField(
            listViewTemplate.getIndexPattern(),
            ListViewTemplate.PROCESS_INSTANCE_KEY,
            List.of(entity.getResourceKey())))
        .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Failed deleting")));

    // when
    job.execute().exceptionally(ex -> 0).toCompletableFuture().join();

    // then
    dependants.stream()
        .filter(t -> !(t instanceof OperationTemplate))
        .filter(t -> !(t instanceof AuditLogTemplate))
        .forEach(
            dependant ->
                verify(repository)
                    .deleteDocumentsByField(
                        dependant.getFullQualifiedName() + "*",
                        dependant.getProcessInstanceDependantField(),
                        List.of(entity.getResourceKey())));

    verify(repository)
        .deleteDocumentsByField(
            listViewTemplate.getIndexPattern(),
            ListViewTemplate.PROCESS_INSTANCE_KEY,
            List.of(entity.getResourceKey()));
    verify(repository, never()).deleteDocumentsById(any(), any());
  }

  @Test
  void shouldDeleteProcessDefinitionHistory() {
    // given
    final var entity =
        new HistoryDeletionEntity()
            .setId("id")
            .setResourceKey(1L)
            .setResourceType(HistoryDeletionType.PROCESS_DEFINITION)
            .setBatchOperationKey(2L)
            .setPartitionId(1);
    when(repository.getNextBatch())
        .thenReturn(CompletableFuture.completedFuture(new HistoryDeletionBatch(List.of(entity))));
    when(repository.deleteDocumentsById(anyString(), anyList()))
        .thenReturn(CompletableFuture.completedFuture(0));

    // when
    job.execute().toCompletableFuture().join();

    // then Process Definition is deleted
    verify(repository, atMostOnce())
        .deleteDocumentsById(
            processIndex.getFullQualifiedName(), List.of(String.valueOf(entity.getResourceKey())));
    verify(repository, atMostOnce())
        .deleteDocumentsById(historyDeletionIndex.getFullQualifiedName(), List.of(entity.getId()));
  }

  @Test
  void shouldNotDeleteProcessDefinitionFromHistoryIndexIfDeletionFailed() {
    // given
    final var entity =
        new HistoryDeletionEntity()
            .setId("id")
            .setResourceKey(1L)
            .setResourceType(HistoryDeletionType.PROCESS_DEFINITION)
            .setBatchOperationKey(2L)
            .setPartitionId(1);
    when(repository.getNextBatch())
        .thenReturn(CompletableFuture.completedFuture(new HistoryDeletionBatch(List.of(entity))));
    when(repository.deleteDocumentsById(eq(processIndex.getFullQualifiedName()), any()))
        .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Failed deleting")));

    // when
    job.execute().exceptionally(ex -> 0).toCompletableFuture().join();

    // then
    verify(repository, atMostOnce())
        .deleteDocumentsById(
            processIndex.getFullQualifiedName(), List.of(String.valueOf(entity.getResourceKey())));
    verify(repository, never())
        .deleteDocumentsById(eq(historyDeletionIndex.getFullQualifiedName()), any());
  }

  @Test
  void shouldNotDeleteProcessDefinitionIfProcessInstanceDeletionFailed() {
    // given
    final var entity1 =
        new HistoryDeletionEntity()
            .setId("id1")
            .setResourceKey(1L)
            .setResourceType(HistoryDeletionType.PROCESS_INSTANCE)
            .setBatchOperationKey(2L)
            .setPartitionId(1);
    final var entity2 =
        new HistoryDeletionEntity()
            .setId("id2")
            .setResourceKey(2L)
            .setResourceType(HistoryDeletionType.PROCESS_DEFINITION)
            .setBatchOperationKey(2L)
            .setPartitionId(1);
    when(repository.getNextBatch())
        .thenReturn(
            CompletableFuture.completedFuture(new HistoryDeletionBatch(List.of(entity1, entity2))));
    when(repository.deleteDocumentsByField(anyString(), anyString(), anyList()))
        .thenReturn(CompletableFuture.completedFuture(List.of()));
    when(repository.deleteDocumentsByField(
            listViewTemplate.getIndexPattern(),
            ListViewTemplate.PROCESS_INSTANCE_KEY,
            List.of(entity1.getResourceKey())))
        .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Failed deleting")));

    // when
    job.execute().exceptionally(ex -> 0).toCompletableFuture().join();

    // then
    verify(repository, never()).deleteDocumentsById(any(), any());
  }

  @Test
  void shouldDeletePIsFromDeletionIndexIfProcessDefinitionDeletionFailed() {
    // given
    final var processInstanceEntity =
        new HistoryDeletionEntity()
            .setId("id1")
            .setResourceKey(1L)
            .setResourceType(HistoryDeletionType.PROCESS_INSTANCE)
            .setBatchOperationKey(2L)
            .setPartitionId(1);
    final var processDefinitionEntity =
        new HistoryDeletionEntity()
            .setId("id2")
            .setResourceKey(2L)
            .setResourceType(HistoryDeletionType.PROCESS_DEFINITION)
            .setBatchOperationKey(2L)
            .setPartitionId(1);
    when(repository.getNextBatch())
        .thenReturn(
            CompletableFuture.completedFuture(
                new HistoryDeletionBatch(List.of(processInstanceEntity, processDefinitionEntity))));
    when(repository.deleteDocumentsById(anyString(), anyList()))
        .thenReturn(CompletableFuture.completedFuture(0));
    when(repository.deleteDocumentsByField(anyString(), anyString(), anyList()))
        .thenReturn(CompletableFuture.completedFuture(List.of()));
    when(repository.deleteDocumentsById(eq(processIndex.getFullQualifiedName()), anyList()))
        .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Failed deleting")));

    // when
    job.execute().toCompletableFuture().join();

    // then
    verify(repository)
        .deleteDocumentsById(
            historyDeletionIndex.getFullQualifiedName(), List.of(processInstanceEntity.getId()));
  }

  @Test
  void shouldDeleteDecisionInstanceHistory() {
    // given
    final var entity1 =
        new HistoryDeletionEntity()
            .setId("id1")
            .setResourceKey(1L)
            .setResourceType(HistoryDeletionType.DECISION_INSTANCE)
            .setBatchOperationKey(2L)
            .setPartitionId(1);
    final var entity2 =
        new HistoryDeletionEntity()
            .setId("id2")
            .setResourceKey(2L)
            .setResourceType(HistoryDeletionType.DECISION_INSTANCE)
            .setBatchOperationKey(2L)
            .setPartitionId(1);
    when(repository.getNextBatch())
        .thenReturn(
            CompletableFuture.completedFuture(new HistoryDeletionBatch(List.of(entity1, entity2))));
    when(repository.deleteDocumentsByField(anyString(), anyString(), anyList()))
        .thenReturn(CompletableFuture.completedFuture(List.of()));
    when(repository.deleteDocumentsById(anyString(), anyList()))
        .thenReturn(CompletableFuture.completedFuture(0));

    // when
    job.execute().toCompletableFuture().join();

    // then
    verify(repository)
        .deleteDocumentsByField(
            decisionInstanceTemplate.getIndexPattern(),
            DecisionInstanceTemplate.KEY,
            List.of(entity1.getResourceKey(), entity2.getResourceKey()));
    verify(repository)
        .deleteDocumentsById(
            historyDeletionIndex.getFullQualifiedName(), List.of(entity1.getId(), entity2.getId()));
  }

  @Test
  void shouldNotDeleteFromDeletionIndexIfDecisionInstanceDeletionFailed() {
    // given
    final var entity =
        new HistoryDeletionEntity()
            .setId("id1")
            .setResourceKey(1L)
            .setResourceType(HistoryDeletionType.DECISION_INSTANCE)
            .setBatchOperationKey(2L)
            .setPartitionId(1);
    when(repository.getNextBatch())
        .thenReturn(CompletableFuture.completedFuture(new HistoryDeletionBatch(List.of(entity))));
    when(repository.deleteDocumentsByField(anyString(), anyString(), anyList()))
        .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Failed deleting")));

    // when
    job.execute().exceptionally(ex -> 0).toCompletableFuture().join();

    // then
    verify(repository, never()).deleteDocumentsById(any(), any());
  }

  @Test
  void shouldDeleteDecisionInstancesIfProcessInstanceDeletionFailed() {
    // given
    final var processInstanceEntity =
        new HistoryDeletionEntity()
            .setId("id1")
            .setResourceKey(1L)
            .setResourceType(HistoryDeletionType.PROCESS_INSTANCE)
            .setBatchOperationKey(2L)
            .setPartitionId(1);
    final var decisionInstanceEntity =
        new HistoryDeletionEntity()
            .setId("id2")
            .setResourceKey(2L)
            .setResourceType(HistoryDeletionType.DECISION_INSTANCE)
            .setBatchOperationKey(2L)
            .setPartitionId(1);
    when(repository.getNextBatch())
        .thenReturn(
            CompletableFuture.completedFuture(
                new HistoryDeletionBatch(List.of(processInstanceEntity, decisionInstanceEntity))));
    when(repository.deleteDocumentsByField(anyString(), anyString(), anyList()))
        .thenReturn(CompletableFuture.completedFuture(List.of()));
    when(repository.deleteDocumentsByField(
            listViewTemplate.getIndexPattern(),
            ListViewTemplate.PROCESS_INSTANCE_KEY,
            List.of(processInstanceEntity.getResourceKey())))
        .thenReturn(
            CompletableFuture.failedFuture(
                new RuntimeException("Failed deleting process instance")));
    when(repository.deleteDocumentsById(anyString(), anyList()))
        .thenReturn(CompletableFuture.completedFuture(0));

    // when
    job.execute().exceptionally(ex -> 0).toCompletableFuture().join();

    // then
    verify(repository)
        .deleteDocumentsByField(
            decisionInstanceTemplate.getIndexPattern(),
            DecisionInstanceTemplate.KEY,
            List.of(decisionInstanceEntity.getResourceKey()));
    verify(repository)
        .deleteDocumentsById(
            historyDeletionIndex.getFullQualifiedName(), List.of(decisionInstanceEntity.getId()));
  }

  @Test
  void shouldDeletePIsAndDIsFromDeletionIndexIfProcessDefinitionDeletionFailed() {
    // given
    final var processInstanceEntity =
        new HistoryDeletionEntity()
            .setId("id1")
            .setResourceKey(1L)
            .setResourceType(HistoryDeletionType.PROCESS_INSTANCE)
            .setBatchOperationKey(2L)
            .setPartitionId(1);
    final var processDefinitionEntity =
        new HistoryDeletionEntity()
            .setId("id2")
            .setResourceKey(2L)
            .setResourceType(HistoryDeletionType.PROCESS_DEFINITION)
            .setBatchOperationKey(2L)
            .setPartitionId(1);
    final var decisionInstanceEntity =
        new HistoryDeletionEntity()
            .setId("id3")
            .setResourceKey(3L)
            .setResourceType(HistoryDeletionType.DECISION_INSTANCE)
            .setBatchOperationKey(2L)
            .setPartitionId(1);
    when(repository.getNextBatch())
        .thenReturn(
            CompletableFuture.completedFuture(
                new HistoryDeletionBatch(
                    List.of(
                        processInstanceEntity, processDefinitionEntity, decisionInstanceEntity))));
    when(repository.deleteDocumentsById(anyString(), anyList()))
        .thenReturn(CompletableFuture.completedFuture(0));
    when(repository.deleteDocumentsByField(anyString(), anyString(), anyList()))
        .thenReturn(CompletableFuture.completedFuture(List.of()));
    when(repository.deleteDocumentsById(eq(processIndex.getFullQualifiedName()), anyList()))
        .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Failed deleting")));

    // when
    job.execute().toCompletableFuture().join();

    // then
    verify(repository)
        .deleteDocumentsById(
            historyDeletionIndex.getFullQualifiedName(),
            List.of(processInstanceEntity.getId(), decisionInstanceEntity.getId()));
  }

  @Test
  void shouldDeleteDecisionRequirementsHistory() {
    // given
    final var entity1 =
        new HistoryDeletionEntity()
            .setId("id1")
            .setResourceKey(1L)
            .setResourceType(HistoryDeletionType.DECISION_REQUIREMENTS)
            .setBatchOperationKey(2L)
            .setPartitionId(1);
    final var entity2 =
        new HistoryDeletionEntity()
            .setId("id2")
            .setResourceKey(2L)
            .setResourceType(HistoryDeletionType.DECISION_REQUIREMENTS)
            .setBatchOperationKey(2L)
            .setPartitionId(1);
    when(repository.getNextBatch())
        .thenReturn(
            CompletableFuture.completedFuture(new HistoryDeletionBatch(List.of(entity1, entity2))));
    when(repository.deleteDocumentsByField(anyString(), anyString(), anyList()))
        .thenReturn(CompletableFuture.completedFuture(List.of()));
    when(repository.deleteDocumentsById(anyString(), anyList()))
        .thenReturn(CompletableFuture.completedFuture(0));

    // when
    job.execute().toCompletableFuture().join();

    // then
    verify(repository)
        .deleteDocumentsByField(
            decisionIndex.getFullQualifiedName(),
            DecisionIndex.DECISION_REQUIREMENTS_KEY,
            List.of(entity1.getResourceKey(), entity2.getResourceKey()));
    verify(repository)
        .deleteDocumentsById(
            decisionRequirementsIndex.getFullQualifiedName(),
            List.of(
                String.valueOf(entity1.getResourceKey()),
                String.valueOf(entity2.getResourceKey())));
    verify(repository)
        .deleteDocumentsById(
            historyDeletionIndex.getFullQualifiedName(), List.of(entity1.getId(), entity2.getId()));
  }

  @Test
  void shouldNotDeleteFromDeletionIndexIfDecisionRequirementsDeletionFailed() {
    // given
    final var entity =
        new HistoryDeletionEntity()
            .setId("id1")
            .setResourceKey(1L)
            .setResourceType(HistoryDeletionType.DECISION_REQUIREMENTS)
            .setBatchOperationKey(2L)
            .setPartitionId(1);
    when(repository.getNextBatch())
        .thenReturn(CompletableFuture.completedFuture(new HistoryDeletionBatch(List.of(entity))));
    when(repository.deleteDocumentsByField(anyString(), anyString(), anyList()))
        .thenReturn(CompletableFuture.completedFuture(List.of()));
    when(repository.deleteDocumentsById(
            eq(decisionRequirementsIndex.getFullQualifiedName()), anyList()))
        .thenReturn(
            CompletableFuture.failedFuture(
                new RuntimeException("Failed deleting decision requirements")));

    // when
    job.execute().exceptionally(ex -> 0).toCompletableFuture().join();

    // then
    verify(repository, never())
        .deleteDocumentsById(eq(historyDeletionIndex.getFullQualifiedName()), any());
  }

  @Test
  void shouldNotDeleteFromDeletionIndexIfDecisionDeletionFailed() {
    // given
    final var entity =
        new HistoryDeletionEntity()
            .setId("id1")
            .setResourceKey(1L)
            .setResourceType(HistoryDeletionType.DECISION_REQUIREMENTS)
            .setBatchOperationKey(2L)
            .setPartitionId(1);
    when(repository.getNextBatch())
        .thenReturn(CompletableFuture.completedFuture(new HistoryDeletionBatch(List.of(entity))));
    when(repository.deleteDocumentsByField(
            eq(decisionIndex.getFullQualifiedName()), anyString(), anyList()))
        .thenReturn(
            CompletableFuture.failedFuture(new RuntimeException("Failed deleting decision")));

    // when
    job.execute().exceptionally(ex -> 0).toCompletableFuture().join();

    // then
    verify(repository, never())
        .deleteDocumentsById(eq(decisionRequirementsIndex.getFullQualifiedName()), any());
    verify(repository, never())
        .deleteDocumentsById(eq(historyDeletionIndex.getFullQualifiedName()), any());
  }

  @Test
  void shouldDeleteDecisionRequirementsIfProcessInstanceDeletionFailed() {
    // given
    final var processInstanceEntity =
        new HistoryDeletionEntity()
            .setId("id1")
            .setResourceKey(1L)
            .setResourceType(HistoryDeletionType.PROCESS_INSTANCE)
            .setBatchOperationKey(2L)
            .setPartitionId(1);
    final var decisionRequirementsEntity =
        new HistoryDeletionEntity()
            .setId("id2")
            .setResourceKey(2L)
            .setResourceType(HistoryDeletionType.DECISION_REQUIREMENTS)
            .setBatchOperationKey(2L)
            .setPartitionId(1);
    when(repository.getNextBatch())
        .thenReturn(
            CompletableFuture.completedFuture(
                new HistoryDeletionBatch(
                    List.of(processInstanceEntity, decisionRequirementsEntity))));
    when(repository.deleteDocumentsByField(anyString(), anyString(), anyList()))
        .thenReturn(CompletableFuture.completedFuture(List.of()));
    when(repository.deleteDocumentsByField(
            listViewTemplate.getIndexPattern(),
            ListViewTemplate.PROCESS_INSTANCE_KEY,
            List.of(processInstanceEntity.getResourceKey())))
        .thenReturn(
            CompletableFuture.failedFuture(
                new RuntimeException("Failed deleting process instance")));
    when(repository.deleteDocumentsById(anyString(), anyList()))
        .thenReturn(CompletableFuture.completedFuture(0));

    // when
    job.execute().exceptionally(ex -> 0).toCompletableFuture().join();

    // then
    verify(repository)
        .deleteDocumentsByField(
            decisionIndex.getFullQualifiedName(),
            DecisionIndex.DECISION_REQUIREMENTS_KEY,
            List.of(decisionRequirementsEntity.getResourceKey()));
    verify(repository)
        .deleteDocumentsById(
            decisionRequirementsIndex.getFullQualifiedName(),
            List.of(String.valueOf(decisionRequirementsEntity.getResourceKey())));
    verify(repository)
        .deleteDocumentsById(
            historyDeletionIndex.getFullQualifiedName(),
            List.of(decisionRequirementsEntity.getId()));
  }

  @Test
  void shouldDeleteDecisionInstanceIfDecisionRequirementsDeletionFailed() {
    // given
    final var decisionInstanceEntity =
        new HistoryDeletionEntity()
            .setId("id1")
            .setResourceKey(1L)
            .setResourceType(HistoryDeletionType.DECISION_INSTANCE)
            .setBatchOperationKey(2L)
            .setPartitionId(1);
    final var decisionRequirementsEntity =
        new HistoryDeletionEntity()
            .setId("id2")
            .setResourceKey(2L)
            .setResourceType(HistoryDeletionType.DECISION_REQUIREMENTS)
            .setBatchOperationKey(2L)
            .setPartitionId(1);
    when(repository.getNextBatch())
        .thenReturn(
            CompletableFuture.completedFuture(
                new HistoryDeletionBatch(
                    List.of(decisionInstanceEntity, decisionRequirementsEntity))));
    when(repository.deleteDocumentsByField(anyString(), anyString(), anyList()))
        .thenReturn(CompletableFuture.completedFuture(List.of()));
    when(repository.deleteDocumentsById(anyString(), anyList()))
        .thenReturn(CompletableFuture.completedFuture(0));
    when(repository.deleteDocumentsById(
            eq(decisionRequirementsIndex.getFullQualifiedName()), anyList()))
        .thenReturn(
            CompletableFuture.failedFuture(
                new RuntimeException("Failed deleting decision requirements")));

    // when
    job.execute().exceptionally(ex -> 0).toCompletableFuture().join();

    // then
    verify(repository)
        .deleteDocumentsByField(
            decisionInstanceTemplate.getIndexPattern(),
            DecisionInstanceTemplate.KEY,
            List.of(decisionInstanceEntity.getResourceKey()));
    verify(repository)
        .deleteDocumentsById(
            historyDeletionIndex.getFullQualifiedName(), List.of(decisionInstanceEntity.getId()));
  }
}
