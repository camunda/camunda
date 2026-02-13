/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.historydeletion;

import io.camunda.exporter.ExporterResourceProvider;
import io.camunda.exporter.tasks.BackgroundTask;
import io.camunda.webapps.schema.descriptors.ProcessInstanceDependant;
import io.camunda.webapps.schema.descriptors.index.DecisionIndex;
import io.camunda.webapps.schema.descriptors.index.DecisionRequirementsIndex;
import io.camunda.webapps.schema.descriptors.index.HistoryDeletionIndex;
import io.camunda.webapps.schema.descriptors.index.ProcessIndex;
import io.camunda.webapps.schema.descriptors.template.AuditLogTemplate;
import io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate;
import io.camunda.webapps.schema.descriptors.template.ListViewTemplate;
import io.camunda.webapps.schema.descriptors.template.OperationTemplate;
import io.camunda.zeebe.protocol.record.value.HistoryDeletionType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import org.slf4j.Logger;

/**
 * Background task that queries the history-deletion index for resources marked for deletion and
 * deletes the corresponding data from secondary storage. Work is distributed across partitions to
 * balance the deletion load.
 *
 * <p>The job interacts with the history-deletion index to identify resources scheduled for
 * deletion, and executes deletion requests in batches, partitioned by partition ID to ensure
 * balanced processing.
 */
public class HistoryDeletionJob implements BackgroundTask {
  private final List<ProcessInstanceDependant> processInstanceDependants;
  private final Executor executor;
  private final HistoryDeletionRepository deleterRepository;
  private final Logger logger;
  private final HistoryDeletionIndex historyDeletionIndex;
  private final ListViewTemplate listViewTemplate;
  private final ProcessIndex processIndex;
  private final DecisionInstanceTemplate decisionInstanceTemplate;
  private final DecisionRequirementsIndex decisionRequirementsIndex;
  private final DecisionIndex decisionIndex;

  public HistoryDeletionJob(
      final List<ProcessInstanceDependant> processInstanceDependants,
      final Executor executor,
      final HistoryDeletionRepository historyDeletionRepository,
      final Logger logger,
      final ExporterResourceProvider resourceProvider) {
    this.processInstanceDependants =
        processInstanceDependants.stream()
            .sorted(Comparator.comparing(ProcessInstanceDependant::getFullQualifiedName))
            .toList(); // sort to ensure the execution order is stable
    this.executor = executor;
    deleterRepository = historyDeletionRepository;
    this.logger = logger;
    historyDeletionIndex = resourceProvider.getIndexDescriptor(HistoryDeletionIndex.class);
    listViewTemplate = resourceProvider.getIndexTemplateDescriptor(ListViewTemplate.class);
    processIndex = resourceProvider.getIndexDescriptor(ProcessIndex.class);
    decisionInstanceTemplate =
        resourceProvider.getIndexTemplateDescriptor(DecisionInstanceTemplate.class);
    decisionRequirementsIndex =
        resourceProvider.getIndexDescriptor(DecisionRequirementsIndex.class);
    decisionIndex = resourceProvider.getIndexDescriptor(DecisionIndex.class);
  }

  @Override
  public CompletionStage<Integer> execute() {
    return deleterRepository.getNextBatch().thenComposeAsync(this::deleteBatch, executor);
  }

  /**
   * This deletes all entities in a given batch. A batch could contain different types of entities,
   * such as process instances, process definitions, or decision instances.
   *
   * @param batch The batch of entities to delete
   * @return A future containing the amount of entities that were deleted
   */
  private CompletionStage<Integer> deleteBatch(final HistoryDeletionBatch batch) {
    logger.trace("Deleting historic data for entities: {}", batch.historyDeletionEntities());

    if (batch.historyDeletionEntities().isEmpty()) {
      logger.trace("No historic data to delete");
      return CompletableFuture.completedFuture(0);
    }

    final var deleteProcessInstancesAndDefinitionsFuture =
        deleteProcessInstances(batch)
            .thenCompose(
                ids ->
                    deleteProcessDefinitions(batch, ids)
                        .exceptionally(
                            ex -> {
                              logger.warn(
                                  "Failed to delete process definitions for batch: {}", batch, ex);
                              return ids;
                            }))
            .exceptionally(
                ex -> {
                  logger.warn("Failed to delete process instances for batch: {}", batch, ex);
                  return List.of();
                })
            .toCompletableFuture();

    final var deleteDecisionInstancesAndRequirementsFuture =
        deleteDecisionInstances(batch)
            .thenCompose(
                ids ->
                    deleteDecisionRequirements(batch, ids)
                        .exceptionally(
                            ex -> {
                              logger.warn(
                                  "Failed to delete decision requirements for batch: {}",
                                  batch,
                                  ex);
                              return ids;
                            }))
            .exceptionally(
                ex -> {
                  logger.warn("Failed to delete decision instances for batch: {}", batch, ex);
                  return List.of();
                })
            .toCompletableFuture();

    return CompletableFuture.allOf(
            deleteProcessInstancesAndDefinitionsFuture,
            deleteDecisionInstancesAndRequirementsFuture)
        .thenCompose(
            ignored -> {
              final var deletedResources = new ArrayList<String>();
              deletedResources.addAll(deleteProcessInstancesAndDefinitionsFuture.join());
              deletedResources.addAll(deleteDecisionInstancesAndRequirementsFuture.join());
              return deleteFromHistoryDeletionIndex(deletedResources);
            });
  }

