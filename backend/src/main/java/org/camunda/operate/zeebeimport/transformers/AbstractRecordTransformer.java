/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.zeebeimport.transformers;

import java.util.List;
import org.camunda.operate.entities.EventEntity;
import org.camunda.operate.entities.EventSourceType;
import org.camunda.operate.entities.EventType;
import org.camunda.operate.entities.OperateZeebeEntity;
import org.camunda.operate.util.DateUtil;
import io.zeebe.exporter.record.Record;
import io.zeebe.exporter.record.RecordMetadata;

public interface AbstractRecordTransformer {

  List<OperateZeebeEntity> convert(Record record);

  default void loadEventGeneralData(Record record, EventEntity eventEntity) {
    RecordMetadata metadata = record.getMetadata();

    eventEntity.setId(String.valueOf(record.getPosition()));
    eventEntity.setKey(record.getKey());
    eventEntity.setPartitionId(record.getMetadata().getPartitionId());
    eventEntity.setEventSourceType(EventSourceType.fromZeebeValueType(metadata.getValueType()));
    eventEntity.setDateTime(DateUtil.toOffsetDateTime(record.getTimestamp()));
    eventEntity.setEventType(EventType.fromZeebeIntent(record.getMetadata().getIntent().name()));
  }
}
