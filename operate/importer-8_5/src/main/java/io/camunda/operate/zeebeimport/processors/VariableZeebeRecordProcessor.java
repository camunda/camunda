/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.operate.zeebeimport.processors;

import static io.camunda.operate.schema.templates.TemplateDescriptor.POSITION;
import static io.camunda.operate.schema.templates.VariableTemplate.BPMN_PROCESS_ID;
import static io.camunda.operate.schema.templates.VariableTemplate.FULL_VALUE;
import static io.camunda.operate.schema.templates.VariableTemplate.IS_PREVIEW;
import static io.camunda.operate.schema.templates.VariableTemplate.PROCESS_DEFINITION_KEY;
import static io.camunda.operate.schema.templates.VariableTemplate.VALUE;
import static io.camunda.operate.zeebeimport.util.ImportUtil.tenantOrDefault;

import io.camunda.operate.entities.VariableEntity;
import io.camunda.operate.entities.listview.VariableForListViewEntity;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.templates.VariableTemplate;
import io.camunda.operate.store.BatchRequest;
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
    processVariableRecords(variablesGroupedByScopeKey, batchRequest, false);
  }

  public void processVariableRecords(
      final Map<Long, List<Record<VariableRecordValue>>> variablesGroupedByScopeKey,
      final BatchRequest batchRequest,
      final boolean concurrencyMode)
      throws PersistenceException {

    for (final var variableRecords : variablesGroupedByScopeKey.entrySet()) {

      final List<Record<VariableRecordValue>> records = variableRecords.getValue();
      final var temporaryVariableCache = new HashMap<String, VariableEntity>();
      final var shouldUpdateValue = new HashMap<String, Boolean>();

      for (final var scopedVariable : records) {
        final var recordValue = scopedVariable.getValue();
        final var varId =
            VariableForListViewEntity.getIdBy(recordValue.getScopeKey(), recordValue.getName());
        if (temporaryVariableCache.get(varId) == null) {
          temporaryVariableCache.put(varId, new VariableEntity());
          shouldUpdateValue.put(varId, false);
        }
        processVariableRecord(scopedVariable, temporaryVariableCache.get(varId), shouldUpdateValue);
      }

      for (final var variableEntity : temporaryVariableCache.values()) {

        LOGGER.debug("Variable instance: id {}", variableEntity.getId());

        final Map<String, Object> updateFields = new HashMap<>();
        updateFields.put(POSITION, variableEntity.getPosition());

        updateFields.put(PROCESS_DEFINITION_KEY, variableEntity.getProcessDefinitionKey());
        updateFields.put(BPMN_PROCESS_ID, variableEntity.getBpmnProcessId());
        if (shouldUpdateValue.get(variableEntity.getId())) {
          updateFields.put(VALUE, variableEntity.getValue());
          updateFields.put(FULL_VALUE, variableEntity.getFullValue());
          updateFields.put(IS_PREVIEW, variableEntity.getIsPreview());
        }

        if (concurrencyMode) {
          batchRequest.upsertWithScript(
              variableTemplate.getFullQualifiedName(),
              variableEntity.getId(),
              variableEntity,
              getScript(),
              updateFields);
        } else {
          batchRequest.upsert(
              variableTemplate.getFullQualifiedName(),
              variableEntity.getId(),
              variableEntity,
              updateFields);
        }
      }
    }
  }

  private String getScript() {
    return String.format(
        "if (ctx._source.%s == null || ctx._source.%s < params.%s) { "
            + "ctx._source.%s = params.%s; " // position
            + "if (params.%s != null) {"
            + "   ctx._source.%s = params.%s; " // VALUE
            + "   ctx._source.%s = params.%s; " // FULL_VALUE
            + "   ctx._source.%s = params.%s; " // IS_PREVIEW
            + "}"
            + "if (params.%s != null) {"
            + "   ctx._source.%s = params.%s; " // PROCESS_DEFINITION_KEY
            + "   ctx._source.%s = params.%s; " // BPMN_PROCESS_ID
            + "}"
            + "}",
        POSITION,
        POSITION,
        POSITION,
        POSITION,
        POSITION,
        VALUE,
        VALUE,
        VALUE,
        FULL_VALUE,
        FULL_VALUE,
        IS_PREVIEW,
        IS_PREVIEW,
        PROCESS_DEFINITION_KEY,
        PROCESS_DEFINITION_KEY,
        PROCESS_DEFINITION_KEY,
        BPMN_PROCESS_ID,
        BPMN_PROCESS_ID);
  }

  private void processVariableRecord(
      final Record<VariableRecordValue> record,
      final VariableEntity entity,
      final Map<String, Boolean> shouldUpdateValue) {
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
    if (!record.getIntent().equals(VariableIntent.MIGRATED)) {
      shouldUpdateValue.put(entity.getId(), true);
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
