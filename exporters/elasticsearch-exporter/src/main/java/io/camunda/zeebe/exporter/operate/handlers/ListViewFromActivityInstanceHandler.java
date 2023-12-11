package io.camunda.zeebe.exporter.operate.handlers;

import static io.camunda.operate.zeebeimport.util.ImportUtil.tenantOrDefault;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_ACTIVATING;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_COMPLETED;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_TERMINATED;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.camunda.operate.entities.FlowNodeState;
import io.camunda.operate.entities.FlowNodeType;
import io.camunda.operate.entities.listview.FlowNodeInstanceForListViewEntity;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.store.BatchRequest;
import io.camunda.operate.util.ConversionUtils;
import io.camunda.zeebe.exporter.operate.ExportHandler;
import io.camunda.zeebe.exporter.operate.schema.templates.ListViewTemplate;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;

public class ListViewFromActivityInstanceHandler
    implements ExportHandler<FlowNodeInstanceForListViewEntity, ProcessInstanceRecordValue> {

  // TODO: unify with ListViewFromProcessInstanceHandler and get rid of code duplication

  private static final Logger logger =
      LoggerFactory.getLogger(ListViewFromActivityInstanceHandler.class);
  private static final Set<String> PI_AND_AI_START_STATES = new HashSet<>();
  private static final Set<String> PI_AND_AI_FINISH_STATES = new HashSet<>();

  static {
    PI_AND_AI_START_STATES.add(ELEMENT_ACTIVATING.name());
    PI_AND_AI_FINISH_STATES.add(ELEMENT_COMPLETED.name());
    PI_AND_AI_FINISH_STATES.add(ELEMENT_TERMINATED.name());
  }

  private ListViewTemplate listViewTemplate;

  public ListViewFromActivityInstanceHandler(ListViewTemplate listViewTemplate) {
    this.listViewTemplate = listViewTemplate;
  }

  @Override
  public ValueType getHandledValueType() {
    return ValueType.PROCESS_INSTANCE;
  }

  @Override
  public Class<FlowNodeInstanceForListViewEntity> getEntityType() {
    return FlowNodeInstanceForListViewEntity.class;
  }

  @Override
  public boolean handlesRecord(Record<ProcessInstanceRecordValue> record) {
    return shouldProcessProcessInstanceRecord(record) && !isProcessEvent(record.getValue());
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

  private boolean shouldProcessProcessInstanceRecord(
      final Record<ProcessInstanceRecordValue> record) {
    final var intent = record.getIntent().name();
    return PI_AND_AI_START_STATES.contains(intent) || PI_AND_AI_FINISH_STATES.contains(intent);
  }

  @Override
  public String generateId(Record<ProcessInstanceRecordValue> record) {
    return ConversionUtils.toStringOrNull(record.getKey());
  }

  @Override
  public FlowNodeInstanceForListViewEntity createNewEntity(String id) {
    return new FlowNodeInstanceForListViewEntity().setId(id);
  }

  @Override
  public void updateEntity(Record<ProcessInstanceRecordValue> record,
      FlowNodeInstanceForListViewEntity entity) {

    final var recordValue = record.getValue();
    final var intentStr = record.getIntent().name();

    entity.setKey(record.getKey());
    entity.setId(ConversionUtils.toStringOrNull(record.getKey()));
    entity.setPartitionId(record.getPartitionId());
    entity.setActivityId(recordValue.getElementId());
    entity.setProcessInstanceKey(recordValue.getProcessInstanceKey());
    entity.setTenantId(tenantOrDefault(recordValue.getTenantId()));

    if (PI_AND_AI_FINISH_STATES.contains(intentStr)) {
      entity.setEndTime(record.getTimestamp());
      if (intentStr.equals(ELEMENT_TERMINATED.name())) {
        entity.setActivityState(FlowNodeState.TERMINATED);
      } else {
        entity.setActivityState(FlowNodeState.COMPLETED);
      }
    } else {
      entity.setActivityState(FlowNodeState.ACTIVE);
      if (PI_AND_AI_START_STATES.contains(intentStr)) {
        entity.setStartTime(record.getTimestamp());
      }
    }

    entity.setActivityType(FlowNodeType.fromZeebeBpmnElementType(
        recordValue.getBpmnElementType() == null ? null : recordValue.getBpmnElementType().name()));

    // TODO: restore call activity id cache if needed
    // if (FlowNodeType.CALL_ACTIVITY.equals(entity.getActivityType())) {
    // getCallActivityIdCache().put(entity.getId(), entity.getActivityId());
    // }

    // set parent
    Long processInstanceKey = recordValue.getProcessInstanceKey();
    entity.getJoinRelation().setParent(processInstanceKey);



  }

  @Override
  public void flush(FlowNodeInstanceForListViewEntity actEntity, BatchRequest batchRequest)
      throws PersistenceException {

    Long processInstanceKey = actEntity.getProcessInstanceKey();

    logger.debug("Flow node instance for list view: id {}", actEntity.getId());
    if (canOptimizeFlowNodeInstanceIndexing(actEntity)) {
      batchRequest.addWithRouting(listViewTemplate.getFullQualifiedName(), actEntity,
          processInstanceKey.toString());
    } else {
      Map<String, Object> updateFields = new HashMap<>();
      updateFields.put(ListViewTemplate.ID, actEntity.getId());
      updateFields.put(ListViewTemplate.PARTITION_ID, actEntity.getPartitionId());
      updateFields.put(ListViewTemplate.ACTIVITY_TYPE, actEntity.getActivityType());
      updateFields.put(ListViewTemplate.ACTIVITY_STATE, actEntity.getActivityState());

      batchRequest.upsertWithRouting(listViewTemplate.getFullQualifiedName(), actEntity.getId(),
          actEntity, updateFields, processInstanceKey.toString());
    }
  }

  private boolean canOptimizeFlowNodeInstanceIndexing(
      final FlowNodeInstanceForListViewEntity entity) {
    final var startTime = entity.getStartTime();
    final var endTime = entity.getEndTime();

    if (startTime != null && endTime != null) {
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
      return (endTime - startTime) <= 2000L;
    }

    return false;
  }

}
