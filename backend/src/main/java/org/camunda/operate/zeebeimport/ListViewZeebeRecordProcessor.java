package org.camunda.operate.zeebeimport;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.camunda.operate.entities.ActivityState;
import org.camunda.operate.entities.ActivityType;
import org.camunda.operate.entities.WorkflowInstanceState;
import org.camunda.operate.entities.listview.ActivityInstanceForListViewEntity;
import org.camunda.operate.entities.listview.WorkflowInstanceForListViewEntity;
import org.camunda.operate.es.schema.templates.ListViewTemplate;
import org.camunda.operate.exceptions.PersistenceException;
import org.camunda.operate.util.DateUtil;
import org.camunda.operate.util.ElasticsearchUtil;
import org.camunda.operate.util.IdUtil;
import org.camunda.operate.zeebeimport.cache.WorkflowCache;
import org.camunda.operate.zeebeimport.record.value.IncidentRecordValueImpl;
import org.camunda.operate.zeebeimport.record.value.WorkflowInstanceRecordValueImpl;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.update.UpdateRequestBuilder;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.zeebe.exporter.record.Record;
import io.zeebe.protocol.BpmnElementType;
import io.zeebe.protocol.intent.IncidentIntent;
import static io.zeebe.protocol.intent.WorkflowInstanceIntent.ELEMENT_ACTIVATED;
import static io.zeebe.protocol.intent.WorkflowInstanceIntent.ELEMENT_COMPLETED;
import static io.zeebe.protocol.intent.WorkflowInstanceIntent.ELEMENT_TERMINATED;
import static io.zeebe.protocol.intent.WorkflowInstanceIntent.EVENT_ACTIVATED;
import static io.zeebe.protocol.intent.WorkflowInstanceIntent.EVENT_TRIGGERED;

@Component
public class ListViewZeebeRecordProcessor {

  private static final Logger logger = LoggerFactory.getLogger(ListViewZeebeRecordProcessor.class);

  private static final Set<String> AI_FINISH_STATES = new HashSet<>();

  static {
    AI_FINISH_STATES.add(ELEMENT_COMPLETED.name());
    AI_FINISH_STATES.add(ELEMENT_TERMINATED.name());
    AI_FINISH_STATES.add(EVENT_TRIGGERED.name());
  }

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private TransportClient esClient;

  @Autowired
  private ListViewTemplate listViewTemplate;

  @Autowired
  private WorkflowCache workflowCache;

  public void processIncidentRecord(Record record, BulkRequestBuilder bulkRequestBuilder) throws PersistenceException {
    final String intentStr = record.getMetadata().getIntent().name();
    IncidentRecordValueImpl recordValue = (IncidentRecordValueImpl)record.getValue();

    //update activity instance
    bulkRequestBuilder.add(persistActivityInstanceFromIncident(record, intentStr, recordValue));

  }

  public void processWorkflowInstanceRecord(Record record, BulkRequestBuilder bulkRequestBuilder) throws PersistenceException {

    final String intentStr = record.getMetadata().getIntent().name();
    WorkflowInstanceRecordValueImpl recordValue = (WorkflowInstanceRecordValueImpl)record.getValue();

    if (isProcessEvent(recordValue)) {
      bulkRequestBuilder.add(persistWorkflowInstance(record, intentStr, recordValue));
    } else if (!isOfType(recordValue, BpmnElementType.SEQUENCE_FLOW)){
      bulkRequestBuilder.add(persistActivityInstance(record, intentStr, recordValue));
    }
  }

  private UpdateRequestBuilder persistActivityInstanceFromIncident(Record record, String intentStr, IncidentRecordValueImpl recordValue) throws PersistenceException {
    ActivityInstanceForListViewEntity entity = new ActivityInstanceForListViewEntity();
    entity.setId(IdUtil.getId(recordValue.getElementInstanceKey(), record));
    entity.setKey(recordValue.getElementInstanceKey());
    entity.setPartitionId(record.getMetadata().getPartitionId());
    entity.setActivityId(recordValue.getElementId());
    entity.setWorkflowInstanceId(IdUtil.getId(recordValue.getWorkflowInstanceKey(), record));
    if (intentStr.equals(IncidentIntent.CREATED.name())) {
      entity.setErrorMessage(recordValue.getErrorMessage());
      entity.setIncidentKey(record.getKey());
      entity.setIncidentJobKey(recordValue.getJobKey());
    } else if (intentStr.equals(IncidentIntent.CREATED.name())) {
      entity.setErrorMessage(null);
      entity.setIncidentKey(null);
      entity.setIncidentJobKey(null);
    }

    //set parent
    String workflowInstanceId = IdUtil.getId(recordValue.getWorkflowInstanceKey(), record);
    entity.getJoinRelation().setParent(workflowInstanceId);

    return getActivityInstanceFromIncidentQuery(entity, workflowInstanceId);
  }

