/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.archiver;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.exporter.ExporterResourceProvider;
import io.camunda.exporter.config.ExporterConfiguration;
import io.camunda.search.test.utils.SearchClientAdapter;
import io.camunda.webapps.schema.descriptors.BatchOperationDependant;
import io.camunda.webapps.schema.descriptors.template.AuditLogTemplate;
import io.camunda.webapps.schema.descriptors.template.BatchOperationTemplate;
import io.camunda.webapps.schema.entities.auditlog.AuditLogEntity;
import io.camunda.webapps.schema.entities.auditlog.AuditLogEntityType;
import io.camunda.webapps.schema.entities.operation.BatchOperationEntity;
import io.camunda.webapps.schema.entities.operation.BatchOperationEntity.BatchOperationState;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.TestTemplate;

@TestInstance(Lifecycle.PER_CLASS)
public class BatchOperationArchiverJobIT extends ArchiverJobIT<BatchOperationArchiverJob> {

  private static final AtomicLong ID_GENERATOR = new AtomicLong(1);

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

  @TestTemplate
  void shouldArchiveBatchOperationAndDependantAuditLog(
      final ExporterConfiguration config, final SearchClientAdapter client) throws Exception {
    withArchiverJob(
        config,
        (job, resourceProvider) -> {
          // given - batch operation with numeric ID and matching audit log entry
          final var batchOpTemplate =
              resourceProvider.getIndexTemplateDescriptor(BatchOperationTemplate.class);
          final var auditLogTemplate =
              resourceProvider.getIndexTemplateDescriptor(AuditLogTemplate.class);

          final var batchOp = batchOperationEntity("100", "2020-01-01T00:00:00+00:00");
          final var auditLog = auditLogEntity(100L);

          store(batchOpTemplate, client, batchOp);
          store(auditLogTemplate, client, auditLog);
          client.refresh();

          // when
          final var archived = job.execute();

          // then
          assertThat(archived).succeedsWithin(Duration.ofSeconds(5L)).isEqualTo(1);
          verifyMoved(batchOpTemplate, client, batchOp, "2020-01-01");
          verifyMoved(auditLogTemplate, client, auditLog, "2020-01-01");
        });
  }

  @TestTemplate
  void shouldArchiveBatchOperationWithGuidIdWithoutFailure(
      final ExporterConfiguration config, final SearchClientAdapter client) throws Exception {
    withArchiverJob(
        config,
        (job, resourceProvider) -> {
          // given - legacy 8.8 batch operation with GUID id
          final var batchOpTemplate =
              resourceProvider.getIndexTemplateDescriptor(BatchOperationTemplate.class);
          final var auditLogTemplate =
              resourceProvider.getIndexTemplateDescriptor(AuditLogTemplate.class);

          final var batchOp = batchOperationEntity("abc-def-123-guid", "2020-01-01T00:00:00+00:00");
          // unrelated audit log entry that should NOT be archived with this batch operation
          final var auditLog = auditLogEntity(999L);

          store(batchOpTemplate, client, batchOp);
          store(auditLogTemplate, client, auditLog);
          client.refresh();

          // when - should complete without number_format_exception
          final var archived = job.execute();

          // then
          assertThat(archived).succeedsWithin(Duration.ofSeconds(5L)).isEqualTo(1);
          verifyMoved(batchOpTemplate, client, batchOp, "2020-01-01");
          verifyNotMoved(auditLogTemplate, client, auditLog);
        });
  }

  @TestTemplate
  void shouldArchiveMixedBatchOperationsAndOnlyMatchNumericDependants(
      final ExporterConfiguration config, final SearchClientAdapter client) throws Exception {
    withArchiverJob(
        config,
        (job, resourceProvider) -> {
          // given - mix of 8.8 GUID and 8.9 numeric batch operation IDs with same end date
          final var batchOpTemplate =
              resourceProvider.getIndexTemplateDescriptor(BatchOperationTemplate.class);
          final var auditLogTemplate =
              resourceProvider.getIndexTemplateDescriptor(AuditLogTemplate.class);

          final var numericBatchOp = batchOperationEntity("200", "2020-01-01T00:00:00+00:00");
          final var guidBatchOp =
              batchOperationEntity("abc-def-456-guid", "2020-01-01T00:00:00+00:00");
          final var matchingAuditLog = auditLogEntity(200L);
          final var unrelatedAuditLog = auditLogEntity(999L);

          store(batchOpTemplate, client, numericBatchOp);
          store(batchOpTemplate, client, guidBatchOp);
          store(auditLogTemplate, client, matchingAuditLog);
          store(auditLogTemplate, client, unrelatedAuditLog);
          client.refresh();

          // when - should complete without error, GUID filtered out for dependant query
          final var archived = job.execute();

          // then
          assertThat(archived).succeedsWithin(Duration.ofSeconds(5L)).isEqualTo(2);
          verifyMoved(batchOpTemplate, client, numericBatchOp, "2020-01-01");
          verifyMoved(batchOpTemplate, client, guidBatchOp, "2020-01-01");
          verifyMoved(auditLogTemplate, client, matchingAuditLog, "2020-01-01");
          verifyNotMoved(auditLogTemplate, client, unrelatedAuditLog);
        });
  }

  private BatchOperationEntity batchOperationEntity(final String id, final String endDate) {
    return new BatchOperationEntity()
        .setId(id)
        .setEndDate(OffsetDateTime.parse(endDate))
        .setState(BatchOperationState.COMPLETED);
  }

  private AuditLogEntity auditLogEntity(final Long batchOperationKey) {
    final var entity = new AuditLogEntity();
    entity.setId(String.valueOf(ID_GENERATOR.incrementAndGet()));
    entity.setBatchOperationKey(batchOperationKey);
    entity.setEntityType(AuditLogEntityType.BATCH);
    return entity;
  }
}
