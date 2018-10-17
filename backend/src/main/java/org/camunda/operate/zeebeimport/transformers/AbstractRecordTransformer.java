package org.camunda.operate.zeebeimport.transformers;

import java.util.List;
import org.camunda.operate.entities.EventEntity;
import org.camunda.operate.entities.EventSourceType;
import org.camunda.operate.entities.EventType;
import org.camunda.operate.entities.OperateZeebeEntity;
import org.camunda.operate.util.DateUtil;
import org.camunda.operate.util.IdUtil;
import io.zeebe.exporter.record.Record;
import io.zeebe.exporter.record.RecordMetadata;

public interface AbstractRecordTransformer {

  List<OperateZeebeEntity> convert(Record record);

  default void loadEventGeneralData(Record record, EventEntity eventEntity) {
    RecordMetadata metadata = record.getMetadata();

    eventEntity.setId(IdUtil.createId(record.getPosition(), record.getMetadata().getPartitionId()));
    eventEntity.setKey(record.getKey());
    eventEntity.setPartitionId(record.getMetadata().getPartitionId());
    eventEntity.setEventSourceType(EventSourceType.fromZeebeValueType(metadata.getValueType()));
    eventEntity.setDateTime(DateUtil.toOffsetDateTime(record.getTimestamp()));
    eventEntity.setEventType(EventType.fromZeebeIntent(record.getMetadata().getIntent().name()));
  }
}
