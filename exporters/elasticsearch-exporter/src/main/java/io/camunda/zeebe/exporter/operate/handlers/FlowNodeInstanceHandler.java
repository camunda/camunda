package io.camunda.zeebe.exporter.operate.handlers;

import static io.camunda.operate.zeebeimport.util.ImportUtil.tenantOrDefault;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_ACTIVATING;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_COMPLETED;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_TERMINATED;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.camunda.operate.entities.FlowNodeInstanceEntity;
import io.camunda.operate.entities.FlowNodeState;
import io.camunda.operate.entities.FlowNodeType;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.store.BatchRequest;
import io.camunda.operate.util.ConversionUtils;
import io.camunda.operate.util.DateUtil;
import io.camunda.zeebe.exporter.operate.ExportHandler;
import io.camunda.zeebe.exporter.operate.schema.templates.FlowNodeInstanceTemplate;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;

public class FlowNodeInstanceHandler
    implements ExportHandler<FlowNodeInstanceEntity, ProcessInstanceRecordValue> {

  private static final Logger logger = LoggerFactory.getLogger(FlowNodeInstanceHandler.class);

  private static final Set<Intent> AI_FINISH_STATES = Set.of(ELEMENT_COMPLETED, ELEMENT_TERMINATED);
  private static final Set<Intent> AI_START_STATES = Set.of(ELEMENT_ACTIVATING);

  // TODO: fix spring-wired property access
  private FlowNodeInstanceTemplate flowNodeInstanceTemplate;

  public FlowNodeInstanceHandler(FlowNodeInstanceTemplate flowNodeInstanceTemplate) {
    this.flowNodeInstanceTemplate = flowNodeInstanceTemplate;
  }

  @Override
  public ValueType getHandledValueType() {
    return ValueType.PROCESS_INSTANCE;
  }

  @Override
  public Class<FlowNodeInstanceEntity> getEntityType() {
    return FlowNodeInstanceEntity.class;
  }

  @Override
  public boolean handlesRecord(Record<ProcessInstanceRecordValue> record) {
    final ProcessInstanceRecordValue processInstanceRecordValue = record.getValue();
    final Intent intent = record.getIntent();
    return !isProcessEvent(processInstanceRecordValue)
        && (AI_START_STATES.contains(intent) || AI_FINISH_STATES.contains(intent));

  }

  private boolean isProcessEvent(ProcessInstanceRecordValue recordValue) {
    return isOfType(recordValue, BpmnElementType.PROCESS);
  }

  private boolean isOfType(ProcessInstanceRecordValue recordValue, BpmnElementType type) {
    final BpmnElementType bpmnElementType = recordValue.getBpmnElementType();
    if (bpmnElementType == null) {
      return false;
    }
    return bpmnElementType.equals(type);
  }

  @Override
  public String generateId(Record<ProcessInstanceRecordValue> record) {
    return ConversionUtils.toStringOrNull(record.getKey());
  }

  @Override
  public FlowNodeInstanceEntity createNewEntity(String id) {
    FlowNodeInstanceEntity entity = new FlowNodeInstanceEntity();

    entity.setId(id);
    // TODO: key and partition?

    return entity;
  }

  @Override
  public void updateEntity(Record<ProcessInstanceRecordValue> record,
      FlowNodeInstanceEntity entity) {

    final var recordValue = record.getValue();
    final Intent intent = record.getIntent();

    entity.setKey(record.getKey());
    entity.setId(ConversionUtils.toStringOrNull(record.getKey()));
    entity.setPartitionId(record.getPartitionId());
    entity.setFlowNodeId(recordValue.getElementId());
    entity.setProcessInstanceKey(recordValue.getProcessInstanceKey());
    entity.setProcessDefinitionKey(recordValue.getProcessDefinitionKey());
    entity.setBpmnProcessId(recordValue.getBpmnProcessId());
    entity.setTenantId(tenantOrDefault(recordValue.getTenantId()));

    // TODO: restore tree path logic
    // if (entity.getTreePath() == null) {
    //
    // String parentTreePath = getParentTreePath(record, recordValue);
    // entity.setTreePath(
    // String.join("/", parentTreePath, ConversionUtils.toStringOrNull(record.getKey())));
    // entity.setLevel(parentTreePath.split("/").length);
    // }

    if (AI_FINISH_STATES.contains(intent)) {
      if (intent.equals(ELEMENT_TERMINATED)) {
        entity.setState(FlowNodeState.TERMINATED);
      } else {
        entity.setState(FlowNodeState.COMPLETED);
      }
      entity.setEndDate(DateUtil.toOffsetDateTime(Instant.ofEpochMilli(record.getTimestamp())));
    } else {
      entity.setState(FlowNodeState.ACTIVE);
      if (AI_START_STATES.contains(intent)) {
        entity.setStartDate(DateUtil.toOffsetDateTime(Instant.ofEpochMilli(record.getTimestamp())));
        entity.setPosition(record.getPosition());
      }
    }

    entity.setType(FlowNodeType.fromZeebeBpmnElementType(
        recordValue.getBpmnElementType() == null ? null : recordValue.getBpmnElementType().name()));
  }

  @Override
  public void flush(FlowNodeInstanceEntity fniEntity, BatchRequest batchRequest)
      throws PersistenceException {
    logger.debug("Flow node instance: id {}", fniEntity.getId());
    if (canOptimizeFlowNodeInstanceIndexing(fniEntity)) {
      batchRequest.add(flowNodeInstanceTemplate.getFullQualifiedName(), fniEntity);
    } else {
      Map<String, Object> updateFields = new HashMap<>();
      updateFields.put(FlowNodeInstanceTemplate.ID, fniEntity.getId());
      updateFields.put(FlowNodeInstanceTemplate.PARTITION_ID, fniEntity.getPartitionId());
      updateFields.put(FlowNodeInstanceTemplate.TYPE, fniEntity.getType());
      updateFields.put(FlowNodeInstanceTemplate.STATE, fniEntity.getState());
      updateFields.put(FlowNodeInstanceTemplate.TREE_PATH, fniEntity.getTreePath());
      updateFields.put(FlowNodeInstanceTemplate.FLOW_NODE_ID, fniEntity.getFlowNodeId());
      updateFields.put(FlowNodeInstanceTemplate.PROCESS_DEFINITION_KEY,
          fniEntity.getProcessDefinitionKey());
      updateFields.put(FlowNodeInstanceTemplate.LEVEL, fniEntity.getLevel());
      if (fniEntity.getStartDate() != null) {
        updateFields.put(FlowNodeInstanceTemplate.START_DATE, fniEntity.getStartDate());
      }
      if (fniEntity.getEndDate() != null) {
        updateFields.put(FlowNodeInstanceTemplate.END_DATE, fniEntity.getEndDate());
      }
      if (fniEntity.getPosition() != null) {
        updateFields.put(FlowNodeInstanceTemplate.POSITION, fniEntity.getPosition());
      }
      batchRequest.upsert(flowNodeInstanceTemplate.getFullQualifiedName(), fniEntity.getId(),
          fniEntity, updateFields);
    }
  }

  // TODO: Why do we need this? Why the 2 seconds?
  private boolean canOptimizeFlowNodeInstanceIndexing(final FlowNodeInstanceEntity entity) {
    final var startDate = entity.getStartDate();
    final var endDate = entity.getEndDate();

    if (startDate != null && endDate != null) {
      // When the activating and completed/terminated events
      // for a flow node instance is part of the same batch
      // to import, then we can try to optimize the request
      // by submitting an IndexRequest instead of a UpdateRequest.
      // In such case, the following is assumed:
      // * When the duration between start and end time is lower than
      // (or equal to) 2 seconds, then it can "safely" be assumed
      // that there was no incident in between.
      // * The 2s duration is chosen arbitrarily. However, it should
      // not be too short but not too long to avoid any negative
      // side effects with incidents.
      final var duration = Duration.between(startDate, endDate);
      return duration.getSeconds() <= 2L;
    }

    return false;
  }
  
  @Override
  public String getIndexName() {
    return flowNodeInstanceTemplate.getFullQualifiedName();
  }
}
