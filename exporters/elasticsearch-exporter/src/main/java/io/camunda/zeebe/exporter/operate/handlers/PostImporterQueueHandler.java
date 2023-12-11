package io.camunda.zeebe.exporter.operate.handlers;

import java.time.OffsetDateTime;
import io.camunda.operate.entities.post.PostImporterActionType;
import io.camunda.operate.entities.post.PostImporterQueueEntity;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.store.BatchRequest;
import io.camunda.zeebe.exporter.operate.ExportHandler;
import io.camunda.zeebe.exporter.operate.schema.templates.PostImporterQueueTemplate;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;

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
    return String.format("%d-%s", record.getKey(), record.getIntent().name());
  }

  @Override
  public PostImporterQueueEntity createNewEntity(String id) {
    return new PostImporterQueueEntity().setId(id);
  }

  @Override
  public void updateEntity(Record<IncidentRecordValue> record, PostImporterQueueEntity entity) {
    String intent = record.getIntent().name();
    entity.setActionType(PostImporterActionType.INCIDENT).setIntent(intent).setKey(record.getKey())
        .setPosition(record.getPosition()).setCreationTime(OffsetDateTime.now())
        .setPartitionId(record.getPartitionId())
        .setProcessInstanceKey(record.getValue().getProcessInstanceKey());

  }

  @Override
  public void flush(PostImporterQueueEntity entity, BatchRequest batchRequest)
      throws PersistenceException {
    batchRequest.add(postImporterQueueTemplate.getFullQualifiedName(), entity);
  }
}
