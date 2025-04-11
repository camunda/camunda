/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.zeebeimport.v8_5.processors;

import static io.camunda.operate.schema.templates.TemplateDescriptor.POSITION;
import static io.camunda.operate.schema.templates.VariableTemplate.*;
import static io.camunda.operate.zeebeimport.util.ImportUtil.tenantOrDefault;

import io.camunda.operate.entities.VariableEntity;
import io.camunda.operate.entities.listview.VariableForListViewEntity;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.templates.VariableTemplate;
import io.camunda.operate.store.BatchRequest;
import io.camunda.operate.util.Tuple;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class VariableZeebeRecordProcessor {

  private static final Logger LOGGER = LoggerFactory.getLogger(VariableZeebeRecordProcessor.class);

  @Autowired private VariableTemplate variableTemplate;

  @Autowired private OperateProperties operateProperties;

  public void processVariableRecords(
      final Map<Long, List<Record<VariableRecordValue>>> variablesGroupedByScopeKey,
      final BatchRequest batchRequest)
      throws PersistenceException {
    for (final var variableRecords : variablesGroupedByScopeKey.entrySet()) {

      final List<Record<VariableRecordValue>> records = variableRecords.getValue();

      // map: variable id => { shouldUpdateValue, variable entity }
      final var temporaryVariableCache = new HashMap<String, Tuple<Boolean, VariableEntity>>();

      for (final var scopedVariable : records) {
        final var recordValue = scopedVariable.getValue();
        final var varId =
            VariableForListViewEntity.getIdBy(recordValue.getScopeKey(), recordValue.getName());

        final var variableCacheElement =
            temporaryVariableCache.computeIfAbsent(
                varId, key -> new Tuple<Boolean, VariableEntity>(false, new VariableEntity()));

        final boolean shouldUpdateValue =
            !VariableIntent.MIGRATED.equals(scopedVariable.getIntent());

        if (shouldUpdateValue) {
          // as soon as there is one non-MIGRATED record in this batch we want to update the value
          variableCacheElement.setLeft(true);
        }

        processVariableRecord(scopedVariable, variableCacheElement.getRight(), shouldUpdateValue);
      }

      for (final var variableCacheElement : temporaryVariableCache.values()) {

        final var shouldUpdateValue = variableCacheElement.getLeft();
        final var variableEntity = variableCacheElement.getRight();

        LOGGER.debug("Variable instance: id {}", variableEntity.getId());

        final Map<String, Object> updateFields = new HashMap<>();
        updateFields.put(POSITION, variableEntity.getPosition());

        updateFields.put(PROCESS_DEFINITION_KEY, variableEntity.getProcessDefinitionKey());
        updateFields.put(BPMN_PROCESS_ID, variableEntity.getBpmnProcessId());
        if (shouldUpdateValue) {
          updateFields.put(VALUE, variableEntity.getValue());
          updateFields.put(FULL_VALUE, variableEntity.getFullValue());
          updateFields.put(IS_PREVIEW, variableEntity.getIsPreview());
        }

        batchRequest.upsert(
            variableTemplate.getFullQualifiedName(),
            variableEntity.getId(),
            variableEntity,
            updateFields);
      }
    }
  }

  private void processVariableRecord(
      final Record<VariableRecordValue> record,
      final VariableEntity entity,
      final boolean shouldUpdateValue) {
    final var recordValue = record.getValue();

    entity
        .setId(VariableForListViewEntity.getIdBy(recordValue.getScopeKey(), recordValue.getName()))
        .setKey(record.getKey())
        .setPartitionId(record.getPartitionId())
        .setScopeKey(recordValue.getScopeKey())
        .setProcessInstanceKey(recordValue.getProcessInstanceKey())
        .setProcessDefinitionKey(recordValue.getProcessDefinitionKey())
        .setBpmnProcessId(recordValue.getBpmnProcessId())
        .setName(recordValue.getName())
        .setTenantId(tenantOrDefault(recordValue.getTenantId()))
        .setPosition(record.getPosition());

    if (shouldUpdateValue) {

      if (recordValue.getValue().length()
          > operateProperties.getImporter().getVariableSizeThreshold()) {
        // store preview
        entity.setValue(
            recordValue
                .getValue()
                .substring(0, operateProperties.getImporter().getVariableSizeThreshold()));
        entity.setFullValue(recordValue.getValue());
        entity.setIsPreview(true);
      } else {
        entity.setValue(recordValue.getValue());
        entity.setFullValue(null);
        entity.setIsPreview(false);
      }
    }
  }
}
