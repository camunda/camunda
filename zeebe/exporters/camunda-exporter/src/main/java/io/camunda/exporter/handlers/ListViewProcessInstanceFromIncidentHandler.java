/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import static io.camunda.webapps.schema.descriptors.operate.template.ListViewTemplate.TREE_PATH;
import static io.camunda.zeebe.protocol.record.intent.IncidentIntent.CREATED;

import io.camunda.exporter.cache.ExporterEntityCache;
import io.camunda.exporter.cache.process.CachedProcessEntity;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.exporter.utils.ProcessCacheUtil;
import io.camunda.webapps.operate.TreePath;
import io.camunda.webapps.schema.entities.operate.listview.ProcessInstanceForListViewEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Handler will update process instances with `treePath` values from the incident */
public class ListViewProcessInstanceFromIncidentHandler
    implements ExportHandler<ProcessInstanceForListViewEntity, IncidentRecordValue> {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(ListViewProcessInstanceFromIncidentHandler.class);

  private final String indexName;
  private final ExporterEntityCache<Long, CachedProcessEntity> processCache;

  public ListViewProcessInstanceFromIncidentHandler(
      final String indexName, final ExporterEntityCache<Long, CachedProcessEntity> processCache) {
    this.indexName = indexName;
    this.processCache = processCache;
  }

  @Override
  public ValueType getHandledValueType() {
    return ValueType.INCIDENT;
  }

  @Override
  public Class<ProcessInstanceForListViewEntity> getEntityType() {
    return ProcessInstanceForListViewEntity.class;
  }

  @Override
  public boolean handlesRecord(final Record<IncidentRecordValue> record) {
    return record.getIntent().equals(CREATED);
  }

  @Override
  public List<String> generateIds(final Record<IncidentRecordValue> record) {
    final List<List<Long>> elementInstancePath = record.getValue().getElementInstancePath();
    // every element is a list of keys, the first key is process instance key
    return elementInstancePath.stream().map(keys -> String.valueOf(keys.get(0))).toList();
  }

  @Override
  public ProcessInstanceForListViewEntity createNewEntity(final String id) {
    return new ProcessInstanceForListViewEntity().setId(id);
  }

  @Override
  public void updateEntity(
      final Record<IncidentRecordValue> record, final ProcessInstanceForListViewEntity entity) {

    final IncidentRecordValue value = record.getValue();
    final List<List<Long>> elementInstancePath = value.getElementInstancePath();
    final List<Integer> callingElementPath = value.getCallingElementPath();
    final List<Long> processDefinitionPath = value.getProcessDefinitionPath();

    final Long processInstanceKey = Long.valueOf(entity.getId());

    // example of how the tree path is built when current instance is on the third level of calling
    // hierarchy:
    // PI_<parentProcessInstanceKey>/FN_<parentCallActivityId>/FNI_<parentCallActivityInstanceKey>/
    // PI_<secondLevelProcessInstanceKey>/FN_<secondLevelCallActivityId>/FNI_<secondLevelCallActivityInstanceKey>/PI_<currentProcessInstanceKey>
    final TreePath treePath = new TreePath();
    for (int i = 0; i < elementInstancePath.size(); i++) {
      final List<Long> keysWithinOnePI = elementInstancePath.get(i);
      treePath.appendProcessInstance(keysWithinOnePI.get(0));
      if (keysWithinOnePI.get(0).equals(processInstanceKey)) {
        // we reached the leaf of the tree path, when we reached current processInstanceKey
        break;
      }
      final var callActivityId =
          ProcessCacheUtil.getCallActivityId(
              processCache, processDefinitionPath.get(i), callingElementPath.get(i));
      if (callActivityId.isPresent()) {
        treePath.appendFlowNode(callActivityId.get());
      } else {
        LOGGER.warn(
            "No process found in cache. TreePath won't contain proper callActivityId. processInstanceKey: {}, processDefinitionKey: {}, incidentKey: {}",
            processInstanceKey,
            processDefinitionPath.get(i),
            record.getKey());
        treePath.appendFlowNode(String.valueOf(callingElementPath.get(i)));
      }
      treePath.appendFlowNodeInstance(String.valueOf(keysWithinOnePI.get(1)));
    }
    entity.setTreePath(treePath.toString());
  }

  @Override
  public void flush(
      final ProcessInstanceForListViewEntity entity, final BatchRequest batchRequest) {

    final Map<String, Object> updateFields = new LinkedHashMap<>();
    updateFields.put(TREE_PATH, entity.getTreePath());

    batchRequest.upsert(indexName, entity.getId(), entity, updateFields);
  }

  @Override
  public String getIndexName() {
    return indexName;
  }
}
