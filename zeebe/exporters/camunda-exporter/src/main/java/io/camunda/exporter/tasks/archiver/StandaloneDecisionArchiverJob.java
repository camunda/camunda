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
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.slf4j.Logger;

public class StandaloneDecisionArchiverJob extends ArchiverJob {

  private final DecisionInstanceTemplate decisionInstanceTemplate;
  private final List<DecisionInstanceDependant> decisionInstanceDependants;

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
    this.decisionInstanceDependants = decisionInstanceDependants;
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

  private CompletableFuture<Void> archiveProcessDependants(
      final String finishDate, final List<String> decisionInstanceKeys) {
    final var moveDependentDocuments =
        decisionInstanceDependants.stream()
            .map(
                dependant -> {
                  final var dependentSourceIdx = dependant.getFullQualifiedName();
                  final var dependentIdFieldName = dependant.getDrgDependantField();
                  return super.archive(
                      dependentSourceIdx, finishDate, dependentIdFieldName, decisionInstanceKeys);
                })
            .toArray(CompletableFuture[]::new);
    return CompletableFuture.allOf(moveDependentDocuments);
  }
}
