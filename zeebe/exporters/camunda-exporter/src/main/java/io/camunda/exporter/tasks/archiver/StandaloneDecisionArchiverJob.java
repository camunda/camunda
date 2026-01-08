/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.archiver;

import io.camunda.exporter.metrics.CamundaExporterMetrics;
import io.camunda.webapps.schema.descriptors.DecisionInstanceDependant;
import io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.slf4j.Logger;

public class StandaloneDecisionArchiverJob extends ArchiverJob {

  private final DecisionInstanceTemplate decisionInstanceTemplate;
  private final List<DecisionInstanceDependant> decisionInstanceDependants;
  private final Executor executor;

  public StandaloneDecisionArchiverJob(
      final ArchiverRepository repository,
      final DecisionInstanceTemplate decisionInstanceTemplate,
      final CamundaExporterMetrics metrics,
      final Logger logger,
      final Executor executor,
      final List<DecisionInstanceDependant> decisionInstanceDependants) {
    super(
        repository,
        metrics,
        logger,
        executor,
        metrics::recordStandaloneDecisionsArchiving,
        metrics::recordStandaloneDecisionsArchived);
    this.decisionInstanceTemplate = decisionInstanceTemplate;
    this.decisionInstanceDependants =
        decisionInstanceDependants.stream()
            .sorted(Comparator.comparing(DecisionInstanceDependant::getFullQualifiedName))
            .toList(); // sort to ensure the execution order is stable;
    this.executor = executor;
  }

  @Override
  String getJobName() {
    return "standalone-decision";
  }

  @Override
  CompletableFuture<ArchiveBatch> getNextBatch() {
    return getArchiverRepository().getStandaloneDecisionNextBatch();
  }

  @Override
  String getSourceIndexName() {
    return decisionInstanceTemplate.getFullQualifiedName();
  }

  @Override
  String getIdFieldName() {
    return DecisionInstanceTemplate.ID;
  }

  /**
   * Overridden to archive dependants first and then move the decision instances themselves.
   *
   * @param sourceIdx decision instance index
   * @param finishDate move to the dated index
   * @param idFieldName decision instance key field
   * @param ids list of decision instance keys to archive
   * @return number of archived decision instances
   */
  @Override
  protected CompletableFuture<Integer> archive(
      final String sourceIdx,
      final String finishDate,
      final String idFieldName,
      final List<String> ids) {
    return archiveDecisionDependants(finishDate, ids)
        .thenComposeAsync(v -> super.archive(sourceIdx, finishDate, idFieldName, ids), executor);
  }

  private CompletableFuture<Void> archiveDecisionDependants(
      final String finishDate, final List<String> decisionInstanceKeys) {
    final var moveDependentDocuments =
        decisionInstanceDependants.stream()
            .map(
                dependant -> {
                  final var dependentSourceIdx = dependant.getFullQualifiedName();
                  final var dependentIdFieldName = dependant.getDecisionDependantField();
                  return super.archive(
                      dependentSourceIdx, finishDate, dependentIdFieldName, decisionInstanceKeys);
                })
            .toArray(CompletableFuture[]::new);
    return CompletableFuture.allOf(moveDependentDocuments);
  }
}
