/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import static io.camunda.exporter.utils.ExporterUtil.tenantOrDefault;
import static io.camunda.search.entities.MessageSubscriptionEntity.MessageSubscriptionType.START_EVENT_SUBSCRIPTION;

import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.descriptors.template.MessageSubscriptionTemplate;
import io.camunda.webapps.schema.entities.messagesubscription.MessageSubscriptionEntity;
import io.camunda.webapps.schema.entities.messagesubscription.MessageSubscriptionMetadataEntity;
import io.camunda.zeebe.exporter.common.cache.ExporterEntityCache;
import io.camunda.zeebe.exporter.common.cache.process.CachedProcessEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.MessageStartEventSubscriptionIntent;
import io.camunda.zeebe.protocol.record.value.MessageStartEventSubscriptionRecordValue;
import java.util.List;
import java.util.Set;

public class MessageSubscriptionFromMessageStartEventSubscriptionHandler
    extends AbstractEventHandler<MessageStartEventSubscriptionRecordValue> {

  public static final Set<Intent> STATES =
      Set.of(
          MessageStartEventSubscriptionIntent.CREATED, MessageStartEventSubscriptionIntent.DELETED);

  private final ExporterEntityCache<Long, CachedProcessEntity> processCache;

  public MessageSubscriptionFromMessageStartEventSubscriptionHandler(
      final String indexName, final ExporterEntityCache<Long, CachedProcessEntity> processCache) {
    super(indexName);
    this.processCache = processCache;
  }

  @Override
  public ValueType getHandledValueType() {
    return ValueType.MESSAGE_START_EVENT_SUBSCRIPTION;
  }

  @Override
  public boolean handlesRecord(final Record<MessageStartEventSubscriptionRecordValue> record) {
    return STATES.contains(record.getIntent());
  }

  @Override
  public List<String> generateIds(final Record<MessageStartEventSubscriptionRecordValue> record) {
    return List.of(String.valueOf(record.getKey()));
  }

  @Override
  public void updateEntity(
      final Record<MessageStartEventSubscriptionRecordValue> record,
      final MessageSubscriptionEntity entity) {

    final MessageStartEventSubscriptionRecordValue recordValue = record.getValue();

    loadEventGeneralData(record, entity);

    final long processDefinitionKey = recordValue.getProcessDefinitionKey();
    if (processDefinitionKey > 0) {
      entity.setProcessDefinitionKey(processDefinitionKey);
      final var cached = processCache.get(processDefinitionKey);
      entity.setProcessDefinitionName(cached.map(CachedProcessEntity::name).orElse(null));
      entity.setProcessDefinitionVersion(cached.map(CachedProcessEntity::version).orElse(null));
      entity.setExtensionProperties(
          cached
              .map(CachedProcessEntity::flowNodesMap)
              .map(m -> m.get(recordValue.getStartEventId()))
              .map(fn -> fn.extensionProperties())
              .orElse(null));
    }

    entity
        .setBpmnProcessId(recordValue.getBpmnProcessId())
        .setFlowNodeId(recordValue.getStartEventId())
        .setTenantId(tenantOrDefault(recordValue.getTenantId()))
        .setMessageSubscriptionType(START_EVENT_SUBSCRIPTION.name());

    final MessageSubscriptionMetadataEntity eventMetadata = new MessageSubscriptionMetadataEntity();
    eventMetadata.setMessageName(recordValue.getMessageName());
    entity.setMetadata(eventMetadata);
  }

  @Override
  public void flush(final MessageSubscriptionEntity entity, final BatchRequest batchRequest) {
    persistEvent(entity, MessageSubscriptionTemplate.POSITION_MESSAGE, 0L, batchRequest);
  }
}
