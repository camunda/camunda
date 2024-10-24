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

import io.camunda.exporter.store.BatchRequest;
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

  public ListViewProcessInstanceFromIncidentHandler(final String indexName) {
    this.indexName = indexName;
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
    return record.getIntent().name().equals(CREATED.name());
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

    final List<List<Long>> elementInstancePath = record.getValue().getElementInstancePath();
    final List<Integer> callingElementPath = record.getValue().getCallingElementPath();

    final Long processInstanceKey = Long.valueOf(entity.getId());

    final TreePath treePath = new TreePath();
    for (int i = 0; i < elementInstancePath.size(); i++) {
      final List<Long> keysWithinOnePI = elementInstancePath.get(i);
      if (treePath.isEmpty()) {
        treePath.startTreePath(keysWithinOnePI.get(0));
      } else {
        treePath.appendProcessInstance(keysWithinOnePI.get(0));
      }
      if (keysWithinOnePI.get(0).equals(processInstanceKey)) {
        break;
      }
      treePath
          .appendFlowNode(String.valueOf(callingElementPath.get(i)))
          .appendFlowNodeInstance(String.valueOf(keysWithinOnePI.get(1)));
    }
    entity.setTreePath(treePath.toString());
  }

  @Override
  public void flush(
      final ProcessInstanceForListViewEntity entity, final BatchRequest batchRequest) {

    LOGGER.debug("Flow node instance for list view: id {}", entity.getId());
    final Map<String, Object> updateFields = new LinkedHashMap<>();
    updateFields.put(TREE_PATH, entity.getTreePath());

    final Long processInstanceKey = entity.getProcessInstanceKey();
    batchRequest.upsert(indexName, entity.getId(), entity, updateFields);
  }
}