  private UpdateRequestBuilder persistActivityInstance(Record record, String intentStr, WorkflowInstanceRecordValueImpl recordValue) throws PersistenceException {
    ActivityInstanceForListViewEntity entity = new ActivityInstanceForListViewEntity();
    entity.setId(IdUtil.getId(record.getKey(), record));
    entity.setPartitionId(record.getMetadata().getPartitionId());
    entity.setActivityId(recordValue.getElementId());
    entity.setWorkflowInstanceId(IdUtil.getId(recordValue.getWorkflowInstanceKey(), record));

    boolean activityFinished = AI_FINISH_STATES.contains(intentStr);
    if (!activityFinished && intentStr.equals(EVENT_ACTIVATED.name()) && isEndEvent(recordValue)) {
      activityFinished = true;
    }
    if (activityFinished) {
      if (intentStr.equals(ELEMENT_TERMINATED.name())) {
        entity.setActivityState(ActivityState.TERMINATED);
      } else {
        entity.setActivityState(ActivityState.COMPLETED);
      }
    } else {
      entity.setActivityState(ActivityState.ACTIVE);
    }

    entity.setActivityType(ActivityType.fromZeebeBpmnElementType(recordValue.getBpmnElementType()));

    //set parent
    String workflowInstanceId = IdUtil.getId(recordValue.getWorkflowInstanceKey(), record);
    entity.getJoinRelation().setParent(workflowInstanceId);

    return getActivityInstanceQuery(entity, workflowInstanceId);

  }

  private UpdateRequestBuilder getActivityInstanceQuery(ActivityInstanceForListViewEntity entity, String workflowInstanceId) throws PersistenceException {
    try {
      logger.debug("Activity instance for list view: id {}", entity.getId());
      Map<String, Object> updateFields = new HashMap<>();
      updateFields.put(ListViewTemplate.ID, entity.getId());
      updateFields.put(ListViewTemplate.PARTITION_ID, entity.getPartitionId());
      updateFields.put(ListViewTemplate.ACTIVITY_TYPE, entity.getActivityType());
      updateFields.put(ListViewTemplate.ACTIVITY_STATE, entity.getActivityState());

      //TODO some weird not efficient magic is needed here, in order to format date fields properly, may be this can be improved
      Map<String, Object> jsonMap = objectMapper.readValue(objectMapper.writeValueAsString(updateFields), HashMap.class);

      return esClient
        .prepareUpdate(listViewTemplate.getAlias(), ElasticsearchUtil.ES_INDEX_TYPE, entity.getId())
        .setUpsert(objectMapper.writeValueAsString(entity), XContentType.JSON)
        .setDoc(jsonMap)
        .setRouting(workflowInstanceId);

    } catch (IOException e) {
      logger.error("Error preparing the query to upsert activity instance for list view", e);
      throw new PersistenceException(String.format("Error preparing the query to upsert activity instance [%s]  for list view", entity.getId()), e);
    }
  }

  private UpdateRequestBuilder getActivityInstanceFromIncidentQuery(ActivityInstanceForListViewEntity entity, String workflowInstanceId) throws PersistenceException {
    try {
      logger.debug("Activity instance for list view: id {}", entity.getId());
      Map<String, Object> updateFields = new HashMap<>();
      updateFields.put(ListViewTemplate.ERROR_MSG, entity.getErrorMessage());
      updateFields.put(ListViewTemplate.INCIDENT_KEY, entity.getIncidentKey());
      updateFields.put(ListViewTemplate.INCIDENT_JOB_KEY, entity.getIncidentJobKey());

      //TODO some weird not efficient magic is needed here, in order to format date fields properly, may be this can be improved
      Map<String, Object> jsonMap = objectMapper.readValue(objectMapper.writeValueAsString(updateFields), HashMap.class);

      return esClient
        .prepareUpdate(listViewTemplate.getAlias(), ElasticsearchUtil.ES_INDEX_TYPE, entity.getId())
        .setUpsert(objectMapper.writeValueAsString(entity), XContentType.JSON)
        .setDoc(jsonMap)
        .setRouting(workflowInstanceId);

    } catch (IOException e) {
      logger.error("Error preparing the query to upsert activity instance for list view", e);
      throw new PersistenceException(String.format("Error preparing the query to upsert activity instance [%s]  for list view", entity.getId()), e);
    }
  }

