/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.operate.exporter.handlers;

import io.camunda.operate.entities.post.PostImporterActionType;
import io.camunda.operate.entities.post.PostImporterQueueEntity;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.schema.templates.PostImporterQueueTemplate;
import io.camunda.operate.store.elasticsearch.NewElasticsearchBatchRequest;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import java.time.OffsetDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PostImporterQueueFromIncidentHandler
    implements ExportHandler<PostImporterQueueEntity, IncidentRecordValue> {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(PostImporterQueueFromIncidentHandler.class);

  private final PostImporterQueueTemplate postImporterQueueTemplate;

  public PostImporterQueueFromIncidentHandler(PostImporterQueueTemplate postImporterQueueTemplate) {
    this.postImporterQueueTemplate = postImporterQueueTemplate;
  }

  @Override
  public ValueType getHandledValueType() {
    return ValueType.INCIDENT;
  }

  @Override
  public Class<PostImporterQueueEntity> getEntityType() {
    return PostImporterQueueEntity.class;
  }

  @Override
  public boolean handlesRecord(Record<IncidentRecordValue> record) {
    return true;
  }

  @Override
  public List<String> generateIds(Record<IncidentRecordValue> record) {
    String intent = record.getIntent().name();
    if (intent.equals(IncidentIntent.MIGRATED.toString())) {
      intent = IncidentIntent.CREATED.toString();
    }
    return List.of(String.format("%d-%s", record.getKey(), intent));
  }

  @Override
  public PostImporterQueueEntity createNewEntity(String id) {
    return new PostImporterQueueEntity().setId(id);
  }

  @Override
  public void updateEntity(Record<IncidentRecordValue> record, PostImporterQueueEntity entity) {

    final IncidentRecordValue recordValue = record.getValue();
    String intent = record.getIntent().name();
    if (intent.equals(IncidentIntent.MIGRATED.toString())) {
      intent = IncidentIntent.CREATED.toString();
    }
    entity
        // id = incident key + intent
        .setId(String.format("%d-%s", record.getKey(), intent))
        .setActionType(PostImporterActionType.INCIDENT)
        .setIntent(intent)
        .setKey(record.getKey())
        .setPosition(record.getPosition())
        .setCreationTime(OffsetDateTime.now())
        .setPartitionId(record.getPartitionId())
        .setProcessInstanceKey(recordValue.getProcessInstanceKey());
  }

  @Override
  public void flush(PostImporterQueueEntity entity, NewElasticsearchBatchRequest batchRequest)
      throws PersistenceException {
    batchRequest.add(postImporterQueueTemplate.getFullQualifiedName(), entity);
  }

  @Override
  public String getIndexName() {
    return postImporterQueueTemplate.getFullQualifiedName();
  }
}
