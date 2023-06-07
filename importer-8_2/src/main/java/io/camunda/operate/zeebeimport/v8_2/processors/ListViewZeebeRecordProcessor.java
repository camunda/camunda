/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.zeebeimport.v8_2.processors;

import static io.camunda.operate.schema.templates.ListViewTemplate.ACTIVITIES_JOIN_RELATION;
import static io.camunda.operate.schema.templates.ListViewTemplate.ACTIVITY_ID;
import static io.camunda.operate.schema.templates.ListViewTemplate.JOIN_RELATION;
import static io.camunda.operate.util.ElasticsearchUtil.UPDATE_RETRY_COUNT;
import static io.camunda.operate.util.ElasticsearchUtil.joinWithAnd;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_ACTIVATING;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_COMPLETED;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_TERMINATED;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.cache.ProcessCache;
import io.camunda.operate.entities.FlowNodeState;
import io.camunda.operate.entities.FlowNodeType;
import io.camunda.operate.entities.OperationType;
import io.camunda.operate.entities.listview.FlowNodeInstanceForListViewEntity;
import io.camunda.operate.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.operate.entities.listview.ProcessInstanceState;
import io.camunda.operate.entities.listview.VariableForListViewEntity;
import io.camunda.operate.es.contract.MetricContract;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.util.ConversionUtils;
import io.camunda.operate.util.DateUtil;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.operate.util.ElasticsearchUtil.QueryType;
import io.camunda.operate.util.OperationsManager;
import io.camunda.operate.util.SoftHashMap;
import io.camunda.operate.zeebe.PartitionHolder;
import io.camunda.operate.zeebeimport.ElasticsearchQueries;
import io.camunda.operate.zeebeimport.ImportBatch;
import io.camunda.operate.zeebeimport.util.TreePath;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import java.io.IOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.elasticsearch.xcontent.XContentType;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ListViewZeebeRecordProcessor {

  private static final Logger logger = LoggerFactory.getLogger(ListViewZeebeRecordProcessor.class);

  private static final Set<String> AI_FINISH_STATES = new HashSet<>();
  protected static final int EMPTY_PARENT_PROCESS_INSTANCE_ID = -1;

  static {
    AI_FINISH_STATES.add(ELEMENT_COMPLETED.name());
    AI_FINISH_STATES.add(ELEMENT_TERMINATED.name());
  }

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private ListViewTemplate listViewTemplate;

  @Autowired
  private ProcessCache processCache;

  @Autowired
  private OperationsManager operationsManager;

  @Autowired
  private ElasticsearchQueries elasticsearchQueries;

  @Autowired
  private RestHighLevelClient esClient;

  @Autowired
  private OperateProperties operateProperties;

  @Autowired
  private PartitionHolder partitionHolder;

  @Autowired
  private MetricContract.Writer metricWriter;

  //treePath by processInstanceKey cache
  private Map<String, String> treePathCache;
  //flowNodeId by flowNodeInstanceId cache for call activities
  private Map<String, String> callActivityIdCache;

  private Map<String, String> getTreePathCache() {
    if (treePathCache == null) {
      //cache must be able to contain all possible processInstanceKeys with there treePaths before
      //the data is persisted: import batch size * number of partitions processed by current import node
      treePathCache = new SoftHashMap<>(operateProperties.getElasticsearch().getBatchSize() *
          partitionHolder.getPartitionIds().size());
    }
    return treePathCache;
  }

  private Map<String, String> getCallActivityIdCache() {
    if (callActivityIdCache == null) {
      callActivityIdCache = new SoftHashMap<>(operateProperties.getElasticsearch().getBatchSize() *
          partitionHolder.getPartitionIds().size());
    }
    return callActivityIdCache;
  }

  public void processIncidentRecord(Record record, BulkRequest bulkRequest) throws PersistenceException {
    final String intentStr = record.getIntent().name();
    IncidentRecordValue recordValue = (IncidentRecordValue)record.getValue();

    //update activity instance
    bulkRequest.add(persistFlowNodeInstanceFromIncident(record, intentStr, recordValue));

  }

  public void processVariableRecord(Record record, BulkRequest bulkRequest) throws PersistenceException {
    VariableRecordValue recordValue = (VariableRecordValue)record.getValue();

    bulkRequest.add(persistVariable(record, recordValue));

  }

  public void processProcessInstanceRecord(
      Map<Long, List<Record<ProcessInstanceRecordValue>>> records, BulkRequest bulkRequest,
      ImportBatch importBatch) throws PersistenceException {
    final Map<String, String> treePathMap = new HashMap<>();
    for (Map.Entry<Long, List<Record<ProcessInstanceRecordValue>>> wiRecordsEntry: records.entrySet()) {
      ProcessInstanceForListViewEntity piEntity = null;
      Map<Long, FlowNodeInstanceForListViewEntity> actEntities = new HashMap<Long, FlowNodeInstanceForListViewEntity>();
      Long processInstanceKey = null;
      for (Record record: wiRecordsEntry.getValue()) {
        processInstanceKey = wiRecordsEntry.getKey();
        final String intentStr = record.getIntent().name();
        ProcessInstanceRecordValue recordValue = (ProcessInstanceRecordValue)record.getValue();
        if (isProcessEvent(recordValue)) {
          //complete operation
          if (intentStr.equals(ELEMENT_TERMINATED.name())) {
            //resolve corresponding operation
            operationsManager.completeOperation(null, record.getKey(), null, OperationType.CANCEL_PROCESS_INSTANCE, bulkRequest);
          }
          piEntity = updateProcessInstance(importBatch, record, intentStr, recordValue, piEntity, treePathMap, bulkRequest);
        } else if (!intentStr.equals(ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN.name()) && !intentStr.equals(Intent.UNKNOWN.name())) {
          updateFlowNodeInstance(record, intentStr, recordValue, actEntities);
        }
      }
      if (piEntity != null) {
        bulkRequest.add(getProcessInstanceQuery(piEntity));
      }
      for (FlowNodeInstanceForListViewEntity actEntity: actEntities.values()) {
        bulkRequest.add(getFlowNodeInstanceQuery(actEntity, processInstanceKey));
      }
    }
  }


  private ProcessInstanceForListViewEntity updateProcessInstance(ImportBatch importBatch,
      Record record,
      String intentStr,
      ProcessInstanceRecordValue recordValue,
      ProcessInstanceForListViewEntity piEntity,
      Map<String, String> treePathMap,
      BulkRequest bulkRequest) {
    if (piEntity == null) {
      piEntity = new ProcessInstanceForListViewEntity();
    }
    piEntity.setId(String.valueOf(recordValue.getProcessInstanceKey()));
    piEntity.setProcessInstanceKey(recordValue.getProcessInstanceKey());
    piEntity.setKey(recordValue.getProcessInstanceKey());

    piEntity.setPartitionId(record.getPartitionId());
    piEntity.setProcessDefinitionKey(recordValue.getProcessDefinitionKey());
    piEntity.setBpmnProcessId(recordValue.getBpmnProcessId());
    piEntity.setProcessVersion(recordValue.getVersion());

    piEntity.setProcessName(processCache.getProcessNameOrDefaultValue(piEntity.getProcessDefinitionKey(), recordValue.getBpmnProcessId()));

    OffsetDateTime timestamp = DateUtil.toOffsetDateTime(Instant.ofEpochMilli(record.getTimestamp()));
    final boolean isRootProcessInstance = recordValue.getParentProcessInstanceKey() == EMPTY_PARENT_PROCESS_INSTANCE_ID;
    if (intentStr.equals(ELEMENT_COMPLETED.name()) || intentStr.equals(ELEMENT_TERMINATED.name())) {
      importBatch.incrementFinishedWiCount();
      piEntity.setEndDate(timestamp);
      if (intentStr.equals(ELEMENT_TERMINATED.name())) {
        piEntity.setState(ProcessInstanceState.CANCELED);
      } else {
        piEntity.setState(ProcessInstanceState.COMPLETED);
      }
    } else if (intentStr.equals(ELEMENT_ACTIVATING.name())) {
      piEntity.setStartDate(timestamp);
      piEntity.setState(ProcessInstanceState.ACTIVE);
      if(isRootProcessInstance){
        registerStartedRootProcessInstance(piEntity, bulkRequest, timestamp);
      }
    } else {
      piEntity.setState(ProcessInstanceState.ACTIVE);
    }
    //call activity related fields
    if (!isRootProcessInstance) {
      piEntity
          .setParentProcessInstanceKey(recordValue.getParentProcessInstanceKey());
      piEntity
          .setParentFlowNodeInstanceKey(recordValue.getParentElementInstanceKey());
      if (piEntity.getTreePath() == null) {
        final String treePath = getTreePathForCalledProcess(recordValue);
        piEntity.setTreePath(treePath);
        treePathMap.put(String.valueOf(record.getKey()), treePath);
      }
    }
    if (piEntity.getTreePath() == null) {
      final String treePath = new TreePath().startTreePath(
          ConversionUtils.toStringOrNull(recordValue.getProcessInstanceKey())).toString();
      piEntity.setTreePath(treePath);
      getTreePathCache()
          .put(ConversionUtils.toStringOrNull(recordValue.getProcessInstanceKey()), treePath);
    }
    return piEntity;
  }

  private void registerStartedRootProcessInstance(ProcessInstanceForListViewEntity piEntity, BulkRequest bulkRequest, OffsetDateTime timestamp) {
    String processInstanceKey = String.valueOf(piEntity.getProcessInstanceKey());
    bulkRequest.add(metricWriter
        .registerProcessInstanceStartEvent(processInstanceKey, timestamp));
  }

  private String getTreePathForCalledProcess(final ProcessInstanceRecordValue recordValue) {
    String parentTreePath = null;

    //search in cache
    if (getTreePathCache().get(ConversionUtils.toStringOrNull(recordValue.getParentProcessInstanceKey()))
        != null) {
      parentTreePath = getTreePathCache()
          .get(ConversionUtils.toStringOrNull(recordValue.getParentProcessInstanceKey()));
    }
    //query from ELS
    if (parentTreePath == null) {
      parentTreePath = elasticsearchQueries
          .findProcessInstanceTreePath(recordValue.getParentProcessInstanceKey());

    }
    //still not found - smth is wrong
    if (parentTreePath == null) {
      throw new OperateRuntimeException(
          "Unable to find parent tree path for parent instance id " + recordValue.getParentProcessInstanceKey());
    }
    final String flowNodeInstanceId = ConversionUtils
        .toStringOrNull(recordValue.getParentElementInstanceKey());
    final String callActivityId = getCallActivityId(flowNodeInstanceId);
    String treePath = new TreePath(parentTreePath).appendEntries(
        callActivityId,
        flowNodeInstanceId,
        ConversionUtils.toStringOrNull(recordValue.getProcessInstanceKey())).toString();

    getTreePathCache().put(ConversionUtils.toStringOrNull(recordValue.getProcessInstanceKey()), treePath);

    return treePath;
  }

  private String getCallActivityId(String flowNodeInstanceId) {
    String callActivityId = getCallActivityIdCache().get(flowNodeInstanceId);
    if (callActivityId == null) {
      callActivityId = getFlowNodeIdByFlowNodeInstanceId(flowNodeInstanceId);
      getCallActivityIdCache().put(flowNodeInstanceId, callActivityId);
    }
    return callActivityId;
  }

  private String getFlowNodeIdByFlowNodeInstanceId(String flowNodeInstanceId) {
    final QueryBuilder query = joinWithAnd(termQuery(JOIN_RELATION, ACTIVITIES_JOIN_RELATION),
        termQuery(ListViewTemplate.ID, flowNodeInstanceId));
    final SearchRequest request = ElasticsearchUtil
        .createSearchRequest(listViewTemplate, QueryType.ONLY_RUNTIME)
        .source(new SearchSourceBuilder()
            .query(query).fetchSource(ACTIVITY_ID, null));
    final SearchResponse response;
    try {
      response = esClient.search(request, RequestOptions.DEFAULT);
      if (response.getHits().getTotalHits().value != 1) {
        throw new OperateRuntimeException("Flow node instance is not found: " + flowNodeInstanceId);
      } else {
        return String.valueOf(response.getHits().getAt(0).getSourceAsMap().get(ACTIVITY_ID));
      }
    } catch (IOException e) {
      throw new OperateRuntimeException(
          "Error occurred when searching for flow node instance: " + flowNodeInstanceId, e);
    }
  }


  private void updateFlowNodeInstance(Record record, String intentStr, ProcessInstanceRecordValue recordValue, Map<Long, FlowNodeInstanceForListViewEntity> entities) {
    if (entities.get(record.getKey()) == null) {
      entities.put(record.getKey(), new FlowNodeInstanceForListViewEntity());
    }
    FlowNodeInstanceForListViewEntity entity = entities.get(record.getKey());
    entity.setKey(record.getKey());
    entity.setId( ConversionUtils.toStringOrNull(record.getKey()));
    entity.setPartitionId(record.getPartitionId());
    entity.setActivityId(recordValue.getElementId());
    entity.setProcessInstanceKey(recordValue.getProcessInstanceKey());

    if (AI_FINISH_STATES.contains(intentStr)) {
      if (intentStr.equals(ELEMENT_TERMINATED.name())) {
        entity.setActivityState(FlowNodeState.TERMINATED);
      } else {
        entity.setActivityState(FlowNodeState.COMPLETED);
      }
    } else {
      entity.setActivityState(FlowNodeState.ACTIVE);
    }

    entity.setActivityType(FlowNodeType.fromZeebeBpmnElementType(recordValue.getBpmnElementType() == null ? null : recordValue.getBpmnElementType().name()));

    if (FlowNodeType.CALL_ACTIVITY.equals(entity.getActivityType())) {
      getCallActivityIdCache().put(entity.getId(), entity.getActivityId());
    }

    //set parent
    Long processInstanceKey = recordValue.getProcessInstanceKey();
    entity.getJoinRelation().setParent(processInstanceKey);

  }

  private UpdateRequest persistFlowNodeInstanceFromIncident(Record record, String intentStr, IncidentRecordValue recordValue) throws PersistenceException {
    FlowNodeInstanceForListViewEntity entity = new FlowNodeInstanceForListViewEntity();
    entity.setId( ConversionUtils.toStringOrNull(recordValue.getElementInstanceKey()));
    entity.setKey(recordValue.getElementInstanceKey());
    entity.setPartitionId(record.getPartitionId());
    entity.setActivityId(recordValue.getElementId());
    entity.setProcessInstanceKey(recordValue.getProcessInstanceKey());
    if (intentStr.equals(IncidentIntent.CREATED.name())) {
      entity.setErrorMessage(StringUtils.trimWhitespace(recordValue.getErrorMessage()));
    } else if (intentStr.equals(IncidentIntent.RESOLVED.name())) {
      entity.setErrorMessage(null);
    }

    //set parent
    Long processInstanceKey = recordValue.getProcessInstanceKey();
    entity.getJoinRelation().setParent(processInstanceKey);

    return getFlowNodeInstanceFromIncidentQuery(entity, processInstanceKey);
  }

  private UpdateRequest persistVariable(Record record, VariableRecordValue recordValue) throws PersistenceException {
    VariableForListViewEntity entity = new VariableForListViewEntity();
    entity.setId(VariableForListViewEntity.getIdBy(recordValue.getScopeKey(), recordValue.getName()));
    entity.setKey(record.getKey());
    entity.setPartitionId(record.getPartitionId());
    entity.setScopeKey(recordValue.getScopeKey());
    entity.setProcessInstanceKey(recordValue.getProcessInstanceKey());
    entity.setVarName(recordValue.getName());
    entity.setVarValue(recordValue.getValue());

    //set parent
    Long processInstanceKey = recordValue.getProcessInstanceKey();
    entity.getJoinRelation().setParent(processInstanceKey);

    return getVariableQuery(entity, processInstanceKey);
  }

  private UpdateRequest getFlowNodeInstanceQuery(FlowNodeInstanceForListViewEntity entity, Long processInstanceKey) throws PersistenceException {
    try {
      logger.debug("Flow node instance for list view: id {}", entity.getId());
      Map<String, Object> updateFields = new HashMap<>();
      updateFields.put(ListViewTemplate.ID, entity.getId());
      updateFields.put(ListViewTemplate.PARTITION_ID, entity.getPartitionId());
      updateFields.put(ListViewTemplate.ACTIVITY_TYPE, entity.getActivityType());
      updateFields.put(ListViewTemplate.ACTIVITY_STATE, entity.getActivityState());

      return new UpdateRequest().index(listViewTemplate.getFullQualifiedName()).id(entity.getId())
        .upsert(objectMapper.writeValueAsString(entity), XContentType.JSON)
        .doc(updateFields)
        .routing(processInstanceKey.toString())
        .retryOnConflict(UPDATE_RETRY_COUNT);

    } catch (IOException e) {
      logger.error("Error preparing the query to upsert activity instance for list view", e);
      throw new PersistenceException(String.format("Error preparing the query to upsert activity instance [%s]  for list view", entity.getId()), e);
    }
  }

  private UpdateRequest getVariableQuery(VariableForListViewEntity entity, Long processInstanceKey) throws PersistenceException {
    try {
      logger.debug("Variable for list view: id {}", entity.getId());
      Map<String, Object> updateFields = new HashMap<>();
      updateFields.put(ListViewTemplate.VAR_NAME, entity.getVarName());
      updateFields.put(ListViewTemplate.VAR_VALUE, entity.getVarValue());

      return new UpdateRequest().index(listViewTemplate.getFullQualifiedName()).id(entity.getId())
        .upsert(objectMapper.writeValueAsString(entity), XContentType.JSON)
        .doc(updateFields)
        .routing(processInstanceKey.toString())
        .retryOnConflict(UPDATE_RETRY_COUNT);

    } catch (IOException e) {
      logger.error("Error preparing the query to upsert variable for list view", e);
      throw new PersistenceException(String.format("Error preparing the query to upsert variable [%s]  for list view", entity.getId()), e);
    }
  }

  private UpdateRequest getFlowNodeInstanceFromIncidentQuery(
      FlowNodeInstanceForListViewEntity entity, Long processInstanceKey) throws PersistenceException {
    try {
      logger.debug("Activity instance for list view: id {}", entity.getId());
      Map<String, Object> updateFields = new HashMap<>();
      updateFields.put(ListViewTemplate.ERROR_MSG, entity.getErrorMessage());
      return new UpdateRequest().index(listViewTemplate.getFullQualifiedName()).id(entity.getId())
          .upsert(objectMapper.writeValueAsString(entity), XContentType.JSON)
          .doc(updateFields)
          .routing(processInstanceKey.toString())
          .retryOnConflict(UPDATE_RETRY_COUNT);

    } catch (IOException e) {
      logger.error("Error preparing the query to upsert activity instance for list view", e);
      throw new PersistenceException(String.format("Error preparing the query to upsert activity instance [%s]  for list view", entity.getId()), e);
    }
  }

  private UpdateRequest getProcessInstanceQuery(ProcessInstanceForListViewEntity piEntity) throws PersistenceException {
    try {
      logger.debug("Process instance for list view: id {}", piEntity.getId());
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

      //TODO some weird not efficient magic is needed here, in order to format date fields properly, may be this can be improved
      Map<String, Object> jsonMap = objectMapper.readValue(objectMapper.writeValueAsString(updateFields), HashMap.class);
      return new UpdateRequest().index(listViewTemplate.getFullQualifiedName()).id(piEntity.getId())
        .upsert(objectMapper.writeValueAsString(piEntity), XContentType.JSON)
        .doc(jsonMap)
        .retryOnConflict(UPDATE_RETRY_COUNT);

    } catch (IOException e) {
      logger.error("Error preparing the query to upsert process instance for list view", e);
      throw new PersistenceException(String.format("Error preparing the query to upsert process instance [%s]  for list view", piEntity.getId()), e);
    }
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

}
