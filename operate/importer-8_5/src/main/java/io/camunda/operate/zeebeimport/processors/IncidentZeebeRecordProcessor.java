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

import static io.camunda.operate.schema.templates.IncidentTemplate.FLOW_NODE_ID;
import static io.camunda.operate.schema.templates.TemplateDescriptor.POSITION;
import static io.camunda.operate.zeebeimport.util.ImportUtil.tenantOrDefault;

import io.camunda.operate.entities.*;
import io.camunda.operate.entities.post.PostImporterActionType;
import io.camunda.operate.entities.post.PostImporterQueueEntity;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.templates.IncidentTemplate;
import io.camunda.operate.schema.templates.PostImporterQueueTemplate;
import io.camunda.operate.store.BatchRequest;
import io.camunda.operate.util.ConversionUtils;
import io.camunda.operate.util.DateUtil;
import io.camunda.operate.util.OperationsManager;
import io.camunda.operate.zeebeimport.IncidentNotifier;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class IncidentZeebeRecordProcessor {

  private static final Logger LOGGER = LoggerFactory.getLogger(IncidentZeebeRecordProcessor.class);

  @Autowired private OperateProperties operateProperties;

  @Autowired private IncidentTemplate incidentTemplate;

  @Autowired private PostImporterQueueTemplate postImporterQueueTemplate;

  @Autowired private OperationsManager operationsManager;

  @Autowired private IncidentNotifier incidentNotifier;

  public void processIncidentRecord(final List<Record> records, final BatchRequest batchRequest)
      throws PersistenceException {
    final List<IncidentEntity> newIncidents = new ArrayList<>();
    for (final Record record : records) {
      processIncidentRecord(record, batchRequest, newIncidents::add);
    }
    if (operateProperties.getAlert().getWebhook() != null) {
      incidentNotifier.notifyOnIncidents(newIncidents);
    }
  }

  public void processIncidentRecord(
      final Record record,
      final BatchRequest batchRequest,
      final Consumer<IncidentEntity> newIncidentHandler)
      throws PersistenceException {
    final IncidentRecordValue recordValue = (IncidentRecordValue) record.getValue();

    persistIncident(record, recordValue, batchRequest, newIncidentHandler);

    persistPostImportQueueEntry(record, recordValue, batchRequest);
  }

  private void persistPostImportQueueEntry(
      final Record record, final IncidentRecordValue recordValue, final BatchRequest batchRequest)
      throws PersistenceException {
    String intent = record.getIntent().name();
    if (intent.equals(IncidentIntent.MIGRATED.toString())) {
      intent = IncidentIntent.CREATED.toString();
    }
    final PostImporterQueueEntity postImporterQueueEntity =
        new PostImporterQueueEntity()
            // id = incident key + intent
            .setId(String.format("%d-%s", record.getKey(), intent))
            .setActionType(PostImporterActionType.INCIDENT)
            .setIntent(intent)
            .setKey(record.getKey())
            .setPosition(record.getPosition())
            .setCreationTime(OffsetDateTime.now())
            .setPartitionId(record.getPartitionId())
            .setProcessInstanceKey(recordValue.getProcessInstanceKey());

    batchRequest.add(postImporterQueueTemplate.getFullQualifiedName(), postImporterQueueEntity);
  }

  private void persistIncident(
      final Record record,
      final IncidentRecordValue recordValue,
      final BatchRequest batchRequest,
      final Consumer<IncidentEntity> newIncidentHandler)
      throws PersistenceException {
    final String intentStr = record.getIntent().name();
    final Long incidentKey = record.getKey();
    if (intentStr.equals(IncidentIntent.RESOLVED.toString())) {

      // resolve corresponding operation
      operationsManager.completeOperation(
          null,
          recordValue.getProcessInstanceKey(),
          incidentKey,
          OperationType.RESOLVE_INCIDENT,
          batchRequest);
      // resolved incident is not updated directly, only in post importer
    } else {
      final IncidentEntity incident =
          new IncidentEntity()
              .setId(ConversionUtils.toStringOrNull(incidentKey))
              .setKey(incidentKey)
              .setPartitionId(record.getPartitionId())
              .setPosition(record.getPosition());
      if (recordValue.getJobKey() > 0) {
        incident.setJobKey(recordValue.getJobKey());
      }
      if (recordValue.getProcessInstanceKey() > 0) {
        incident.setProcessInstanceKey(recordValue.getProcessInstanceKey());
      }
      if (recordValue.getProcessDefinitionKey() > 0) {
        incident.setProcessDefinitionKey(recordValue.getProcessDefinitionKey());
      }
      incident.setBpmnProcessId(recordValue.getBpmnProcessId());
      final String errorMessage = StringUtils.trimWhitespace(recordValue.getErrorMessage());
      incident
          .setErrorMessage(errorMessage)
          .setErrorType(
              ErrorType.fromZeebeErrorType(
                  recordValue.getErrorType() == null ? null : recordValue.getErrorType().name()))
          .setFlowNodeId(recordValue.getElementId());
      if (recordValue.getElementInstanceKey() > 0) {
        incident.setFlowNodeInstanceKey(recordValue.getElementInstanceKey());
      }
      incident
          .setState(IncidentState.PENDING)
          .setCreationTime(DateUtil.toOffsetDateTime(Instant.ofEpochMilli(record.getTimestamp())))
          .setTenantId(tenantOrDefault(recordValue.getTenantId()));

      LOGGER.debug("Index incident: id {}", incident.getId());

      final Map<String, Object> updateFields = getUpdateFieldsMapByIntent(intentStr, incident);
      updateFields.put(POSITION, incident.getPosition());

      batchRequest.upsert(
          incidentTemplate.getFullQualifiedName(),
          String.valueOf(incident.getKey()),
          incident,
          updateFields);
      newIncidentHandler.accept(incident);
    }
  }

  private static Map<String, Object> getUpdateFieldsMapByIntent(
      final String intent, final IncidentEntity incidentEntity) {
    final Map<String, Object> updateFields = new HashMap<>();
    if (intent.equals(IncidentIntent.MIGRATED.name())) {
      updateFields.put(IncidentTemplate.BPMN_PROCESS_ID, incidentEntity.getBpmnProcessId());
      updateFields.put(
          IncidentTemplate.PROCESS_DEFINITION_KEY, incidentEntity.getProcessDefinitionKey());
      updateFields.put(FLOW_NODE_ID, incidentEntity.getFlowNodeId());
    }
    return updateFields;
  }
}
