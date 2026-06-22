/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.entities.post.PostImporterActionType;
import io.camunda.webapps.schema.entities.post.PostImporterQueueEntity;
import io.camunda.zeebe.exporter.common.cache.ExporterEntityCache;
import io.camunda.zeebe.exporter.common.cache.process.CachedProcessEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceMigrationIntent;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceMigrationRecordValue;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Enqueues a backfill entry into the post-importer queue when a process instance is migrated from a
 * process definition with no user tasks to one that has user tasks. The backfill is only needed
 * when {@code skipVariableWriteWithoutUserTasks} is enabled, because in that case the instance's
 * variables were never written to the {@code tasklist-task} index.
 *
 * <p>The entry is consumed by {@link
 * io.camunda.exporter.tasks.migrationvariablebackfill.MigrationVariableBackfillTask}.
 */
public class PostImporterQueueFromProcessInstanceMigrationHandler
    implements ExportHandler<PostImporterQueueEntity, ProcessInstanceMigrationRecordValue> {

  private final String indexName;
  private final ExporterEntityCache<Long, CachedProcessEntity> processCache;
  private final boolean skipVariableWriteWithoutUserTasks;

  public PostImporterQueueFromProcessInstanceMigrationHandler(
      final String indexName,
      final ExporterEntityCache<Long, CachedProcessEntity> processCache,
      final boolean skipVariableWriteWithoutUserTasks) {
    this.indexName = indexName;
    this.processCache = processCache;
    this.skipVariableWriteWithoutUserTasks = skipVariableWriteWithoutUserTasks;
  }

  @Override
  public ValueType getHandledValueType() {
    return ValueType.PROCESS_INSTANCE_MIGRATION;
  }

  @Override
  public Class<PostImporterQueueEntity> getEntityType() {
    return PostImporterQueueEntity.class;
  }

  @Override
  public boolean handlesRecord(final Record<ProcessInstanceMigrationRecordValue> record) {
    if (!ProcessInstanceMigrationIntent.MIGRATED.equals(record.getIntent())) {
      return false;
    }
    if (!skipVariableWriteWithoutUserTasks) {
      return false;
    }
    final long sourceKey = record.getValue().getProcessDefinitionKey();
    final long targetKey = record.getValue().getTargetProcessDefinitionKey();
    final var sourceProcess = processCache.get(sourceKey);
    final var targetProcess = processCache.get(targetKey);
    // Backfill is needed only when source definitely had no user tasks but target has them.
    // On a cache miss for either process we skip (safe default: assume backfill not needed).
    return sourceProcess.isPresent()
        && !sourceProcess.get().hasUserTasks()
        && targetProcess.isPresent()
        && targetProcess.get().hasUserTasks();
  }

  @Override
  public List<String> generateIds(final Record<ProcessInstanceMigrationRecordValue> record) {
    // Use the processInstanceKey as the queue entry ID so that multiple qualifying migrations
    // of the same instance overwrite the same entry rather than accumulate unboundedly.
    return List.of(String.valueOf(record.getValue().getProcessInstanceKey()));
  }

  @Override
  public PostImporterQueueEntity createNewEntity(final String id) {
    return new PostImporterQueueEntity().setId(id);
  }

  @Override
  public void updateEntity(
      final Record<ProcessInstanceMigrationRecordValue> record,
      final PostImporterQueueEntity entity) {
    final var value = record.getValue();
    entity
        .setId(String.valueOf(value.getProcessInstanceKey()))
        .setActionType(PostImporterActionType.PROCESS_INSTANCE_MIGRATION)
        .setKey(value.getProcessInstanceKey())
        .setProcessInstanceKey(value.getProcessInstanceKey())
        .setPosition(record.getPosition())
        .setCreationTime(OffsetDateTime.now())
        .setPartitionId(record.getPartitionId())
        .setIntent(ProcessInstanceMigrationIntent.MIGRATED.name());
    final long rootProcessInstanceKey = value.getRootProcessInstanceKey();
    if (rootProcessInstanceKey > 0) {
      entity.setRootProcessInstanceKey(rootProcessInstanceKey);
    }
  }

  @Override
  public void flush(final PostImporterQueueEntity entity, final BatchRequest batchRequest) {
    batchRequest.add(indexName, entity);
  }

  @Override
  public String getIndexName() {
    return indexName;
  }
}
