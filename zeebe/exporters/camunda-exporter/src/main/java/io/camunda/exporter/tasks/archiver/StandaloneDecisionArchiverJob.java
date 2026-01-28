/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.archiver;

import io.camunda.exporter.metrics.CamundaExporterMetrics;
import io.camunda.exporter.tasks.archiver.ArchiveBatch.BasicArchiveBatch;
import io.camunda.webapps.schema.descriptors.DecisionInstanceDependant;
import io.camunda.webapps.schema.descriptors.IndexTemplateDescriptor;
import io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.slf4j.Logger;

public class StandaloneDecisionArchiverJob extends ArchiverJob<BasicArchiveBatch> {

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
  CompletableFuture<BasicArchiveBatch> getNextBatch() {
    return getArchiverRepository().getStandaloneDecisionNextBatch();
  }

  @Override
  DecisionInstanceTemplate getTemplateDescriptor() {
    return decisionInstanceTemplate;
  }

  /**
   * Overridden to archive dependants first and then move the decision instances themselves.
   *
   * @param templateDescriptor template descriptor of the decision instance index
   * @param batch the batch of records to archive
   * @return future
   */
  @Override
  protected CompletableFuture<Integer> archive(
      final IndexTemplateDescriptor templateDescriptor, final BasicArchiveBatch batch) {
    return archiveDecisionDependants(batch)
        .thenComposeAsync(v -> super.archive(templateDescriptor, batch), executor);
  }

  @Override
  protected Map<String, List<String>> createIdsByFieldMap(
      final IndexTemplateDescriptor templateDescriptor, final BasicArchiveBatch batch) {
    if (templateDescriptor instanceof DecisionInstanceTemplate) {
      return Map.of(DecisionInstanceTemplate.ID, batch.ids());
    } else if (templateDescriptor instanceof final DecisionInstanceDependant did) {
      return Map.of(did.getDecisionDependantField(), batch.ids());
    }
    throw new IllegalArgumentException(
        "Unsupported template descriptor: " + templateDescriptor.getClass().getName());
  }

  private CompletableFuture<Void> archiveDecisionDependants(final BasicArchiveBatch batch) {
    final var moveDependentDocuments =
        decisionInstanceDependants.stream()
            .map(dependant -> super.archive(dependant, batch))
            .toArray(CompletableFuture[]::new);
    return CompletableFuture.allOf(moveDependentDocuments);
  }
}
