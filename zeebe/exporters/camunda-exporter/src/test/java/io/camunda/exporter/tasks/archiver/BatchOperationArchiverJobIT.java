/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.archiver;

import io.camunda.exporter.ExporterResourceProvider;
import io.camunda.exporter.config.ExporterConfiguration;
import io.camunda.webapps.schema.descriptors.BatchOperationDependant;
import io.camunda.webapps.schema.descriptors.template.BatchOperationTemplate;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

@TestInstance(Lifecycle.PER_CLASS)
public class BatchOperationArchiverJobIT extends ArchiverJobIT<BatchOperationArchiverJob> {
  @Override
  BatchOperationArchiverJob createArchiveJob(
      final ExporterConfiguration config,
      final ExporterResourceProvider resourceProvider,
      final ArchiverRepository repository) {

    final var dependantTemplates =
        resourceProvider.getIndexTemplateDescriptors().stream()
            .filter(BatchOperationDependant.class::isInstance)
            .map(BatchOperationDependant.class::cast)
            .toList();

    return new BatchOperationArchiverJob(
        repository,
        resourceProvider.getIndexTemplateDescriptor(BatchOperationTemplate.class),
        dependantTemplates,
        exporterMetrics,
        LOGGER,
        executor);
  }
}
