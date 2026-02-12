/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.handlers.batchoperation;

import io.camunda.db.rdbms.write.domain.BatchOperationDbModel;
import io.camunda.db.rdbms.write.service.BatchOperationWriter;
import io.camunda.exporter.rdbms.RdbmsExportHandler;
import io.camunda.search.entities.AuditLogEntity.AuditLogActorType;
import io.camunda.search.entities.BatchOperationEntity.BatchOperationState;
import io.camunda.search.entities.BatchOperationType;
import io.camunda.zeebe.exporter.common.auditlog.AuditLogInfo.AuditLogActor;
import io.camunda.zeebe.exporter.common.cache.ExporterEntityCache;
import io.camunda.zeebe.exporter.common.cache.batchoperation.CachedBatchOperationEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.BatchOperationIntent;
import io.camunda.zeebe.protocol.record.value.BatchOperationCreationRecordValue;
import io.camunda.zeebe.util.SemanticVersion;

/**
 * Exports a batch operation creation record to the database. Where the creation in the db just
 * happens, if it's not already existing. (Could be if another partition was faster)
 */
public class BatchOperationCreatedExportHandler
    implements RdbmsExportHandler<BatchOperationCreationRecordValue> {

  private final BatchOperationWriter batchOperationWriter;
  private final ExporterEntityCache<String, CachedBatchOperationEntity> batchOperationCache;
  private final ActorInfoExtractor actorInfoExtractor = new ActorInfoExtractor();

  public BatchOperationCreatedExportHandler(
      final BatchOperationWriter batchOperationWriter,
      final ExporterEntityCache<String, CachedBatchOperationEntity> batchOperationCache) {
    this.batchOperationWriter = batchOperationWriter;
    this.batchOperationCache = batchOperationCache;
  }

  @Override
  public boolean canExport(final Record<BatchOperationCreationRecordValue> record) {
    return record.getValueType() == ValueType.BATCH_OPERATION_CREATION
        && record.getIntent().equals(BatchOperationIntent.CREATED);
  }

  @Override
  public void export(final Record<BatchOperationCreationRecordValue> record) {
    batchOperationWriter.createIfNotAlreadyExists(map(record));
    batchOperationCache.put(
        String.valueOf(record.getKey()),
        new CachedBatchOperationEntity(
            String.valueOf(record.getValue().getBatchOperationKey()),
            BatchOperationType.valueOf(record.getValue().getBatchOperationType().name())));
  }

  private BatchOperationDbModel map(final Record<BatchOperationCreationRecordValue> record) {
    final var value = record.getValue();
    final var actorInfo = actorInfoExtractor.extract(record);
    final String batchOperationKey = String.valueOf(record.getKey());
    return new BatchOperationDbModel.Builder()
        .batchOperationKey(batchOperationKey)
        .state(BatchOperationState.CREATED)
        .operationType(BatchOperationType.valueOf(value.getBatchOperationType().name()))
        .actorType(actorInfo.type())
        .actorId(actorInfo.id())
        .build();
  }

  private record ActorInfo(AuditLogActorType type, String id) {
    static final ActorInfo NULL_VALUES = new ActorInfo(null, null);
  }

  private static final class ActorInfoExtractor {

    private static final SemanticVersion VERSION_8_9_0 = new SemanticVersion(8, 9, 0, null, null);

    /**
     * Extracts the actor information from the record's authorizations based on the broker version.
     * Actor info is only provided for broker versions 8.9.0 and above. If the actor info is not
     * available, null values are provided.
     */
    private ActorInfo extract(final Record<BatchOperationCreationRecordValue> record) {
      final var semanticVersion = SemanticVersion.parse(record.getBrokerVersion()).orElse(null);
      if (semanticVersion == null || semanticVersion.compareTo(VERSION_8_9_0) < 0) {
        // actor info should only be set for versions 8.9.0 and above
        return ActorInfo.NULL_VALUES;
      }

      final AuditLogActor auditLogActor = AuditLogActor.of(record);
      if (auditLogActor == null) {
        return ActorInfo.NULL_VALUES;
      }

      final var actorType =
          switch (auditLogActor.actorType()) {
            case USER -> AuditLogActorType.USER;
            case CLIENT -> AuditLogActorType.CLIENT;
            case ANONYMOUS -> AuditLogActorType.ANONYMOUS;
            case UNKNOWN -> AuditLogActorType.UNKNOWN;
            case null -> null;
          };
      return new ActorInfo(actorType, auditLogActor.actorId());
    }
  }
}
