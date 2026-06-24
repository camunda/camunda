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
import io.camunda.exporter.config.ConnectionTypes;
import io.camunda.exporter.config.ExporterConfiguration;
import io.camunda.search.test.utils.SearchClientAdapter;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.webapps.schema.descriptors.index.AuditLogCleanupIndex;
import io.camunda.webapps.schema.descriptors.template.AuditLogTemplate;
import io.camunda.webapps.schema.entities.auditlog.AuditLogCleanupEntity;
import io.camunda.webapps.schema.entities.auditlog.AuditLogEntity;
import io.camunda.webapps.schema.entities.auditlog.AuditLogEntityType;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.TestTemplate;

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

  @TestTemplate
  void shouldArchiveAuditLogsAndDeleteCleanupMetadata(
      final ExporterConfiguration config, final SearchClientAdapter client) throws Exception {
    withArchiverJob(
        config,
        (job, resourceProvider) -> {
          // given - a cleanup entity pointing at audit logs by processInstanceKey
          final var auditLogTemplate =
              resourceProvider.getIndexTemplateDescriptor(AuditLogTemplate.class);
          final var cleanupIndex = resourceProvider.getIndexDescriptor(AuditLogCleanupIndex.class);

          final var cleanupEntity =
              create(AuditLogCleanupEntity::new)
                  .setKey("123")
                  .setKeyField(AuditLogTemplate.PROCESS_INSTANCE_KEY)
                  .setEntityType(AuditLogEntityType.PROCESS_INSTANCE)
                  .setPartitionId(PARTITION_ID);

          final var auditLog = create(AuditLogEntity::new);
          auditLog.setProcessInstanceKey(123L);
          auditLog.setEntityType(AuditLogEntityType.PROCESS_INSTANCE);

          final var unrelatedAuditLog = create(AuditLogEntity::new);
          unrelatedAuditLog.setProcessInstanceKey(999L);
          unrelatedAuditLog.setEntityType(AuditLogEntityType.PROCESS_INSTANCE);

          store(cleanupIndex, client, cleanupEntity);
          store(auditLogTemplate, client, auditLog);
          store(auditLogTemplate, client, unrelatedAuditLog);
          client.refresh();

          // when
          final var archived = job.execute();

          // then - should archive 1 audit log + delete 1 cleanup = 2
          assertThat(archived).succeedsWithin(Duration.ofSeconds(30L)).isEqualTo(2);

          // then - matching audit log should be moved to the dated index
          client.refresh();
          verifyMoved(auditLogTemplate, client, auditLog, todayDateSuffix());

          // then - unrelated audit log should remain in the original index
          verifyNotMoved(auditLogTemplate, client, unrelatedAuditLog);

          // then - cleanup metadata should be deleted
          verifyCleanupDeleted(cleanupIndex, client, cleanupEntity);
        });
  }

  @TestTemplate
  void shouldDeleteCleanupMetadataWhenNoMatchingAuditLogs(
      final ExporterConfiguration config, final SearchClientAdapter client) throws Exception {
    withArchiverJob(
        config,
        (job, resourceProvider) -> {
          // given - a cleanup entity with no matching audit logs in the index
          final var cleanupIndex = resourceProvider.getIndexDescriptor(AuditLogCleanupIndex.class);

          final var cleanupEntity =
              create(AuditLogCleanupEntity::new)
                  .setKey("-1") // should not match any existing audit logs
                  .setKeyField(AuditLogTemplate.PROCESS_INSTANCE_KEY)
                  .setEntityType(AuditLogEntityType.PROCESS_INSTANCE)
                  .setPartitionId(PARTITION_ID);

          store(cleanupIndex, client, cleanupEntity);
          client.refresh();

          // when
          final var archived = job.execute();

          // then - should delete the cleanup entry even though there were no audit logs to move
          assertThat(archived).succeedsWithin(Duration.ofSeconds(30L)).isEqualTo(1);

          // then - cleanup metadata should be deleted
          client.refresh();
          verifyCleanupDeleted(cleanupIndex, client, cleanupEntity);
        });
  }

  @TestTemplate
  void shouldNotDeleteCleanupMetadataWhenBatchSizeReached(
      final ExporterConfiguration config, final SearchClientAdapter client) throws Exception {
    // set a small rollover batch size so we can trigger the threshold condition
    config.getHistory().setRolloverBatchSize(1);
    withArchiverJob(
        config,
        (job, resourceProvider) -> {
          // given - a cleanup entity with matching audit logs exceeding the batch size
          final var auditLogTemplate =
              resourceProvider.getIndexTemplateDescriptor(AuditLogTemplate.class);
          final var cleanupIndex = resourceProvider.getIndexDescriptor(AuditLogCleanupIndex.class);

          final var cleanupEntity =
              create(AuditLogCleanupEntity::new)
                  .setKey("456")
                  .setKeyField(AuditLogTemplate.PROCESS_INSTANCE_KEY)
                  .setEntityType(AuditLogEntityType.PROCESS_INSTANCE)
                  .setPartitionId(PARTITION_ID);

          final var auditLog1 = create(AuditLogEntity::new);
          auditLog1.setProcessInstanceKey(456L);
          auditLog1.setEntityType(AuditLogEntityType.PROCESS_INSTANCE);

          final var auditLog2 = create(AuditLogEntity::new);
          auditLog2.setProcessInstanceKey(456L);
          auditLog2.setEntityType(AuditLogEntityType.PROCESS_INSTANCE);

          store(cleanupIndex, client, cleanupEntity);
          store(auditLogTemplate, client, auditLog1);
          store(auditLogTemplate, client, auditLog2);
          client.refresh();

          // when
          final var archived = job.execute();

          // then - should archive audit logs but not delete the cleanup metadata since
          // the number of archived audit logs >= rolloverBatchSize, indicating more work remains
          assertThat(archived)
              .succeedsWithin(Duration.ofSeconds(30L))
              .satisfies(count -> assertThat((int) count).isGreaterThanOrEqualTo(1));

          // then - cleanup metadata should NOT be deleted because batch size was reached
          client.refresh();
          verifyCleanupNotDeleted(cleanupIndex, client, cleanupEntity);
        });
  }

  @TestTemplate
  void shouldArchiveAuditLogsFromMultipleCleanupEntities(
      final ExporterConfiguration config, final SearchClientAdapter client) throws Exception {
    withArchiverJob(
        config,
        (job, resourceProvider) -> {
          // given - two cleanup entities with matching audit logs
          final var auditLogTemplate =
              resourceProvider.getIndexTemplateDescriptor(AuditLogTemplate.class);
          final var cleanupIndex = resourceProvider.getIndexDescriptor(AuditLogCleanupIndex.class);

          final var cleanupEntity1 =
              create(AuditLogCleanupEntity::new)
                  .setKey("100")
                  .setKeyField(AuditLogTemplate.PROCESS_INSTANCE_KEY)
                  .setEntityType(AuditLogEntityType.PROCESS_INSTANCE)
                  .setPartitionId(PARTITION_ID);

          final var cleanupEntity2 =
              create(AuditLogCleanupEntity::new)
                  .setKey("200")
                  .setKeyField(AuditLogTemplate.PROCESS_INSTANCE_KEY)
                  .setEntityType(AuditLogEntityType.PROCESS_INSTANCE)
                  .setPartitionId(PARTITION_ID);

          final var auditLog1 = create(AuditLogEntity::new);
          auditLog1.setProcessInstanceKey(100L);
          auditLog1.setEntityType(AuditLogEntityType.PROCESS_INSTANCE);

          final var auditLog2 = create(AuditLogEntity::new);
          auditLog2.setProcessInstanceKey(200L);
          auditLog2.setEntityType(AuditLogEntityType.PROCESS_INSTANCE);

          store(cleanupIndex, client, cleanupEntity1);
          store(cleanupIndex, client, cleanupEntity2);
          store(auditLogTemplate, client, auditLog1);
          store(auditLogTemplate, client, auditLog2);
          client.refresh();

          // when
          final var archived = job.execute();

          // then - should archive both audit logs and delete both cleanup entries
          assertThat(archived).succeedsWithin(Duration.ofSeconds(30L)).isEqualTo(4);

          // then - both audit logs should be moved
          client.refresh();
          verifyMoved(auditLogTemplate, client, auditLog1, todayDateSuffix());
          verifyMoved(auditLogTemplate, client, auditLog2, todayDateSuffix());

          // then - both cleanup entries should be deleted
          verifyCleanupDeleted(cleanupIndex, client, cleanupEntity1);
          verifyCleanupDeleted(cleanupIndex, client, cleanupEntity2);
        });
  }

  private String todayDateSuffix() {
    // test is using a fixed instant clock, so this should be safe to do
    return LocalDate.ofInstant(NOW, ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE);
  }

  private void verifyCleanupDeleted(
      final IndexDescriptor cleanupIndex,
      final SearchClientAdapter client,
      final AuditLogCleanupEntity entity)
      throws IOException {
    final var result =
        client.get(
            entity.getId(), cleanupIndex.getFullQualifiedName(), AuditLogCleanupEntity.class);
    assertThat(result)
        .describedAs(
            "Expected cleanup entity %s to be deleted from %s",
            entity.getId(), cleanupIndex.getFullQualifiedName())
        .isNull();
  }

  private void verifyCleanupNotDeleted(
      final IndexDescriptor cleanupIndex,
      final SearchClientAdapter client,
      final AuditLogCleanupEntity entity)
      throws IOException {
    final var result =
        client.get(
            entity.getId(), cleanupIndex.getFullQualifiedName(), AuditLogCleanupEntity.class);
    assertThat(result)
        .describedAs(
            "Expected cleanup entity %s to still be in %s",
            entity.getId(), cleanupIndex.getFullQualifiedName())
        .isNotNull();
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
