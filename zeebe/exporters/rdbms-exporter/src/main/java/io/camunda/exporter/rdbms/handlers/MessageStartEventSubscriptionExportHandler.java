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
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.MessageStartEventSubscriptionIntent;
import io.camunda.zeebe.protocol.record.value.MessageStartEventSubscriptionRecordValue;
import java.time.Instant;
import java.util.Set;

public class MessageStartEventSubscriptionExportHandler
    implements RdbmsExportHandler<MessageStartEventSubscriptionRecordValue> {

  private static final Set<Intent> SUPPORTED_INTENTS =
      Set.of(
          MessageStartEventSubscriptionIntent.CREATED,
          MessageStartEventSubscriptionIntent.DELETED);

  private final MessageSubscriptionWriter messageSubscriptionWriter;
  private final ExporterEntityCache<Long, CachedProcessEntity> processCache;

  public MessageStartEventSubscriptionExportHandler(
      final MessageSubscriptionWriter messageSubscriptionWriter,
      final ExporterEntityCache<Long, CachedProcessEntity> processCache) {
    this.messageSubscriptionWriter = messageSubscriptionWriter;
    this.processCache = processCache;
  }

  @Override
  public boolean canExport(final Record<MessageStartEventSubscriptionRecordValue> record) {
    return SUPPORTED_INTENTS.contains(record.getIntent());
  }

  @Override
  public void export(final Record<MessageStartEventSubscriptionRecordValue> record) {
    switch (record.getIntent()) {
      case MessageStartEventSubscriptionIntent.CREATED:
        messageSubscriptionWriter.create(map(record));
        break;
      case MessageStartEventSubscriptionIntent.DELETED:
        messageSubscriptionWriter.update(map(record));
        break;
      default:
        // do nothing
    }
  }

  private MessageSubscriptionDbModel map(
      final Record<MessageStartEventSubscriptionRecordValue> record) {
    final MessageStartEventSubscriptionRecordValue value = record.getValue();
    final var processDefinitionKey = value.getProcessDefinitionKey();
    final var cachedProcess = processCache.get(processDefinitionKey);
    return new MessageSubscriptionDbModel.Builder()
        .messageSubscriptionKey(record.getKey())
        .processDefinitionId(value.getBpmnProcessId())
        .processDefinitionKey(processDefinitionKey)
        .processDefinitionName(cachedProcess.map(CachedProcessEntity::name).orElse(null))
        .processDefinitionVersion(cachedProcess.map(CachedProcessEntity::version).orElse(null))
        .flowNodeId(value.getStartEventId())
        .messageSubscriptionState(MessageSubscriptionState.valueOf(record.getIntent().name()))
        .messageSubscriptionType(MessageSubscriptionType.START_EVENT_SUBSCRIPTION)
        .dateTime(toOffsetDateTime(Instant.ofEpochMilli(record.getTimestamp())))
        .messageName(value.getMessageName())
        .tenantId(value.getTenantId())
        .partitionId(record.getPartitionId())
        .build();
  }
}