  /**
   * This method will delete a batch of process instances in two steps:
   *
   * <ol>
   *   <li>It deletes the PI related data from all process instance dependants (eg, variable, jobs)
   *   <li>It deletes the PIs from the list-view
   * </ol>
   *
   * @param batch The batch of entities to delete
   * @return A future containing the list of history-deletion IDs that were processed
   */
  private CompletionStage<List<String>> deleteProcessInstances(final HistoryDeletionBatch batch) {
    final var processInstanceKeys = batch.getResourceKeys(HistoryDeletionType.PROCESS_INSTANCE);
    if (processInstanceKeys.isEmpty()) {
      return CompletableFuture.completedFuture(List.of());
    }

    final var deletionFutures =
        processInstanceDependants.stream()
            .filter(t -> !(t instanceof OperationTemplate))
            .filter(t -> !(t instanceof AuditLogTemplate))
            .map(
                dependant -> {
                  final var dependentSourceIdx = dependant.getFullQualifiedName() + "*";
                  final var dependentIdFieldName = dependant.getProcessInstanceDependantField();
                  return deleterRepository.deleteDocumentsByField(
                      dependentSourceIdx, dependentIdFieldName, processInstanceKeys);
                })
            .toList();

    return CompletableFuture.allOf(deletionFutures.toArray(CompletableFuture[]::new))
        .thenCompose(
            ignored -> {
              final var dependentSourceIdx = listViewTemplate.getIndexPattern();
              final var dependentIdFieldName = ListViewTemplate.PROCESS_INSTANCE_KEY;
              return deleterRepository.deleteDocumentsByField(
                  dependentSourceIdx, dependentIdFieldName, processInstanceKeys);
            })
        .thenApply(ignored -> batch.getHistoryDeletionIds(HistoryDeletionType.PROCESS_INSTANCE));
  }

  /**
   * This method will delete a batch of process definitions by deleting the process definitions from
   * the process index
   *
   * @param batch The batch of entities to delete
   * @return A future containing the list of history-deletion IDs that were processed
   */
  private CompletionStage<List<String>> deleteProcessDefinitions(
      final HistoryDeletionBatch batch, final List<String> deletedResourceIds) {
    final var processDefinitions = batch.getResourceKeys(HistoryDeletionType.PROCESS_DEFINITION);
    if (processDefinitions.isEmpty()) {
      return CompletableFuture.completedFuture(deletedResourceIds);
    }

    return deleterRepository
        .deleteDocumentsById(
            processIndex.getFullQualifiedName(),
            processDefinitions.stream().map(Object::toString).toList())
        .thenApply(
            ignored -> {
              final var ids = new ArrayList<>(deletedResourceIds);
              ids.addAll(batch.getHistoryDeletionIds(HistoryDeletionType.PROCESS_DEFINITION));
              return ids;
            });
  }

  /**
   * This method will delete a batch of decision instances.
   *
   * @param batch The batch of entities to delete
   * @return A future containing the list of history-deletion IDs that were processed
   */
  private CompletionStage<List<String>> deleteDecisionInstances(final HistoryDeletionBatch batch) {
    final var decisionInstances = batch.getResourceKeys(HistoryDeletionType.DECISION_INSTANCE);
    if (decisionInstances.isEmpty()) {
      return CompletableFuture.completedFuture(List.of());
    }

    return deleterRepository
        .deleteDocumentsByField(
            decisionInstanceTemplate.getIndexPattern(),
            DecisionInstanceTemplate.KEY,
            decisionInstances)
        .thenApply(ignored -> batch.getHistoryDeletionIds(HistoryDeletionType.DECISION_INSTANCE));
  }

  /**
   * This method will delete a batch of decision requirements. It will first delete any decisions
   * that belong to the decision requirements. After it will delete the decision requirements
   * themselves.
   *
   * @param batch The batch of entities to delete
   * @return A future containing the list of history-deletion IDs that were processed
   */
  private CompletionStage<List<String>> deleteDecisionRequirements(
      final HistoryDeletionBatch batch, final List<String> deletedResourceIds) {
    final var decisionRequirements =
        batch.getResourceKeys(HistoryDeletionType.DECISION_REQUIREMENTS);
    if (decisionRequirements.isEmpty()) {
      return CompletableFuture.completedFuture(deletedResourceIds);
    }

    return deleterRepository
        .deleteDocumentsByField(
            decisionIndex.getFullQualifiedName(),
            DecisionIndex.DECISION_REQUIREMENTS_KEY,
            decisionRequirements)
        .thenCompose(
            ignored ->
                deleterRepository.deleteDocumentsById(
                    decisionRequirementsIndex.getFullQualifiedName(),
                    decisionRequirements.stream().map(Object::toString).toList()))
        .thenApply(
            ignored -> {
              final var ids = new ArrayList<>(deletedResourceIds);
              ids.addAll(batch.getHistoryDeletionIds(HistoryDeletionType.DECISION_REQUIREMENTS));
              return ids;
            });
  }

  private CompletionStage<Integer> deleteFromHistoryDeletionIndex(final List<String> ids) {
    if (ids.isEmpty()) {
      return CompletableFuture.completedFuture(0);
    }
    return deleterRepository.deleteDocumentsById(historyDeletionIndex.getFullQualifiedName(), ids);
  }
}
