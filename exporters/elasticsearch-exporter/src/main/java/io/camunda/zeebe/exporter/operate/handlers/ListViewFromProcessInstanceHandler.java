/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.exporter.operate.handlers;

import static io.camunda.operate.schema.templates.ListViewTemplate.ACTIVITIES_JOIN_RELATION;
import static io.camunda.operate.schema.templates.ListViewTemplate.ACTIVITY_ID;
import static io.camunda.operate.schema.templates.ListViewTemplate.JOIN_RELATION;
import static io.camunda.operate.util.ElasticsearchUtil.joinWithAnd;
import static io.camunda.operate.zeebeimport.util.ImportUtil.tenantOrDefault;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_ACTIVATING;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_COMPLETED;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_TERMINATED;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

import io.camunda.operate.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.operate.entities.listview.ProcessInstanceState;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.util.ConversionUtils;
import io.camunda.operate.util.DateUtil;
import io.camunda.operate.util.SoftHashMap;
import io.camunda.operate.util.TreePath;
import io.camunda.zeebe.exporter.operate.ExportHandler;
import io.camunda.zeebe.exporter.operate.OperateElasticsearchBulkRequest;
import io.camunda.zeebe.exporter.operate.OperateElasticsearchExporterConfiguration;
import io.camunda.zeebe.exporter.operate.schema.templates.ListViewTemplate;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ListViewFromProcessInstanceHandler
    implements ExportHandler<ProcessInstanceForListViewEntity, ProcessInstanceRecordValue> {

  protected static final int EMPTY_PARENT_PROCESS_INSTANCE_ID = -1;
  private static final Logger LOGGER =
      LoggerFactory.getLogger(ListViewFromProcessInstanceHandler.class);
  private static final Set<String> PI_AND_AI_START_STATES = new HashSet<>();
  private static final Set<String> PI_AND_AI_FINISH_STATES = new HashSet<>();

  static {
    PI_AND_AI_START_STATES.add(ELEMENT_ACTIVATING.name());
    PI_AND_AI_FINISH_STATES.add(ELEMENT_COMPLETED.name());
    PI_AND_AI_FINISH_STATES.add(ELEMENT_TERMINATED.name());
  }

  private OperateElasticsearchExporterConfiguration configuration;
  private ListViewTemplate listViewTemplate;
  private Map<String, String> treePathCache;
  private Map<String, String> callActivityIdCache;
  private final RestHighLevelClient esClient;

  public ListViewFromProcessInstanceHandler(
      ListViewTemplate listViewTemplate,
      OperateElasticsearchExporterConfiguration configuration,
      Map<String, String> callActivityIdCache,
      RestHighLevelClient esClient) {
    this.listViewTemplate = listViewTemplate;
    this.configuration = configuration;
    this.esClient = esClient;
    this.callActivityIdCache = callActivityIdCache;
    if (configuration.isCalculateTreePaths()) {
      initTreePathCache();
    }
  }

  private void initTreePathCache() {
    if (treePathCache == null) {
      treePathCache = new SoftHashMap<>(configuration.getTreePathCacheSize());
    }
  }

  @Override
  public ValueType getHandledValueType() {
    return ValueType.PROCESS_INSTANCE;
  }

  @Override
  public Class<ProcessInstanceForListViewEntity> getEntityType() {
    return ProcessInstanceForListViewEntity.class;
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
  public void updateEntity(
      Record<ProcessInstanceRecordValue> record, ProcessInstanceForListViewEntity piEntity) {

    if (isProcessInstanceTerminated(record)) {
      // resolve corresponding operation

      // TODO: complete operations again; consider doing this in a separate handler?
      // operationsManager.completeOperation(null, record.getKey(), null,
      // OperationType.CANCEL_PROCESS_INSTANCE, batchRequest);
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
        .setProcessVersion(recordValue.getVersion());
    // TODO: restore process name resolving
    // .setProcessName(processCache.getProcessNameOrDefaultValue(piEntity.getProcessDefinitionKey(),
    // recordValue.getBpmnProcessId()))

    final OffsetDateTime timestamp =
        DateUtil.toOffsetDateTime(Instant.ofEpochMilli(record.getTimestamp()));
    final boolean isRootProcessInstance =
        recordValue.getParentProcessInstanceKey() == EMPTY_PARENT_PROCESS_INSTANCE_ID;
    if (intentStr.equals(ELEMENT_COMPLETED.name()) || intentStr.equals(ELEMENT_TERMINATED.name())) {

      // TODO: restore metrics
      // importBatch.incrementFinishedWiCount();
      piEntity.setEndDate(timestamp);
      if (intentStr.equals(ELEMENT_TERMINATED.name())) {
        piEntity.setState(ProcessInstanceState.CANCELED);
      } else {
        piEntity.setState(ProcessInstanceState.COMPLETED);
      }
    } else if (intentStr.equals(ELEMENT_ACTIVATING.name())) {
      piEntity.setStartDate(timestamp).setState(ProcessInstanceState.ACTIVE);
      // TODO: restore metrics
      // if(isRootProcessInstance){
      // registerStartedRootProcessInstance(piEntity, batchRequest, timestamp);
      // }
    } else {
      piEntity.setState(ProcessInstanceState.ACTIVE);
    }
    // call activity related fields
    if (!isRootProcessInstance) {
      piEntity
          .setParentProcessInstanceKey(recordValue.getParentProcessInstanceKey())
          .setParentFlowNodeInstanceKey(recordValue.getParentElementInstanceKey());
      if (piEntity.getTreePath() == null && configuration.isCalculateTreePaths()) {
        final String treePath = getTreePathForCalledProcess(recordValue);
        piEntity.setTreePath(treePath);
        treePathCache.put(String.valueOf(record.getKey()), treePath);
      }
    }
    if (piEntity.getTreePath() == null) {
      final String treePath =
          new TreePath()
              .startTreePath(ConversionUtils.toStringOrNull(recordValue.getProcessInstanceKey()))
              .toString();
      piEntity.setTreePath(treePath);
      treePathCache.put(
          ConversionUtils.toStringOrNull(recordValue.getProcessInstanceKey()), treePath);
    }
  }

  private String getTreePathForCalledProcess(final ProcessInstanceRecordValue recordValue) {
    String parentTreePath = null;

    // search in cache
    final String cachedValue =
        treePathCache.get(
            ConversionUtils.toStringOrNull(recordValue.getParentProcessInstanceKey()));
    if (cachedValue != null) {
      parentTreePath = cachedValue;
    }
    // query from ELS
    if (parentTreePath == null) {
      parentTreePath = findProcessInstanceTreePathFor(recordValue.getParentProcessInstanceKey());
    }
    if (parentTreePath != null) {
      final String flowNodeInstanceId =
          ConversionUtils.toStringOrNull(recordValue.getParentElementInstanceKey());
      final String callActivityId = getCallActivityId(flowNodeInstanceId);
      String treePath =
          new TreePath(parentTreePath)
              .appendEntries(
                  callActivityId,
                  flowNodeInstanceId,
                  ConversionUtils.toStringOrNull(recordValue.getProcessInstanceKey()))
              .toString();
      treePathCache.put(
          ConversionUtils.toStringOrNull(recordValue.getProcessInstanceKey()), treePath);
      return treePath;
    } else {
      LOGGER.warn(
          "Unable to find parent tree path for parent instance id "
              + recordValue.getParentProcessInstanceKey());
      String treePath =
          new TreePath()
              .startTreePath(ConversionUtils.toStringOrNull(recordValue.getProcessInstanceKey()))
              .toString();
      treePathCache.put(
          ConversionUtils.toStringOrNull(recordValue.getProcessInstanceKey()), treePath);
      return treePath;
    }
  }

  private String getCallActivityId(String flowNodeInstanceId) {
    String callActivityId = callActivityIdCache.get(flowNodeInstanceId);
    if (callActivityId == null) {
      callActivityId = getFlowNodeIdByFlowNodeInstanceId(flowNodeInstanceId);
      callActivityIdCache.put(flowNodeInstanceId, callActivityId);
    }
    return callActivityId;
  }

  public String getFlowNodeIdByFlowNodeInstanceId(String flowNodeInstanceId) {
    final QueryBuilder query =
        joinWithAnd(
            termQuery(JOIN_RELATION, ACTIVITIES_JOIN_RELATION),
            termQuery(io.camunda.operate.schema.templates.ListViewTemplate.ID, flowNodeInstanceId));
    final SearchRequest request =
        new SearchRequest(listViewTemplate.getFullQualifiedName())
            .source(new SearchSourceBuilder().query(query).fetchSource(ACTIVITY_ID, null));
    final SearchResponse response;
    try {
      response = esClient.search(request, RequestOptions.DEFAULT);
      if (response.getHits().getTotalHits().value != 1) {
        return null;
        //        throw new OperateRuntimeException("Flow node instance is not found: " +
        // flowNodeInstanceId);
      } else {
        return String.valueOf(response.getHits().getAt(0).getSourceAsMap().get(ACTIVITY_ID));
      }
    } catch (IOException e) {
      throw new OperateRuntimeException(
          "Error occurred when searching for flow node instance: " + flowNodeInstanceId, e);
    }
  }

  private String findProcessInstanceTreePathFor(final long processInstanceKey) {
    final SearchRequest searchRequest =
        new SearchRequest(listViewTemplate.getFullQualifiedName())
            .source(
                new SearchSourceBuilder()
                    .query(
                        termQuery(
                            io.camunda.operate.schema.templates.ListViewTemplate.KEY,
                            processInstanceKey))
                    .fetchSource(
                        io.camunda.operate.schema.templates.ListViewTemplate.TREE_PATH, null));
    try {
      final SearchHits hits = esClient.search(searchRequest, RequestOptions.DEFAULT).getHits();
      if (hits.getTotalHits().value > 0) {
        return (String)
            hits.getHits()[0]
                .getSourceAsMap()
                .get(io.camunda.operate.schema.templates.ListViewTemplate.TREE_PATH);
      }
      return null;
    } catch (IOException e) {
      final String message =
          String.format(
              "Exception occurred, while searching for process instance tree path: %s",
              e.getMessage());
      throw new OperateRuntimeException(message, e);
    }
  }

  private boolean shouldProcessProcessInstanceRecord(
      final Record<ProcessInstanceRecordValue> record) {
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
  public void flush(
      ProcessInstanceForListViewEntity piEntity, OperateElasticsearchBulkRequest batchRequest)
      throws PersistenceException {

    LOGGER.debug("Process instance for list view: id {}", piEntity.getId());

    if (canOptimizeProcessInstanceIndexing(piEntity)) {
      batchRequest.index(listViewTemplate.getFullQualifiedName(), piEntity);
    } else {
      final Map<String, Object> updateFields = new HashMap<>();
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

      batchRequest.upsert(listViewTemplate.getFullQualifiedName(), piEntity, updateFields);
    }
  }

  // TODO: put this logic in a single place
  // check if it is still needed
  private boolean canOptimizeProcessInstanceIndexing(
      final ProcessInstanceForListViewEntity entity) {
    final var startDate = entity.getStartDate();
    final var endDate = entity.getEndDate();

    if (startDate != null && endDate != null) {
      // When the activating and completed/terminated events
      // for a process instance is part of the same batch
      // to import, then we can try to optimize the request
      // by submitting an IndexRequest instead of a UpdateRequest.
      // In such case, the following is assumed:
      // * When the duration between start and end time is lower than
      // (or equal to) 2 seconds, then it can safely be assumed that
      // there was no incident in between.
      // * The 2s duration is chosen arbitrarily. It should not be
      // too short but not too long to avoid any negative side.
      final var duration = Duration.between(startDate, endDate);
      return duration.getSeconds() <= 2L;
    }

    return false;
  }

  @Override
  public String getIndexName() {
    return listViewTemplate.getFullQualifiedName();
  }
}
