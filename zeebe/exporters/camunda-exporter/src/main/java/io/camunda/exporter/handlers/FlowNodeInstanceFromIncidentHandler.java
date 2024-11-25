/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.descriptors.operate.template.FlowNodeInstanceTemplate;
import io.camunda.webapps.schema.entities.operate.FlowNodeInstanceEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FlowNodeInstanceFromIncidentHandler
    implements ExportHandler<FlowNodeInstanceEntity, IncidentRecordValue> {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(FlowNodeInstanceFromIncidentHandler.class);

  private final String indexName;

  public FlowNodeInstanceFromIncidentHandler(final String indexName) {
    this.indexName = indexName;
  }

  @Override
  public ValueType getHandledValueType() {
    return ValueType.INCIDENT;
  }

  @Override
  public Class<FlowNodeInstanceEntity> getEntityType() {
    return FlowNodeInstanceEntity.class;
  }

  @Override
  public boolean handlesRecord(final Record<IncidentRecordValue> record) {
    return true;
  }

  @Override
  public List<String> generateIds(final Record<IncidentRecordValue> record) {
    // in case of incident we update tree path in all flow node instances in call hierarchy
    final IncidentRecordValue recordValue = record.getValue();
    if (!recordValue.getElementInstancePath().isEmpty()) {
      final List<Long> callHierarchy = recordValue.getElementInstancePath().getLast();
      // we skip the 1st element, as it contains process instance key
      return callHierarchy.stream().skip(1).map(String::valueOf).toList();
    } else {
      return List.of(String.valueOf(recordValue.getElementInstanceKey()));
    }
  }

  @Override
  public FlowNodeInstanceEntity createNewEntity(final String id) {
    return new FlowNodeInstanceEntity().setId(id);
  }

  @Override
  public void updateEntity(
      final Record<IncidentRecordValue> record, final FlowNodeInstanceEntity entity) {
    final IncidentRecordValue recordValue = record.getValue();
    // Build the treePath in the format
    // <processInstanceKey>/<flowNodeInstanceKey>/.../<flowNodeInstanceKey>
    // where upper level flowNodeInstanceKeys are normally subprocess(es) or multi-instance body
    // This is internal tree path that shows position of flow node instance inside one process
    // instance.
    if (recordValue.getElementInstancePath() != null
        && !recordValue.getElementInstancePath().isEmpty()) {
      final Long currentElementInstanceKey = Long.valueOf(entity.getId());
      // call hierarchy for current process instance
      final List<Long> elementInstancePath = recordValue.getElementInstancePath().getLast();
      // we need to stop appending treePath on the current flowNodeInstanceKey
      // e.g. incident returns call hierarchy [111,222,333] and we're currently building the
      // treePath for
      // flow node with id 222, then the treePath = 111/222
      final int i = elementInstancePath.indexOf(currentElementInstanceKey);
      final List<String> treePathEntries =
          elementInstancePath.stream().limit(i + 1).map(String::valueOf).toList();
      entity.setTreePath(String.join("/", treePathEntries));
      entity.setLevel(treePathEntries.size() - 1);
    } else {
      LOGGER.warn(
          "No elementInstancePath was provided in the incident. Tree path for process instance {} will be default.",
          recordValue.getProcessInstanceKey());
    }
    // we update incidentKey only for the leaf flow node instance, same way it was before
    if (entity.getId().equals(String.valueOf(recordValue.getElementInstanceKey()))) {
      final var intent = record.getIntent();
      if (intent.equals(IncidentIntent.CREATED) || intent.equals(IncidentIntent.MIGRATED)) {
        entity.setIncidentKey(record.getKey());
      } else if (intent.equals(IncidentIntent.RESOLVED)) {
        entity.setIncidentKey(null);
      }
    }
  }

  @Override
  public void flush(final FlowNodeInstanceEntity entity, final BatchRequest batchRequest) {
    final Map<String, Object> updateFields = new HashMap<>();
    if (entity.getTreePath() != null) {
      updateFields.put(FlowNodeInstanceTemplate.TREE_PATH, entity.getTreePath());
      updateFields.put(FlowNodeInstanceTemplate.LEVEL, entity.getLevel());
    }
    updateFields.put(FlowNodeInstanceTemplate.INCIDENT_KEY, entity.getIncidentKey());
    batchRequest.upsert(indexName, entity.getId(), entity, updateFields);
  }

  @Override
  public String getIndexName() {
    return indexName;
  }
}
