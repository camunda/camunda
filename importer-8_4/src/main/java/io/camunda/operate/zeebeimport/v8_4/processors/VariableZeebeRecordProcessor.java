/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.zeebeimport.v8_4.processors;

import io.camunda.operate.entities.VariableEntity;
import io.camunda.operate.entities.listview.VariableForListViewEntity;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.templates.VariableTemplate;
import io.camunda.operate.store.BatchRequest;
import io.camunda.operate.util.Tuple;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

import static io.camunda.operate.zeebeimport.util.ImportUtil.tenantOrDefault;

@Component
public class VariableZeebeRecordProcessor {

  private static final Logger logger = LoggerFactory.getLogger(VariableZeebeRecordProcessor.class);

  @Autowired
  private VariableTemplate variableTemplate;

  @Autowired
  private OperateProperties operateProperties;

  public void processVariableRecords(final Map<Long, List<Record<VariableRecordValue>>> variablesGroupedByScopeKey,
      final BatchRequest batchRequest) throws PersistenceException {
    for (final var variableRecords : variablesGroupedByScopeKey.entrySet()) {
      final var temporaryVariableCache = new HashMap<String, Tuple<Intent, VariableEntity>>();
      final var scopedVariables = variableRecords.getValue();

      for (final var scopedVariable : scopedVariables) {
        final var intent = scopedVariable.getIntent();
        final var variableValue = scopedVariable.getValue();
        final var variableName = variableValue.getName();
        final var cachedVariable = temporaryVariableCache.computeIfAbsent(variableName, (k) -> {
          return Tuple.of(intent, new VariableEntity());
        });
        final var variableEntity = cachedVariable.getRight();
        processVariableRecord(scopedVariable, variableEntity);
      }

      for (final var cachedVariable : temporaryVariableCache.values()) {
        final var initialIntent = cachedVariable.getLeft();
        final var variableEntity = cachedVariable.getRight();

        logger.debug("Variable instance: id {}", variableEntity.getId());

        if (initialIntent == VariableIntent.CREATED) {
          batchRequest.add(variableTemplate.getFullQualifiedName(), variableEntity);
        } else if (initialIntent == VariableIntent.MIGRATED){
          Map<String, Object> updateFields = new HashMap<>();
          updateFields.put(VariableTemplate.PROCESS_DEFINITION_KEY, variableEntity.getProcessDefinitionKey());
          updateFields.put(VariableTemplate.BPMN_PROCESS_ID, variableEntity.getBpmnProcessId());
          batchRequest.upsert(variableTemplate.getFullQualifiedName(), variableEntity.getId(), variableEntity, updateFields);
        } else {
          Map<String, Object> updateFields = new HashMap<>();
          updateFields.put(VariableTemplate.VALUE, variableEntity.getValue());
          updateFields.put(VariableTemplate.FULL_VALUE, variableEntity.getFullValue());
          updateFields.put(VariableTemplate.IS_PREVIEW, variableEntity.getIsPreview());
          updateFields.put(VariableTemplate.PROCESS_DEFINITION_KEY, variableEntity.getProcessDefinitionKey());
          updateFields.put(VariableTemplate.BPMN_PROCESS_ID, variableEntity.getBpmnProcessId());
          batchRequest.upsert(variableTemplate.getFullQualifiedName(), variableEntity.getId(), variableEntity,
              updateFields);
        }
      }
    }
  }

  private void processVariableRecord(Record<VariableRecordValue> record, VariableEntity entity) {
    final var recordValue = record.getValue();

    entity.setId(VariableForListViewEntity.getIdBy(recordValue.getScopeKey(), recordValue.getName()))
        .setKey(record.getKey())
        .setPartitionId(record.getPartitionId())
        .setScopeKey(recordValue.getScopeKey())
        .setProcessInstanceKey(recordValue.getProcessInstanceKey())
        .setProcessDefinitionKey(recordValue.getProcessDefinitionKey())
        .setBpmnProcessId(recordValue.getBpmnProcessId())
        .setName(recordValue.getName())
        .setTenantId(tenantOrDefault(recordValue.getTenantId()));
    if (recordValue.getValue().length() > operateProperties.getImporter().getVariableSizeThreshold()) {
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
