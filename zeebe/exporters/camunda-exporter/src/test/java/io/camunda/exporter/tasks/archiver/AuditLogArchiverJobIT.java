/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.archiver;

import io.camunda.exporter.ExporterResourceProvider;
import io.camunda.exporter.config.ConnectionTypes;
import io.camunda.exporter.config.ExporterConfiguration;
import io.camunda.webapps.schema.descriptors.index.AuditLogCleanupIndex;
import io.camunda.webapps.schema.descriptors.template.AuditLogTemplate;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

@TestInstance(Lifecycle.PER_CLASS)
public class AuditLogArchiverJobIT extends ArchiverJobIT<AuditLogArchiverJob> {
  @Override
  AuditLogArchiverJob createArchiveJob(
      final ExporterConfiguration config,
      final ExporterResourceProvider resourceProvider,
      final ArchiverRepository repository) {

    final var auditLogArchiverRepository =
        closeLater(createAuditLogArchiverRepository(config, resourceProvider));

    return new AuditLogArchiverJob(
        auditLogArchiverRepository,
        repository,
        resourceProvider.getIndexTemplateDescriptor(AuditLogTemplate.class),
        exporterMetrics,
        config.getHistory(),
        LOGGER,
        executor);
  }

  private AuditLogArchiverRepository createAuditLogArchiverRepository(
      final ExporterConfiguration config, final ExporterResourceProvider resourceProvider) {
    final var isElasticsearch = ConnectionTypes.isElasticSearch(config.getConnect().getType());
    if (isElasticsearch) {
      return new ElasticsearchAuditLogArchiverRepository(
          PARTITION_ID,
          createAsyncESClient(config),
          executor,
          LOGGER,
          resourceProvider.getIndexDescriptor(AuditLogCleanupIndex.class),
          resourceProvider.getIndexTemplateDescriptor(AuditLogTemplate.class),
          config.getHistory(),
          context.clock());
    } else {
      return new OpensearchAuditLogArchiverRepository(
          PARTITION_ID,
          createOSAsyncClient(config),
          executor,
          LOGGER,
          resourceProvider.getIndexDescriptor(AuditLogCleanupIndex.class),
          resourceProvider.getIndexTemplateDescriptor(AuditLogTemplate.class),
          config.getHistory(),
          context.clock());
    }
  }
}
