/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.zeebeimport.v8_3.processors;

import static io.camunda.operate.util.ElasticsearchUtil.UPDATE_RETRY_COUNT;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_ACTIVATING;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_COMPLETED;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_TERMINATED;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.entities.FlowNodeInstanceEntity;
import io.camunda.operate.entities.FlowNodeState;
import io.camunda.operate.entities.FlowNodeType;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.templates.FlowNodeInstanceTemplate;
import io.camunda.operate.util.ConversionUtils;
import io.camunda.operate.util.DateUtil;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.operate.util.ElasticsearchUtil.QueryType;
import io.camunda.operate.util.SoftHashMap;
import io.camunda.operate.util.ThreadUtil;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import jakarta.annotation.PostConstruct;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.xcontent.XContentType;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class FlowNodeInstanceZeebeRecordProcessor {

  private static final Logger logger = LoggerFactory.getLogger(FlowNodeInstanceZeebeRecordProcessor.class);

  private static final Set<String> AI_FINISH_STATES = Set.of(ELEMENT_COMPLETED.name(), ELEMENT_TERMINATED.name());
  private static final Set<String> AI_START_STATES = Set.of(ELEMENT_ACTIVATING.name());

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private FlowNodeInstanceTemplate flowNodeInstanceTemplate;

  @Autowired
  protected RestHighLevelClient esClient;

  @Autowired
  private OperateProperties operateProperties;

  //treePath by flowNodeInstanceKey cache
  private Map<String, String> treePathCache;

  @PostConstruct
  private void init() {
    treePathCache = new SoftHashMap<>(operateProperties.getImporter().getFlowNodeTreeCacheSize());
  }

  public void processIncidentRecord(Record record, BulkRequest bulkRequest) throws PersistenceException {
    final String intentStr = record.getIntent().name();
    IncidentRecordValue recordValue = (IncidentRecordValue)record.getValue();

    //update activity instance
    bulkRequest.add(persistFlowNodeInstanceFromIncident(record, intentStr, recordValue));

  }

  public void processProcessInstanceRecord(
      Map<Long, List<Record<ProcessInstanceRecordValue>>> records,
      final List<Long> flowNodeInstanceKeysOrdered, BulkRequest bulkRequest) throws PersistenceException {

    for (Long key: flowNodeInstanceKeysOrdered) {
      List<Record<ProcessInstanceRecordValue>> wiRecords = records.get(key);
      FlowNodeInstanceEntity fniEntity = null;
      for (Record record: wiRecords) {
        final String intentStr = record.getIntent().name();
        ProcessInstanceRecordValue recordValue = (ProcessInstanceRecordValue)record.getValue();
        if (!isProcessEvent(recordValue) && !intentStr.equals(ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN.name()) && !intentStr.equals(
            Intent.UNKNOWN.name())) {
          fniEntity = updateFlowNodeInstance(record, intentStr, recordValue, fniEntity);
        }
      }
      if (fniEntity != null) {
        bulkRequest.add(getFlowNodeInstanceQuery(fniEntity));
      }
    }
  }

  private FlowNodeInstanceEntity updateFlowNodeInstance(Record record, String intentStr,
      ProcessInstanceRecordValue recordValue, FlowNodeInstanceEntity entity) {
    if (entity == null) {
      entity = new FlowNodeInstanceEntity();
    }
    entity.setKey(record.getKey());
    entity.setId(ConversionUtils.toStringOrNull(record.getKey()));
    entity.setPartitionId(record.getPartitionId());
    entity.setFlowNodeId(recordValue.getElementId());
    entity.setProcessInstanceKey(recordValue.getProcessInstanceKey());
    entity.setProcessDefinitionKey(recordValue.getProcessDefinitionKey());
    entity.setBpmnProcessId(recordValue.getBpmnProcessId());

    if (entity.getTreePath() == null) {

      String parentTreePath = getParentTreePath(record, recordValue);
      entity.setTreePath(
          String.join("/", parentTreePath, ConversionUtils.toStringOrNull(record.getKey())));
      entity.setLevel(parentTreePath.split("/").length);

    }

    if (AI_FINISH_STATES.contains(intentStr)) {
      if (intentStr.equals(ELEMENT_TERMINATED.name())) {
        entity.setState(FlowNodeState.TERMINATED);
      } else {
        entity.setState(FlowNodeState.COMPLETED);
      }
      entity.setEndDate(DateUtil.toOffsetDateTime(Instant.ofEpochMilli(record.getTimestamp())));
    } else {
      entity.setState(FlowNodeState.ACTIVE);
      if (AI_START_STATES.contains(intentStr)) {
        entity.setStartDate(DateUtil.toOffsetDateTime(Instant.ofEpochMilli(record.getTimestamp())));
        entity.setPosition(record.getPosition());
      }
    }

    entity.setType(FlowNodeType
        .fromZeebeBpmnElementType(recordValue.getBpmnElementType() == null ? null
            : recordValue.getBpmnElementType().name()));

    return entity;

  }

  private String getParentTreePath(final Record record,
      final ProcessInstanceRecordValue recordValue) {
    String parentTreePath;
    //if scopeKey differs from processInstanceKey, then it's inner tree level and we need to search for parent 1st
    if (recordValue.getFlowScopeKey() == recordValue.getProcessInstanceKey()) {
      parentTreePath = ConversionUtils.toStringOrNull(recordValue.getProcessInstanceKey());
    } else {
      //find parent flow node instance
      parentTreePath = null;
      //search in cache
      if (treePathCache.get(ConversionUtils.toStringOrNull(recordValue.getFlowScopeKey()))
          != null) {
        parentTreePath = treePathCache
            .get(ConversionUtils.toStringOrNull(recordValue.getFlowScopeKey()));
      }
      //query from ELS
      if (parentTreePath == null) {
        parentTreePath = findParentTreePath(recordValue.getFlowScopeKey());
      }
      //still not found - smth is wrong
      if (parentTreePath == null) {
        throw new OperateRuntimeException(
            "Unable to find parent tree path for flow node instance id " + record.getKey());
      }
    }
    treePathCache.put(ConversionUtils.toStringOrNull(record.getKey()),
        String.join("/", parentTreePath, ConversionUtils.toStringOrNull(record.getKey())));
    return parentTreePath;
  }

  private String findParentTreePath(final long parentFlowNodeInstanceKey) {
    return findParentTreePath(parentFlowNodeInstanceKey, 0);
  }

  private String findParentTreePath(final long parentFlowNodeInstanceKey, int attemptCount) {
    final SearchRequest searchRequest = ElasticsearchUtil
        .createSearchRequest(flowNodeInstanceTemplate, QueryType.ONLY_RUNTIME)
        .source(new SearchSourceBuilder()
            .query(termQuery(FlowNodeInstanceTemplate.KEY, parentFlowNodeInstanceKey))
            .fetchSource(FlowNodeInstanceTemplate.TREE_PATH, null));
    try {
      final SearchHits hits = esClient.search(searchRequest, RequestOptions.DEFAULT).getHits();
      if (hits.getTotalHits().value > 0) {
        return (String)hits.getHits()[0].getSourceAsMap().get(FlowNodeInstanceTemplate.TREE_PATH);
      } else if (attemptCount < 1){
        //retry for the case, when ELS has not yet refreshed the indices
        ThreadUtil.sleepFor(2000L);
        return findParentTreePath(parentFlowNodeInstanceKey, attemptCount + 1);
      } else {
        return null;
      }
    } catch (IOException e) {
      final String message = String
          .format("Exception occurred, while searching for parent flow node instance processes: %s",
              e.getMessage());
      throw new OperateRuntimeException(message, e);
    }
  }

  private UpdateRequest persistFlowNodeInstanceFromIncident(Record record, String intentStr, IncidentRecordValue recordValue) throws PersistenceException {
    FlowNodeInstanceEntity entity = new FlowNodeInstanceEntity();
    entity.setId(ConversionUtils.toStringOrNull(recordValue.getElementInstanceKey()));
    entity.setKey(recordValue.getElementInstanceKey());
    entity.setPartitionId(record.getPartitionId());
    entity.setFlowNodeId(recordValue.getElementId());
    entity.setProcessInstanceKey(recordValue.getProcessInstanceKey());
    entity.setProcessDefinitionKey(recordValue.getProcessDefinitionKey());
    entity.setBpmnProcessId(recordValue.getBpmnProcessId());
    if (intentStr.equals(IncidentIntent.CREATED.name())) {
      entity.setIncidentKey(record.getKey());
    } else if (intentStr.equals(IncidentIntent.RESOLVED.name())) {
      entity.setIncidentKey(null);
    }

    return getFlowNodeInstanceFromIncidentQuery(entity);
  }

  private UpdateRequest getFlowNodeInstanceQuery(FlowNodeInstanceEntity entity) throws PersistenceException {
    try {
      logger.debug("Flow node instance: id {}", entity.getId());
      Map<String, Object> updateFields = new HashMap<>();
      updateFields.put(FlowNodeInstanceTemplate.ID, entity.getId());
      updateFields.put(FlowNodeInstanceTemplate.PARTITION_ID, entity.getPartitionId());
      updateFields.put(FlowNodeInstanceTemplate.TYPE, entity.getType());
      updateFields.put(FlowNodeInstanceTemplate.STATE, entity.getState());
      updateFields.put(FlowNodeInstanceTemplate.TREE_PATH, entity.getTreePath());
      updateFields.put(FlowNodeInstanceTemplate.FLOW_NODE_ID, entity.getFlowNodeId());
      updateFields.put(FlowNodeInstanceTemplate.PROCESS_DEFINITION_KEY, entity.getProcessDefinitionKey());
      updateFields.put(FlowNodeInstanceTemplate.LEVEL, entity.getLevel());
      if (entity.getStartDate() != null) {
        updateFields.put(FlowNodeInstanceTemplate.START_DATE, entity.getStartDate());
      }
      if (entity.getEndDate() != null) {
        updateFields.put(FlowNodeInstanceTemplate.END_DATE, entity.getEndDate());
      }
      if (entity.getPosition() != null) {
        updateFields.put(FlowNodeInstanceTemplate.POSITION, entity.getPosition());
      }

      //TODO some weird not efficient magic is needed here, in order to format date fields properly, may be this can be improved
      Map<String, Object> jsonMap = objectMapper.readValue(objectMapper.writeValueAsString(updateFields), HashMap.class);

      return new UpdateRequest().index(flowNodeInstanceTemplate.getFullQualifiedName()).id(entity.getId())
        .upsert(objectMapper.writeValueAsString(entity), XContentType.JSON)
        .doc(jsonMap)
        .retryOnConflict(UPDATE_RETRY_COUNT);

    } catch (IOException e) {
      throw new PersistenceException(String.format("Error preparing the query to upsert flow node instance [%s]  for list view", entity.getId()), e);
    }
  }

  private UpdateRequest getFlowNodeInstanceFromIncidentQuery(FlowNodeInstanceEntity entity) throws PersistenceException {
    try {
      logger.debug("Flow node instance: id {}", entity.getId());
      Map<String, Object> jsonMap = new HashMap<>();
      jsonMap.put(FlowNodeInstanceTemplate.INCIDENT_KEY, entity.getIncidentKey());

      return new UpdateRequest().index(flowNodeInstanceTemplate.getFullQualifiedName()).id(entity.getId())
        .upsert(objectMapper.writeValueAsString(entity), XContentType.JSON)
        .doc(jsonMap)
        .retryOnConflict(UPDATE_RETRY_COUNT);

    } catch (IOException e) {
      throw new PersistenceException(String.format("Error preparing the query to upsert flow node instance [%s]  for list view", entity.getId()), e);
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
