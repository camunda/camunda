/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.handlers;

import static io.camunda.exporter.rdbms.utils.DateUtil.toOffsetDateTime;

import io.camunda.db.rdbms.write.domain.MessageSubscriptionDbModel;
import io.camunda.db.rdbms.write.service.MessageSubscriptionWriter;
import io.camunda.exporter.rdbms.RdbmsExportHandler;
import io.camunda.search.entities.MessageSubscriptionEntity.MessageSubscriptionState;
import io.camunda.search.entities.MessageSubscriptionEntity.MessageSubscriptionType;
import io.camunda.zeebe.exporter.common.cache.ExporterEntityCache;
import io.camunda.zeebe.exporter.common.cache.process.CachedProcessEntity;
import io.camunda.zeebe.exporter.common.utils.ProcessCacheUtil;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.value.ProcessMessageSubscriptionRecordValue;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class MessageSubscriptionExportHandler
    implements RdbmsExportHandler<ProcessMessageSubscriptionRecordValue> {

  private static final Set<Intent> STATES =
      Set.of(
          ProcessMessageSubscriptionIntent.CORRELATED,
          ProcessMessageSubscriptionIntent.CREATED,
          ProcessMessageSubscriptionIntent.DELETED,
          ProcessMessageSubscriptionIntent.MIGRATED);
  private final MessageSubscriptionWriter messageSubscriptionWriter;
  private final ExporterEntityCache<Long, CachedProcessEntity> processCache;

  public MessageSubscriptionExportHandler(
      final MessageSubscriptionWriter messageSubscriptionWriter,
      final ExporterEntityCache<Long, CachedProcessEntity> processCache) {
    this.messageSubscriptionWriter = messageSubscriptionWriter;
    this.processCache = processCache;
  }

  @Override
  public boolean canExport(final Record<ProcessMessageSubscriptionRecordValue> record) {
    return STATES.contains(record.getIntent());
  }

  @Override
  public void export(final Record<ProcessMessageSubscriptionRecordValue> record) {
    switch (record.getIntent()) {
      case ProcessMessageSubscriptionIntent.CREATED:
        messageSubscriptionWriter.create(map(record));
        break;
      case ProcessMessageSubscriptionIntent.CORRELATED,
      ProcessMessageSubscriptionIntent.DELETED,
      ProcessMessageSubscriptionIntent.MIGRATED:
        messageSubscriptionWriter.update(map(record));
        break;
      default:
        // do nothing
    }
  }

  private MessageSubscriptionDbModel map(
      final Record<ProcessMessageSubscriptionRecordValue> record) {
    final ProcessMessageSubscriptionRecordValue value = record.getValue();
    final long pdKey = value.getProcessDefinitionKey();
    final Optional<CachedProcessEntity> cached = processCache.get(pdKey);
    final var ext =
        cached
            .map(CachedProcessEntity::elementExtensionProperties)
            .map(p -> p.get(value.getElementId()))
            .orElse(Map.of());
    return new MessageSubscriptionDbModel.Builder()
        .messageSubscriptionKey(record.getKey())
        .processDefinitionId(value.getBpmnProcessId())
        .processInstanceKey(value.getProcessInstanceKey())
        .rootProcessInstanceKey(value.getRootProcessInstanceKey())
        .flowNodeId(value.getElementId())
        .flowNodeInstanceKey(value.getElementInstanceKey())
        .processDefinitionKey(value.getProcessDefinitionKey())
        .messageSubscriptionState(MessageSubscriptionState.valueOf(record.getIntent().name()))
        .messageSubscriptionType(MessageSubscriptionType.PROCESS_EVENT)
        .dateTime(toOffsetDateTime(Instant.ofEpochMilli(record.getTimestamp())))
        .messageName(value.getMessageName())
        .correlationKey(value.getCorrelationKey())
        .tenantId(value.getTenantId())
        .partitionId(record.getPartitionId())
        .processDefinitionName(
            cached.map(CachedProcessEntity::name).filter(s -> !s.isBlank()).orElse(null))
        .processDefinitionVersion(cached.map(CachedProcessEntity::version).orElse(null))
        .extensionProperties(ext)
        .toolName(ProcessCacheUtil.getToolName(ext))
        .inboundConnectorType(ProcessCacheUtil.getInboundConnectorType(ext))
        .build();
  }
}
