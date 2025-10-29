/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import static io.camunda.exporter.utils.ExporterUtil.tenantOrDefault;

import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.descriptors.template.MessageSubscriptionTemplate;
import io.camunda.webapps.schema.entities.messagesubscription.MessageSubscriptionEntity;
import io.camunda.webapps.schema.entities.messagesubscription.MessageSubscriptionMetadataEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.value.ProcessMessageSubscriptionRecordValue;
import java.util.List;
import java.util.Set;

public class MessageSubscriptionFromProcessMessageSubscriptionHandler
    extends AbstractEventHandler<ProcessMessageSubscriptionRecordValue> {

  public static final Set<Intent> STATES =
      Set.of(
          ProcessMessageSubscriptionIntent.CORRELATED,
          ProcessMessageSubscriptionIntent.CREATED,
          ProcessMessageSubscriptionIntent.DELETED,
          ProcessMessageSubscriptionIntent.MIGRATED);

  public MessageSubscriptionFromProcessMessageSubscriptionHandler(final String indexName) {
    super(indexName);
  }

  @Override
  public ValueType getHandledValueType() {
    return ValueType.PROCESS_MESSAGE_SUBSCRIPTION;
  }

  @Override
  public boolean handlesRecord(final Record<ProcessMessageSubscriptionRecordValue> record) {
    return STATES.contains(record.getIntent());
  }

  @Override
  public List<String> generateIds(final Record<ProcessMessageSubscriptionRecordValue> record) {
    return List.of(
        String.format(
            ID_PATTERN,
            record.getValue().getProcessInstanceKey(),
            record.getValue().getElementInstanceKey()));
  }

  @Override
  public void updateEntity(
      final Record<ProcessMessageSubscriptionRecordValue> record,
      final MessageSubscriptionEntity entity) {

    final ProcessMessageSubscriptionRecordValue recordValue = record.getValue();
    entity
        .setId(
            String.format(
                ID_PATTERN,
                recordValue.getProcessInstanceKey(),
                recordValue.getElementInstanceKey()))
        .setPositionProcessMessageSubscription(record.getPosition());

    loadEventGeneralData(record, entity);

    final long processInstanceKey = recordValue.getProcessInstanceKey();
    if (processInstanceKey > 0) {
      entity.setProcessInstanceKey(processInstanceKey);
    }

    entity
        .setBpmnProcessId(recordValue.getBpmnProcessId())
        .setFlowNodeId(recordValue.getElementId())
        .setTenantId(tenantOrDefault(recordValue.getTenantId()))
        .setPositionProcessMessageSubscription(record.getPosition());

    final long activityInstanceKey = recordValue.getElementInstanceKey();
    if (activityInstanceKey > 0) {
      entity.setFlowNodeInstanceKey(activityInstanceKey);
    }

    final MessageSubscriptionMetadataEntity eventMetadata = new MessageSubscriptionMetadataEntity();
    eventMetadata.setMessageName(recordValue.getMessageName());
    eventMetadata.setCorrelationKey(recordValue.getCorrelationKey());

    entity.setMetadata(eventMetadata);
  }

  @Override
  public void flush(final MessageSubscriptionEntity entity, final BatchRequest batchRequest) {
    persistEvent(
        entity,
        MessageSubscriptionTemplate.POSITION_MESSAGE,
        entity.getPositionProcessMessageSubscription(),
        batchRequest);
  }
}
