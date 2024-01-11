/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.exporter.operate.handlers;

import io.camunda.operate.entities.post.PostImporterActionType;
import io.camunda.operate.entities.post.PostImporterQueueEntity;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.zeebe.exporter.operate.ExportHandler;
import io.camunda.zeebe.exporter.operate.OperateElasticsearchBulkRequest;
import io.camunda.zeebe.exporter.operate.schema.templates.PostImporterQueueTemplate;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import java.time.OffsetDateTime;

public class PostImporterQueueHandler
    implements ExportHandler<PostImporterQueueEntity, IncidentRecordValue> {

  private PostImporterQueueTemplate postImporterQueueTemplate;

  public PostImporterQueueHandler(PostImporterQueueTemplate postImporterQueueTemplate) {
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
  public String generateId(Record<IncidentRecordValue> record) {

    final StringBuilder sb = new StringBuilder();
    sb.append(record.getKey());
    sb.append("-");
    sb.append(record.getIntent().name());

    return sb.toString();
  }

  @Override
  public PostImporterQueueEntity createNewEntity(String id) {
    return new PostImporterQueueEntity().setId(id);
  }

  @Override
  public void updateEntity(Record<IncidentRecordValue> record, PostImporterQueueEntity entity) {
    final String intent = record.getIntent().name();
    entity
        .setActionType(PostImporterActionType.INCIDENT)
        .setIntent(intent)
        .setKey(record.getKey())
        .setPosition(record.getPosition())
        .setCreationTime(OffsetDateTime.now())
        .setPartitionId(record.getPartitionId())
        .setProcessInstanceKey(record.getValue().getProcessInstanceKey());
  }

  @Override
  public void flush(PostImporterQueueEntity entity, OperateElasticsearchBulkRequest batchRequest)
      throws PersistenceException {
    batchRequest.index(postImporterQueueTemplate.getFullQualifiedName(), entity);
  }

  @Override
  public String getIndexName() {
    return postImporterQueueTemplate.getFullQualifiedName();
  }
}
