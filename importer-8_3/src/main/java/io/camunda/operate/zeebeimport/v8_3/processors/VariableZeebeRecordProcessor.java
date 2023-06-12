/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.zeebeimport.v8_3.processors;

import static io.camunda.operate.util.ElasticsearchUtil.UPDATE_RETRY_COUNT;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.entities.VariableEntity;
import io.camunda.operate.entities.listview.VariableForListViewEntity;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.templates.VariableTemplate;
import io.camunda.operate.util.Tuple;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.elasticsearch.action.DocWriteRequest;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.xcontent.XContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class VariableZeebeRecordProcessor {

  private static final Logger logger = LoggerFactory.getLogger(VariableZeebeRecordProcessor.class);

  private static final Set<String> VARIABLE_STATES = new HashSet<>();

  static {
    VARIABLE_STATES.add(VariableIntent.CREATED.name());
    VARIABLE_STATES.add(VariableIntent.UPDATED.name());
  }

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private VariableTemplate variableTemplate;

  @Autowired
  private OperateProperties operateProperties;

  public void processVariableRecords(final Map<Long, List<Record<VariableRecordValue>>> variablesGroupedByScopeKey,
      final BulkRequest bulkRequest) throws PersistenceException {
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
        final DocWriteRequest<?> request;

        logger.debug("Variable instance: id {}", variableEntity.getId());

        if (initialIntent == VariableIntent.CREATED) {
          request = prepareVariableIndexRequest(variableEntity);
        } else {
          request = prepareVariableUpdateRequest(variableEntity);
        }

        bulkRequest.add(request);
      }
    }
  }

  private void processVariableRecord(Record<VariableRecordValue> record, VariableEntity entity) {
    final var recordValue = record.getValue();

    entity.setId(VariableForListViewEntity.getIdBy(recordValue.getScopeKey(), recordValue.getName()));
    entity.setKey(record.getKey());
    entity.setPartitionId(record.getPartitionId());
    entity.setScopeKey(recordValue.getScopeKey());
    entity.setProcessInstanceKey(recordValue.getProcessInstanceKey());
    entity.setProcessDefinitionKey(recordValue.getProcessDefinitionKey());
    entity.setBpmnProcessId(recordValue.getBpmnProcessId());
    entity.setName(recordValue.getName());
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

  private IndexRequest prepareVariableIndexRequest(VariableEntity entity) throws PersistenceException {
    try {
      return new IndexRequest()
          .index(variableTemplate.getFullQualifiedName())
          .id(entity.getId())
          .source(objectMapper.writeValueAsString(entity), XContentType.JSON);
    } catch (IOException e) {
      logger.error("Error preparing the query to index variable instance", e);
      throw new PersistenceException(String.format("Error preparing the query to index variable instance [%s]", entity.getId()), e);
    }
  }

  private UpdateRequest prepareVariableUpdateRequest(VariableEntity entity) throws PersistenceException {
    try {
      Map<String, Object> updateFields = new HashMap<>();
      updateFields.put(VariableTemplate.VALUE, entity.getValue());
      updateFields.put(VariableTemplate.FULL_VALUE, entity.getFullValue());
      updateFields.put(VariableTemplate.IS_PREVIEW, entity.getIsPreview());

      return new UpdateRequest().index(variableTemplate.getFullQualifiedName()).id(entity.getId())
        .upsert(objectMapper.writeValueAsString(entity), XContentType.JSON)
        .doc(updateFields)
        .retryOnConflict(UPDATE_RETRY_COUNT);

    } catch (IOException e) {
      logger.error("Error preparing the query to upsert variable instance", e);
      throw new PersistenceException(String.format("Error preparing the query to upsert variable instance [%s]", entity.getId()), e);
    }
  }

}