  private UpdateRequestBuilder persistWorkflowInstance(Record record, String intentStr, WorkflowInstanceRecordValueImpl recordValue) throws PersistenceException {
    WorkflowInstanceForListViewEntity wiEntity = new WorkflowInstanceForListViewEntity();
    wiEntity.setId(IdUtil.getId(recordValue.getWorkflowInstanceKey(), record));
    wiEntity.setKey(recordValue.getWorkflowInstanceKey());
    wiEntity.setPartitionId(record.getMetadata().getPartitionId());
    wiEntity.setWorkflowId(String.valueOf(recordValue.getWorkflowKey()));
    wiEntity.setBpmnProcessId(recordValue.getBpmnProcessId());

    //find out workflow name and version
    wiEntity.setWorkflowName(workflowCache.getWorkflowName(wiEntity.getWorkflowId(), recordValue.getBpmnProcessId()));
    wiEntity.setWorkflowVersion(workflowCache.getWorkflowVersion(wiEntity.getWorkflowId(), recordValue.getBpmnProcessId()));

    if (intentStr.equals(ELEMENT_COMPLETED.name()) || intentStr.equals(ELEMENT_TERMINATED.name())) {
      wiEntity.setEndDate(DateUtil.toOffsetDateTime(record.getTimestamp()));
      if (intentStr.equals(ELEMENT_TERMINATED.name())) {
        wiEntity.setState(WorkflowInstanceState.CANCELED);
      } else {
        wiEntity.setState(WorkflowInstanceState.COMPLETED);
      }
    } else if (intentStr.equals(ELEMENT_ACTIVATED.name())) {
      wiEntity.setStartDate(DateUtil.toOffsetDateTime(record.getTimestamp()));
      wiEntity.setState(WorkflowInstanceState.ACTIVE);
    }

    return getWorfklowInstanceQuery(wiEntity);
  }

  private UpdateRequestBuilder getWorfklowInstanceQuery(WorkflowInstanceForListViewEntity wiEntity) throws PersistenceException {
    try {
      logger.debug("Workflow instance for list view: id {}", wiEntity.getId());
      Map<String, Object> updateFields = new HashMap<>();
      if (wiEntity.getStartDate() != null) {
        updateFields.put(ListViewTemplate.START_DATE, wiEntity.getStartDate());
      }
      if (wiEntity.getEndDate() != null) {
        updateFields.put(ListViewTemplate.END_DATE, wiEntity.getEndDate());
      }
      updateFields.put(ListViewTemplate.WORKFLOW_NAME, wiEntity.getWorkflowName());
      updateFields.put(ListViewTemplate.WORKFLOW_VERSION, wiEntity.getWorkflowVersion());
      updateFields.put(ListViewTemplate.STATE, wiEntity.getState());

      //TODO some weird not efficient magic is needed here, in order to format date fields properly, may be this can be improved
      Map<String, Object> jsonMap = objectMapper.readValue(objectMapper.writeValueAsString(updateFields), HashMap.class);

      return esClient
        .prepareUpdate(listViewTemplate.getAlias(), ElasticsearchUtil.ES_INDEX_TYPE, wiEntity.getId())
        .setUpsert(objectMapper.writeValueAsString(wiEntity), XContentType.JSON)
        .setDoc(jsonMap);

    } catch (IOException e) {
      logger.error("Error preparing the query to upsert workflow instance for list view", e);
      throw new PersistenceException(String.format("Error preparing the query to upsert workflow instance [%s]  for list view", wiEntity.getId()), e);
    }
  }

  private boolean isProcessEvent(WorkflowInstanceRecordValueImpl recordValue) {
    return isOfType(recordValue, BpmnElementType.PROCESS);
  }

  private boolean isEndEvent(WorkflowInstanceRecordValueImpl recordValue) {
    return isOfType(recordValue, BpmnElementType.END_EVENT);
  }

  private boolean isOfType(WorkflowInstanceRecordValueImpl recordValue, BpmnElementType type) {
    final BpmnElementType bpmnElementType = recordValue.getBpmnElementType();
    if (bpmnElementType == null) {
      return false;
    }
    return bpmnElementType.equals(type);
  }

}
