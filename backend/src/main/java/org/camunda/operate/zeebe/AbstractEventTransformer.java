package org.camunda.operate.zeebe;

import org.camunda.operate.entities.EventEntity;
import org.camunda.operate.entities.EventSourceType;
import org.camunda.operate.entities.EventType;
import org.camunda.operate.entities.OperateEntity;
import org.camunda.operate.util.DateUtil;
import io.zeebe.client.api.record.Record;


public abstract class AbstractEventTransformer {

  protected void updateMetadataFields(OperateEntity operateEntity, Record zeebeRecord) {
    operateEntity.setPartitionId(zeebeRecord.getMetadata().getPartitionId());
    operateEntity.setPosition(zeebeRecord.getMetadata().getPosition());
  }

  protected void loadEventGeneralData(Record record, EventEntity eventEntity) {
    eventEntity.setId(String.valueOf(record.getMetadata().getPosition()));
    eventEntity.setEventSourceType(EventSourceType.fromZeebeValueType(record.getMetadata().getValueType()));
    eventEntity.setDateTime(DateUtil.toOffsetDateTime(record.getMetadata().getTimestamp()));
    eventEntity.setEventType(EventType.fromZeebeIntent(record.getMetadata().getIntent()));
  }
}
