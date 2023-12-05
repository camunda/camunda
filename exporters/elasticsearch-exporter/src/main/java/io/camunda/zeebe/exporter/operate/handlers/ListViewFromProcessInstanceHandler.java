package io.camunda.zeebe.exporter.operate.handlers;

import static io.camunda.operate.zeebeimport.util.ImportUtil.tenantOrDefault;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_ACTIVATING;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_COMPLETED;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_TERMINATED;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.camunda.operate.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.operate.entities.listview.ProcessInstanceState;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.store.BatchRequest;
import io.camunda.operate.util.DateUtil;
import io.camunda.zeebe.exporter.operate.ExportHandler;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;

public class ListViewFromProcessInstanceHandler
    implements ExportHandler<ProcessInstanceForListViewEntity, ProcessInstanceRecordValue> {

  private static final Logger logger = LoggerFactory.getLogger(ListViewFromProcessInstanceHandler.class);
  private static final Set<String> PI_AND_AI_START_STATES = new HashSet<>();
  private static final Set<String> PI_AND_AI_FINISH_STATES = new HashSet<>();
  protected static final int EMPTY_PARENT_PROCESS_INSTANCE_ID = -1;

  private ListViewTemplate listViewTemplate = new ListViewTemplate();
  
  static {
    PI_AND_AI_START_STATES.add(ELEMENT_ACTIVATING.name());
    PI_AND_AI_FINISH_STATES.add(ELEMENT_COMPLETED.name());
    PI_AND_AI_FINISH_STATES.add(ELEMENT_TERMINATED.name());
  }
  
  @Override
  public ValueType handlesValueType() {
    return ValueType.PROCESS_INSTANCE;
  }

  @Override
  public boolean handlesRecord(Record<ProcessInstanceRecordValue> record) {
    return shouldProcessProcessInstanceRecord(record) && isProcessEvent(record.getValue());
  }

  @Override
  public String generateId(Record<ProcessInstanceRecordValue> record) {
    return String.valueOf(record.getValue().getProcessInstanceKey());
  }

  @Override
  public ProcessInstanceForListViewEntity createNewEntity(String id) {
    return new ProcessInstanceForListViewEntity().setId(id);
  }

  @Override
  public void updateEntity(Record<ProcessInstanceRecordValue> record,
      ProcessInstanceForListViewEntity piEntity) {
    
    if (isProcessInstanceTerminated(record)) {
      //resolve corresponding operation
      
      // TODO: complete operations again; consider doing this in a separate handler?
//          operationsManager.completeOperation(null, record.getKey(), null, OperationType.CANCEL_PROCESS_INSTANCE, batchRequest);
    }

    final var recordValue = record.getValue();
    final var intentStr = record.getIntent().name();

    piEntity
        .setProcessInstanceKey(recordValue.getProcessInstanceKey())
        .setKey(recordValue.getProcessInstanceKey())
        .setTenantId(tenantOrDefault(recordValue.getTenantId()))
        .setPartitionId(record.getPartitionId())
        .setProcessDefinitionKey(recordValue.getProcessDefinitionKey())
        .setBpmnProcessId(recordValue.getBpmnProcessId())
        .setProcessVersion(recordValue.getVersion())
        // TODO: restore process name resolving
//        .setProcessName(processCache.getProcessNameOrDefaultValue(piEntity.getProcessDefinitionKey(), recordValue.getBpmnProcessId()))
        ;

    OffsetDateTime timestamp = DateUtil.toOffsetDateTime(Instant.ofEpochMilli(record.getTimestamp()));
    final boolean isRootProcessInstance = recordValue.getParentProcessInstanceKey() == EMPTY_PARENT_PROCESS_INSTANCE_ID;
    if (intentStr.equals(ELEMENT_COMPLETED.name()) || intentStr.equals(ELEMENT_TERMINATED.name())) {
      
      // TODO: restore metrics
//      importBatch.incrementFinishedWiCount();
      piEntity.setEndDate(timestamp);
      if (intentStr.equals(ELEMENT_TERMINATED.name())) {
        piEntity.setState(ProcessInstanceState.CANCELED);
      } else {
        piEntity.setState(ProcessInstanceState.COMPLETED);
      }
    } else if (intentStr.equals(ELEMENT_ACTIVATING.name())) {
      piEntity.setStartDate(timestamp)
          .setState(ProcessInstanceState.ACTIVE);
      // TODO: restore metrics
//      if(isRootProcessInstance){
//        registerStartedRootProcessInstance(piEntity, batchRequest, timestamp);
//      }
    } else {
      piEntity.setState(ProcessInstanceState.ACTIVE);
    }
    //call activity related fields
    if (!isRootProcessInstance) {
      piEntity
          .setParentProcessInstanceKey(recordValue.getParentProcessInstanceKey())
          .setParentFlowNodeInstanceKey(recordValue.getParentElementInstanceKey());
      // TODO: restore tree path logic
//      if (piEntity.getTreePath() == null) {
//        final String treePath = getTreePathForCalledProcess(recordValue);
//        piEntity.setTreePath(treePath);
//        treePathMap.put(String.valueOf(record.getKey()), treePath);
//      }
    }
    // TODO: restore tree path
//    if (piEntity.getTreePath() == null) {
//      final String treePath = new TreePath().startTreePath(
//          ConversionUtils.toStringOrNull(recordValue.getProcessInstanceKey())).toString();
//      piEntity.setTreePath(treePath);
//      getTreePathCache()
//          .put(ConversionUtils.toStringOrNull(recordValue.getProcessInstanceKey()), treePath);
//    }
  }
  
  private boolean shouldProcessProcessInstanceRecord(final Record<ProcessInstanceRecordValue> record) {
    final var intent = record.getIntent().name();
    return PI_AND_AI_START_STATES.contains(intent) || PI_AND_AI_FINISH_STATES.contains(intent);
  }
  
  // TODO: this is duplicated in other handlers
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
  

  private boolean isProcessInstanceTerminated(final Record<ProcessInstanceRecordValue> record) {
    return record.getIntent() == ELEMENT_TERMINATED;
  }
  
  @Override
  public void flush(ProcessInstanceForListViewEntity piEntity, BatchRequest batchRequest)
      throws PersistenceException {
    
    logger.debug("Process instance for list view: id {}", piEntity.getId());

    if (canOptimizeProcessInstanceIndexing(piEntity)) {
      batchRequest.add(listViewTemplate.getFullQualifiedName(), piEntity);
    } else {
      Map<String, Object> updateFields = new HashMap<>();
      if (piEntity.getStartDate() != null) {
        updateFields.put(ListViewTemplate.START_DATE, piEntity.getStartDate());
      }
      if (piEntity.getEndDate() != null) {
        updateFields.put(ListViewTemplate.END_DATE, piEntity.getEndDate());
      }
      updateFields.put(ListViewTemplate.PROCESS_NAME, piEntity.getProcessName());
      updateFields.put(ListViewTemplate.PROCESS_VERSION, piEntity.getProcessVersion());
      if (piEntity.getState() != null) {
        updateFields.put(ListViewTemplate.STATE, piEntity.getState());
      }

      batchRequest.upsert(listViewTemplate.getFullQualifiedName(), piEntity.getId(), piEntity, updateFields);
    }
    
  }
  
  // TODO: put this logic in a single place
  // check if it is still needed
  private boolean canOptimizeProcessInstanceIndexing(final ProcessInstanceForListViewEntity entity) {
    final var startDate = entity.getStartDate();
    final var endDate = entity.getEndDate();

    if (startDate != null && endDate != null) {
      // When the activating and completed/terminated events
      // for a process instance is part of the same batch
      // to import, then we can try to optimize the request
      // by submitting an IndexRequest instead of a UpdateRequest.
      // In such case, the following is assumed:
      // * When the duration between start and end time is lower than
      //   (or equal to) 2 seconds, then it can safely be assumed that
      //   there was no incident in between.
      // * The 2s duration is chosen arbitrarily. It should not be
      //   too short but not too long to avoid any negative side.
      final var duration = Duration.between(startDate, endDate);
      return duration.getSeconds() <= 2L;
    }

    return false;
  }

}
