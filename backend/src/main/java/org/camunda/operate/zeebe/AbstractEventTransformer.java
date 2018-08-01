package org.camunda.operate.zeebe;

import org.camunda.operate.entities.EventEntity;
import org.camunda.operate.entities.EventSourceType;
import org.camunda.operate.entities.EventType;
import org.camunda.operate.util.DateUtil;
import io.zeebe.client.api.record.Record;
import io.zeebe.client.api.record.RecordMetadata;


public abstract class AbstractEventTransformer {

  protected void loadEventGeneralData(Record record, EventEntity eventEntity) {
    RecordMetadata metadata = record.getMetadata();

    eventEntity.setId(String.valueOf(metadata.getPosition()));
    eventEntity.setEventSourceType(EventSourceType.fromZeebeValueType(metadata.getValueType()));
    eventEntity.setDateTime(DateUtil.toOffsetDateTime(metadata.getTimestamp()));
    eventEntity.setEventType(EventType.fromZeebeIntent(metadata.getIntent()));
  }
}
