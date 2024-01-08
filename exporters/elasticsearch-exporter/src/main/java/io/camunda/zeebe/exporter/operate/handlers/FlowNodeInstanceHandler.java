/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.exporter.operate.handlers;

import static io.camunda.operate.zeebeimport.util.ImportUtil.tenantOrDefault;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_ACTIVATING;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_COMPLETED;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_TERMINATED;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

import io.camunda.operate.entities.FlowNodeInstanceEntity;
import io.camunda.operate.entities.FlowNodeState;
import io.camunda.operate.entities.FlowNodeType;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.util.ConversionUtils;
import io.camunda.operate.util.DateUtil;
import io.camunda.zeebe.exporter.operate.ExportHandler;
import io.camunda.zeebe.exporter.operate.OperateElasticsearchBulkRequest;
import io.camunda.zeebe.exporter.operate.OperateElasticsearchExporterConfiguration;
import io.camunda.zeebe.exporter.operate.OperateElasticsearchSoftHashMap;
import io.camunda.zeebe.exporter.operate.schema.templates.FlowNodeInstanceTemplate;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FlowNodeInstanceHandler
    implements ExportHandler<FlowNodeInstanceEntity, ProcessInstanceRecordValue> {

  private static final Logger LOGGER = LoggerFactory.getLogger(FlowNodeInstanceHandler.class);

  private static final Set<Intent> AI_FINISH_STATES = Set.of(ELEMENT_COMPLETED, ELEMENT_TERMINATED);
  private static final Set<Intent> AI_START_STATES = Set.of(ELEMENT_ACTIVATING);

  // TODO: fix spring-wired property access
  private FlowNodeInstanceTemplate flowNodeInstanceTemplate;
  private OperateElasticsearchExporterConfiguration configuration;
  private final RestHighLevelClient esClient;
  // treePath by flowNodeInstanceKey cache
  private Map<String, String> treePathCache;
  private final Map<String, String> callActivityIdCache;

  public FlowNodeInstanceHandler(
      FlowNodeInstanceTemplate flowNodeInstanceTemplate,
      OperateElasticsearchExporterConfiguration configuration,
      Map<String, String> callActivityIdCache,
      RestHighLevelClient esClient) {
    this.flowNodeInstanceTemplate = flowNodeInstanceTemplate;
    this.configuration = configuration;
    this.esClient = esClient;
    this.callActivityIdCache = callActivityIdCache;
    if (configuration.isCalculateTreePaths()) {
      initTreePathCaches();
    }
  }

  private void initTreePathCaches() {
    if (treePathCache == null) {
      treePathCache = new OperateElasticsearchSoftHashMap<>(configuration.getTreePathCacheSize());
    }
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
    final FlowNodeInstanceEntity entity = new FlowNodeInstanceEntity();

    entity.setId(id);
    // TODO: key and partition?

    return entity;
  }

  @Override
  public void updateEntity(
      Record<ProcessInstanceRecordValue> record, FlowNodeInstanceEntity entity) {

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

    if (entity.getTreePath() == null) {

      String parentTreePath = getParentTreePath(record, recordValue);
      entity.setTreePath(
          String.join("/", parentTreePath, ConversionUtils.toStringOrNull(record.getKey())));
      entity.setLevel(parentTreePath.split("/").length);
    }

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
        if (record.getValue().getBpmnElementType().equals(BpmnElementType.CALL_ACTIVITY)) {
          callActivityIdCache.put(
              String.valueOf(record.getKey()), record.getValue().getElementId());
        }
        entity.setStartDate(DateUtil.toOffsetDateTime(Instant.ofEpochMilli(record.getTimestamp())));
        entity.setPosition(record.getPosition());
      }
    }

    entity.setType(
        FlowNodeType.fromZeebeBpmnElementType(
            recordValue.getBpmnElementType() == null
                ? null
                : recordValue.getBpmnElementType().name()));
  }

  private String getParentTreePath(
      final Record record, final ProcessInstanceRecordValue recordValue) {
    String parentTreePath;
    // if scopeKey differs from processInstanceKey, then it's inner tree level and we need to search
    // for parent 1st
    if (recordValue.getFlowScopeKey() == recordValue.getProcessInstanceKey()) {
      parentTreePath = ConversionUtils.toStringOrNull(recordValue.getProcessInstanceKey());
    } else {
      // find parent flow node instance
      parentTreePath = null;
      // search in cache
      if (treePathCache.get(ConversionUtils.toStringOrNull(recordValue.getFlowScopeKey()))
          != null) {
        parentTreePath =
            treePathCache.get(ConversionUtils.toStringOrNull(recordValue.getFlowScopeKey()));
      }
      // query from ELS
      if (parentTreePath == null) {
        parentTreePath = findParentTreePath(recordValue.getFlowScopeKey());
      }

      if (parentTreePath == null) {
        LOGGER.warn(
            "Unable to find parent tree path for flow node instance id ["
                + record.getKey()
                + "], parent flow node instance id ["
                + recordValue.getFlowScopeKey()
                + "]");
        parentTreePath = ConversionUtils.toStringOrNull(recordValue.getProcessInstanceKey());
      }
    }
    treePathCache.put(
        ConversionUtils.toStringOrNull(record.getKey()),
        String.join("/", parentTreePath, ConversionUtils.toStringOrNull(record.getKey())));
    return parentTreePath;
  }

  private String findParentTreePath(final long parentFlowNodeInstanceKey) {
    final SearchRequest searchRequest =
        new SearchRequest(flowNodeInstanceTemplate.getFullQualifiedName())
            .source(
                new SearchSourceBuilder()
                    .query(
                        termQuery(
                            io.camunda.operate.schema.templates.FlowNodeInstanceTemplate.KEY,
                            parentFlowNodeInstanceKey))
                    .fetchSource(
                        io.camunda.operate.schema.templates.FlowNodeInstanceTemplate.TREE_PATH,
                        null));
    try {
      final SearchHits hits = esClient.search(searchRequest, RequestOptions.DEFAULT).getHits();
      if (hits.getTotalHits().value > 0) {
        return (String)
            hits.getHits()[0]
                .getSourceAsMap()
                .get(io.camunda.operate.schema.templates.FlowNodeInstanceTemplate.TREE_PATH);
      } else {
        return null;
      }
    } catch (IOException e) {
      final String message =
          String.format(
              "Exception occurred, while searching for parent flow node instance processes: %s",
              e.getMessage());
      throw new OperateRuntimeException(message, e);
    }
  }

  @Override
  public void flush(FlowNodeInstanceEntity fniEntity, OperateElasticsearchBulkRequest batchRequest)
      throws PersistenceException {
    LOGGER.debug("Flow node instance: id {}", fniEntity.getId());
    if (canOptimizeFlowNodeInstanceIndexing(fniEntity)) {
      batchRequest.index(flowNodeInstanceTemplate.getFullQualifiedName(), fniEntity);
    } else {
      final Map<String, Object> updateFields = new HashMap<>();
      updateFields.put(FlowNodeInstanceTemplate.ID, fniEntity.getId());
      updateFields.put(FlowNodeInstanceTemplate.PARTITION_ID, fniEntity.getPartitionId());
      updateFields.put(FlowNodeInstanceTemplate.TYPE, fniEntity.getType());
      updateFields.put(FlowNodeInstanceTemplate.STATE, fniEntity.getState());
      updateFields.put(FlowNodeInstanceTemplate.TREE_PATH, fniEntity.getTreePath());
      updateFields.put(FlowNodeInstanceTemplate.FLOW_NODE_ID, fniEntity.getFlowNodeId());
      updateFields.put(
          FlowNodeInstanceTemplate.PROCESS_DEFINITION_KEY, fniEntity.getProcessDefinitionKey());
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
      batchRequest.upsert(flowNodeInstanceTemplate.getFullQualifiedName(), fniEntity, updateFields);
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
