/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import static io.camunda.exporter.utils.ExporterUtil.tenantOrDefault;

import io.camunda.exporter.ExporterMetadata;
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

  private final ExporterMetadata exporterMetadata;

  public MessageSubscriptionFromProcessMessageSubscriptionHandler(
      final String indexName, final ExporterMetadata exporterMetadata) {
    super(indexName);
    this.exporterMetadata = exporterMetadata;
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
    if (record.getIntent().equals(ProcessMessageSubscriptionIntent.CREATED)) {
      exporterMetadata.setFirstProcessMessageSubscriptionKey(record.getKey());
    }
    if (exporterMetadata.keyIsBeforeFirstProcessMessageSubscriptionKey(record.getKey())) {
      return List.of(
          String.format(
              ID_PATTERN,
              record.getValue().getProcessInstanceKey(),
              record.getValue().getElementInstanceKey()));
    }
    return List.of(String.valueOf(record.getKey()));
  }

  @Override
  public void updateEntity(
      final Record<ProcessMessageSubscriptionRecordValue> record,
      final MessageSubscriptionEntity entity) {

    final ProcessMessageSubscriptionRecordValue recordValue = record.getValue();

    entity.setPositionProcessMessageSubscription(record.getPosition());

    loadEventGeneralData(record, entity);

    final long processInstanceKey = recordValue.getProcessInstanceKey();
    if (processInstanceKey > 0) {
      entity.setProcessInstanceKey(processInstanceKey);
    }

    final long processDefinitionKey = recordValue.getProcessDefinitionKey();
    if (processDefinitionKey > 0) {
      entity.setProcessDefinitionKey(processDefinitionKey);
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

    final long rootProcessInstanceKey = recordValue.getRootProcessInstanceKey();
    if (rootProcessInstanceKey > 0) {
      entity.setRootProcessInstanceKey(rootProcessInstanceKey);
    }
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
